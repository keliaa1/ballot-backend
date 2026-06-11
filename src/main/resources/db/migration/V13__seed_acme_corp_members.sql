-- V13: Seed Acme Corp organization with 20 members for pagination/sorting demo
-- Passwords are all: Password1! (bcrypt encoded)
-- Users are set as ACTIVE + emailVerified = true so they can log in immediately

DO $$
DECLARE
    -- Acme Corp owner (primary demo user)
    v_owner_id    VARCHAR(36) := gen_random_uuid()::text;

    -- 19 more members
    v_user2_id    VARCHAR(36) := gen_random_uuid()::text;
    v_user3_id    VARCHAR(36) := gen_random_uuid()::text;
    v_user4_id    VARCHAR(36) := gen_random_uuid()::text;
    v_user5_id    VARCHAR(36) := gen_random_uuid()::text;
    v_user6_id    VARCHAR(36) := gen_random_uuid()::text;
    v_user7_id    VARCHAR(36) := gen_random_uuid()::text;
    v_user8_id    VARCHAR(36) := gen_random_uuid()::text;
    v_user9_id    VARCHAR(36) := gen_random_uuid()::text;
    v_user10_id   VARCHAR(36) := gen_random_uuid()::text;
    v_user11_id   VARCHAR(36) := gen_random_uuid()::text;
    v_user12_id   VARCHAR(36) := gen_random_uuid()::text;
    v_user13_id   VARCHAR(36) := gen_random_uuid()::text;
    v_user14_id   VARCHAR(36) := gen_random_uuid()::text;
    v_user15_id   VARCHAR(36) := gen_random_uuid()::text;
    v_user16_id   VARCHAR(36) := gen_random_uuid()::text;
    v_user17_id   VARCHAR(36) := gen_random_uuid()::text;
    v_user18_id   VARCHAR(36) := gen_random_uuid()::text;
    v_user19_id   VARCHAR(36) := gen_random_uuid()::text;
    v_user20_id   VARCHAR(36) := gen_random_uuid()::text;

    v_org_id      VARCHAR(36) := gen_random_uuid()::text;

    -- bcrypt of 'Password1!'
    v_pwd CONSTANT TEXT := '$2a$12$9KNl2S3Ff3WjJGYdxJlEf.8eCZdN0z7kWlSbTr3GVCDkZjZ1lKvze';
