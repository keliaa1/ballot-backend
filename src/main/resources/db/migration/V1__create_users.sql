-- V1: Create users table
CREATE TABLE users (
    id                              VARCHAR(36)  PRIMARY KEY,
    first_name                      VARCHAR(100) NOT NULL,
    last_name                       VARCHAR(100) NOT NULL,
    email                           VARCHAR(255) NOT NULL UNIQUE,
    password                        VARCHAR(255) NOT NULL,
    profile_image                   TEXT,
    email_verified                  BOOLEAN      NOT NULL DEFAULT FALSE,
    status                          VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    role                            VARCHAR(30)  NOT NULL DEFAULT 'USER',
    failed_login_attempts           INTEGER      NOT NULL DEFAULT 0,
    locked_until                    TIMESTAMP,
    email_verification_token        VARCHAR(255),
    email_verification_token_expiry TIMESTAMP,
    password_reset_token            VARCHAR(255),
    password_reset_token_expiry     TIMESTAMP,
    created_at                      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_email_verification_token ON users(email_verification_token);
CREATE INDEX idx_users_password_reset_token ON users(password_reset_token);
