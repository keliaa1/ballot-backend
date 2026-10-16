# Ballot — Backend

The REST API for an online voting platform built around organizations. Users register, create or join **organizations**, and organization admins create **ballots** (polls) that members vote on. Ballots open and close on their own at the times set for them, results are calculated automatically, and members get notified in the app (live, via Server-Sent Events) and by email.

**Stack:** Java 21 · Spring Boot 3.3 · Spring Security + JWT (jjwt) · Spring Data JPA / Hibernate · PostgreSQL · Flyway · Thymeleaf (email templates) · Bucket4j (rate limiting) · Cloudinary (image uploads) · springdoc-openapi (Swagger)

---

## 1. Folder layout

```
ballot-backend/
├── pom.xml                         Maven build + dependencies
├── mvnw / mvnw.cmd                 Maven wrapper (no local Maven install needed)
└── src/
    ├── main/java/com/ballot/
    │   ├── BallotApplication.java  Entry point (@SpringBootApplication, @EnableScheduling)
    │   ├── config/                 Spring configuration beans
    │   ├── security/               JWT, authentication filter, rate limiter
    │   ├── controller/             REST endpoints (HTTP layer)
    │   ├── service/                Business logic
    │   ├── repository/             Spring Data JPA interfaces (database access)
    │   ├── entity/                 JPA entities + enums (database tables)
    │   ├── dto/                    Request/response objects, grouped by feature
    │   ├── event/                  Application events used for audit logging
    │   ├── audit/                  Listener that writes audit logs
    │   ├── exception/              Custom exceptions + global error handler
    │   └── util/                   Helpers (pagination, invite codes, Cloudinary)
    ├── main/resources/
    │   ├── application.yml         Main config (reads env variables)
    │   ├── application-dev.yml     Dev profile: verbose SQL/security logging
    │   ├── application-prod.yml    Prod profile: quiet logs, optional SSL
    │   ├── db/migration/           Flyway SQL migrations V1 … V14
    │   └── templates/email/        Thymeleaf HTML email templates
    └── test/java/com/ballot/       Unit tests (JWT, Auth, Ballot, Vote, Scheduler)
```

---

## 2. Layered architecture

Every request goes through the same layers:

```
 HTTP request
     │
     ▼
 ┌──────────────────────────── Security filter chain ────────────────────────────┐
 │  RateLimitingFilter  →  JwtAuthenticationFilter  →  Spring Security rules     │
 └───────────────────────────────────────────────────────────────────────────────┘
     │
     ▼
 Controller   (controller/)   checks the DTO with @Valid, reads the current user
     │                        from @AuthenticationPrincipal, wraps the result in ApiResponse
     ▼
 Service      (service/)      business rules, permission checks, @Transactional,
     │                        publishes events, triggers notifications/scheduling
     ▼
 Repository   (repository/)   Spring Data JPA: derived queries + custom JPQL
     │
     ▼
 PostgreSQL   (schema managed by Flyway; Hibernate is set to `ddl-auto: validate`)
```

- **Controllers** stay thin. They map URLs to service calls and return `ApiResponse<T>` (`success`, `message`, `data`, `timestamp`). Paged lists use `PageResponse<T>`.
- **Services** hold all the logic. Permission checks happen here, not in annotations. For example, `requireAdminOrOwner(orgId, userId)` looks up the caller's `Membership` for that organization.
- **Entities never leave the service layer.** Each service converts entities to DTOs with a private `toResponse(...)` method.
- **Errors** are thrown as custom exceptions (`ResourceNotFoundException`, `BadRequestException`, `VotingPeriodException`, …). `GlobalExceptionHandler` (`@RestControllerAdvice`) turns them into a consistent `ErrorResponse` JSON with the correct HTTP status.

---

## 3. Domain model

```
User ──< Membership >── Organization ──< Ballot ──< BallotOption
 │        (role, status)                    │             │
 │                                          │             │
 ├──────────────< Vote >────────────────────┴─────────────┘
 │                                          │
 ├──< Notification                          └──< Result (vote count + % per option)
 ├──< Token (refresh / blacklisted JWTs)
 ├──< ChatMessage >── Organization
 └──< AuditLog
```

