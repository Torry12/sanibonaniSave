-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — BANK-NEUTRAL ARCHITECTURE PROVISIONING
-- Version: 1.0 (Unified Financial Providers)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. FINANCIAL PROVIDERS TABLE
CREATE TABLE IF NOT EXISTS public.financial_providers (
    id                TEXT PRIMARY KEY,
    name              TEXT NOT NULL,
    type              TEXT NOT NULL CHECK (type IN ('bank', 'payment_gateway', 'insurer', 'lender', 'investment')),
    adapter_type      TEXT NOT NULL, -- Internal key to route to the correct adapter module
    logo_url          TEXT,
    supported_ops     TEXT[] DEFAULT '{}', -- List of FinancialOperationType keys
    is_active         BOOLEAN DEFAULT TRUE,
    config            JSONB DEFAULT '{}', -- Encrypted or sensitive config keys (server-side only)
    metadata          JSONB DEFAULT '{}', -- Public metadata
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- 2. ENABLE RLS
ALTER TABLE public.financial_providers ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Financial providers are visible to all authenticated users"
    ON public.financial_providers FOR SELECT
    USING (auth.role() = 'authenticated');

CREATE POLICY "Only platform admins can modify financial providers"
    ON public.financial_providers FOR ALL
    USING (public.is_platform_admin(auth.uid()));

-- 3. SEED INITIAL PROVIDERS
INSERT INTO public.financial_providers (id, name, type, adapter_type, supported_ops)
VALUES
    ('sim_bank', 'Sanibonani Virtual Bank', 'bank', 'sim_bank', '{get_balance, get_transactions, make_payment}'),
    ('yoco', 'YoCo Card Payments', 'payment_gateway', 'yoco', '{initiate_checkout}'),
    ('stitch', 'Stitch Instant EFT', 'payment_gateway', 'stitch', '{initiate_checkout}'),
    ('nedbank', 'Nedbank (Direct)', 'bank', 'nedbank', '{get_balance, make_payment}'),
    ('fnb', 'First National Bank', 'bank', 'fnb', '{get_balance, get_transactions}');

-- 4. UPDATE GROUPS TABLE (Handled by Room migration in app, but for reference here)
-- ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS financial_provider_id TEXT REFERENCES public.financial_providers(id);
-- ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS financial_provider_metadata JSONB DEFAULT '{}';

-- 5. FUNCTION TO RESOLVE GROUP PROVIDER
CREATE OR REPLACE FUNCTION public.get_group_financial_provider(p_group_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_result JSONB;
BEGIN
    SELECT jsonb_build_object(
        'provider', to_jsonb(p),
        'metadata', g.financial_provider_metadata
    ) INTO v_result
    FROM public.groups g
    JOIN public.financial_providers p ON g.financial_provider_id = p.id
    WHERE g.id = p_group_id;

    RETURN v_result;
END;
$$;
