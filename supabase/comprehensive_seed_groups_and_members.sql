-- comprehensive_seed_groups_and_members.sql
-- Cleans and seeds public tables for robust scenario testing.
-- Covers diverse group types, beneficiary counts, and loan eligibility.

-- 1. Clean relevant tables
TRUNCATE TABLE
  public.payments,
  public.platform_fees,
  public.payouts,
  public.notifications,
  public.beneficiaries,
  public.member_documents,
  public.contributions,
  public.loans,
  public.loan_repayments,
  public.members,
  public.groups
RESTART IDENTITY CASCADE;

-- 2. Ensure admin profile exists (Canonical Platform Admin)
-- ID should match the one in enforce_single_platform_admin.sql if possible,
-- but here we use a consistent UUID for testing.
INSERT INTO public.profiles (id, full_name, email, role)
VALUES ('a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'Torry Msimango', 'torrymsimango@gmail.com', 'platform_admin')
ON CONFLICT (id) DO UPDATE SET role = 'platform_admin';

-- 3. Insert diverse demo groups
INSERT INTO public.groups (
  id, name, type, province, city, township, admin_user_id, is_public, joining_fee, monthly_contribution,
  max_beneficiaries, current_members, balance, fee_status, registration_paid, is_platform_suspended, created_at
)
VALUES
  -- Burial Society: Paid, active, 10 beneficiaries allowed
  ('11111111-1111-1111-1111-111111111111', 'Zondi Burial Society', 'burial_society', 'Gauteng', 'Johannesburg', 'Soweto', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', TRUE, 100, 200,
   10, 4, 1500, 'paid', TRUE, FALSE, NOW() - INTERVAL '12 months'),

  -- Stokvel: Paid, active, 0 beneficiaries (not relevant for stokvel usually)
  ('22222222-2222-2222-2222-222222222222', 'KZN Savings Stokvel', 'stokvel', 'KZN', 'Durban', 'Umlazi', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', TRUE, 0, 500,
   0, 2, 5000, 'paid', TRUE, FALSE, NOW() - INTERVAL '8 months'),

  -- Investment Club: Pending registration payment -> MUST BE SUSPENDED
  ('33333333-3333-3333-3333-333333333333', 'Elite Investment Club', 'investment_club', 'Western Cape', 'Cape Town', 'Khayelitsha', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', TRUE, 500, 1000,
   0, 1, 0, 'suspended', FALSE, TRUE, NOW() - INTERVAL '1 month'),

  -- ROSCA: Paid, active, 2 beneficiaries
  ('44444444-4444-4444-4444-444444444444', 'Pretoria ROSCA', 'rosca', 'Gauteng', 'Pretoria', 'Mamelodi', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', TRUE, 50, 300,
   2, 1, 300, 'paid', TRUE, FALSE, NOW() - INTERVAL '3 months');

-- 4. Insert members with diverse states
-- Note: UUIDs must be hexadecimal (0-9, a-f). Using 'f' prefix for members.
INSERT INTO public.members (
    id, group_id, user_id, full_name, id_number, phone, email, notification_pref, status,
    joined_at, total_contributions, total_paid
) VALUES
  -- Eligible for Loan (Member of Zondi for 12 months, active, paid up)
  ('f1111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'Torry Msimango', '9001015800001', '0810000001', 'torrymsimango@gmail.com', 'whatsapp', 'active',
   NOW() - INTERVAL '12 months', 12, 2400.00),

  -- Ineligible (Too recent): Joined 2 months ago
  ('f1111111-1111-1111-1111-111111111112', '11111111-1111-1111-1111-111111111111', NULL, 'New Member Joe', '9201015800002', '0810000002', 'joe@test.com', 'email', 'active',
   NOW() - INTERVAL '2 months', 2, 400.00),

  -- Ineligible (Not active): Probation
  ('f1111111-1111-1111-1111-111111111113', '11111111-1111-1111-1111-111111111111', NULL, 'Probation Pete', '9301015800003', '0810000003', 'pete@test.com', 'both', 'probation',
   NOW() - INTERVAL '4 months', 4, 800.00),

  -- Ineligible (Shortfall): Only paid 5/7 months
  ('f1111111-1111-1111-1111-111111111114', '11111111-1111-1111-1111-111111111111', NULL, 'Arrears Alice', '9401015800004', '0810000004', 'alice@test.com', 'whatsapp', 'active',
   NOW() - INTERVAL '7 months', 5, 1000.00),

  -- Member of KZN Stokvel (Eligible)
  ('f2222222-2222-2222-2222-222222222221', '22222222-2222-2222-2222-222222222222', NULL, 'Stokvel Sam', '9501015800005', '0810000005', 'sam@test.com', 'email', 'active',
   NOW() - INTERVAL '8 months', 8, 4000.00);

-- 5. Add Beneficiaries (Demonstrating different allowance usage)
INSERT INTO public.beneficiaries (group_id, member_id, full_name, relationship, is_over_65)
VALUES
  ('11111111-1111-1111-1111-111111111111', 'f1111111-1111-1111-1111-111111111111', 'Msimango Spouse', 'Spouse', FALSE),
  ('11111111-1111-1111-1111-111111111111', 'f1111111-1111-1111-1111-111111111111', 'Msimango Elder', 'Parent', TRUE),
  ('44444444-4444-4444-4444-444444444444', 'f2222222-2222-2222-2222-222222222221', 'Sam Dependent', 'Child', FALSE);

-- 6. Add contributions to support loan eligibility logic (PaymentCalculator check)
-- For Alice (Shortfall)
INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, status)
VALUES
  ('f1111111-1111-1111-1111-111111111114', '11111111-1111-1111-1111-111111111111', 200, 'contribution', NOW() - INTERVAL '1 month', NULL, 'overdue'),
  ('f1111111-1111-1111-1111-111111111114', '11111111-1111-1111-1111-111111111111', 200, 'contribution', NOW(), NULL, 'due');

-- 7. Add one active loan to show in UI
INSERT INTO public.loans (member_id, group_id, amount, total_to_repay, monthly_repayment, status, start_date)
VALUES
  ('f1111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 1000, 1100, 275, 'active', (NOW() - INTERVAL '1 month')::date);

-- 8. Add platform fees for audit
INSERT INTO public.platform_fees (group_id, fee_type, amount, status, due_date, paid_at)
VALUES
  ('11111111-1111-1111-1111-111111111111', 'registration', 700, 'paid', (NOW() - INTERVAL '12 months')::text, NOW() - INTERVAL '12 months'),
  ('33333333-3333-3333-3333-333333333333', 'registration', 700, 'due', NOW()::text, NULL);
