-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — CONSOLIDATED PRODUCTION UPDATE (JULY 30, 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- [PATCH 1] BACKUP SYSTEM SETUP
INSERT INTO storage.buckets (id, name, public)
VALUES ('backups', 'backups', false)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY "Platform Admins can manage backups"
ON storage.objects FOR ALL
TO authenticated
USING (
  bucket_id = 'backups' AND (
    (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'platform_admin'
  )
)
WITH CHECK (
  bucket_id = 'backups' AND (
    (SELECT role FROM public.profiles WHERE id = auth.uid()) = 'platform_admin'
  )
);

GRANT ALL ON TABLE storage.objects TO service_role;
GRANT ALL ON TABLE storage.buckets TO service_role;

-- [PATCH 2] ENFORCE ADMIN MEMBERSHIP
CREATE OR REPLACE FUNCTION public.fn_ensure_admin_is_member()
RETURNS TRIGGER AS $$
DECLARE
    v_profile RECORD;
BEGIN
    SELECT * INTO v_profile FROM public.profiles WHERE id = NEW.admin_user_id;
    IF v_profile.id IS NULL THEN
        RAISE WARNING 'Group admin profile % not found. Member registration skipped.', NEW.admin_user_id;
        RETURN NEW;
    END IF;
    INSERT INTO public.members (id, group_id, user_id, full_name, email, phone, status, joined_at, probation_end_at)
    VALUES (gen_random_uuid(), NEW.id, NEW.admin_user_id, v_profile.full_name, v_profile.email, v_profile.phone, 'active', NOW(), NOW() + (NEW.probation_months || ' months')::INTERVAL)
    ON CONFLICT (group_id, user_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS tr_ensure_admin_is_member ON public.groups;
CREATE TRIGGER tr_ensure_admin_is_member AFTER INSERT ON public.groups FOR EACH ROW EXECUTE FUNCTION public.fn_ensure_admin_is_member();

INSERT INTO public.members (id, group_id, user_id, full_name, email, phone, status, joined_at, probation_end_at)
SELECT gen_random_uuid(), g.id, g.admin_user_id, p.full_name, p.email, p.phone, 'active', g.created_at, g.created_at + (g.probation_months || ' months')::INTERVAL
FROM public.groups g JOIN public.profiles p ON g.admin_user_id = p.id LEFT JOIN public.members m ON g.id = m.group_id AND g.admin_user_id = m.user_id
WHERE m.id IS NULL;

-- [PATCH 3] BANK-NEUTRAL ARCHITECTURE PROVISIONING
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

ALTER TABLE public.financial_providers ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Financial providers are visible to all authenticated users" ON public.financial_providers;
CREATE POLICY "Financial providers are visible to all authenticated users" ON public.financial_providers FOR SELECT USING (auth.role() = 'authenticated');
DROP POLICY IF EXISTS "Only platform admins can modify financial providers" ON public.financial_providers;
CREATE POLICY "Only platform admins can modify financial providers" ON public.financial_providers FOR ALL USING (public.is_platform_admin(auth.uid()));

INSERT INTO public.financial_providers (id, name, type, adapter_type, supported_ops)
VALUES
    ('sim_bank', 'Sanibonani Virtual Bank', 'bank', 'sim_bank', '{get_balance, get_transactions, make_payment}'),
    ('yoco', 'YoCo Card Payments', 'payment_gateway', 'yoco', '{initiate_checkout}'),
    ('stitch', 'Stitch Instant EFT', 'payment_gateway', 'stitch', '{initiate_checkout}'),
    ('nedbank', 'Nedbank (Direct)', 'bank', 'nedbank', '{get_balance, make_payment}'),
    ('fnb', 'First National Bank', 'bank', 'fnb', '{get_balance, get_transactions}')
ON CONFLICT (id) DO NOTHING;

ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS financial_provider_id TEXT REFERENCES public.financial_providers(id);
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS financial_provider_metadata JSONB DEFAULT '{}'::jsonb;

CREATE OR REPLACE FUNCTION public.get_group_financial_provider(p_group_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE v_result JSONB;
BEGIN
    SELECT jsonb_build_object('provider', to_jsonb(p), 'metadata', g.financial_provider_metadata) INTO v_result
    FROM public.groups g JOIN public.financial_providers p ON g.financial_provider_id = p.id WHERE g.id = p_group_id;
    RETURN v_result;
END;
$$;

-- [PATCH 4] PLATFORM ACCOUNT PROVISIONING
INSERT INTO public.financial_providers (id, name, type, adapter_type, supported_ops, config)
VALUES ('platform_main', 'SanibonaniSave Revenue Account', 'bank', 'internal', '{make_payment, get_balance}',
    jsonb_build_object('bank_name', 'FNB', 'account_number', '62889900112', 'branch_code', '250655', 'account_holder', 'SanibonaniSave (Pty) Ltd'))
ON CONFLICT (id) DO UPDATE SET config = EXCLUDED.config;

INSERT INTO public.platform_settings (key, value) VALUES ('platform_bank_name_code', 1) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;
INSERT INTO public.platform_settings (key, value) VALUES ('platform_account_number', 62889900112) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;
INSERT INTO public.platform_settings (key, value) VALUES ('platform_branch_code', 250655) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

CREATE OR REPLACE FUNCTION public.process_complex_payment_v3(
    p_group_id UUID, p_member_id UUID, p_total_amount NUMERIC, p_tx_id TEXT, p_allocations JSONB,
    p_method TEXT DEFAULT 'bank', p_payment_type TEXT DEFAULT 'contribution', p_destination TEXT DEFAULT 'group'
) RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    RETURN public.process_complex_payment_v2(p_group_id, p_member_id, p_total_amount, p_tx_id, p_allocations, p_method, p_payment_type);
END;
$$;

RAISE NOTICE 'Online Database July 30 Update Sequence Complete.';
