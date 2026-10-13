-- SanibonaniSave: Minimal Seed and RLS Enablement Script
-- This script ensures at least one visible group and enables dev RLS policies.

-- 1. Ensure at least one group is visible in the app
UPDATE public.groups
SET is_public = true,
    registration_paid = true
WHERE id = (
    SELECT id FROM public.groups
    ORDER BY created_at ASC
    LIMIT 1
);

-- 2. Insert a new visible group if none exists
INSERT INTO public.groups (
  id, name, type, province, city, township, description, logo_emoji,
  joining_fee, monthly_contribution, late_fee, late_fee_grace_days,
  probation_months, payment_due_day, max_members, is_public, allow_partial_payment,
  auto_suspend_after, bank_name, account_number, branch_code, account_type,
  gateway_public_key, balance, admin_user_id, fee_status, registration_paid,
  is_platform_suspended, constitution_status, latitude, longitude,
  max_beneficiaries, beneficiary_increase_pct, goal_amount, period_months,
  loan_interest_rate, loan_max_amount, loan_max_months, current_members,
  created_at, updated_at
)
SELECT
  gen_random_uuid(), 'Test Group', 'stokvel', 'Gauteng', 'Johannesburg', 'Soweto', 'A test group', '💰',
  100.0, 200.0, 10.0, 5,
  3, 1, 50, true, true,
  2, 'FNB', '1234567890', '250655', 'Savings',
  null, 1000.0, 'admin-user-uuid', 'active', true,
  false, 'complete', -26.2041, 28.0473,
  5, 10.0, 5000.0, 12,
  5.0, 2000.0, 6, 10,
  now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM public.groups WHERE is_public = true AND registration_paid = true
);

-- 3. Enable RLS and allow all for dev (repeat for all relevant tables)
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
CREATE POLICY IF NOT EXISTS "Allow all for dev" ON public.groups FOR ALL USING (true);

ALTER TABLE public.members ENABLE ROW LEVEL SECURITY;
CREATE POLICY IF NOT EXISTS "Allow all for dev" ON public.members FOR ALL USING (true);

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
CREATE POLICY IF NOT EXISTS "Allow all for dev" ON public.profiles FOR ALL USING (true);

ALTER TABLE public.contributions ENABLE ROW LEVEL SECURITY;
CREATE POLICY IF NOT EXISTS "Allow all for dev" ON public.contributions FOR ALL USING (true);

ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;
CREATE POLICY IF NOT EXISTS "Allow all for dev" ON public.payments FOR ALL USING (true);

ALTER TABLE public.group_ledger ENABLE ROW LEVEL SECURITY;
CREATE POLICY IF NOT EXISTS "Allow all for dev" ON public.group_ledger FOR ALL USING (true);

ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
CREATE POLICY IF NOT EXISTS "Allow all for dev" ON public.audit_logs FOR ALL USING (true);

-- 4. Minimal seed for related tables if a visible group exists
DO $$
DECLARE v_group_id UUID;
BEGIN
    SELECT id INTO v_group_id FROM public.groups WHERE is_public = true AND registration_paid = true ORDER BY created_at ASC LIMIT 1;
    IF v_group_id IS NOT NULL THEN
        INSERT INTO public.policies (id, group_id, name, description, required_amount, status, created_at, updated_at)
        VALUES (gen_random_uuid(), v_group_id, 'SEEDMIN_POLICY', 'Minimal policy for visible group', 1000.00, 'active', now(), now())
        ON CONFLICT (name) DO NOTHING;

        INSERT INTO public.group_polls (id, group_id, created_by_member_id, title, description, status, starts_at, ends_at, created_at, updated_at)
        VALUES (gen_random_uuid(), v_group_id, NULL, 'SEEDMIN_POLL', 'Minimal poll for visible group', 'open', now(), now() + interval '10 days', now(), now())
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO public.beneficiaries (id, group_id, member_id, full_name, id_number, relationship, date_of_birth, is_over_65, document_status, created_at, updated_at)
        VALUES (gen_random_uuid(), v_group_id, NULL, 'SEEDMIN_BENEFICIARY', '9900000000001', 'child', '2010-01-01', FALSE, 'verified', now(), now())
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO public.member_documents (id, member_id, group_id, label, document_url, document_type, status, created_at, updated_at)
        VALUES (gen_random_uuid(), NULL, v_group_id, 'SEEDMIN_DOC', 'https://example.com/doc.pdf', 'id_card', 'verified', now(), now())
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO public.notifications (id, group_id, member_id, message, channel, trigger_event, created_at)
        VALUES (gen_random_uuid(), v_group_id, NULL, 'SEEDMIN notification for visible group', 'both', 'seed', now())
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO public.platform_fees (id, group_id, fee_type, amount, status, due_date, paid_at, transaction_id, created_at, updated_at)
        VALUES (gen_random_uuid(), v_group_id, 'registration', 700.00, 'paid', to_char(now(), 'YYYY-MM-DD'), now(), 'SEEDMIN_FEE', now(), now())
        ON CONFLICT (id) DO NOTHING;
    END IF;
END $$;
