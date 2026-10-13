-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — 05. MAXIMAL SEED DATA (Targeted Test Scenarios)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. CLEANUP (Idempotent)
BEGIN;
    -- Delete in reverse order of dependencies
    DELETE FROM public.audit_logs;
    DELETE FROM public.platform_ledger;
    DELETE FROM public.group_ledger;
    DELETE FROM public.virtual_bank_transactions;
    DELETE FROM public.notifications;
    DELETE FROM public.loan_repayments;
    DELETE FROM public.loans;
    DELETE FROM public.payments;
    DELETE FROM public.contributions;
    DELETE FROM public.beneficiary_payout_claims;
    DELETE FROM public.beneficiaries;
    DELETE FROM public.members;
    DELETE FROM public.platform_fees;
    DELETE FROM public.group_actuarial_metrics;
    DELETE FROM public.group_polls;
    DELETE FROM public.groups;
    DELETE FROM public.profiles;
    DELETE FROM public.virtual_bank_accounts;
    -- auth.users handled separately or via cascading if possible,
    -- but usually we keep users and just update profiles to avoid session invalidation.

    -- FIX: Remove restrictive check constraint that prevents historical data seeding
    ALTER TABLE public.contributions DROP CONSTRAINT IF EXISTS contributions_due_date_check;
    -- FIX: Update type check to include 'member_fee'
    ALTER TABLE public.contributions DROP CONSTRAINT IF EXISTS contributions_type_check;
    ALTER TABLE public.contributions ADD CONSTRAINT contributions_type_check CHECK (type IN ('contribution', 'joining_fee', 'registration_contribution', 'late_fee', 'member_fee'));

    -- FIX: Update claim status check to include 'escalated'
    ALTER TABLE public.beneficiary_payout_claims DROP CONSTRAINT IF EXISTS beneficiary_payout_claims_status_check;
    ALTER TABLE public.beneficiary_payout_claims ADD CONSTRAINT beneficiary_payout_claims_status_check CHECK (status IN ('pending', 'approved', 'paid', 'rejected', 'escalated'));
COMMIT;

DO $$
DECLARE
    -- IDs
    v_platform_admin_id UUID := '00000000-0000-0000-0000-000000000001';
    v_multi_admin_id UUID := '00000000-0000-0000-0000-000000000002';

    v_group_burial_id UUID := '11111111-1111-1111-1111-111111111111';
    v_group_stokvel_id UUID := '22222222-2222-2222-2222-222222222222';
    v_group_rosca_id UUID := '33333333-3333-3333-3333-333333333333';

    v_mem_perfect_id UUID;
    v_mem_struggling_id UUID;
    v_mem_borrower_id UUID;
    v_mem_deceased_id UUID;
    v_mem_probation_id UUID;

    v_loan_id UUID;
    v_claim_id UUID;
    v_beneficiary_id UUID;

    v_now TIMESTAMPTZ := NOW();
    v_month_ago DATE := (CURRENT_DATE - INTERVAL '1 month')::DATE;
    v_two_months_ago DATE := (CURRENT_DATE - INTERVAL '2 months')::DATE;
    v_next_month DATE := (CURRENT_DATE + INTERVAL '1 month')::DATE;

