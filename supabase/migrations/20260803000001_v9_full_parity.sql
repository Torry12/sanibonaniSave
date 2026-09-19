-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — FULL PARITY MIGRATION (v9)
-- Version: 1.0 (August 3, 2026)
-- ─────────────────────────────────────────────────────────────────────────────
-- This migration consolidates all hardening scripts from 13 through 45.

-- 1. MISSING FINANCIAL RPCS (From 13_ADD_MISSING_RPCS.sql)
CREATE OR REPLACE FUNCTION public.record_loan_settlement_extraction_v1(
    p_member_id UUID,
    p_group_id UUID,
    p_amount NUMERIC,
    p_loan_id UUID
) RETURNS JSONB AS $$
DECLARE
    v_contribution RECORD;
    v_new_balance NUMERIC;
    v_member_name TEXT;
    v_loan RECORD;
BEGIN
    SELECT full_name INTO v_member_name FROM public.members WHERE id = p_member_id;
    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id;
    IF v_loan.id IS NULL THEN RAISE EXCEPTION 'Loan not found'; END IF;

    -- 1. Create a negative contribution record
    INSERT INTO public.contributions (member_id, group_id, amount, due_date, paid_at, status, type, transaction_id)
    VALUES (p_member_id, p_group_id, -p_amount, CURRENT_DATE, NOW(), 'paid', 'loan_settlement_extraction', 'settlement-' || p_loan_id::text)
    RETURNING * INTO v_contribution;

    -- 2. Update member total_paid
    UPDATE public.members SET total_paid = total_paid - p_amount, updated_at = NOW() WHERE id = p_member_id;

    -- 3. Update loan status to completed
    UPDATE public.loans SET total_repaid = total_repaid + p_amount, status = 'completed', updated_at = NOW() WHERE id = p_loan_id;

    SELECT balance INTO v_new_balance FROM public.groups WHERE id = p_group_id;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (p_group_id, v_contribution.id::text, -p_amount, v_new_balance, 'Loan settlement extraction for ' || v_member_name, 'loan_settlement');

    RETURN jsonb_build_object('contribution', to_jsonb(v_contribution), 'new_balance', v_new_balance);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;


-- 2. BANK INTEGRATION SCHEMA (From 26_BANK_INTEGRATION_SCHEMA.sql)
CREATE TABLE IF NOT EXISTS public.bank_transactions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    bank_reference    TEXT NOT NULL,
    amount            NUMERIC(12,2) NOT NULL,
    transaction_date  DATE NOT NULL,
    description       TEXT,
    metadata          JSONB,
    status            TEXT NOT NULL DEFAULT 'unreconciled' CHECK (status IN ('unreconciled', 'reconciled', 'ignored')),
    reconciled_with   UUID,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bank_tx_group_status ON public.bank_transactions(group_id, status);
CREATE INDEX IF NOT EXISTS idx_bank_tx_reference ON public.bank_transactions(bank_reference);