| Entity | Purpose | Key fields / enums |
|---|---|---|
| `User` | An account. Implements Spring's `UserDetails`, so it is used directly for authentication. | `email` (unique), BCrypt `password`, `Role` (USER, OWNER, ADMIN, SUPER_ADMIN), `UserStatus`, failed-login counter, `lockedUntil`, email-verification / password-reset tokens |
| `Organization` | A group that holds ballots | unique `name`, `inviteCode`, `JoinType` (PUBLIC, APPROVAL_REQUIRED, INVITE_ONLY), `logoUrl`, `OrganizationStatus` (soft delete) |
| `Membership` | Links a user to an organization | `MembershipRole` (OWNER, ADMIN, MEMBER), `MembershipStatus` (ACTIVE, PENDING, LEFT, …). Unique on (user, org) |
| `Ballot` | A poll inside an organization | `startDate`, `endDate`, `anonymous`, `multipleChoice`, `BallotStatus` (DRAFT → ACTIVE → CLOSED, or CANCELLED) |
| `BallotOption` | One choice on a ballot | `optionText`, `displayOrder` |
| `Vote` | One selected option by one user | unique on (ballot, user, option). A multiple-choice vote is stored as several rows |
| `Result` | Totals saved once a ballot closes | `voteCount`, `percentage` |
| `Notification` | An in-app notification | `NotificationType`, `NotificationStatus` (UNREAD/READ) |
| `Token` | Server-side token records | `TokenType`: REFRESH (active refresh tokens) or BLACKLISTED (logged-out access tokens) |
| `ChatMessage` | A message in an organization's chat | org, user, content |
| `AuditLog` | Security/audit trail | userId, action, entity type/id, IP, user agent |

All primary keys are UUID strings (`VARCHAR(36)`), generated by Hibernate.

### Database migrations (Flyway)

The schema is created only by the SQL files in `src/main/resources/db/migration`. Flyway runs them in order at startup, and Hibernate then checks that the entities match the tables (`validate`), so **a schema change always needs a new `V15__...sql` file**. Never edit a migration that has already run.

| Migration | What it does |
|---|---|
| V1–V9 | users, organizations, memberships, ballots, ballot_options, votes, results, notifications, tokens |
| V10 | audit_logs |
| V11 | Changes the vote unique constraint from (ballot, user) to (ballot, user, option) so multiple-choice voting works |
| V12 | chat_messages |
| V13–V14 | Seed data (example "Acme Corp" members) |

The database also enforces rules of its own, for example `CHECK (end_date > start_date)` on ballots and the unique constraints listed above.

---

## 4. Security and authentication

Configured in `config/SecurityConfig.java` and the `security/` package.

**Stateless JWT.** There is no HTTP session and CSRF is disabled. Each request carries `Authorization: Bearer <accessToken>`.

### Filter chain (runs on every request)

1. **`RateLimitingFilter`** only applies to `POST /api/v1/auth/login`. It keeps a Bucket4j token bucket per client IP (from `X-Forwarded-For` or the remote address). The default is 5 attempts per 15 minutes; after that it returns HTTP 429.
2. **`JwtAuthenticationFilter`**
   - Reads the token from the `Authorization` header, or from a `?token=` query parameter. The query parameter exists because the browser's `EventSource` (used for SSE) cannot send headers.
   - Rejects tokens saved in the `tokens` table as `BLACKLISTED` (tokens that were logged out).
   - Checks the signature and expiry with `JwtService` (HMAC-SHA256), loads the `User`, and puts it into the `SecurityContext`.
3. **Authorization rules**
   - Public: register, login, refresh, verify-email, resend-verification, forgot/reset-password, Swagger, and `GET /api/v1/organizations` (browse organizations).
   - `/api/v1/admin/**` requires `ROLE_SUPER_ADMIN`.
   - Everything else requires authentication. Rules specific to an organization (owner/admin/member) are checked in the services.

