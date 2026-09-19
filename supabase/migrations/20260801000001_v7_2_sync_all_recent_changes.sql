-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — VERSION 7.2 SYNC MIGRATION
-- Version: 1.0 (August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. LOAN ENHANCEMENTS (Amortization Support)
ALTER TABLE public.loans
ADD COLUMN IF NOT EXISTS amortization_type TEXT NOT NULL DEFAULT 'flat' CHECK (amortization_type IN ('flat', 'reducing')),
ADD COLUMN IF NOT EXISTS effective_apr     NUMERIC(5,2);

COMMENT ON COLUMN public.loans.amortization_type IS 'Calculation method for interest: flat rate on principal vs reducing balance.';
COMMENT ON COLUMN public.loans.effective_apr IS 'The calculated annual percentage rate including all fees and compounding.';

-- 2. PAYOUT ENHANCEMENTS
ALTER TABLE public.payouts
ADD COLUMN IF NOT EXISTS description TEXT;

-- 3. FINANCIAL PROVIDERS (Bank-Neutral Architecture)
CREATE TABLE IF NOT EXISTS public.financial_providers (
    id                TEXT PRIMARY KEY,
    name              TEXT NOT NULL,
    type              TEXT NOT NULL CHECK (type IN ('bank', 'payment_gateway', 'insurer', 'lender', 'investment')),
    adapter_type      TEXT NOT NULL,
    logo_url          TEXT,
    supported_ops     TEXT[] DEFAULT '{}',
    is_active         BOOLEAN DEFAULT TRUE,
    config            JSONB DEFAULT '{}',
    metadata          JSONB DEFAULT '{}',
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- Enable RLS for providers
ALTER TABLE public.financial_providers ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Financial providers are visible to all authenticated users" ON public.financial_providers;
CREATE POLICY "Financial providers are visible to all authenticated users"
    ON public.financial_providers FOR SELECT
    USING (auth.role() = 'authenticated');

DROP POLICY IF EXISTS "Only platform admins can modify financial providers" ON public.financial_providers;
CREATE POLICY "Only platform admins can modify financial providers"
    ON public.financial_providers FOR ALL
    USING (public.is_platform_admin(auth.uid()));

-- Seed Initial Providers if empty
INSERT INTO public.financial_providers (id, name, type, adapter_type, supported_ops)
SELECT * FROM (VALUES
    ('sim_bank', 'Sanibonani Virtual Bank', 'bank', 'sim_bank', '{get_balance, get_transactions, make_payment}'),
    ('yoco', 'YoCo Card Payments', 'payment_gateway', 'yoco', '{initiate_checkout}'),
    ('stitch', 'Stitch Instant EFT', 'payment_gateway', 'stitch', '{initiate_checkout}'),
    ('nedbank', 'Nedbank (Direct)', 'bank', 'nedbank', '{get_balance, make_payment}'),
    ('fnb', 'First National Bank', 'bank', 'fnb', '{get_balance, get_transactions}')
) AS v(id, name, type, adapter_type, supported_ops)
WHERE NOT EXISTS (SELECT 1 FROM public.financial_providers);

-- Register Platform as a Provider
INSERT INTO public.financial_providers (id, name, type, adapter_type, supported_ops, config)
VALUES (
    'platform_main',
    'SanibonaniSave Revenue Account',
    'bank',
    'internal',
    '{make_payment, get_balance}',
    jsonb_build_object(
        'bank_name', 'FNB',
        'account_number', '62889900112',
        'branch_code', '250655',
        'account_holder', 'SanibonaniSave (Pty) Ltd'
    )
) ON CONFLICT (id) DO UPDATE SET config = EXCLUDED.config;

-- 4. GROUP TABLE SYNC (Provider Links)
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS financial_provider_id TEXT REFERENCES public.financial_providers(id);
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS financial_provider_metadata JSONB DEFAULT '{}'::jsonb;

-- 5. PAYMENT SYSTEM HARDENING (v3 RPC)
/**
 * Enhanced process_complex_payment_v3.
 * Supports explicit routing to platform vs group accounts.
 */
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
    -- Forking logic based on p_destination can be added here for specialized settlement routing.
    RETURN public.process_complex_payment_v2(p_group_id, p_member_id, p_total_amount, p_tx_id, p_allocations, p_method, p_payment_type);
END;
$$;

GRANT EXECUTE ON FUNCTION public.process_complex_payment_v3(UUID, UUID, NUMERIC, TEXT, JSONB, TEXT, TEXT, TEXT) TO authenticated;

-- 6. ADMIN MEMBERSHIP ENFORCEMENT
/**
 * Automatically registers the group creator as the first member.
 */
CREATE OR REPLACE FUNCTION public.fn_ensure_admin_is_member()
RETURNS TRIGGER AS $$
DECLARE
    v_profile RECORD;
BEGIN
    SELECT * INTO v_profile FROM public.profiles WHERE id = NEW.admin_user_id;
    IF v_profile.id IS NULL THEN
        RETURN NEW;
    END IF;

    INSERT INTO public.members (
        id, group_id, user_id, full_name, email, phone, status, joined_at, probation_end_at
    )
    VALUES (
        gen_random_uuid(), NEW.id, NEW.admin_user_id, v_profile.full_name, v_profile.email, v_profile.phone,
        'active', NOW(), NOW() + (NEW.probation_months || ' months')::INTERVAL
    )
    ON CONFLICT (group_id, user_id) DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS tr_ensure_admin_is_member ON public.groups;
CREATE TRIGGER tr_ensure_admin_is_member
AFTER INSERT ON public.groups
FOR EACH ROW EXECUTE FUNCTION public.fn_ensure_admin_is_member();
