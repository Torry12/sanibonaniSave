-- SanibonaniSave hotfix: record_contribution_v1 type mapping and grants
-- Date: 2026-04-19

CREATE OR REPLACE FUNCTION public.record_contribution_v1(
    p_member_id UUID,
    p_group_id UUID,
    p_amount NUMERIC,
    p_due_date DATE,
    p_paid_at TIMESTAMPTZ,
    p_status TEXT,
    p_yoco_tx_id TEXT DEFAULT NULL,
    p_type TEXT DEFAULT 'contribution'
) RETURNS public.contributions AS $$
DECLARE
    v_contribution public.contributions;
BEGIN
    -- IMPORTANT: p_type maps to contributions.type, not payment_method.
    INSERT INTO public.contributions (
        member_id,
        group_id,
        amount,
        due_date,
        paid_at,
        status,
        yoco_transaction_id,
        type,
        payment_method
    ) VALUES (
        p_member_id,
        p_group_id,
        p_amount,
        p_due_date,
        p_paid_at,
        p_status,
        p_yoco_tx_id,
        p_type,
        'yoco'
    ) RETURNING * INTO v_contribution;

    UPDATE public.members
    SET total_contributions = COALESCE(total_contributions, 0) + 1,
        total_paid = COALESCE(total_paid, 0) + p_amount,
        updated_at = NOW()
    WHERE id = p_member_id;

    UPDATE public.groups
    SET balance = COALESCE(balance, 0) + p_amount,
        updated_at = NOW()
    WHERE id = p_group_id;

    RETURN v_contribution;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION public.record_contribution_v1(UUID, UUID, NUMERIC, DATE, TIMESTAMPTZ, TEXT, TEXT, TEXT)
TO authenticated, service_role;
