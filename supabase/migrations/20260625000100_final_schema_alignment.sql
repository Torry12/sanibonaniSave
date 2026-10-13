-- SanibonaniSave - Final Schema Alignment
-- Date: 2026-06-25
-- Purpose:
--   1) Create missing behavior tracking tables
--   2) Align members table with app projections
--   3) Update RPCs to handle member fees correctly
--   4) Re-verify RLS and Platform Admin Bypass

BEGIN;

-- 1. MISSING TABLES
CREATE TABLE IF NOT EXISTS public.fraud_detection_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id         UUID REFERENCES public.members(id) ON DELETE CASCADE NOT NULL,
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE NOT NULL,
    event_type        TEXT NOT NULL,
    severity          TEXT NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    details_json      JSONB NOT NULL DEFAULT '{}'::jsonb,
    action_taken      TEXT,
    resolved          BOOLEAN DEFAULT FALSE,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.behavior_analytics_summary (
    group_id                 UUID PRIMARY KEY REFERENCES public.groups(id) ON DELETE CASCADE,
    total_members_tracked    INTEGER DEFAULT 0,
    excellent_members        INTEGER DEFAULT 0,
    good_members             INTEGER DEFAULT 0,
    fair_members             INTEGER DEFAULT 0,
    poor_members             INTEGER DEFAULT 0,
    suspended_members        INTEGER DEFAULT 0,
    high_fraud_risk_count    INTEGER DEFAULT 0,
    flagged_members_count    INTEGER DEFAULT 0,
    average_behavior_score   NUMERIC(5,2) DEFAULT 0,
    average_fraud_score      NUMERIC(5,2) DEFAULT 0,
    on_time_payment_rate     NUMERIC(5,2) DEFAULT 0,
    loan_default_rate        NUMERIC(5,2) DEFAULT 0,
    calculated_at            TIMESTAMPTZ DEFAULT NOW()
);

-- 2. MEMBER TABLE ALIGNMENT
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS member_key TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS beneficiary_count INTEGER DEFAULT 0;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS beneficiary_over_65_count INTEGER DEFAULT 0;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS monthly_contribution_override NUMERIC(10,2);
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS total_contributions INTEGER DEFAULT 0;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS total_paid NUMERIC(12,2) DEFAULT 0;

-- 3. UPDATED RPCs

-- Atomic balance adjustment with Ledger logging
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
    VALUES (p_group_id, p_transaction_id::text, p_amount, v_new_balance, p_description, p_category);

    RETURN v_new_balance;
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
    -- Virtual banking logic
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

    -- 3. Update Member internal stats (Excluding platform fees)
    IF p_type != 'member_fee' AND p_type != 'platform_fee' THEN
        UPDATE public.members
        SET total_contributions = total_contributions + 1,
            total_paid = total_paid + p_amount
        WHERE id = p_member_id;
    END IF;

    -- 4. Update Group Balance and Ledger
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

    -- 5. Record Platform Ledger if it's a platform fee
    IF p_type = 'member_fee' THEN
        SELECT name INTO v_ledger_desc FROM public.groups WHERE id = p_group_id;
        PERFORM public.record_platform_ledger_entry(p_amount, 'Member Fee from group ' || v_ledger_desc, 'member_fee', v_contribution.id);
    END IF;

    RETURN jsonb_build_object(
        'contribution', to_jsonb(v_contribution),
        'new_balance', v_new_balance
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 4. RLS ENFORCEMENT
-- Re-run the security file logic to cover new tables
\i 'supabase/02_SECURITY_AND_RLS.sql'

COMMIT;