Security headers are also set: CSP, `X-Frame-Options: DENY`, HSTS, and Referrer-Policy. CORS origins come from `CORS_ORIGINS` (see `CorsConfig`).

### Auth flows (`AuthService` + `TokenService`)

| Flow | How it works |
|---|---|
| **Register** | Saves the email in lowercase, hashes the password with BCrypt (strength 12), and creates the user as `PENDING_VERIFICATION` with a 24-hour verification token. Sends the verification email asynchronously and returns tokens. |
| **Verify email** | `GET /auth/verify-email?token=…` sets `emailVerified = true` and `status = ACTIVE`. |
| **Login** | Refuses locked accounts, unverified emails and disabled accounts, then authenticates with `AuthenticationManager`. **5 failed attempts lock the account for 30 minutes.** A successful login resets the counter, publishes `UserLoginEvent`, and returns a new access + refresh token pair. |
| **Tokens** | Access token lasts **15 minutes**. Refresh token lasts **7 days** and is saved in the `tokens` table. |
| **Refresh** | Checks that the refresh token exists in the DB and has not been revoked, then **rotates** it: the old one is revoked and a new pair is issued. |
| **Logout** | Saves the access token as `BLACKLISTED` and revokes the refresh token. |
| **Forgot / reset password** | Always returns success, so nobody can find out which emails are registered. The emailed reset token lasts 15 minutes. |

---

## 5. Feature modules

### Organizations and memberships (`OrganizationService`, `MembershipService`)

- **Creating** an organization generates a unique invite code (`InviteCodeGenerator`) and makes the creator a member with role **OWNER**.
- **Joining** depends on the organization's `JoinType`:
  - `PUBLIC`: the user becomes an ACTIVE member immediately.
  - `INVITE_ONLY`: a valid invite code is required, then the user becomes ACTIVE.
  - `APPROVAL_REQUIRED`: the membership is created as PENDING. An admin approves or rejects it at `/members/requests/{id}/approve|reject`, and the user gets a notification either way.
- **Roles:** OWNER (everything, including deleting the organization and uploading the logo), ADMIN (manage ballots and members), MEMBER (vote, view, chat). Owners cannot leave their own organization.
- **Deleting** an organization is a soft delete (`status = DELETED`). Leaving sets the membership to `LEFT`.
- **Logos** go to Cloudinary (`util/CloudinaryService`). The previous logo is deleted from Cloudinary when it is replaced.

### Ballots (`BallotService`)

- Only organization **OWNER/ADMIN** can create, edit, delete or close a ballot. Only **active members** can view one.
- If you create a ballot with `startImmediately = true`, it starts out **ACTIVE**. Otherwise it is a **DRAFT** with a future `startDate`.
- Only DRAFT ballots can be **edited or deleted**. Editing options replaces them all, and deleting sets the status to `CANCELLED`.
- **Closing manually** (`POST /ballots/{id}/close`) sets the status to CLOSED and `endDate` to now, calculates the results right away, and notifies members.
- Every create or update calls `DynamicBallotScheduler.scheduleAll(ballot)` (see section 6).

### Voting (`VoteService`)

`castVote` checks these rules in order:

1. The voter is an **active member** of the ballot's organization.
2. The ballot is **ACTIVE** and the current time is between `startDate` and `endDate`.
3. The user **has not voted** on this ballot yet.
4. Only one option is selected, unless the ballot allows multiple choice.
5. Every selected option belongs to this ballot.

The service saves one `Vote` row per selected option and publishes a `VoteCastEvent` for the audit log. On top of these checks, the database's unique constraint stops the same option from being stored twice for one user. On anonymous ballots, `userId` is removed from the response. `GET /ballots/{id}/my-vote` always shows the user their own vote.

### Results (`ResultService`)

- `computeResults(ballot)` is **idempotent**: if results already exist for the ballot, it does nothing. Otherwise it counts votes per option with a grouped query and saves a `Result` row per option with its count and percentage (2 decimal places).
- `GET /results/{ballotId}` only works once the ballot is **CLOSED** and only for active members. Results are sorted by vote count, highest first.

### Notifications (`NotificationService`, `SseService`)

