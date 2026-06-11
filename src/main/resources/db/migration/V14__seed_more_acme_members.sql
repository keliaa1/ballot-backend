-- V14: Add more demo members to existing Acme Corp for pagination testing

DO $$
DECLARE
    v_org_id      VARCHAR(36);
    
    -- 19 new members
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

    -- bcrypt of 'Password1!'
    v_pwd CONSTANT TEXT := '$2a$12$9KNl2S3Ff3WjJGYdxJlEf.8eCZdN0z7kWlSbTr3GVCDkZjZ1lKvze';
BEGIN

    -- Get the ID of the existing Acme Corp organization
    SELECT id INTO v_org_id FROM organizations WHERE name = 'Acme Corp' LIMIT 1;
    
    IF v_org_id IS NULL THEN
        RAISE NOTICE 'Acme Corp does not exist. Skipping.';
        RETURN;
    END IF;

    -- Skip if we already seeded Bob Smith (means script already ran or V13 actually worked)
    IF EXISTS (SELECT 1 FROM users WHERE email = 'bob.smith@acmecorp.com') THEN
        RAISE NOTICE 'Demo users already exist. Skipping.';
        RETURN;
    END IF;

    -- ── Insert 19 users ──────────────────────────────────────────────────────
    INSERT INTO users (id, first_name, last_name, email, password, email_verified, status, role, failed_login_attempts, created_at, updated_at)
    VALUES
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

    -- ── Insert memberships into existing Acme Corp ──────────────────────────
    INSERT INTO memberships (id, user_id, organization_id, role, status, joined_at, created_at)
    VALUES
        (gen_random_uuid()::text, v_user2_id,  v_org_id, 'ADMIN',  'ACTIVE', NOW() - INTERVAL '87 days', NOW() - INTERVAL '87 days'),
        (gen_random_uuid()::text, v_user3_id,  v_org_id, 'ADMIN',  'ACTIVE', NOW() - INTERVAL '84 days', NOW() - INTERVAL '84 days'),
        (gen_random_uuid()::text, v_user4_id,  v_org_id, 'ADMIN',  'ACTIVE', NOW() - INTERVAL '79 days', NOW() - INTERVAL '79 days'),
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

    RAISE NOTICE 'Added 19 demo members to existing Acme Corp organization.';
END $$;
