-- new_seed_groups_and_members_public_only.sql
-- This script seeds only public.* tables and assumes admin1@test.com already exists in auth.users and profiles.
-- It guarantees:
--   - At least 3 public groups
--   - The admin is a member of every group
--   - At least one group has a second member
--   - All foreign keys are valid

-- 1. Clean relevant tables
TRUNCATE TABLE
  public.payments,
  public.platform_fees,
  public.payouts,
  public.notifications,
  public.beneficiaries,
  public.member_documents,
  public.contributions,
  public.members,
  public.groups
RESTART IDENTITY CASCADE;

-- 2. Ensure admin profile exists (assume user already in auth.users)
INSERT INTO public.profiles (id, full_name, email, role)
VALUES ('a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'Test Admin', 'admin1@test.com', 'group_admin')
ON CONFLICT (id) DO NOTHING;

-- 3. Insert demo groups
INSERT INTO public.groups (id, name, type, province, city, township, admin_user_id, is_public, joining_fee, monthly_contribution, created_at)
VALUES
  ('11111111-1111-1111-1111-111111111111', 'Demo Public Group', 'burial_society', 'Gauteng', 'Johannesburg', 'Soweto', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', TRUE, 100, 200, NOW()),
  ('22222222-2222-2222-2222-222222222222', 'Demo Second Group', 'burial_society', 'KZN', 'Durban', 'Umlazi', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', TRUE, 150, 250, NOW()),
  ('33333333-3333-3333-3333-333333333333', 'Demo Third Group', 'burial_society', 'Western Cape', 'Cape Town', 'Khayelitsha', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', TRUE, 120, 220, NOW())
ON CONFLICT (id) DO NOTHING;

-- 4. Insert admin as a member of every group
INSERT INTO public.members (
    id, group_id, user_id, full_name, id_number, phone, email, notification_pref, status, joined_at, probation_end_at
) VALUES
  ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'Test Admin', '9001015800001', '0810000001', 'admin1@test.com', 'whatsapp', 'active', NOW() - INTERVAL '12 months', NOW() - INTERVAL '6 months'),
  ('22222222-2222-2222-2222-222222222202', '22222222-2222-2222-2222-222222222222', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'Test Admin', '9001015800001', '0810000001', 'admin1@test.com', 'whatsapp', 'active', NOW() - INTERVAL '12 months', NOW() - INTERVAL '6 months'),
  ('22222222-2222-2222-2222-222222222203', '33333333-3333-3333-3333-333333333333', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'Test Admin', '9001015800001', '0810000001', 'admin1@test.com', 'whatsapp', 'active', NOW() - INTERVAL '12 months', NOW() - INTERVAL '6 months')
ON CONFLICT (id) DO NOTHING;

-- 5. Add a second member to the first group
INSERT INTO public.members (
    id, group_id, user_id, full_name, id_number, phone, email, notification_pref, status, joined_at, probation_end_at
) VALUES (
    '44444444-4444-4444-4444-444444444444',
    '11111111-1111-1111-1111-111111111111',
    NULL,
    'Member Two',
    '9001015800002',
    '0810000002',
    'member2@test.com',
    'email',
    'active',
    NOW() - INTERVAL '6 months',
    NOW() + INTERVAL '6 months'
) ON CONFLICT (id) DO NOTHING;

-- 6. Add more demo data as needed, following this pattern.
-- This structure guarantees all seeded groups and members are visible and relationally correct.

