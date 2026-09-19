-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — VISIBILITY DIAGNOSTIC RPC
-- Version: 1.0 (August 2, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * Diagnostic function for Support Mode.
 * Returns table row counts as seen by the current session.
 */
CREATE OR REPLACE FUNCTION public.get_support_visibility_diagnostic(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_res JSONB;
BEGIN
    -- Only allow Platform Admins to run this
    IF NOT public.is_platform_admin() THEN
        RAISE EXCEPTION 'UNAUTHORIZED: Only platform admins can run diagnostics.';
    END IF;

    SELECT jsonb_build_object(
        'current_user', auth.uid(),
        'current_email', auth.jwt() ->> 'email',
        'is_platform_admin', public.is_platform_admin(),
        'counts', jsonb_build_object(
            'members', (SELECT count(*) FROM public.members WHERE group_id = p_group_id),
            'ledger', (SELECT count(*) FROM public.group_ledger WHERE group_id = p_group_id),
            'contributions', (SELECT count(*) FROM public.contributions WHERE group_id = p_group_id),
            'loans', (SELECT count(*) FROM public.loans WHERE group_id = p_group_id),
            'payouts', (SELECT count(*) FROM public.payouts WHERE group_id = p_group_id)
        ),
        'group_metadata', (SELECT jsonb_build_object(
            'balance', balance,
            'current_members', current_members,
            'fee_status', fee_status
        ) FROM public.groups WHERE id = p_group_id)
    ) INTO v_res;

    RETURN v_res;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_support_visibility_diagnostic(UUID) TO authenticated;
