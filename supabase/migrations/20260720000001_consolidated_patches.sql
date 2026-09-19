-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — CONSOLIDATED PATCHES MIGRATION
-- Version: 1.0 (July 2026)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * Enhanced Platform Fee payment.
 * Supports both direct admin payments and group-fund payments.
 */
CREATE OR REPLACE FUNCTION public.pay_platform_fee_v1(
    p_group_id UUID,
    p_amount NUMERIC,
    p_fee_type TEXT, -- 'registration' or 'monthly'
    p_tx_id TEXT DEFAULT NULL,
    p_from_group_fund BOOLEAN DEFAULT TRUE
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_new_group_balance NUMERIC;
BEGIN
    -- 1. Deduct from group balance if requested
    IF p_from_group_fund THEN
        SELECT public.increment_group_balance(
            p_group_id,
            -p_amount,
            'Platform ' || p_fee_type || ' fee payment',
            'platform_fee',
            p_tx_id
        ) INTO v_new_group_balance;
    ELSE
        SELECT balance INTO v_new_group_balance FROM public.groups WHERE id = p_group_id;
    END IF;

    -- 2. Record as platform revenue
    PERFORM public.record_platform_revenue_v1(
        p_amount,
        'Platform ' || p_fee_type || ' fee from group ' || p_group_id,
        p_fee_type,
        p_tx_id
    );

    -- 3. Update group status
    UPDATE public.groups
    SET registration_paid = CASE WHEN p_fee_type = 'registration' THEN TRUE ELSE registration_paid END,
        fee_status = 'paid',
        is_platform_suspended = FALSE,
        updated_at = NOW()
    WHERE id = p_group_id;

    RETURN v_new_group_balance;
END;
$$;

-- Alias v2 to v1 logic for now to ensure compatibility
CREATE OR REPLACE FUNCTION public.pay_platform_fee_v2(
    p_group_id UUID,
    p_amount NUMERIC,
    p_fee_type TEXT,
    p_tx_id TEXT DEFAULT NULL,
    p_from_group_fund BOOLEAN DEFAULT TRUE
) RETURNS NUMERIC AS $$
BEGIN
    RETURN public.pay_platform_fee_v1(p_group_id, p_amount, p_fee_type, p_tx_id, p_from_group_fund);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
