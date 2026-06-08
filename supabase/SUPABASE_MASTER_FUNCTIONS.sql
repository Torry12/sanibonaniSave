-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — SUPABASE MASTER FUNCTIONS & TRIGGERS
-- Version: 6.9 (Unified & Feature-Complete - June 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. UTILITY FUNCTIONS
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. VIRTUAL BANK SIMULATION (External Banking Integration)
CREATE OR REPLACE FUNCTION public.virtual_bank_transfer(
    p_from_account TEXT,
    p_to_account TEXT,
    p_amount NUMERIC,
    p_reference TEXT DEFAULT 'Transfer'
) RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_from_id UUID;
    v_to_id UUID;
    v_tx_id UUID;
BEGIN
    SELECT id INTO v_from_id FROM public.virtual_bank_accounts WHERE account_number = p_from_account;
    SELECT id INTO v_to_id FROM public.virtual_bank_accounts WHERE account_number = p_to_account;

    IF v_from_id IS NULL THEN RAISE EXCEPTION 'Source account % not found', p_from_account; END IF;
    IF v_to_id IS NULL THEN RAISE EXCEPTION 'Destination account % not found', p_to_account; END IF;

    UPDATE public.virtual_bank_accounts
    SET balance = balance - p_amount, updated_at = NOW()
    WHERE id = v_from_id AND balance >= p_amount;

    IF NOT FOUND THEN RAISE EXCEPTION 'Insufficient funds in source account %', p_from_account; END IF;

    UPDATE public.virtual_bank_accounts
    SET balance = balance + p_amount, updated_at = NOW()
    WHERE id = v_to_id;

    INSERT INTO public.virtual_bank_transactions (source_account_id, destination_account_id, amount, reference, transaction_type)
    VALUES (v_from_id, v_to_id, p_amount, p_reference, 'transfer')
    RETURNING id INTO v_tx_id;

    RETURN v_tx_id;
END;
$$;

-- 3. AUTOMATIC TRACKERS & REGISTRATIONS

-- Tracker: Member Count in Groups
CREATE OR REPLACE FUNCTION public.update_member_count()
RETURNS TRIGGER AS $$
DECLARE
    v_group_id UUID;
BEGIN
    v_group_id := COALESCE(NEW.group_id, OLD.group_id);
    UPDATE public.groups
    SET current_members = (SELECT count(*) FROM public.members WHERE group_id = v_group_id),
        updated_at = NOW()
    WHERE id = v_group_id;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Tracker: Group Admin Auto-Registration
CREATE OR REPLACE FUNCTION public.auto_register_group_admin()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.members (
        group_id,
        user_id,
        full_name,
        email,
        phone,
        status,
        joined_at,
        member_key
    )
    SELECT
        NEW.id,
        NEW.admin_user_id,
        COALESCE(p.full_name, 'Group Admin'),
        COALESCE(p.email, ''),
        COALESCE(p.phone, '0000000000'), -- Provide a fallback for not-null safety
        'active',
        NOW(),
        'ADMIN-' || substring(NEW.id::text from 1 for 8)
    FROM public.profiles p
    WHERE p.id = NEW.admin_user_id
    ON CONFLICT (group_id, user_id) DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Tracker: Profile Sync from Auth
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, full_name, email, phone, role)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.raw_user_meta_data->>'name', ''),
        NEW.email,
        NEW.raw_user_meta_data->>'phone',
        COALESCE(NEW.raw_user_meta_data->>'role', 'member')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. ATOMIC FINANCIAL OPERATIONS (Ledger Integrated)