There are two delivery channels:

1. **In-app notifications.** Saved in the `notifications` table, then pushed live to the user with **Server-Sent Events**. The client connects to `GET /api/v1/notifications/stream?token=<jwt>`. `SseService` keeps one `SseEmitter` per user in an in-memory map (1-hour timeout) and sends `notification` and `chat_message` events.
2. **Email.** Built from Thymeleaf templates in `templates/email/` (verify-email, password-reset, ballot-started, ballot-ended, org-invitation) and sent with `JavaMailSender`.

All notification methods are `@Async`, so sending email never slows down the request that triggered it.

### Organization chat (`ChatService`)

`GET/POST /api/v1/organizations/{orgId}/chat`. Messages are saved in the database, and each new one is pushed to every active member of the organization over the same SSE connection (`chat_message` event).

### Audit logging (`event/` + `audit/`)

Services publish Spring application events: `UserRegisteredEvent`, `UserLoginEvent`, `OrgCreatedEvent`, `BallotCreatedEvent`, `VoteCastEvent` and `RoleChangedEvent`. All of them extend `BallotAuditEvent`. `AuditEventListener` handles them with `@Async @EventListener` and writes an `AuditLog` row (user, action, entity, IP, user agent). The business code doesn't need to know about auditing, and audit writes never slow down a request.

---

## 6. Scheduling: how ballots open and close on their own

There are two schedulers that work together.

### a) `DynamicBallotScheduler`: exact-time tasks

When a ballot is created or updated, `scheduleAll(ballot)` cancels any tasks it already has and schedules new ones on a `ThreadPoolTaskScheduler` (`SchedulerConfig`, 4 threads) **at the exact time** each one should run:

| Task | Runs at | What it does |
|---|---|---|
| `ACTIVATE` | `startDate` | DRAFT → ACTIVE, notifies all members |
| `REMINDER_24H` | `endDate − 24h` | Notifies members who **haven't voted** yet |
| `REMINDER_1H` | `endDate − 1h` | Same, 1 hour before the end |
| `CLOSE` | `endDate` | ACTIVE → CLOSED, calculates results |
| `RESULTS` | `endDate + 2 min` | Makes sure results exist (idempotent) and notifies members that results are out |

Pending tasks are kept in in-memory maps (`ballotId:TASK` → future + metadata), so they can be cancelled when a ballot is edited or deleted. `SchedulerStatusController` (`GET /api/v1/scheduler/status[/{ballotId}]`) exposes them for the UI's timeline.

`BallotService` and `DynamicBallotScheduler` depend on each other. The cycle is broken with `@Lazy` on the scheduler's `BallotService` parameter.

### b) `BallotSchedulerService`: polling backup and maintenance jobs (`@Scheduled`)

| Job | Schedule | Purpose |
|---|---|---|
| `activatePendingBallots` | every 60 s (fixedDelay) | Activates any DRAFT ballot whose start time has passed |
| `closeExpiredBallots` | every 60 s (fixedDelay) | Closes overdue ACTIVE ballots and calculates their results |
| `cleanupExpiredTokens` | daily 02:00 (cron) | Deletes expired refresh/blacklisted tokens |
| `purgeOldReadNotifications` | daily 03:00 (cron) | Deletes READ notifications older than 90 days |
| `logSchedulerHealth` | hourly (fixedRate) | Writes a heartbeat to the log |

**Why both?** The dynamic tasks live only in memory and are **lost when the app restarts**. The 60-second polling jobs act as a safety net: after a restart, any ballot that should have opened or closed in the meantime is picked up within a minute. Both paths check the ballot's current status first, and `computeResults` is idempotent, so a ballot handled by both is not processed twice.

### Thread pools

- `taskExecutor` (`AsyncConfig`, 4–16 threads): `@Async` work such as emails and audit logs.
- `taskScheduler` (`SchedulerConfig`, 4 threads): timed tasks.

They are kept separate so that slow emails can't hold up a ballot that needs to close on time.

---

## 7. API overview

Interactive docs (with a JWT "Authorize" button): **http://localhost:8080/swagger-ui.html**. The raw spec is at `/api-docs`.

