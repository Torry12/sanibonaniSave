-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — CONSOLIDATED RLS ALIGNMENT
-- Date: 2026-05-15
-- Purpose:
--   Ensure RLS policies align with app structure for Group Admins and Members
--   across all feature tables (Loans, Payouts, Notifications, etc.)
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

-- 1. BENEFICIARIES
DROP POLICY IF EXISTS "Beneficiaries: Member view own" ON public.beneficiaries;
CREATE POLICY "Beneficiaries: Member view own" ON public.beneficiaries
FOR SELECT TO authenticated
USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "Beneficiaries: Admin view group" ON public.beneficiaries;
CREATE POLICY "Beneficiaries: Admin view group" ON public.beneficiaries
FOR SELECT TO authenticated
USING (public.is_group_admin(group_id));

DROP POLICY IF EXISTS "Beneficiaries: Member manage own" ON public.beneficiaries;
CREATE POLICY "Beneficiaries: Member manage own" ON public.beneficiaries
FOR ALL TO authenticated
USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()))
WITH CHECK (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "Beneficiaries: Admin manage group" ON public.beneficiaries;
CREATE POLICY "Beneficiaries: Admin manage group" ON public.beneficiaries
FOR ALL TO authenticated
USING (public.is_group_admin(group_id))
WITH CHECK (public.is_group_admin(group_id));


-- 2. CONTRIBUTIONS
DROP POLICY IF EXISTS "Contributions: Member view own" ON public.contributions;
CREATE POLICY "Contributions: Member view own" ON public.contributions
FOR SELECT TO authenticated
USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "Contributions: Admin view group" ON public.contributions;
CREATE POLICY "Contributions: Admin view group" ON public.contributions
FOR SELECT TO authenticated
USING (public.is_group_admin(group_id));


-- 3. PAYMENTS
DROP POLICY IF EXISTS "Payments: Member view own" ON public.payments;
CREATE POLICY "Payments: Member view own" ON public.payments
FOR SELECT TO authenticated
USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "Payments: Admin view group" ON public.payments;
CREATE POLICY "Payments: Admin view group" ON public.payments
FOR SELECT TO authenticated
USING (public.is_group_admin(group_id));


-- 4. LOANS & REPAYMENTS
DROP POLICY IF EXISTS "Loans: Member view own" ON public.loans;
CREATE POLICY "Loans: Member view own" ON public.loans
FOR SELECT TO authenticated
USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "Loans: Admin view group" ON public.loans;
CREATE POLICY "Loans: Admin view group" ON public.loans
FOR SELECT TO authenticated
USING (public.is_group_admin(group_id));

DROP POLICY IF EXISTS "Loans: Member request" ON public.loans;
CREATE POLICY "Loans: Member request" ON public.loans
FOR INSERT TO authenticated
WITH CHECK (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "Loan Repayments: Member view own" ON public.loan_repayments;
CREATE POLICY "Loan Repayments: Member view own" ON public.loan_repayments
FOR SELECT TO authenticated
USING (member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid()));

DROP POLICY IF EXISTS "Loan Repayments: Admin view group" ON public.loan_repayments;
CREATE POLICY "Loan Repayments: Admin view group" ON public.loan_repayments
FOR SELECT TO authenticated
USING (public.is_group_admin(group_id));


-- 5. NOTIFICATIONS
DROP POLICY IF EXISTS "Notifications: Member view" ON public.notifications;
CREATE POLICY "Notifications: Member view" ON public.notifications
FOR SELECT TO authenticated
USING (
    member_id IN (SELECT id FROM public.members WHERE user_id = auth.uid())
    OR (member_id IS NULL AND public.is_group_member(group_id))
);

DROP POLICY IF EXISTS "Notifications: Admin manage" ON public.notifications;
CREATE POLICY "Notifications: Admin manage" ON public.notifications
FOR ALL TO authenticated
USING (public.is_group_admin(group_id))
WITH CHECK (public.is_group_admin(group_id));


-- 6. PAYOUTS
DROP POLICY IF EXISTS "Payouts: Admin manage" ON public.payouts;
CREATE POLICY "Payouts: Admin manage" ON public.payouts
FOR ALL TO authenticated
USING (public.is_group_admin(group_id))
WITH CHECK (public.is_group_admin(group_id));


-- 7. GROUP LEDGER
DROP POLICY IF EXISTS "Ledger: Admin view group" ON public.group_ledger;
CREATE POLICY "Ledger: Admin view group" ON public.group_ledger
FOR SELECT TO authenticated
USING (public.is_group_admin(group_id));


-- 8. AUDIT LOGS
DROP POLICY IF EXISTS "Audit Logs: Admin view group" ON public.audit_logs;
CREATE POLICY "Audit Logs: Admin view group" ON public.audit_logs
FOR SELECT TO authenticated
USING (public.is_group_admin(target_group_id));


-- 9. GROUP ACTUARIAL METRICS
DROP POLICY IF EXISTS "Metrics: Admin view group" ON public.group_actuarial_metrics;
CREATE POLICY "Metrics: Admin view group" ON public.group_actuarial_metrics
FOR SELECT TO authenticated
USING (public.is_group_admin(group_id));


-- 10. POLICIES (Group rules/insurance)
DROP POLICY IF EXISTS "Policies: View" ON public.policies;
CREATE POLICY "Policies: View" ON public.policies
FOR SELECT TO authenticated
USING (public.is_group_member(group_id) OR public.is_group_admin(group_id));

DROP POLICY IF EXISTS "Policies: Admin manage" ON public.policies;
CREATE POLICY "Policies: Admin manage" ON public.policies
FOR ALL TO authenticated
USING (public.is_group_admin(group_id))
WITH CHECK (public.is_group_admin(group_id));

COMMIT;
