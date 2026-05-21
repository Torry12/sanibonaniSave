-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — ENGINEERING & PERFORMANCE OPTIMIZATIONS
-- Version: 1.0 (May 2026)
--
-- IMPROVEMENTS:
-- 1. Atomic Loan Operations (Prevents desync between status and ledger)
-- 2. Server-Side Aggregate Views (Solves N+1 query problem for analytics)
-- 3. Optimized RLS Helpers (Uses caching where possible)
-- ─────────────────────────────────────────────────────────────────────────────


BEGIN;

-- Error log table for failed transactions
CREATE TABLE IF NOT EXISTS public.transaction_error_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    error_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    operation TEXT NOT NULL,
    reference_id UUID,
    error_message TEXT NOT NULL,
    context JSONB
);

-- 0. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 1. ATOMIC LOAN APPROVAL & DISBURSEMENT
-- This ensures a loan cannot be approved without being recorded in the ledger.

CREATE OR REPLACE FUNCTION public.approve_and_disburse_loan_v1(
    p_loan_id UUID,
    p_admin_id UUID,
    p_payment_method TEXT DEFAULT 'bank'
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_loan public.loans;
    v_member_name TEXT;
BEGIN
    -- 1. Fetch loan details with row-level lock
    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id FOR UPDATE;
    IF v_loan.id IS NULL THEN RAISE EXCEPTION 'Loan % not found', p_loan_id; END IF;

    -- 2. Check if already active/disbursed
    IF v_loan.status IN ('active', 'completed', 'partially_paid') THEN
        RETURN; -- Already processed
    END IF;

    -- 3. Get member name for ledger
    SELECT full_name INTO v_member_name FROM public.members WHERE id = v_loan.member_id;

    BEGIN
        -- 4. Update Loan Status
        UPDATE public.loans
        SET status = 'active',
            reviewed_by = p_admin_id,
            reviewed_at = NOW(),
            updated_at = NOW()
        WHERE id = p_loan_id;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Loan % not found for update', p_loan_id;
        END IF;

        -- 5. Record Disbursement (Decrements balance + adds Ledger entry)
        PERFORM public.record_disbursement_v1(
            v_loan.group_id,
            v_loan.amount,
            'Loan Disbursement to ' || v_member_name || ' (Loan #' || SUBSTRING(v_loan.id::text, 1, 8) || ')',
            'loan_disbursement',
            v_loan.id
        );
    EXCEPTION WHEN OTHERS THEN
        -- Rollback loan status if disbursement fails
        UPDATE public.loans
        SET status = 'pending',
            reviewed_by = NULL,
            reviewed_at = NULL,
            updated_at = NOW()
        WHERE id = p_loan_id;
        -- Log error
        INSERT INTO public.transaction_error_log(operation, reference_id, error_message, context)
        VALUES ('approve_and_disburse_loan_v1', p_loan_id, SQLERRM, jsonb_build_object('admin_id', p_admin_id));
        RAISE;
    END;
END;
$$;


-- 2. CONSOLIDATED ANALYTICS MATERIALIZED VIEW (for high-frequency dashboard use)
DROP MATERIALIZED VIEW IF EXISTS public.platform_summary_stats_mat;
CREATE MATERIALIZED VIEW public.platform_summary_stats_mat AS
SELECT
    COUNT(id)::INT AS total_groups,
    COALESCE(SUM(current_members), 0)::INT AS total_members,
    COALESCE(SUM(balance), 0)::NUMERIC(15,2) AS total_balance,
    (SELECT COUNT(DISTINCT province) FROM public.groups WHERE province IS NOT NULL AND province != '')::INT AS total_provinces,
    (SELECT COALESCE(SUM(amount), 0) FROM public.platform_ledger)::NUMERIC(15,2) AS platform_revenue,
    (
        SELECT COALESCE(AVG(composite_risk_score), 0)
        FROM public.group_actuarial_metrics
    )::FLOAT AS average_risk_score
FROM public.groups;

-- (Optional) Schedule a periodic refresh for the materialized view using pg_cron or external scheduler

-- 3. OPTIMIZED RLS HELPER
-- Uses a simpler check for membership to reduce subquery depth in large results.
CREATE OR REPLACE FUNCTION public.check_is_member(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE -- Stable allows Postgres to cache result within a single query
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.members
        WHERE group_id = p_group_id AND user_id = auth.uid()
    );
$$;

-- 4. PERFORMANCE INDEXES
-- Accelerated search for members and groups
CREATE INDEX IF NOT EXISTS idx_members_full_name_trgm ON public.members USING gin (full_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_groups_name_trgm ON public.groups USING gin (name gin_trgm_ops);

-- 5. GRANTS
GRANT SELECT ON public.platform_summary_stats_mat TO authenticated, anon;
GRANT EXECUTE ON FUNCTION public.approve_and_disburse_loan_v1(UUID, UUID, TEXT) TO authenticated, service_role;

COMMIT;
-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — ENGINEERING & PERFORMANCE OPTIMIZATIONS
-- Version: 1.0 (May 2026)
--
-- IMPROVEMENTS:
-- 1. Atomic Loan Operations (Prevents desync between status and ledger)
-- 2. Server-Side Aggregate Views (Solves N+1 query problem for analytics)
-- 3. Optimized RLS Helpers (Uses caching where possible)
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

-- 0. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 1. ATOMIC LOAN APPROVAL & DISBURSEMENT
-- This ensures a loan cannot be approved without being recorded in the ledger.
CREATE OR REPLACE FUNCTION public.approve_and_disburse_loan_v1(
    p_loan_id UUID,
    p_admin_id UUID,
    p_payment_method TEXT DEFAULT 'bank'
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_loan public.loans;
    v_member_name TEXT;
BEGIN
    -- 1. Fetch loan details
    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id;
    IF v_loan.id IS NULL THEN RAISE EXCEPTION 'Loan % not found', p_loan_id; END IF;

    -- 2. Check if already active/disbursed
    IF v_loan.status IN ('active', 'completed', 'partially_paid') THEN
        RETURN; -- Already processed
    END IF;

    -- 3. Get member name for ledger
    SELECT full_name INTO v_member_name FROM public.members WHERE id = v_loan.member_id;

    -- 4. Update Loan Status
    UPDATE public.loans
    SET status = 'active',
        reviewed_by = p_admin_id,
        reviewed_at = NOW(),
        updated_at = NOW()
    WHERE id = p_loan_id;

    -- 5. Record Disbursement (Decrements balance + adds Ledger entry)
    PERFORM public.record_disbursement_v1(
        v_loan.group_id,
        v_loan.amount,
        'Loan Disbursement to ' || v_member_name || ' (Loan #' || SUBSTRING(v_loan.id::text, 1, 8) || ')',
        'loan_disbursement',
        v_loan.id
    );
END;
$$;

-- 2. CONSOLIDATED ANALYTICS VIEW
-- Moves expensive memory math (sums/averages) to the database.
DROP VIEW IF EXISTS public.platform_summary_stats;
CREATE VIEW public.platform_summary_stats AS
SELECT
    COUNT(id)::INT AS total_groups,
    COALESCE(SUM(current_members), 0)::INT AS total_members,
    COALESCE(SUM(balance), 0)::NUMERIC(15,2) AS total_balance,
    (SELECT COUNT(DISTINCT province) FROM public.groups WHERE province IS NOT NULL AND province != '')::INT AS total_provinces,
    (SELECT COALESCE(SUM(amount), 0) FROM public.platform_ledger)::NUMERIC(15,2) AS platform_revenue,
    (
        SELECT COALESCE(AVG(composite_risk_score), 0)
        FROM public.group_actuarial_metrics
    )::FLOAT AS average_risk_score
FROM public.groups;

-- 3. OPTIMIZED RLS HELPER
-- Uses a simpler check for membership to reduce subquery depth in large results.
CREATE OR REPLACE FUNCTION public.check_is_member(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE -- Stable allows Postgres to cache result within a single query
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.members
        WHERE group_id = p_group_id AND user_id = auth.uid()
    );
$$;

-- 4. PERFORMANCE INDEXES
-- Accelerated search for members and groups
CREATE INDEX IF NOT EXISTS idx_members_full_name_trgm ON public.members USING gin (full_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_groups_name_trgm ON public.groups USING gin (name gin_trgm_ops);

-- 5. GRANTS
GRANT SELECT ON public.platform_summary_stats TO authenticated, anon;
GRANT EXECUTE ON FUNCTION public.approve_and_disburse_loan_v1(UUID, UUID, TEXT) TO authenticated, service_role;

COMMIT;
-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — ENGINEERING & PERFORMANCE OPTIMIZATIONS
-- Version: 1.0 (May 2026)
--
-- IMPROVEMENTS:
-- 1. Atomic Loan Operations (Prevents desync between status and ledger)
-- 2. Server-Side Aggregate Views (Solves N+1 query problem for analytics)
-- 3. Optimized RLS Helpers (Uses caching where possible)
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

-- 0. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 1. ATOMIC LOAN APPROVAL & DISBURSEMENT
-- This ensures a loan cannot be approved without being recorded in the ledger.
CREATE OR REPLACE FUNCTION public.approve_and_disburse_loan_v1(
    p_loan_id UUID,
    p_admin_id UUID,
    p_payment_method TEXT DEFAULT 'bank'
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_loan public.loans;
    v_member_name TEXT;
BEGIN
    -- 1. Fetch loan details
    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id;
    IF v_loan.id IS NULL THEN RAISE EXCEPTION 'Loan % not found', p_loan_id; END IF;

    -- 2. Check if already active/disbursed
    IF v_loan.status IN ('active', 'completed', 'partially_paid') THEN
        RETURN; -- Already processed
    END IF;

    -- 3. Get member name for ledger
    SELECT full_name INTO v_member_name FROM public.members WHERE id = v_loan.member_id;

    -- 4. Update Loan Status
    UPDATE public.loans
    SET status = 'active',
        reviewed_by = p_admin_id,
        reviewed_at = NOW(),
        updated_at = NOW()
    WHERE id = p_loan_id;

    -- 5. Record Disbursement (Decrements balance + adds Ledger entry)
    PERFORM public.record_disbursement_v1(
        v_loan.group_id,
        v_loan.amount,
        'Loan Disbursement to ' || v_member_name || ' (Loan #' || SUBSTRING(v_loan.id::text, 1, 8) || ')',
        'loan_disbursement',
        v_loan.id
    );
END;
$$;

-- 2. CONSOLIDATED ANALYTICS VIEW
-- Moves expensive memory math (sums/averages) to the database.
DROP VIEW IF EXISTS public.platform_summary_stats;
CREATE VIEW public.platform_summary_stats AS
SELECT
    COUNT(id)::INT AS total_groups,
    COALESCE(SUM(current_members), 0)::INT AS total_members,
    COALESCE(SUM(balance), 0)::NUMERIC(15,2) AS total_balance,
    (SELECT COUNT(DISTINCT province) FROM public.groups WHERE province IS NOT NULL AND province != '')::INT AS total_provinces,
    (SELECT COALESCE(SUM(amount), 0) FROM public.platform_ledger)::NUMERIC(15,2) AS platform_revenue,
    (
        SELECT COALESCE(AVG(composite_risk_score), 0)
        FROM public.group_actuarial_metrics
    )::FLOAT AS average_risk_score
FROM public.groups;

-- 3. OPTIMIZED RLS HELPER
-- Uses a simpler check for membership to reduce subquery depth in large results.
CREATE OR REPLACE FUNCTION public.check_is_member(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE -- Stable allows Postgres to cache result within a single query
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.members
        WHERE group_id = p_group_id AND user_id = auth.uid()
    );
$$;

-- 4. PERFORMANCE INDEXES
-- Accelerated search for members and groups
CREATE INDEX IF NOT EXISTS idx_members_full_name_trgm ON public.members USING gin (full_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_groups_name_trgm ON public.groups USING gin (name gin_trgm_ops);

-- 5. GRANTS
GRANT SELECT ON public.platform_summary_stats TO authenticated, anon;
GRANT EXECUTE ON FUNCTION public.approve_and_disburse_loan_v1(UUID, UUID, TEXT) TO authenticated, service_role;

COMMIT;
-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — ENGINEERING & PERFORMANCE OPTIMIZATIONS
-- Version: 1.0 (May 2026)
--
-- IMPROVEMENTS:
-- 1. Atomic Loan Operations (Prevents desync between status and ledger)
-- 2. Server-Side Aggregate Views (Solves N+1 query problem for analytics)
-- 3. Optimized RLS Helpers (Uses caching where possible)
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

-- 0. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 1. ATOMIC LOAN APPROVAL & DISBURSEMENT
-- This ensures a loan cannot be approved without being recorded in the ledger.
CREATE OR REPLACE FUNCTION public.approve_and_disburse_loan_v1(
    p_loan_id UUID,
    p_admin_id UUID,
    p_payment_method TEXT DEFAULT 'bank'
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_loan public.loans;
    v_member_name TEXT;
BEGIN
    -- 1. Fetch loan details
    SELECT * INTO v_loan FROM public.loans WHERE id = p_loan_id;
    IF v_loan.id IS NULL THEN RAISE EXCEPTION 'Loan % not found', p_loan_id; END IF;

    -- 2. Check if already active/disbursed
    IF v_loan.status IN ('active', 'completed', 'partially_paid') THEN
        RETURN; -- Already processed
    END IF;

    -- 3. Get member name for ledger
    SELECT full_name INTO v_member_name FROM public.members WHERE id = v_loan.member_id;

    -- 4. Update Loan Status
    UPDATE public.loans
    SET status = 'active',
        reviewed_by = p_admin_id,
        reviewed_at = NOW(),
        updated_at = NOW()
    WHERE id = p_loan_id;

    -- 5. Record Disbursement (Decrements balance + adds Ledger entry)
    PERFORM public.record_disbursement_v1(
        v_loan.group_id,
        v_loan.amount,
        'Loan Disbursement to ' || v_member_name || ' (Loan #' || SUBSTRING(v_loan.id::text, 1, 8) || ')',
        'loan_disbursement',
        v_loan.id
    );
END;
$$;

-- 2. CONSOLIDATED ANALYTICS VIEW
-- Moves expensive memory math (sums/averages) to the database.
DROP VIEW IF EXISTS public.platform_summary_stats;
CREATE VIEW public.platform_summary_stats AS
SELECT
    COUNT(id)::INT AS total_groups,
    COALESCE(SUM(current_members), 0)::INT AS total_members,
    COALESCE(SUM(balance), 0)::NUMERIC(15,2) AS total_balance,
    (SELECT COUNT(DISTINCT province) FROM public.groups WHERE province IS NOT NULL AND province != '')::INT AS total_provinces,
    (SELECT COALESCE(SUM(amount), 0) FROM public.platform_ledger)::NUMERIC(15,2) AS platform_revenue,
    (
        SELECT COALESCE(AVG(composite_risk_score), 0)
        FROM public.group_actuarial_metrics
    )::FLOAT AS average_risk_score
FROM public.groups;

-- 3. OPTIMIZED RLS HELPER
-- Uses a simpler check for membership to reduce subquery depth in large results.
CREATE OR REPLACE FUNCTION public.check_is_member(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE -- Stable allows Postgres to cache result within a single query
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.members
        WHERE group_id = p_group_id AND user_id = auth.uid()
    );
$$;

-- 4. PERFORMANCE INDEXES
-- Accelerated search for members and groups
CREATE INDEX IF NOT EXISTS idx_members_full_name_trgm ON public.members USING gin (full_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_groups_name_trgm ON public.groups USING gin (name gin_trgm_ops);

-- 5. GRANTS
GRANT SELECT ON public.platform_summary_stats TO authenticated, anon;
GRANT EXECUTE ON FUNCTION public.approve_and_disburse_loan_v1(UUID, UUID, TEXT) TO authenticated, service_role;

COMMIT;