BEGIN

    -- Skip if org already exists
    IF EXISTS (SELECT 1 FROM organizations WHERE name = 'Acme Corp') THEN
        RAISE NOTICE 'Acme Corp seed already present, skipping.';
        RETURN;
    END IF;

    -- ── Insert 20 users ──────────────────────────────────────────────────────
    INSERT INTO users (id, first_name, last_name, email, password, email_verified, status, role, failed_login_attempts, created_at, updated_at)
    VALUES
        (v_owner_id,   'Alice',    'Johnson',   'alice.johnson@acmecorp.com',   v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '90 days', NOW()),
        (v_user2_id,   'Bob',      'Smith',     'bob.smith@acmecorp.com',       v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '88 days', NOW()),
        (v_user3_id,   'Carol',    'Williams',  'carol.williams@acmecorp.com',  v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '85 days', NOW()),
        (v_user4_id,   'David',    'Brown',     'david.brown@acmecorp.com',     v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '80 days', NOW()),
        (v_user5_id,   'Emma',     'Jones',     'emma.jones@acmecorp.com',      v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '78 days', NOW()),
        (v_user6_id,   'Frank',    'Garcia',    'frank.garcia@acmecorp.com',    v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '75 days', NOW()),
        (v_user7_id,   'Grace',    'Martinez',  'grace.martinez@acmecorp.com',  v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '70 days', NOW()),
        (v_user8_id,   'Henry',    'Davis',     'henry.davis@acmecorp.com',     v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '65 days', NOW()),
        (v_user9_id,   'Iris',     'Rodriguez', 'iris.rodriguez@acmecorp.com',  v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '60 days', NOW()),
        (v_user10_id,  'Jack',     'Wilson',    'jack.wilson@acmecorp.com',     v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '55 days', NOW()),
        (v_user11_id,  'Kate',     'Anderson',  'kate.anderson@acmecorp.com',   v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '50 days', NOW()),
        (v_user12_id,  'Liam',     'Taylor',    'liam.taylor@acmecorp.com',     v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '45 days', NOW()),
        (v_user13_id,  'Mia',      'Thomas',    'mia.thomas@acmecorp.com',      v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '40 days', NOW()),
        (v_user14_id,  'Noah',     'Jackson',   'noah.jackson@acmecorp.com',    v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '35 days', NOW()),
        (v_user15_id,  'Olivia',   'White',     'olivia.white@acmecorp.com',    v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '30 days', NOW()),
        (v_user16_id,  'Peter',    'Harris',    'peter.harris@acmecorp.com',    v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '25 days', NOW()),
        (v_user17_id,  'Quinn',    'Martin',    'quinn.martin@acmecorp.com',    v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '20 days', NOW()),
        (v_user18_id,  'Rachel',   'Thompson',  'rachel.thompson@acmecorp.com', v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '15 days', NOW()),
        (v_user19_id,  'Samuel',   'Lee',       'samuel.lee@acmecorp.com',      v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '10 days', NOW()),
        (v_user20_id,  'Tina',     'Clark',     'tina.clark@acmecorp.com',      v_pwd, TRUE, 'ACTIVE', 'USER', 0, NOW() - INTERVAL '5 days',  NOW());

    -- ── Insert Acme Corp organization ─────────────────────────────────────────
    INSERT INTO organizations (id, name, description, invite_code, join_type, logo_url, status, created_by, created_at, updated_at)
    VALUES (
        v_org_id,
        'Acme Corp',
        'The official Acme Corporation internal voting platform. Democratizing decisions since 1952.',
        'ACME-DEMO-2024',
        'PUBLIC',
        NULL,
        'ACTIVE',
        v_owner_id,
        NOW() - INTERVAL '90 days',
        NOW()
    );

    -- ── Insert memberships (1 OWNER, 3 ADMIN, 16 MEMBER) ────────────────────
    INSERT INTO memberships (id, user_id, organization_id, role, status, joined_at, created_at)
    VALUES
        -- Owner
        (gen_random_uuid()::text, v_owner_id,  v_org_id, 'OWNER',  'ACTIVE', NOW() - INTERVAL '90 days', NOW() - INTERVAL '90 days'),
        -- Admins
        (gen_random_uuid()::text, v_user2_id,  v_org_id, 'ADMIN',  'ACTIVE', NOW() - INTERVAL '87 days', NOW() - INTERVAL '87 days'),
        (gen_random_uuid()::text, v_user3_id,  v_org_id, 'ADMIN',  'ACTIVE', NOW() - INTERVAL '84 days', NOW() - INTERVAL '84 days'),
        (gen_random_uuid()::text, v_user4_id,  v_org_id, 'ADMIN',  'ACTIVE', NOW() - INTERVAL '79 days', NOW() - INTERVAL '79 days'),
        -- Members
        (gen_random_uuid()::text, v_user5_id,  v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '77 days', NOW() - INTERVAL '77 days'),
        (gen_random_uuid()::text, v_user6_id,  v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '74 days', NOW() - INTERVAL '74 days'),
        (gen_random_uuid()::text, v_user7_id,  v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '69 days', NOW() - INTERVAL '69 days'),
        (gen_random_uuid()::text, v_user8_id,  v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '64 days', NOW() - INTERVAL '64 days'),
        (gen_random_uuid()::text, v_user9_id,  v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '59 days', NOW() - INTERVAL '59 days'),
        (gen_random_uuid()::text, v_user10_id, v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '54 days', NOW() - INTERVAL '54 days'),
        (gen_random_uuid()::text, v_user11_id, v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '49 days', NOW() - INTERVAL '49 days'),
        (gen_random_uuid()::text, v_user12_id, v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '44 days', NOW() - INTERVAL '44 days'),
        (gen_random_uuid()::text, v_user13_id, v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '39 days', NOW() - INTERVAL '39 days'),
        (gen_random_uuid()::text, v_user14_id, v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '34 days', NOW() - INTERVAL '34 days'),
        (gen_random_uuid()::text, v_user15_id, v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '29 days', NOW() - INTERVAL '29 days'),
        (gen_random_uuid()::text, v_user16_id, v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '24 days', NOW() - INTERVAL '24 days'),
        (gen_random_uuid()::text, v_user17_id, v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '19 days', NOW() - INTERVAL '19 days'),
        (gen_random_uuid()::text, v_user18_id, v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '14 days', NOW() - INTERVAL '14 days'),
        (gen_random_uuid()::text, v_user19_id, v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '9 days',  NOW() - INTERVAL '9 days'),
        (gen_random_uuid()::text, v_user20_id, v_org_id, 'MEMBER', 'ACTIVE', NOW() - INTERVAL '4 days',  NOW() - INTERVAL '4 days');

    RAISE NOTICE 'Acme Corp seeded successfully with 20 members.';
END $$;
