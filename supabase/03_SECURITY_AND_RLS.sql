-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — MASTER RLS & SECURITY (Consolidated)
-- Version: 8.1 (Money RPC execute grants withdrawn - October 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. ENABLE RLS GLOBALLY
DO $$
DECLARE
    v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', v_table);
    END LOOP;
END $$;

-- 2. TABLE GRANTS
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO anon, authenticated, service_role;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated, service_role;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO anon, authenticated, service_role;

-- 2b. AUDIT RPCs: TAKE THE ANONYMOUS EXECUTE BACK
-- The four audit report functions are SECURITY DEFINER, so RLS does not filter what they read;
-- they now carry their own platform-admin / group-admin / member checks in
-- 02_FUNCTIONS_AND_TRIGGERS.sql. The blanket grant above still hands EXECUTE to every role, and a
-- guard that relies on auth.uid() can never pass for a request with no JWT at all, so the grant is
-- withdrawn: an unauthenticated caller must not be able to regenerate the platform snapshot, read
-- any group's or member's financial position by id, or rewrite groups.balance with p_fix = true.
-- PUBLIC is revoked as well: PostgreSQL grants EXECUTE to PUBLIC when a function is created, so
-- revoking only the explicit "anon" entry leaves anon executing through the PUBLIC entry.
REVOKE EXECUTE ON FUNCTION
    public.generate_platform_audit_report_v1(),
    public.generate_group_audit_report_v1(UUID),
    public.generate_member_audit_report_v1(UUID),
    public.audit_and_fix_group_balance_v1(UUID, BOOLEAN)
FROM PUBLIC, anon;

REVOKE EXECUTE ON FUNCTION public.settle_expired_loan_v2(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.settle_expired_loan_v2(UUID) TO authenticated, service_role;

-- Retire the pre-v2 settlement entry point. Its caller-supplied member, group, and amount
-- parameters are not validated as one atomic settlement and must not remain an RPC bypass.
REVOKE EXECUTE ON FUNCTION
    public.record_loan_settlement_extraction_v1(UUID, UUID, NUMERIC, UUID)
FROM PUBLIC, anon, authenticated, service_role;

-- 2c. MONEY RPCs: THE SAME TREATMENT
-- Every function below either moves a balance or rewrites a ledger, and the blanket grant in
-- section 2 handed all of them to `anon` as well. Each one now carries its own caller check
-- (02_FUNCTIONS_AND_TRIGGERS.sql, [SEC_002] *_ACCESS_DENIED), and the anonymous grant is withdrawn
-- so a request with no JWT cannot even reach that check.
--
-- Two lists, and the difference between them is deliberate:
--   * The first group stays granted to `authenticated`, because a signed-in member legitimately
--     calls it: paying a contribution, repaying a loan, posting an allocated payment.
--   * The second group is withdrawn from `authenticated` too. These are the ledger primitives and
--     maintenance jobs that only ever run from inside a SECURITY DEFINER wrapper or from the
--     scheduler. A nested call is authorised against the wrapper's owner role, not the session
--     role, so revoking the client grant does not break the wrappers - it only closes the HTTP
--     door on a function the app has no business calling by name.
REVOKE EXECUTE ON FUNCTION
    public.record_platform_revenue_v2(NUMERIC, TEXT, TEXT, TEXT, UUID),
    public.internal_post_contribution(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT, TEXT),
    public.record_contribution_v2(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT, TEXT),
    public.record_loan_repayment_v1(UUID, UUID, UUID, NUMERIC, TEXT, TEXT),
    public.approve_and_disburse_loan_v2(UUID, UUID, TEXT),
    public.complete_payout_v2(UUID, UUID, TEXT),
    public.process_rosca_payout_v2(UUID, UUID, NUMERIC, JSONB, NUMERIC, JSONB),
    public.process_rosca_payout_v3(UUID, UUID, NUMERIC, JSONB, NUMERIC, JSONB, TEXT),
    public.pay_platform_fee_v2(UUID, NUMERIC, TEXT, TEXT, BOOLEAN),
    public.reconcile_bank_transaction_v1(UUID, UUID)
FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION
    public.record_platform_revenue_v2(NUMERIC, TEXT, TEXT, TEXT, UUID),
    public.internal_post_contribution(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT, TEXT),
    public.record_contribution_v2(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT, TEXT),
    public.record_loan_repayment_v1(UUID, UUID, UUID, NUMERIC, TEXT, TEXT),
    public.approve_and_disburse_loan_v2(UUID, UUID, TEXT),
    public.complete_payout_v2(UUID, UUID, TEXT),
    public.process_rosca_payout_v2(UUID, UUID, NUMERIC, JSONB, NUMERIC, JSONB),
    public.process_rosca_payout_v3(UUID, UUID, NUMERIC, JSONB, NUMERIC, JSONB, TEXT),
    public.pay_platform_fee_v2(UUID, NUMERIC, TEXT, TEXT, BOOLEAN),
    public.reconcile_bank_transaction_v1(UUID, UUID)
TO authenticated, service_role;

REVOKE EXECUTE ON FUNCTION
    public.increment_group_balance(UUID, NUMERIC, TEXT, TEXT, TEXT, UUID),
    public.recompute_ledger_running_balances_v2(UUID),
    public.perform_group_reconciliation_v1(UUID),
    public.consolidate_group_ledger_v1(UUID),
    public.create_ledger_checkpoint_v1(UUID),
    public.automate_monthly_billing_v1(),
    public.simulate_bulk_activity_v2(UUID, INT, INT),
    public.record_platform_revenue_v1(NUMERIC, TEXT, TEXT, TEXT, UUID)
FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION
    public.increment_group_balance(UUID, NUMERIC, TEXT, TEXT, TEXT, UUID),
    public.recompute_ledger_running_balances_v2(UUID),
    public.perform_group_reconciliation_v1(UUID),
    public.consolidate_group_ledger_v1(UUID),
    public.create_ledger_checkpoint_v1(UUID),
    public.automate_monthly_billing_v1(),
    public.simulate_bulk_activity_v2(UUID, INT, INT),
    public.record_platform_revenue_v1(NUMERIC, TEXT, TEXT, TEXT, UUID)
TO service_role;

-- Re-derives aggregates from the ledger, so it moves no money, but the platform console calls it
-- over HTTP: the anonymous grant goes, the signed-in one stays.
REVOKE EXECUTE ON FUNCTION public.recalculate_all_group_financials_v3(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.recalculate_all_group_financials_v3(UUID) TO authenticated, service_role;

-- Cross-group member search: the platform console's own endpoint, and the console is signed in.
REVOKE EXECUTE ON FUNCTION public.search_members_v1(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.search_members_v1(TEXT) TO authenticated, service_role;

-- `is_own_member` is the predicate the money guards call, not an endpoint. It reads only the caller's
-- own row through auth.uid(), so an anonymous request gets FALSE and no data, but it should not be
-- part of the callable anonymous surface either. Wrappers keep working: inside a SECURITY DEFINER
-- function PostgreSQL authorises the nested call against the wrapper's owner, not the session role.
REVOKE EXECUTE ON FUNCTION public.is_own_member(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_own_member(UUID) TO authenticated, service_role;

-- 2d. THE ENTRY POINTS THE CLIENT NAMES BUT THE DATABASE DID NOT HAVE
-- close_poll_v1, pay_burial_claim_v1 and record_disbursement_v1 are called by name from :data and
-- existed in no master, so those flows failed with PGRST202 before they were declared in
-- 02_FUNCTIONS_AND_TRIGGERS.sql. PostgreSQL grants EXECUTE to PUBLIC when a function is created and
-- the blanket grant in section 2 hands it to anon as well, so each one is taken back here: all three
-- move money or decide governance for a group, and all three are legitimately called by a signed-in
-- admin, so `authenticated` keeps them. record_disbursement_v1 is reachable by a member-adjacent
-- caller in name only - its own body refuses anyone who is not the group's admin, a platform admin,
-- or the service role.
REVOKE EXECUTE ON FUNCTION
    public.close_poll_v1(UUID),
    public.pay_burial_claim_v1(UUID, UUID, TEXT),
    public.record_disbursement_v1(UUID, NUMERIC, TEXT, TEXT, TEXT, UUID)
FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION
    public.close_poll_v1(UUID),
    public.pay_burial_claim_v1(UUID, UUID, TEXT),
    public.record_disbursement_v1(UUID, NUMERIC, TEXT, TEXT, TEXT, UUID)
TO authenticated, service_role;

-- 3. TIER 1: PUBLIC DISCOVERY
DROP POLICY IF EXISTS "Groups: Discover public" ON public.groups;
CREATE POLICY "Groups: Discover public" ON public.groups FOR SELECT TO anon, authenticated USING (is_public = true);

DROP POLICY IF EXISTS "Platform: Public providers" ON public.financial_providers;
CREATE POLICY "Platform: Public providers" ON public.financial_providers FOR SELECT TO authenticated USING (is_active = true);

-- 4. TIER 2: MEMBER-PRIVATE (Self-Service)
-- Profiles
DROP POLICY IF EXISTS "Profiles: Owner manage" ON public.profiles;
CREATE POLICY "Profiles: Owner manage" ON public.profiles FOR ALL TO authenticated USING (auth.uid() = id);

-- Standardized Member Access for Private Data
DO $$
DECLARE v_t text;
BEGIN
    FOREACH v_t IN ARRAY ARRAY['contributions', 'payments', 'loans', 'loan_repayments', 'payouts', 'beneficiary_payout_claims', 'member_documents', 'notifications', 'group_poll_votes', 'rosca_rotation_executions'] LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Member: View own" ON public.%I', v_t);
        EXECUTE format('CREATE POLICY "Member: View own" ON public.%I FOR SELECT TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()))', v_t);

        -- Allow members to manage their own votes
        IF v_t = 'group_poll_votes' THEN
            EXECUTE format('DROP POLICY IF EXISTS "Member: Manage own votes" ON public.%I', v_t);
            EXECUTE format('CREATE POLICY "Member: Manage own votes" ON public.%I FOR ALL TO authenticated USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()))', v_t);
        END IF;
    END LOOP;
END $$;

-- 5. TIER 3: GROUP-ADMIN (Authoritative)
-- Groups
DROP POLICY IF EXISTS "Groups: Admin manage" ON public.groups;
CREATE POLICY "Groups: Admin manage" ON public.groups FOR ALL TO authenticated USING (admin_user_id = auth.uid());

DROP POLICY IF EXISTS "Groups: Member view" ON public.groups;
CREATE POLICY "Groups: Member view" ON public.groups FOR SELECT TO authenticated USING (public.is_group_member(id));

-- Standardized Admin Access
DO $$
DECLARE v_t text;
BEGIN
    FOREACH v_t IN ARRAY ARRAY[
        'members', 'contributions', 'payments', 'loans', 'loan_repayments', 'payouts',
        'beneficiary_payout_claims', 'member_documents', 'notifications', 'group_polls',
        'group_poll_votes', 'group_actuarial_metrics',
        'group_health_scores', 'ledger_checkpoints', 'group_ledger',
        'rosca_rotation_executions'
    ] LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Group Admin: Manage group" ON public.%I', v_t);
        EXECUTE format('CREATE POLICY "Group Admin: Manage group" ON public.%I FOR ALL TO authenticated USING (public.is_group_admin(group_id))', v_t);
    END LOOP;
END $$;

DROP POLICY IF EXISTS "Group Admin: Manage group" ON public.group_poll_options;
CREATE POLICY "Group Admin: Manage group" ON public.group_poll_options FOR ALL TO authenticated USING (public.is_group_admin((SELECT group_id FROM public.group_polls WHERE id = poll_id)));

-- 6. TIER 4: HIGH-SECURITY (MFA MANDATORY)
-- Sensitive tables require AAL2 for any mutation.
DO $$
DECLARE v_t text;
BEGIN
    FOREACH v_t IN ARRAY ARRAY['audit_logs', 'reconciliations', 'platform_settings', 'platform_fee_config', 'member_behavior_track', 'fraud_detection_events', 'platform_audit_reports'] LOOP
        EXECUTE format('DROP POLICY IF EXISTS "MFA Mandatory: Sensitive mutation" ON public.%I', v_t);
        EXECUTE format('CREATE POLICY "MFA Mandatory: Sensitive mutation" ON public.%I FOR ALL TO authenticated USING (public.is_aal2()) WITH CHECK (public.is_aal2())', v_t);

        -- Admin can still SELECT without MFA (except audit_logs and fraud_events which are restricted)
        IF v_t NOT IN ('audit_logs', 'fraud_detection_events') THEN
            EXECUTE format('DROP POLICY IF EXISTS "Admin: View sensitive" ON public.%I', v_t);
            IF v_t IN ('platform_settings', 'platform_fee_config', 'platform_audit_reports') THEN
                EXECUTE format('CREATE POLICY "Admin: View sensitive" ON public.%I FOR SELECT TO authenticated USING (public.is_platform_admin())', v_t);
            ELSE
                EXECUTE format('CREATE POLICY "Admin: View sensitive" ON public.%I FOR SELECT TO authenticated USING (public.is_group_admin(group_id) OR public.is_platform_admin())', v_t);
            END IF;
        END IF;
    END LOOP;
END $$;

-- 7. TIER 5: PLATFORM-WIDE BYPASS
-- Hardened: Applies to all public tables.
DO $$
DECLARE v_table text;
BEGIN
    FOR v_table IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
        EXECUTE format('DROP POLICY IF EXISTS "Platform Admin Bypass" ON public.%I', v_table);

        -- Audit Logs and Fraud Events require MFA even for Platform Admin
        IF v_table IN ('audit_logs', 'fraud_detection_events', 'reconciliations', 'platform_settings', 'platform_fee_config') THEN
            EXECUTE format('CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin() AND public.is_aal2()) WITH CHECK (public.is_platform_admin() AND public.is_aal2())', v_table);
        ELSE
            EXECUTE format('CREATE POLICY "Platform Admin Bypass" ON public.%I FOR ALL TO authenticated USING (public.is_platform_admin()) WITH CHECK (public.is_platform_admin())', v_table);
        END IF;
    END LOOP;
END $$;

-- 8. SPECIAL CASES
-- Audit Logs: Allow users to see logs where they are the actor or the target
DROP POLICY IF EXISTS "Audit Logs: View personal" ON public.audit_logs;
CREATE POLICY "Audit Logs: View personal" ON public.audit_logs FOR SELECT TO authenticated
USING (actor_id = auth.uid()::text OR target_member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

-- Bank Transactions: Admin only
DROP POLICY IF EXISTS "Bank Transactions: Admin manage" ON public.bank_transactions;
CREATE POLICY "Bank Transactions: Admin manage" ON public.bank_transactions FOR ALL TO authenticated USING (public.is_group_admin(group_id));
