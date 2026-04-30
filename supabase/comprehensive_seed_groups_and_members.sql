-- comprehensive_seed_groups_and_members.sql
-- Cleans and seeds public tables for robust scenario testing. Assumes admin1@test.com exists in auth.users.

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

-- 2. Ensure admin profile exists
INSERT INTO public.profiles (id, full_name, email, role)
VALUES ('a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'Test Admin', 'admin1@test.com', 'group_admin')
ON CONFLICT (id) DO NOTHING;

-- 3. Insert demo groups
INSERT INTO public.groups (
  id, name, type, province, city, township, admin_user_id, is_public, joining_fee, monthly_contribution, created_at,
  current_members, balance, fee_status, registration_paid
)
VALUES
  -- Scenario Group 1: 4 members, R600 balance, paid
  ('11111111-1111-1111-1111-111111111111', 'Scenario Group 1', 'burial_society', 'Gauteng', 'Johannesburg', 'Soweto', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', TRUE, 100, 200, NOW(),
   4, 600, 'paid', TRUE),
  -- Scenario Group 2: 1 member, R0 balance, due
  ('22222222-2222-2222-2222-222222222222', 'Scenario Group 2', 'burial_society', 'KZN', 'Durban', 'Umlazi', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', TRUE, 150, 250, NOW(),
   1, 0, 'due', FALSE),
  -- Scenario Group 3: 1 member, R0 balance, due
  ('33333333-3333-3333-3333-333333333333', 'Scenario Group 3', 'burial_society', 'Western Cape', 'Cape Town', 'Khayelitsha', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', TRUE, 120, 220, NOW(),
   1, 0, 'due', FALSE)
ON CONFLICT (id) DO NOTHING;

-- 4. Insert admin as a member of every group
INSERT INTO public.members (
    id, group_id, user_id, full_name, id_number, phone, email, notification_pref, status, joined_at, probation_end_at
) VALUES
  ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'Test Admin', '9001015800001', '0810000001', 'admin1@test.com', 'whatsapp', 'active', NOW() - INTERVAL '12 months', NOW() - INTERVAL '6 months'),
  ('22222222-2222-2222-2222-222222222202', '22222222-2222-2222-2222-222222222222', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'Test Admin', '9001015800001', '0810000001', 'admin1@test.com', 'whatsapp', 'active', NOW() - INTERVAL '12 months', NOW() - INTERVAL '6 months'),
  ('22222222-2222-2222-2222-222222222203', '33333333-3333-3333-3333-333333333333', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'Test Admin', '9001015800001', '0810000001', 'admin1@test.com', 'whatsapp', 'active', NOW() - INTERVAL '12 months', NOW() - INTERVAL '6 months')
ON CONFLICT (id) DO NOTHING;

-- 5. Add diverse members to Scenario Group 1
INSERT INTO public.members (
    id, group_id, user_id, full_name, id_number, phone, email, notification_pref, status, joined_at, probation_end_at
) VALUES
  ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', NULL, 'Probation Member', '9001015800002', '0810000002', 'probation@test.com', 'email', 'probation', NOW() - INTERVAL '1 month', NOW() + INTERVAL '2 months'),
  ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', NULL, 'Pending Payment', '9001015800003', '0810000003', 'pending@test.com', 'both', 'pending_payment', NOW() - INTERVAL '2 weeks', NOW() + INTERVAL '1 month'),
  ('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', NULL, 'Suspended Member', '9001015800004', '0810000004', 'suspended@test.com', 'whatsapp', 'suspended', NOW() - INTERVAL '8 months', NOW() - INTERVAL '7 months');

-- 6. Add contributions for each scenario
INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status)
VALUES
  ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 200, 'contribution', NOW() - INTERVAL '1 month', NOW() - INTERVAL '1 month', 'yoco', 'paid'),
  ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 200, 'contribution', NOW(), NULL, 'yoco', 'due'),
  ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 200, 'contribution', NOW(), NULL, 'yoco', 'due'),
  ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 100, 'joining_fee', NOW() - INTERVAL '2 weeks', NULL, 'yoco', 'due'),
  ('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', 200, 'contribution', NOW() - INTERVAL '2 months', NULL, 'yoco', 'overdue');

-- 7. Add platform fees, payouts, and payments for scenario coverage
INSERT INTO public.platform_fees (group_id, fee_type, amount, status, due_date, paid_at)
VALUES
  ('11111111-1111-1111-1111-111111111111', 'registration', 700, 'paid', (NOW() - INTERVAL '12 months')::text, NOW() - INTERVAL '12 months'),
  ('11111111-1111-1111-1111-111111111111', 'monthly', 250, 'due', (NOW() - INTERVAL '1 month')::text, NULL);

INSERT INTO public.payouts (group_id, amount, bank_name, account_no, branch_code, status, created_at)
VALUES
  ('11111111-1111-1111-1111-111111111111', 1000, 'FNB', '62123456789', '250655', 'pending', NOW() - INTERVAL '1 week');

INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at)
VALUES
  ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 200, 'contribution', 'yoco', 'tx_active_paid', 'completed', NOW() - INTERVAL '1 month'),
  ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 200, 'contribution', 'yoco', 'tx_active_due', 'pending', NOW());

-- 8. Add more demo data as needed, following this pattern.
-- This structure guarantees all seeded groups and members are visible and relationally correct, and covers all major processing scenarios.