-- Helper: Atomic balance adjustment with Ledger logging
CREATE OR REPLACE FUNCTION public.increment_group_balance(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT DEFAULT 'Atomic balance update',
    p_category TEXT DEFAULT 'adjustment',
    p_transaction_id UUID DEFAULT NULL
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_new_balance NUMERIC;
BEGIN
    UPDATE public.groups
    SET balance = balance + p_amount,
        updated_at = NOW()
    WHERE id = p_group_id
    RETURNING balance INTO v_new_balance;

    IF NOT FOUND THEN RAISE EXCEPTION 'Group not found'; END IF;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (p_group_id, p_transaction_id, p_amount, v_new_balance, p_description, p_category);

    RETURN v_new_balance;
END;
$$;

-- Helper: Record Platform Ledger entry
CREATE OR REPLACE FUNCTION public.record_platform_ledger_entry(
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id UUID DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_last_balance NUMERIC;
BEGIN
    SELECT balance_after INTO v_last_balance
    FROM public.platform_ledger
    ORDER BY created_at DESC LIMIT 1;

    INSERT INTO public.platform_ledger (transaction_id, amount, balance_after, description, category)
    VALUES (p_transaction_id, p_amount, COALESCE(v_last_balance, 0) + p_amount, p_description, p_category);
END;
$$;

-- Helper: Record Disbursement (outflow)
CREATE OR REPLACE FUNCTION public.record_disbursement_v1(
    p_group_id UUID,
    p_amount NUMERIC,
    p_description TEXT,
    p_category TEXT,
    p_transaction_id UUID DEFAULT NULL
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    IF p_amount <= 0 THEN RAISE EXCEPTION 'Disbursement amount must be positive'; END IF;
    RETURN public.increment_group_balance(p_group_id, -p_amount, p_description, p_category, p_transaction_id);
END;
$$;

-- RPC: Record Contribution
CREATE OR REPLACE FUNCTION public.record_contribution_v1(
    p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_due_date DATE,
    p_paid_at TIMESTAMPTZ, p_status TEXT, p_tx_id TEXT DEFAULT NULL, p_type TEXT DEFAULT 'contribution'
 ) RETURNS JSONB AS $$
DECLARE
    v_contribution public.contributions;
    v_new_balance NUMERIC;
    v_member_acc TEXT;
    v_group_acc TEXT;
    v_dest_acc TEXT;
    v_ledger_desc TEXT;
    v_ledger_cat TEXT;
BEGIN
    -- 1. Create contribution record
    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, transaction_id, type)
    VALUES (p_member_id, p_group_id, p_amount, p_due_date, p_paid_at, p_status, p_tx_id, p_type)
    RETURNING * INTO v_contribution;

    -- 2. Determine Destination Account and Ledger Category
    SELECT account_number INTO v_member_acc FROM public.members WHERE id = p_member_id;

    IF p_type = 'member_fee' THEN
        v_dest_acc := 'SANIBONANI_PLATFORM_FEES';
        v_ledger_desc := 'Platform Member Fee';
        v_ledger_cat := 'platform_fee';
    ELSE
        SELECT account_number INTO v_group_acc FROM public.groups WHERE id = p_group_id;
        v_dest_acc := v_group_acc;
        v_ledger_desc := 'Contribution: ' || p_type;
        v_ledger_cat := 'contribution';
    END IF;

    -- 3. Virtual Bank Transfer
    IF v_member_acc IS NOT NULL AND v_dest_acc IS NOT NULL THEN
        PERFORM public.virtual_bank_transfer(v_member_acc, v_dest_acc, p_amount, v_ledger_desc || ' (' || v_contribution.id || ')');
    END IF;

    -- 4. Record Platform Ledger if it's a platform fee
    IF p_type = 'member_fee' THEN
        SELECT name INTO v_ledger_desc FROM public.groups WHERE id = p_group_id;
        PERFORM public.record_platform_ledger_entry(p_amount, 'Member Fee from group ' || v_ledger_desc, 'member_fee', v_contribution.id);
    END IF;

    -- 5. Update Member internal stats (Only for actual contributions/savings)
    IF p_type != 'member_fee' AND p_type != 'platform_fee' THEN
        UPDATE public.members
        SET total_contributions = total_contributions + 1,
            total_paid = total_paid + p_amount
        WHERE id = p_member_id;
    END IF;

    -- 6. Update Group Balance and Ledger
    v_new_balance := (SELECT balance FROM public.groups WHERE id = p_group_id);
    IF p_type != 'member_fee' THEN
        SELECT public.increment_group_balance(
            p_group_id,
            p_amount,
            v_ledger_desc,
            v_ledger_cat,
            v_contribution.id
        ) INTO v_new_balance;
    END IF;

    RETURN jsonb_build_object(
        'contribution', to_jsonb(v_contribution),
        'new_balance', v_new_balance
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- RPC: Record Loan Repayment
CREATE OR REPLACE FUNCTION public.record_loan_repayment_v1(
    p_loan_id UUID, p_member_id UUID, p_group_id UUID, p_amount NUMERIC, p_payment_method TEXT DEFAULT 'bank'
 ) RETURNS JSONB AS $$
DECLARE
    v_repayment public.loan_repayments;
    v_new_balance NUMERIC;
    v_member_acc TEXT;
    v_group_acc TEXT;
BEGIN
    -- 1. Record repayment
    INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, payment_method)
    VALUES (p_loan_id, p_member_id, p_group_id, p_amount, p_payment_method)
    RETURNING * INTO v_repayment;

    -- 2. Virtual Bank Transfer (Member -> Group)
    SELECT account_number INTO v_member_acc FROM public.members WHERE id = p_member_id;
    SELECT account_number INTO v_group_acc FROM public.groups WHERE id = p_group_id;

    IF v_member_acc IS NOT NULL AND v_group_acc IS NOT NULL THEN
        PERFORM public.virtual_bank_transfer(v_member_acc, v_group_acc, p_amount, 'Loan Repayment: ' || p_loan_id);
    END IF;

    -- 3. Update loan status
    UPDATE public.loans
    SET total_repaid = total_repaid + p_amount,
        status = CASE
            WHEN total_repaid + p_amount >= total_to_repay THEN 'completed'::text
            ELSE 'partially_paid'::text
        END,
        updated_at = NOW()
    WHERE id = p_loan_id;

    -- 4. Update balance and ledger
    SELECT public.increment_group_balance(
        p_group_id,
        p_amount,
        'Loan Repayment: ' || p_loan_id,
        'loan_repayment',
        v_repayment.id
    ) INTO v_new_balance;

    RETURN jsonb_build_object(
        'repayment', to_jsonb(v_repayment),
        'new_balance', v_new_balance
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- RPC: Complete Platform Payout
CREATE OR REPLACE FUNCTION public.complete_payout_v1(
    p_payout_id UUID,
    p_admin_id UUID DEFAULT NULL,
    p_payout_reference TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_payout public.payouts;
    v_group_acc TEXT;
    v_processed_by UUID := COALESCE(p_admin_id, auth.uid());
BEGIN
    SELECT * INTO v_payout FROM public.payouts WHERE id = p_payout_id;
    IF v_payout.id IS NULL THEN RAISE EXCEPTION 'Payout not found'; END IF;
    IF v_payout.status = 'completed' THEN RETURN; END IF;

    SELECT account_number INTO v_group_acc FROM public.groups WHERE id = v_payout.group_id;

    -- 1. Virtual Bank Transfer (Platform -> Group)
    PERFORM public.virtual_bank_transfer('SANIBONANI_PLATFORM_MASTER', v_payout.account_no, v_payout.amount, 'Group Payout: ' || p_payout_id);

    -- 2. Update Payout record
    UPDATE public.payouts
    SET status = 'completed',
        processed_at = NOW(),
        processed_by = v_processed_by,
        payout_reference = COALESCE(p_payout_reference, payout_reference),
        updated_at = NOW()
    WHERE id = p_payout_id;

    -- 3. Internal records
    PERFORM public.record_platform_ledger_entry(-v_payout.amount, 'Payout to Group ' || v_payout.group_id, 'group_payout', p_payout_id);
    PERFORM public.increment_group_balance(v_payout.group_id, v_payout.amount, 'Fund Payout Received', 'payout', p_payout_id);
END;
$$;

-- RPC: Pay Burial Claim
CREATE OR REPLACE FUNCTION public.pay_burial_claim_v1(
    p_claim_id UUID,
    p_admin_id UUID DEFAULT NULL,
    p_notes TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_claim public.beneficiary_payout_claims;
    v_group_acc TEXT;
    v_reviewed_by UUID := COALESCE(p_admin_id, auth.uid());
BEGIN
    SELECT * INTO v_claim FROM public.beneficiary_payout_claims WHERE id = p_claim_id;
    IF v_claim.id IS NULL THEN RAISE EXCEPTION 'Claim not found'; END IF;
    IF v_claim.status = 'paid' THEN RETURN; END IF;

    SELECT account_number INTO v_group_acc FROM public.groups WHERE id = v_claim.group_id;

    -- 1. Virtual Bank Transfer (Group -> Beneficiary)
    IF v_group_acc IS NOT NULL AND v_claim.account_no IS NOT NULL THEN
        PERFORM public.virtual_bank_transfer(v_group_acc, v_claim.account_no, v_claim.claim_amount, 'Burial Payout: ' || p_claim_id);
    END IF;

    -- 2. Update Claim record
    UPDATE public.beneficiary_payout_claims
    SET status = 'paid',
        reviewed_at = NOW(),
        reviewed_by = v_reviewed_by,
        admin_notes = COALESCE(p_notes, admin_notes),
        updated_at = NOW()
    WHERE id = p_claim_id;

    -- 3. Update Group Balance and Ledger
    PERFORM public.increment_group_balance(
        v_claim.group_id,
        -v_claim.claim_amount,
        'Burial Payout: ' || v_claim.beneficiary_name,
        'burial_claim',
        p_claim_id
    );
END;
$$;

-- RPC: Pay Platform Fee
CREATE OR REPLACE FUNCTION public.pay_platform_fee_v1(
    p_group_id UUID,
    p_amount NUMERIC,
    p_fee_type TEXT,
    p_tx_id TEXT DEFAULT NULL
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_group_acc TEXT;
    v_group_name TEXT;
    v_platform_acc TEXT := 'SANIBONANI_PLATFORM_FEES';
BEGIN
    SELECT account_number, name INTO v_group_acc, v_group_name FROM public.groups WHERE id = p_group_id;

    -- 1. Virtual Bank Transfer (Group -> Platform Fees)
    IF v_group_acc IS NOT NULL THEN
        PERFORM public.virtual_bank_transfer(v_group_acc, v_platform_acc, p_amount, p_fee_type || ' Fee: ' || v_group_name);
    END IF;

    -- 2. Record Platform Ledger
    PERFORM public.record_platform_ledger_entry(p_amount, p_fee_type || ' Fee from ' || v_group_name, p_fee_type || '_fee');

    -- 3. Update internal group balance
    PERFORM public.increment_group_balance(p_group_id, -p_amount, p_fee_type || ' Platform Fee', 'platform_fee');

    -- 4. Mark corresponding fee record as paid
    UPDATE public.platform_fees
    SET status = 'paid',
        paid_at = NOW(),
        transaction_id = COALESCE(p_tx_id, transaction_id)
    WHERE group_id = p_group_id AND fee_type = p_fee_type AND status != 'paid';
END;
$$;

-- 5. ACTUARIAL FUNCTIONS

CREATE OR REPLACE FUNCTION public.calculate_group_health_score(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_total_score INT;
    v_zone TEXT;
    v_solvency_ratio NUMERIC;
    v_loss_ratio NUMERIC;
    v_reserve_adequacy NUMERIC;
    v_funding_ratio NUMERIC;
    v_retention_ratio NUMERIC;
    v_solvency_score NUMERIC;
    v_loss_score NUMERIC;
    v_reserve_score NUMERIC;
    v_funding_score NUMERIC;
    v_retention_score NUMERIC;
    v_group_balance NUMERIC;
    v_member_count INT;
    v_total_claims NUMERIC;
    v_total_incoming NUMERIC;
    v_avg_monthly_contrib NUMERIC;
    v_recommendations TEXT[] := ARRAY[]::TEXT[];
    v_result JSONB;
BEGIN
    -- 1. Fetch raw data
    SELECT COALESCE(balance, 0) INTO v_group_balance FROM public.groups WHERE id = p_group_id;

    SELECT COUNT(*) INTO v_member_count
    FROM public.members
    WHERE group_id = p_group_id AND status IN ('active', 'probation');

    -- Calculate average monthly incoming contributions (Exclusively 'contribution' type for sustainability)
    SELECT COALESCE(AVG(monthly_sum), 0) INTO v_avg_monthly_contrib
    FROM (
        SELECT SUM(amount) as monthly_sum
        FROM public.contributions
        WHERE group_id = p_group_id
          AND amount > 0
          AND type = 'contribution'
        GROUP BY date_trunc('month', due_date)
    ) as monthly_totals;

    -- Fallback to group's expected monthly contribution * member count if no history
    IF v_avg_monthly_contrib = 0 THEN
        SELECT (COALESCE(monthly_contribution, 0) * GREATEST(v_member_count, 1))
        INTO v_avg_monthly_contrib
        FROM public.groups WHERE id = p_group_id;
    END IF;

    IF v_avg_monthly_contrib = 0 THEN v_avg_monthly_contrib := 1.0; END IF;

    -- 2. Compute Raw Actuarial Ratios

    -- Solvency Ratio (Current Balance / Monthly Outgoings Target)
    v_solvency_ratio := v_group_balance / v_avg_monthly_contrib;

    -- Loss Ratio (Total Claims / Total Contributions)
    SELECT COALESCE(SUM(amount), 0) INTO v_total_claims
    FROM public.contributions
    WHERE group_id = p_group_id AND type IN ('claim', 'payout', 'burial_claim', 'loan_disbursement');

    SELECT COALESCE(SUM(amount), 0) INTO v_total_incoming
    FROM public.contributions
    WHERE group_id = p_group_id AND type = 'contribution';

    IF v_total_incoming > 0 THEN
        v_loss_ratio := v_total_claims / v_total_incoming;
    ELSE
        v_loss_ratio := 0;
    END IF;

    -- Reserve Adequacy (Balance / 6 months of expected income)
    v_reserve_adequacy := v_group_balance / (v_avg_monthly_contrib * 6);

    -- Funding Ratio (Balance / Liabilities)
    IF v_total_claims > 0 THEN
        v_funding_ratio := v_group_balance / v_total_claims;
    ELSE
        v_funding_ratio := 1.5; -- Optimistic default
    END IF;

    -- Member Retention
    v_retention_ratio := 0.95;

    -- 3. Normalize to Component Scores

    IF v_solvency_ratio < 0.5 THEN v_solvency_score := 0;
    ELSIF v_solvency_ratio > 1.5 THEN v_solvency_score := 25;
    ELSE v_solvency_score := ((v_solvency_ratio - 0.5) / 1.0 * 25);
    END IF;

    IF v_loss_ratio > 0.7 THEN v_loss_score := 0;
    ELSE v_loss_score := ((1 - (v_loss_ratio / 0.7)) * 25);
    END IF;

    IF v_reserve_adequacy < 0.25 THEN v_reserve_score := 0;
    ELSIF v_reserve_adequacy > 1.5 THEN v_reserve_score := 20;
    ELSE v_reserve_score := ((v_reserve_adequacy - 0.25) / 1.25 * 20);
    END IF;

    IF v_funding_ratio < 0.8 THEN v_funding_score := 0;
    ELSIF v_funding_ratio > 1.5 THEN v_funding_score := 20;
    ELSE v_funding_score := ((v_funding_ratio - 0.8) / 0.7 * 20);
    END IF;

    IF v_retention_ratio < 0.8 THEN v_retention_score := 0;
    ELSIF v_retention_ratio > 0.95 THEN v_retention_score := 10;
    ELSE v_retention_score := ((v_retention_ratio - 0.8) / 0.15 * 10);
    END IF;

    v_total_score := ROUND(v_solvency_score + v_loss_score + v_reserve_score + v_funding_score + v_retention_score);

    IF v_total_score < 40 THEN v_zone := 'RED';
    ELSIF v_total_score < 70 THEN v_zone := 'YELLOW';
    ELSE v_zone := 'GREEN';
    END IF;

    -- 4. Strategic Recommendations
    IF v_solvency_ratio < 0.7 THEN v_recommendations := array_append(v_recommendations, 'Increase monthly contribution to build basic solvency.'); END IF;
    IF v_loss_ratio > 0.5 THEN v_recommendations := array_append(v_recommendations, 'High claim volume detected. Consider eligibility audits.'); END IF;
    IF v_reserve_adequacy < 0.5 THEN v_recommendations := array_append(v_recommendations, 'Reserves below target. Aim for 6 months of income buffer.'); END IF;
    IF v_zone = 'GREEN' THEN v_recommendations := array_append(v_recommendations, 'Group health is optimal. Maintain current contribution discipline.'); END IF;

    v_result := jsonb_build_object(
        'group_id', p_group_id,
        'overall_score', v_total_score,
        'zone', v_zone,
        'components', jsonb_build_object(
            'Solvency Ratio', ROUND(v_solvency_score),
            'Loss Ratio', ROUND(v_loss_score),
            'Reserve Adequacy', ROUND(v_reserve_score),
            'Funding Ratio', ROUND(v_funding_score),
            'Member Retention', ROUND(v_retention_score)
        ),
        'recommendations', v_recommendations,
        'generated_at', NOW()
    );

    -- 5. Persist to Metrics Table
    INSERT INTO public.group_actuarial_metrics (
        group_id,
        composite_risk_score,
        reserve_adequacy_pct,
        solvency_ratio,
        payment_rate_pct,
        loss_ratio_pct,
        contribution_sufficiency_pct,
        funding_ratio_pct
    ) VALUES (
        p_group_id,
        v_total_score,
        ROUND(v_reserve_adequacy * 100),
        v_solvency_ratio,
        ROUND(v_retention_ratio * 100),
        ROUND(v_loss_ratio * 100),
        ROUND(v_solvency_score / 25 * 100),
        ROUND(v_funding_ratio * 100)
    ) ON CONFLICT (group_id) DO UPDATE SET
        composite_risk_score = EXCLUDED.composite_risk_score,
        reserve_adequacy_pct = EXCLUDED.reserve_adequacy_pct,
        solvency_ratio = EXCLUDED.solvency_ratio,
        payment_rate_pct = EXCLUDED.payment_rate_pct,
        loss_ratio_pct = EXCLUDED.loss_ratio_pct,
        contribution_sufficiency_pct = EXCLUDED.contribution_sufficiency_pct,
        funding_ratio_pct = EXCLUDED.funding_ratio_pct,
        created_at = NOW();

    RETURN v_result;
END;
$$;

-- 6. TRIGGER ATTACHMENTS

-- Member Management Triggers
DROP TRIGGER IF EXISTS trigger_update_member_count ON public.members;
CREATE TRIGGER trigger_update_member_count AFTER INSERT OR DELETE ON public.members FOR EACH ROW EXECUTE FUNCTION public.update_member_count();

DROP TRIGGER IF EXISTS trigger_auto_register_group_admin ON public.groups;
CREATE TRIGGER trigger_auto_register_group_admin AFTER INSERT ON public.groups FOR EACH ROW EXECUTE FUNCTION public.auto_register_group_admin();

-- Auth Trigger
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created AFTER INSERT ON auth.users FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Timestamp Triggers
DROP TRIGGER IF EXISTS update_profiles_updated_at ON public.profiles;
CREATE TRIGGER update_profiles_updated_at BEFORE UPDATE ON public.profiles FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

DROP TRIGGER IF EXISTS update_groups_updated_at ON public.groups;
CREATE TRIGGER update_groups_updated_at BEFORE UPDATE ON public.groups FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

DROP TRIGGER IF EXISTS update_members_updated_at ON public.members;
CREATE TRIGGER update_members_updated_at BEFORE UPDATE ON public.members FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

DROP TRIGGER IF EXISTS update_contributions_updated_at ON public.contributions;
CREATE TRIGGER update_contributions_updated_at BEFORE UPDATE ON public.contributions FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

DROP TRIGGER IF EXISTS update_payments_updated_at ON public.payments;
CREATE TRIGGER update_payments_updated_at BEFORE UPDATE ON public.payments FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

DROP TRIGGER IF EXISTS update_loans_updated_at ON public.loans;
CREATE TRIGGER update_loans_updated_at BEFORE UPDATE ON public.loans FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

DROP TRIGGER IF EXISTS update_payouts_updated_at ON public.payouts;
CREATE TRIGGER update_payouts_updated_at BEFORE UPDATE ON public.payouts FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- 7. GRANTS
GRANT EXECUTE ON FUNCTION public.virtual_bank_transfer(TEXT, TEXT, NUMERIC, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.increment_group_balance(UUID, NUMERIC, TEXT, TEXT, UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.record_platform_ledger_entry(NUMERIC, TEXT, TEXT, UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.record_disbursement_v1(UUID, NUMERIC, TEXT, TEXT, UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.record_contribution_v1(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.record_loan_repayment_v1(UUID, UUID, UUID, NUMERIC, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.complete_payout_v1(UUID, UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.pay_burial_claim_v1(UUID, UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.pay_platform_fee_v1(UUID, NUMERIC, TEXT, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.calculate_group_health_score(UUID) TO authenticated, service_role;
