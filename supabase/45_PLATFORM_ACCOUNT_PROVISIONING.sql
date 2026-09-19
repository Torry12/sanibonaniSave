-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — PLATFORM ACCOUNT PROVISIONING
-- Version: 1.0 (Core Revenue Account)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. REGISTER PLATFORM AS A PROVIDER
-- This allows us to use the same bank-neutral logic for platform fees.
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

-- 2. UPDATE PLATFORM SETTINGS WITH BANK DETAILS
-- These are used for manual Bank Transfer instructions in the app.
INSERT INTO public.platform_settings (key, value) VALUES ('platform_bank_name_code', 1); -- 1 for FNB
INSERT INTO public.platform_settings (key, value) VALUES ('platform_account_number', 62889900112);
INSERT INTO public.platform_settings (key, value) VALUES ('platform_branch_code', 250655);

-- 3. ENFORCE DESTINATION IN PAYMENT RPC (Optional hardening)
-- This can be expanded later to automatically split funds at the gateway level.
