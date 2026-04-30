-- new_seed_groups_and_members.sql
-- This script guarantees that groups and members are always visible and relationally correct for app testing.
-- It ensures:
--   - At least one public group exists
--   - Each group has a valid admin (user in auth.users and profiles)
--   - Each group has at least one member (admin is also a member)
--   - All foreign keys are respected
--   - Test user (admin1@test.com) is always a member of all groups

-- 1. Clean relevant tables (do not truncate auth.users)
TRUNCATE TABLE
  public.payments,
  public.platform_fees,
  public.payouts,
  public.notifications,
  public.beneficiaries,
  public.member_documents,
  public.contributions,
  public.members,
  public.groups,
  public.profiles
RESTART IDENTITY CASCADE;

-- 2. Ensure test admin user exists
INSERT INTO auth.users (
    id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token
) VALUES (
    'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1',
    'authenticated',
    'authenticated',
    'admin1@test.com',
    extensions.crypt('password123', extensions.gen_salt('bf')),
    NOW(),
    '{"provider": "email", "providers": ["email"]}',
    jsonb_build_object('full_name', 'Test Admin', 'role', 'group_admin'),
    NOW(),
    NOW(),
    ''
) ON CONFLICT (id) DO NOTHING;

INSERT INTO public.profiles (id, full_name, email, role)
VALUES ('a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'Test Admin', 'admin1@test.com', 'group_admin')
ON CONFLICT (id) DO NOTHING;

-- 3. Insert at least one public group with admin
INSERT INTO public.groups (id, name, type, province, city, township, admin_user_id, is_public, joining_fee, monthly_contribution, created_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'Demo Public Group', 'burial_society', 'Gauteng', 'Johannesburg', 'Soweto', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', TRUE, 100, 200, NOW())
ON CONFLICT (id) DO NOTHING;

-- 4. Ensure admin is a member of the group
INSERT INTO public.members (
    id, group_id, user_id, full_name, id_number, phone, email, notification_pref, status, joined_at, probation_end_at
) VALUES (
    '22222222-2222-2222-2222-222222222201',
    '11111111-1111-1111-1111-111111111111',
    'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1',
    'Test Admin',
    '9001015800001',
    '0810000001',
    'admin1@test.com',
    'whatsapp',
    'active',
    NOW() - INTERVAL '12 months',
    NOW() - INTERVAL '6 months'
) ON CONFLICT (id) DO NOTHING;

-- 5. Optionally, add more groups and members as needed, following the same pattern.
-- Example: Add a second group and a second member
INSERT INTO auth.users (
    id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token
) VALUES (
    'b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2',
    'authenticated',
    'authenticated',
    'member2@test.com',
    extensions.crypt('password123', extensions.gen_salt('bf')),
    NOW(),
    '{"provider": "email", "providers": ["email"]}',
    jsonb_build_object('full_name', 'Member Two', 'role', 'member'),
    NOW(),
    NOW(),
    ''
) ON CONFLICT (id) DO NOTHING;

INSERT INTO public.profiles (id, full_name, email, role)
VALUES ('b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2', 'Member Two', 'member2@test.com', 'member')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.groups (id, name, type, province, city, township, admin_user_id, is_public, joining_fee, monthly_contribution, created_at)
VALUES ('22222222-2222-2222-2222-222222222222', 'Demo Second Group', 'burial_society', 'KZN', 'Durban', 'Umlazi', 'b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2', TRUE, 150, 250, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.members (
    id, group_id, user_id, full_name, id_number, phone, email, notification_pref, status, joined_at, probation_end_at
) VALUES (
    '33333333-3333-3333-3333-333333333333',
    '22222222-2222-2222-2222-222222222222',
    'b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2',
    'Member Two',
    '9001015800002',
    '0810000002',
    'member2@test.com',
    'email',
    'active',
    NOW() - INTERVAL '6 months',
    NOW() + INTERVAL '6 months'
) ON CONFLICT (id) DO NOTHING;

-- 6. Add admin as a member of the second group for universal visibility
INSERT INTO public.members (
    id, group_id, user_id, full_name, id_number, phone, email, notification_pref, status, joined_at, probation_end_at
) VALUES (
    gen_random_uuid(),
    '22222222-2222-2222-2222-222222222222',
    'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1',
    'Test Admin',
    '9001015800001',
    '0810000001',
    'admin1@test.com',
    'whatsapp',
    'active',
    NOW() - INTERVAL '12 months',
    NOW() - INTERVAL '6 months'
) ON CONFLICT (id) DO NOTHING;

-- 7. Add more demo data as needed, following this pattern.
-- This structure guarantees all seeded groups and members are visible and relationally correct.