| Base path | Endpoints |
|---|---|
| `/api/v1/auth` | `POST register, login, logout, refresh, resend-verification, forgot-password, reset-password` · `GET verify-email, me` |
| `/api/v1/organizations` | `GET /` (public browse, search) · `GET /my` · `GET/PUT/DELETE /{id}` · `POST /` · `POST /{id}/logo` · `POST /join` · `POST /{id}/leave` · `GET /{id}/members`, `/{id}/members/pending` · `POST /{id}/members/requests/{mid}/approve\|reject` · `PUT /{id}/members/{uid}/role` · `DELETE /{id}/members/{uid}` |
| `/api/v1/organizations/{orgId}/chat` | `GET` (paged history) · `POST` (send) |
| `/api/v1/ballots` | `GET /active`, `/my`, `/org/{orgId}`, `/{id}`, `/{id}/my-vote` · `POST /` · `PUT/DELETE /{id}` · `POST /{id}/vote` · `POST /{id}/close` |
| `/api/v1/results` | `GET /{ballotId}` |
| `/api/v1/notifications` | `GET /stream` (SSE) · `GET /` · `GET /unread-count` · `PUT /{id}/read` · `PUT /read-all` |
| `/api/v1/scheduler` | `GET /status` · `GET /status/{ballotId}` |

List endpoints accept `page`, `size`, `sortBy` and `direction`. `PageUtil.of(...)` turns these into a Spring `Pageable`.

### Example: what happens when a user votes

```
POST /api/v1/ballots/{id}/vote   { "selectedOptionIds": ["..."] }
  → RateLimitingFilter (skipped, not /login)
  → JwtAuthenticationFilter: valid token, not blacklisted → SecurityContext = User
  → BallotController.castVote(@Valid CastVoteRequest, @AuthenticationPrincipal User)
  → VoteService.castVote  [@Transactional]
       membership? ACTIVE status? inside time window? already voted? options valid?
       → VoteRepository.save(...) per option
       → publish VoteCastEvent ──async──> AuditEventListener → audit_logs
  ← 200 ApiResponse<VoteResponse>
```

---

## 8. Configuration and running locally

All settings are in `application.yml` and can be overridden with environment variables:

| Variable | Default | Used for |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/ballotjava`, `postgres` | PostgreSQL connection |
| `JWT_SECRET` | — | Base64-encoded HMAC key for signing JWTs |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Gmail SMTP | Sending email |
| `APP_BASE_URL` | `http://localhost:8080` | Links inside emails |
| `CORS_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Allowed frontend origins |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | — | Logo uploads |
| `PORT` | `8080` | HTTP port |
| `SSL_ENABLED`, `SSL_KEY_STORE`, … | off | HTTPS in the prod profile |

> `application.yml` has fallback defaults for some secrets. Always set real values through environment variables in production.

```bash
# 1. Create the database
createdb ballotjava

# 2. Run (Flyway creates the schema on first start)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev      # Windows: mvnw.cmd ...

# 3. Tests
./mvnw test

# 4. Build a jar
./mvnw clean package && java -jar target/ballot-1.0.0.jar --spring.profiles.active=prod
```

---

## 9. Things to know / limitations

- **Single instance only.** Several pieces of state live in the app's memory: SSE connections, rate-limit buckets and dynamic scheduler tasks. If you run several instances behind a load balancer, users may miss live events, the rate limit is counted separately per instance, and timed tasks are not shared. Scaling out would need Redis or another message broker (or a scheduler like ShedLock/Quartz). The 60-second polling jobs keep ballot status correct either way.
- **One SSE connection per user.** If a user opens a second tab, the new connection replaces the first.
- **Concurrent votes.** The "already voted" check happens before the insert. Two requests from the same user at the same moment with *different* options could both pass the check, because the unique constraint covers (ballot, user, option), not (ballot, user). The comment in `VoteService` mentions optimistic locking, but no `@Version` field exists.
- **Time zones.** Dates are `LocalDateTime` in the server's time zone. Keep the server and the database in the same zone.
