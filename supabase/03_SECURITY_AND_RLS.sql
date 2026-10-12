-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — MASTER RLS & SECURITY (Consolidated)
-- Version: 8.0 (Tiered Hardened Security - August 2026)
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
    FOREACH v_t IN ARRAY ARRAY['contributions', 'payments', 'loans', 'loan_repayments', 'payouts', 'beneficiary_payout_claims', 'member_documents', 'notifications', 'group_poll_votes'] LOOP
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
        'group_health_scores', 'ledger_checkpoints', 'group_ledger'
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