BEGIN
    -- ─────────────────────────────────────────────────────────────────────────
    -- 0. PLATFORM ASSETS
    -- ─────────────────────────────────────────────────────────────────────────
    INSERT INTO public.virtual_bank_accounts (account_number, account_holder_name, bank_name, branch_code, balance)
    VALUES
        ('SANIBONANI_PLATFORM_MASTER', 'Sanibonani Platform Master', 'Reserve Bank', '999999', 10000000.00),
        ('SANIBONANI_PLATFORM_FEES', 'Sanibonani Platform Fees', 'Reserve Bank', '999999', 0.00);

    INSERT INTO public.platform_settings (key, value) VALUES
        ('registration_fee', 700.00),
        ('monthly_member_fee', 10.00),
        ('payout_fee', 5.00),
        ('whatsapp_fee', 0.50),
        ('late_fee_percent', 10.00),
        ('auto_suspension_days', 30)
    ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

    -- ─────────────────────────────────────────────────────────────────────────
    -- 1. USERS & PROFILES
    -- ─────────────────────────────────────────────────────────────────────────

    -- Ensure Admin Users exist in auth.users (to satisfy foreign keys)
    IF NOT EXISTS (SELECT 1 FROM auth.users WHERE id = v_platform_admin_id) THEN
        INSERT INTO auth.users (id, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, aud, role)
        VALUES (
            v_platform_admin_id, 'torrymsimango@gmail.com', crypt('SeedAdmin@123', gen_salt('bf')), now(),
            '{"provider":"email","providers":["email"],"role":"platform_admin"}'::jsonb,
            '{"full_name":"Torry Msimango","role":"platform_admin"}'::jsonb,
            now(), now(), 'authenticated', 'authenticated'
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM auth.users WHERE id = v_multi_admin_id) THEN
        INSERT INTO auth.users (id, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, aud, role)
        VALUES (
            v_multi_admin_id, 'seed.admin.multi@example.com', crypt('SeedAdmin@123', gen_salt('bf')), now(),
            '{"provider":"email","providers":["email"],"role":"group_admin"}'::jsonb,
            '{"full_name":"Sarah Mthethwa","role":"group_admin"}'::jsonb,
            now(), now(), 'authenticated', 'authenticated'
        );
    END IF;

    -- PLATFORM ADMIN (Torry)
    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_platform_admin_id, 'Torry Msimango', 'torrymsimango@gmail.com', 'platform_admin')
    ON CONFLICT (id) DO UPDATE SET role = 'platform_admin';

    -- MULTI-GROUP ADMIN
    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (v_multi_admin_id, 'Sarah Mthethwa', 'seed.admin.multi@example.com', 'group_admin')
    ON CONFLICT (id) DO UPDATE SET role = 'group_admin';

    -- ─────────────────────────────────────────────────────────────────────────
    -- 2. GROUPS
    -- ─────────────────────────────────────────────────────────────────────────

    -- A. HEALTHY BURIAL SOCIETY (Gauteng)
    INSERT INTO public.groups (
        id, name, type, province, city, description, logo_emoji,
        joining_fee, monthly_contribution, late_fee, payment_due_day,
        max_members, is_public, balance, admin_user_id, fee_status, registration_paid,
        max_beneficiaries, beneficiary_increase_pct, latitude, longitude
    ) VALUES (
        v_group_burial_id, 'Soweto Unity Burial Society', 'burial_society', 'Gauteng', 'Johannesburg',
        'Leading burial society with stable reserves.', '🕊️',
        150.00, 350.00, 50.00, 28,
        200, TRUE, 85000.00, v_multi_admin_id, 'paid', TRUE,
        5, 10.0, -26.2485, 27.8540
    );
    INSERT INTO public.virtual_bank_accounts (account_number, account_holder_name, bank_name, branch_code, balance)
    VALUES ('1111111111', 'Soweto Unity Burial Society', 'FNB', '250655', 85000.00);

    -- B. STRUGGLING STOKVEL (Western Cape)
    INSERT INTO public.groups (
        id, name, type, province, city, description, logo_emoji,
        joining_fee, monthly_contribution, late_fee, payment_due_day,
        max_members, is_public, balance, admin_user_id, fee_status, registration_paid,
        latitude, longitude
    ) VALUES (
        v_group_stokvel_id, 'Khayelitsha Ladies Stokvel', 'stokvel', 'Western Cape', 'Cape Town',
        'Traditional savings club with high member default rate.', '🥣',
        50.00, 1000.00, 100.00, 1,
        15, TRUE, 5400.00, v_multi_admin_id, 'warning', TRUE,
        -34.0150, 18.6750
    );
    INSERT INTO public.virtual_bank_accounts (account_number, account_holder_name, bank_name, branch_code, balance)
    VALUES ('2222222222', 'Khayelitsha Ladies Stokvel', 'Standard Bank', '051001', 5400.00);

    -- C. RISKY ROSCA (KwaZulu-Natal)
    INSERT INTO public.groups (
        id, name, type, province, city, description, logo_emoji,
        joining_fee, monthly_contribution, late_fee, payment_due_day,
        max_members, is_public, balance, admin_user_id, fee_status, registration_paid,
        rosca_rotation_method, latitude, longitude
    ) VALUES (
        v_group_rosca_id, 'Umlazi Community ROSCA', 'rosca', 'KwaZulu-Natal', 'Durban',
        'Rotating savings group with high volatility.', '🔄',
        100.00, 2000.00, 150.00, 15,
        10, FALSE, 2000.00, v_multi_admin_id, 'due', TRUE,
        'random_draw', -29.9680, 30.8800
    );
    INSERT INTO public.virtual_bank_accounts (account_number, account_holder_name, bank_name, branch_code, balance)
    VALUES ('3333333333', 'Umlazi Community ROSCA', 'Nedbank', '198765', 2000.00);

    -- ─────────────────────────────────────────────────────────────────────────
    -- 3. MEMBERS (Personas)
    -- ─────────────────────────────────────────────────────────────────────────

    -- PERFECT PAYER (Active)
    v_mem_perfect_id := gen_random_uuid();
    INSERT INTO public.members (id, group_id, full_name, email, phone, status, joined_at, account_number, bank_name, branch_code, member_key)
    VALUES (v_mem_perfect_id, v_group_burial_id, 'John Mbeki', 'john.perfect@example.com', '0711111111', 'active', v_now - INTERVAL '6 months', '2000000001', 'FNB', '123456', 'MBE-001');
    INSERT INTO public.virtual_bank_accounts (account_number, account_holder_name, bank_name, branch_code, balance)
    VALUES ('2000000001', 'John Mbeki', 'FNB', '123456', 20000.00);

    -- STRUGGLING PAYER (Suspended)
    v_mem_struggling_id := gen_random_uuid();
    INSERT INTO public.members (id, group_id, full_name, email, phone, status, joined_at, account_number, bank_name, branch_code, member_key)
    VALUES (v_mem_struggling_id, v_group_stokvel_id, 'Grace Zuma', 'grace.struggle@example.com', '0722222222', 'suspended', v_now - INTERVAL '4 months', '2000000002', 'Capitec', '470010', 'ZUM-002');
    INSERT INTO public.virtual_bank_accounts (account_number, account_holder_name, bank_name, branch_code, balance)
    VALUES ('2000000002', 'Grace Zuma', 'Capitec', '470010', 50.00);

    -- LOAN BORROWER (Active)
    v_mem_borrower_id := gen_random_uuid();
    INSERT INTO public.members (id, group_id, full_name, email, phone, status, joined_at, account_number, bank_name, branch_code, member_key)
    VALUES (v_mem_borrower_id, v_group_burial_id, 'Thabo Cele', 'thabo.borrow@example.com', '0733333333', 'active', v_now - INTERVAL '8 months', '2000000003', 'Absa', '632005', 'CEL-003');
    INSERT INTO public.virtual_bank_accounts (account_number, account_holder_name, bank_name, branch_code, balance)
    VALUES ('2000000003', 'Thabo Cele', 'Absa', '632005', 1000.00);

    -- DECEASED MEMBER (Status: active, but has death claim)
    v_mem_deceased_id := gen_random_uuid();
    INSERT INTO public.members (id, group_id, full_name, email, phone, status, joined_at, account_number, bank_name, branch_code, member_key)
    VALUES (v_mem_deceased_id, v_group_burial_id, 'Nomvula Sithole', 'nomvula.rip@example.com', '0744444444', 'active', v_now - INTERVAL '2 years', '2000000004', 'TymeBank', '678910', 'SIT-004');
    INSERT INTO public.virtual_bank_accounts (account_number, account_holder_name, bank_name, branch_code, balance)
    VALUES ('2000000004', 'Nomvula Sithole', 'TymeBank', '678910', 5000.00);

    -- PROBATION MEMBER
    v_mem_probation_id := gen_random_uuid();
    INSERT INTO public.members (id, group_id, full_name, email, phone, status, joined_at, probation_end_at, member_key)
    VALUES (v_mem_probation_id, v_group_burial_id, 'Kevin Hart', 'kevin.new@example.com', '0755555555', 'probation', v_now - INTERVAL '10 days', v_now + INTERVAL '80 days', 'HAR-005');

    -- ─────────────────────────────────────────────────────────────────────────
    -- 4. FINANCIAL HISTORY (Ledger-Integrated)
    -- ─────────────────────────────────────────────────────────────────────────

    -- A. PERFECT PAYER: 5 months of contributions
    PERFORM public.record_contribution_v1(v_mem_perfect_id, v_group_burial_id, 350.00, v_month_ago, v_now - INTERVAL '28 days', 'paid', 'SEED_TX_P1', 'contribution');
    PERFORM public.record_contribution_v1(v_mem_perfect_id, v_group_burial_id, 350.00, v_two_months_ago, v_now - INTERVAL '58 days', 'paid', 'SEED_TX_P2', 'contribution');
    -- One platform fee contribution
    PERFORM public.record_contribution_v1(v_mem_perfect_id, v_group_burial_id, 10.00, v_month_ago, v_now - INTERVAL '28 days', 'paid', 'SEED_TX_P3', 'member_fee');

    -- B. STRUGGLING PAYER: One partial payment, two overdue
    PERFORM public.record_contribution_v1(v_mem_struggling_id, v_group_stokvel_id, 400.00, v_month_ago, v_now - INTERVAL '20 days', 'partial', 'SEED_TX_S1', 'contribution');
    INSERT INTO public.contributions (member_id, group_id, amount, due_date, status, type)
    VALUES (v_mem_struggling_id, v_group_stokvel_id, 1000.00, v_two_months_ago, 'overdue', 'contribution');

    -- C. LOAN BORROWER: Principal, Disbursement, and one Repayment
    INSERT INTO public.loans (id, member_id, group_id, amount, interest_rate, total_to_repay, total_repaid, monthly_repayment, start_date, end_date, status, purpose)
    VALUES (
        '11111111-aaaa-1111-aaaa-111111111111', v_mem_borrower_id, v_group_burial_id, 10000.00, 10.00, 11000.00, 0.00, 1100.00,
        v_month_ago, v_now + INTERVAL '10 months', 'active', 'Business expansion'
    ) RETURNING id INTO v_loan_id;

    -- Perform atomic disbursement (decrements group balance, increments member account, logs to ledger)
    PERFORM public.approve_and_disburse_loan_v1(v_loan_id, v_platform_admin_id, 'bank');

    -- Perform one atomic repayment (increments group balance, decrements member account, logs to ledger)
    PERFORM public.record_loan_repayment_v1(v_loan_id, v_mem_borrower_id, v_group_burial_id, 1100.00, 'bank');

    -- D. BURIAL CLAIM: For deceased member
    INSERT INTO public.beneficiaries (id, group_id, member_id, full_name, relationship, is_over_65)
    VALUES ('99999999-9999-9999-9999-999999999999', v_group_burial_id, v_mem_deceased_id, 'Thandi Sithole', 'daughter', FALSE)
    RETURNING id INTO v_beneficiary_id;

    INSERT INTO public.beneficiary_payout_claims (
        id, group_id, member_id, beneficiary_id, claim_amount, beneficiary_name, status,
        bank_name, account_no, branch_code, account_holder
    ) VALUES (
        'cccccccc-cccc-4ccc-8ccc-cccccccccccc', v_group_burial_id, v_mem_deceased_id, v_beneficiary_id, 15000.00, 'Thandi Sithole', 'escalated',
        'Standard Bank', '1000000009', '123456', 'T Sithole'
    ) RETURNING id INTO v_claim_id;

    -- Note: We leave it escalated so Platform Admin can see it in "Disbursements" tab.

    -- E. PENDING PAYOUT: Group requesting money from platform
    INSERT INTO public.payouts (id, group_id, amount, bank_name, account_no, branch_code, status)
    VALUES (
        'edeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', v_group_burial_id, 5000.00, 'FNB', '1111111111', '250655', 'group_approved'
    );

    -- ─────────────────────────────────────────────────────────────────────────
    -- 5. AUDIT LOGS
    -- ─────────────────────────────────────────────────────────────────────────
    INSERT INTO public.audit_logs (actor_id, target_group_id, action, details)
    VALUES
        (v_multi_admin_id, v_group_burial_id, 'APPROVE_LOAN_REQUEST', '{"loan_id":"11111111-aaaa-1111-aaaa-111111111111", "amount":10000}'::jsonb),
        (v_platform_admin_id, v_group_burial_id, 'PLATFORM_SETTINGS_UPDATE', '{"registration_fee":700}'::jsonb),
        (v_multi_admin_id, v_group_stokvel_id, 'SUSPEND_MEMBER', ('{"member_id":"' || v_mem_struggling_id || '", "reason":"Non-payment for 2 months"}')::jsonb);

    -- ─────────────────────────────────────────────────────────────────────────
    -- 6. GROUP POLLS
    -- ─────────────────────────────────────────────────────────────────────────
    INSERT INTO public.group_polls (id, group_id, created_by_member_id, title, description, status)
    VALUES (
        '77777777-7777-7777-7777-777777777777', v_group_burial_id, v_mem_perfect_id,
        'Increase Monthly Contribution?', 'Should we increase by R50 to build more reserves?', 'open'
    );

    -- ─────────────────────────────────────────────────────────────────────────
    -- 7. ACTUARIAL METRICS (Pre-computed for testing)
    -- ─────────────────────────────────────────────────────────────────────────
    -- Healthy Group
    INSERT INTO public.group_actuarial_metrics (
        group_id, composite_risk_score, reserve_adequacy_pct, solvency_ratio, payment_rate_pct, loss_ratio_pct, created_at
    ) VALUES (
        v_group_burial_id, 85, 120, 1.25, 98, 15, v_now
    );

    -- Struggling Group
    INSERT INTO public.group_actuarial_metrics (
        group_id, composite_risk_score, reserve_adequacy_pct, solvency_ratio, payment_rate_pct, loss_ratio_pct, created_at
    ) VALUES (
        v_group_stokvel_id, 35, 40, 0.45, 62, 75, v_now
    );

END;
$$;