-- 3. GROUP VOTING & GOVERNANCE (From 32_GROUP_VOTING_AND_MULTI_ADMIN.sql)
CREATE TABLE IF NOT EXISTS public.group_polls (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id              UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    created_by_member_id  UUID REFERENCES public.members(id) ON DELETE SET NULL,
    title                 TEXT NOT NULL CHECK (char_length(title) >= 3),
    description           TEXT,
    status                TEXT DEFAULT 'open' CHECK (status IN ('open', 'closed', 'cancelled')),
    allow_multiple_choice BOOLEAN DEFAULT FALSE,
    ends_at               TIMESTAMPTZ,
    created_at            TIMESTAMPTZ DEFAULT NOW(),
    updated_at            TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.group_poll_options (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id     UUID REFERENCES public.group_polls(id) ON DELETE CASCADE NOT NULL,
    label       TEXT NOT NULL,
    position    INTEGER DEFAULT 0,
    vote_count  INTEGER DEFAULT 0,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.group_poll_votes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id     UUID REFERENCES public.group_polls(id) ON DELETE CASCADE NOT NULL,
    option_id   UUID REFERENCES public.group_poll_options(id) ON DELETE CASCADE NOT NULL,
    member_id   UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(poll_id, member_id)
);

ALTER TABLE public.group_polls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_poll_options ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_poll_votes ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Polls: View joined" ON public.group_polls;
CREATE POLICY "Polls: View joined" ON public.group_polls FOR SELECT TO authenticated USING (public.is_group_member(group_id) OR public.is_group_admin(group_id));

DROP POLICY IF EXISTS "Polls: Admin manage" ON public.group_polls;
CREATE POLICY "Polls: Admin manage" ON public.group_polls FOR ALL TO authenticated USING (public.is_group_admin(group_id));

DROP POLICY IF EXISTS "Options: View joined" ON public.group_poll_options;
CREATE POLICY "Options: View joined" ON public.group_poll_options FOR SELECT TO authenticated USING (poll_id IN (SELECT id FROM public.group_polls));

DROP POLICY IF EXISTS "Votes: View joined" ON public.group_poll_votes;
CREATE POLICY "Votes: View joined" ON public.group_poll_votes FOR SELECT TO authenticated USING (poll_id IN (SELECT id FROM public.group_polls));

DROP POLICY IF EXISTS "Votes: Cast own" ON public.group_poll_votes;
CREATE POLICY "Votes: Cast own" ON public.group_poll_votes FOR INSERT TO authenticated WITH CHECK (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

CREATE OR REPLACE FUNCTION public.fn_sync_poll_vote_count()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN UPDATE public.group_poll_options SET vote_count = vote_count + 1 WHERE id = NEW.option_id;
    ELSIF (TG_OP = 'DELETE') THEN UPDATE public.group_poll_options SET vote_count = vote_count - 1 WHERE id = OLD.option_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_sync_poll_vote_count ON public.group_poll_votes;
CREATE TRIGGER tr_sync_poll_vote_count AFTER INSERT OR DELETE ON public.group_poll_votes FOR EACH ROW EXECUTE FUNCTION public.fn_sync_poll_vote_count();


-- 4. LEDGER CONSOLIDATION & AUDITING (From 33_LEDGER_CONSOLIDATION_V1.sql)
CREATE OR REPLACE FUNCTION public.recompute_ledger_running_balances_v1(p_group_id UUID)
RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_entry RECORD;
    v_running_balance NUMERIC := 0;
BEGIN
    FOR v_entry IN SELECT id, amount FROM public.group_ledger WHERE group_id = p_group_id ORDER BY created_at ASC, id ASC LOOP
        v_running_balance := v_running_balance + v_entry.amount;
        UPDATE public.group_ledger SET balance_after = v_running_balance WHERE id = v_entry.id;
    END LOOP;
    UPDATE public.groups SET balance = v_running_balance, updated_at = NOW() WHERE id = p_group_id;
    RETURN v_running_balance;
END;
$$;


-- 5. AUDIT REPORT FACILITIES (From 34_AUDIT_REPORT_FACILITIES_V1.sql)
CREATE OR REPLACE FUNCTION public.generate_member_audit_report_v1(p_member_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_member RECORD;
    v_contribution_stats RECORD;
    v_loan_stats RECORD;
BEGIN
    SELECT * INTO v_member FROM public.members WHERE id = p_member_id;
    IF NOT FOUND THEN RETURN NULL; END IF;
    SELECT COUNT(*) as total_count, SUM(CASE WHEN status = 'paid' THEN amount ELSE 0 END) as total_paid INTO v_contribution_stats FROM public.contributions WHERE member_id = p_member_id;
    SELECT COUNT(*) as loan_count, SUM(amount) as total_borrowed, SUM(total_repaid) as total_repaid INTO v_loan_stats FROM public.loans WHERE member_id = p_member_id AND status NOT IN ('rejected', 'cancelled');
    RETURN jsonb_build_object(
        'member_id', p_member_id,
        'full_name', v_member.full_name,
        'contributions', jsonb_build_object('total_paid', COALESCE(v_contribution_stats.total_paid, 0)),
        'loans', jsonb_build_object('total_borrowed', COALESCE(v_loan_stats.total_borrowed, 0), 'total_repaid', COALESCE(v_loan_stats.total_repaid, 0))
    );
END;
$$;


-- 6. LEDGER CHECKPOINTS & IMMUTABILITY (From 36_LEDGER_REFACTOR_V2.sql)
CREATE TABLE IF NOT EXISTS public.ledger_checkpoints (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    ledger_entry_id   UUID REFERENCES public.group_ledger(id) NOT NULL,
    balance           NUMERIC(12,2) NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(group_id, ledger_entry_id)
);


-- 7. ADMIN MEMBERSHIP ENFORCEMENT (From 43_ENFORCE_ADMIN_MEMBERSHIP.sql)
CREATE OR REPLACE FUNCTION public.fn_ensure_admin_is_member()
RETURNS TRIGGER AS $$
DECLARE
    v_profile RECORD;
BEGIN
    SELECT * INTO v_profile FROM public.profiles WHERE id = NEW.admin_user_id;
    IF v_profile.id IS NULL THEN RETURN NEW; END IF;
    INSERT INTO public.members (id, group_id, user_id, full_name, email, phone, status, joined_at, probation_end_at)
    VALUES (gen_random_uuid(), NEW.id, NEW.admin_user_id, v_profile.full_name, v_profile.email, v_profile.phone, 'active', NOW(), NOW() + (NEW.probation_months || ' months')::INTERVAL)
    ON CONFLICT (group_id, user_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS tr_ensure_admin_is_member ON public.groups;
CREATE TRIGGER tr_ensure_admin_is_member AFTER INSERT ON public.groups FOR EACH ROW EXECUTE FUNCTION public.fn_ensure_admin_is_member();


-- 8. PLATFORM ACCOUNT PROVISIONING (From 45_PLATFORM_ACCOUNT_PROVISIONING.sql)
INSERT INTO public.platform_settings (key, value) VALUES ('platform_bank_name_code', 1) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;
INSERT INTO public.platform_settings (key, value) VALUES ('platform_account_number', 62889900112) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;
INSERT INTO public.platform_settings (key, value) VALUES ('platform_branch_code', 250655) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

CREATE OR REPLACE FUNCTION public.process_complex_payment_v3(
    p_group_id UUID,
    p_member_id UUID,
    p_total_amount NUMERIC,
    p_tx_id TEXT,
    p_allocations JSONB,
    p_method TEXT DEFAULT 'bank',
    p_payment_type TEXT DEFAULT 'contribution',
    p_destination TEXT DEFAULT 'group'
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    RETURN public.process_complex_payment_v2(p_group_id, p_member_id, p_total_amount, p_tx_id, p_allocations, p_method, p_payment_type);
END;
$$;

-- 9. REPAIR SCRIPT
DO $$
DECLARE v_g RECORD;
BEGIN
    FOR v_g IN SELECT id FROM public.groups LOOP
        PERFORM public.recalculate_all_group_financials_v1(v_g.id);
    END LOOP;
END $$;
