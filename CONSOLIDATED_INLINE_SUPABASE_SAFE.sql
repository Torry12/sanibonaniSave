-- CONSOLIDATED_INLINE_SUPABASE_SAFE.sql
-- Non-destructive, Supabase-safe SQL extracted from CONSOLIDATED_INLINE.sql
-- Purpose: paste this entire file into the Supabase SQL editor. It only creates objects IF NOT EXISTS
-- and runs the SAFE seed (which expects an existing platform admin user). Review before running.

-- Extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Minimal set of public tables required by SAFE_SEED (created non-destructively)
CREATE TABLE IF NOT EXISTS public.profiles (
    id          UUID PRIMARY KEY,
    full_name   TEXT,
    email       TEXT,
    role        TEXT DEFAULT 'member',
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.groups (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                   TEXT UNIQUE,
    type                   TEXT DEFAULT 'other',
    province               TEXT,
    city                   TEXT,
    township               TEXT,
    description            TEXT,
    logo_emoji             TEXT DEFAULT '🤝',
    joining_fee            NUMERIC(10,2) DEFAULT 0,
    monthly_contribution   NUMERIC(10,2) DEFAULT 0,
    late_fee               NUMERIC(10,2) DEFAULT 0,
    late_fee_grace_days    INTEGER DEFAULT 5,
    probation_months       INTEGER DEFAULT 3,
    payment_due_day        INTEGER DEFAULT 28,
    max_members            INTEGER DEFAULT 50,
    current_members        INTEGER DEFAULT 0,
    is_public              BOOLEAN DEFAULT TRUE,
    allow_partial_payment  BOOLEAN DEFAULT FALSE,
    auto_suspend_after     INTEGER DEFAULT 2,
    bank_name              TEXT,
    account_number         TEXT,
    branch_code            TEXT,
    account_type           TEXT DEFAULT 'Savings',
    gateway_public_key     TEXT,
    balance                NUMERIC(12,2) DEFAULT 0,
    admin_user_id          UUID,
    fee_status             TEXT DEFAULT 'due',
    registration_paid      BOOLEAN DEFAULT FALSE,
    is_platform_suspended  BOOLEAN DEFAULT FALSE,
    constitution_status    TEXT DEFAULT 'pending',
    latitude               FLOAT8,
    longitude              FLOAT8,
    max_beneficiaries      INTEGER DEFAULT 0,
    beneficiary_increase_pct NUMERIC(5,2) DEFAULT 0,
    goal_amount            NUMERIC(12,2) DEFAULT 0,
    period_months          INTEGER DEFAULT 12,
    rosca_rotation_method  TEXT DEFAULT 'fixed',
    loan_interest_rate     NUMERIC(5,2) DEFAULT 0,
    loan_max_amount        NUMERIC(12,2) DEFAULT 0,
    loan_max_months        INTEGER DEFAULT 12,
    created_at             TIMESTAMPTZ DEFAULT NOW(),
    updated_at             TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.members (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id             UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    user_id              UUID,
    full_name            TEXT,
    id_number            TEXT,
    phone                TEXT,
    email                TEXT,
    street               TEXT,
    suburb               TEXT,
    city                 TEXT,
    province             TEXT,
    notification_pref    TEXT DEFAULT 'both',
    status               TEXT DEFAULT 'probation',
    joined_at            TIMESTAMPTZ DEFAULT NOW(),
    probation_end_at     TIMESTAMPTZ,
    beneficiary_count    INTEGER DEFAULT 0,
    beneficiary_over_65_count INTEGER DEFAULT 0,
    total_contributions  INTEGER DEFAULT 0,
    total_paid           NUMERIC(12,2) DEFAULT 0,
    member_key           TEXT,
    created_at           TIMESTAMPTZ DEFAULT NOW(),
    updated_at           TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.group_ledger (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    transaction_id    UUID,
    amount            NUMERIC(12,2) NOT NULL,
    balance_after     NUMERIC(12,2) NOT NULL,
    description       TEXT NOT NULL,
    category          TEXT NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.audit_logs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id          UUID,
    target_member_id  UUID,
    target_group_id   UUID,
    action            TEXT NOT NULL,
    details           JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.contributions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id              UUID REFERENCES public.members(id) ON DELETE CASCADE,
    group_id               UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    amount                 NUMERIC(10,2) NOT NULL,
    type                   TEXT DEFAULT 'contribution',
    due_date               DATE,
    paid_at                TIMESTAMPTZ,
    payment_method         TEXT,
    transaction_id         TEXT,
    status                 TEXT DEFAULT 'due',
    created_at             TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.payments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id         UUID REFERENCES public.members(id),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    amount            NUMERIC(12,2) NOT NULL,
    payment_type      TEXT,
    payment_method    TEXT,
    transaction_id    TEXT,
    status            TEXT DEFAULT 'pending',
    processed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.payouts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    amount            NUMERIC(12,2) NOT NULL,
    bank_name         TEXT,
    account_no        TEXT,
    branch_code       TEXT,
    status            TEXT DEFAULT 'pending',
    processed_by      UUID,
    processed_at      TIMESTAMPTZ,
    payout_reference  TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.platform_ledger (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id    UUID,
    amount            NUMERIC(12,2) NOT NULL,
    balance_after     NUMERIC(12,2) NOT NULL,
    description       TEXT NOT NULL,
    category          TEXT NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

-- Functions (create or replace)
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.raw_user_meta_data->>'name', ''),
        NEW.email,
        COALESCE(NEW.raw_user_meta_data->>'role', 'member')
    ) ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, email = EXCLUDED.email, role = EXCLUDED.role;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger to sync auth.users to public.profiles (safe recreate)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'on_auth_user_created') THEN
        -- drop then create to ensure linkage
        DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
    END IF;
    CREATE TRIGGER on_auth_user_created
        AFTER INSERT ON auth.users
        FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();
EXCEPTION WHEN undefined_table THEN
    -- auth.users may not be present in this environment; ignore
    RAISE NOTICE 'auth.users not present; skipping trigger creation.';
END $$;

CREATE OR REPLACE FUNCTION public.increment_group_balance(
    p_group_id UUID,
    p_amount NUMERIC
) RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_new_balance NUMERIC;
BEGIN
    UPDATE public.groups
    SET balance = COALESCE(balance,0) + p_amount,
        updated_at = NOW()
    WHERE id = p_group_id
    RETURNING balance INTO v_new_balance;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Group not found';
    END IF;

    INSERT INTO public.group_ledger (group_id, transaction_id, amount, balance_after, description, category)
    VALUES (p_group_id, NULL, p_amount, v_new_balance, 'Atomic balance update', 'adjustment');

    RETURN v_new_balance;
END;
$$;

GRANT EXECUTE ON FUNCTION public.increment_group_balance(UUID, NUMERIC) TO authenticated, service_role;
-- Ensure a UNIQUE index on groups.name exists when safe to create.
-- If duplicate group names exist this will skip index creation and emit a NOTICE.
DO $$
DECLARE
    v_duplicates INT;
BEGIN
    SELECT COUNT(*) INTO v_duplicates
    FROM (
        SELECT name FROM public.groups WHERE name IS NOT NULL GROUP BY name HAVING COUNT(*) > 1
    ) t;

    IF v_duplicates = 0 THEN
        BEGIN
            EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS idx_groups_name_unique ON public.groups (name)';
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Unable to create unique index on public.groups(name): %', SQLERRM;
        END;
    ELSE
        RAISE NOTICE 'Skipping unique index creation for public.groups(name): % duplicate name(s) present.', v_duplicates;
    END IF;
END$$;

-- SAFE seed: canonical safe dataset
-- (This block is idempotent and uses ON CONFLICT / checks; it expects auth.users to exist and a platform admin email to be present.)
DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_extra_admin_id UUID;
    v_group_name TEXT;
    v_types TEXT[] := ARRAY['stokvel','burial_society','investment_club','rosca','community_savings','emergency_fund','tontine','stokvel','burial_society','other'];
    v_provinces TEXT[] := ARRAY['Gauteng','KwaZulu-Natal','Western Cape','Eastern Cape','Limpopo','Mpumalanga','North West','Free State','Northern Cape','Gauteng'];
    v_cities TEXT[] := ARRAY['Johannesburg','Durban','Cape Town','Gqeberha','Polokwane','Mbombela','Mahikeng','Bloemfontein','Kimberley','Pretoria'];
    v_townships TEXT[] := ARRAY['Soweto','Umlazi','Khayelitsha','Motherwell','Seshego','Kanyamazane','Mmabatho','Mangaung','Galeshewe','Mamelodi'];
    v_group_balance NUMERIC(12,2);
    v_contribution_amount NUMERIC(10,2);
    v_disbursement_amount NUMERIC(12,2);
    v_disbursement_status TEXT;
    v_extra_admin_password TEXT := 'SeedSafe@1234';
BEGIN
    -- Ensure platform admin exists
    SELECT id INTO v_admin_id FROM auth.users WHERE lower(email) = lower('torrymsimango@gmail.com') LIMIT 1;
    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform admin user not found. Run platform admin setup first.';
    END IF;

    -- Cleanup prior canonical-seed groups only (non-destructive: deletes seed-created rows)
    DELETE FROM public.audit_logs WHERE action = 'SAFE_SEED_GROUP_CREATED';
    DELETE FROM public.platform_ledger WHERE description LIKE 'SAFE_SEED %';
    DELETE FROM public.groups WHERE name LIKE 'SAFE_SEED-G%';

    FOR g IN 1..10 LOOP
        v_group_name := format('SAFE_SEED-G%s %s', lpad(g::text,2,'0'), initcap(replace(v_types[g],'_',' ')));
        v_group_balance := (4000.00 + (g * 1000))::numeric(12,2);
        v_contribution_amount := (250.00 + (g * 10))::numeric(10,2);
        v_disbursement_amount := (v_contribution_amount * 3)::numeric(12,2);

        INSERT INTO public.groups (
            id, name, type, province, city, township, description, logo_emoji, joining_fee, monthly_contribution,
            late_fee, late_fee_grace_days, probation_months, payment_due_day, max_members, is_public, allow_partial_payment,
            auto_suspend_after, bank_name, account_number, branch_code, account_type, gateway_public_key, balance,
            admin_user_id, fee_status, registration_paid, is_platform_suspended, constitution_status, latitude, longitude,
            max_beneficiaries, beneficiary_increase_pct, goal_amount, period_months, loan_interest_rate, loan_max_amount, loan_max_months, created_at, updated_at
        ) VALUES (
            gen_random_uuid(), v_group_name, v_types[g], v_provinces[g], v_cities[g], v_townships[g],
            'Canonical safe seed for deterministic testing', CASE WHEN v_types[g] = 'burial_society' THEN '🕊️' ELSE '🤝' END,
            150.00, 300.00 + (g * 10), 50.00, 5, 3, 28, 120, TRUE, (g % 2 = 0), 2, 'FNB',
            '6200777' || lpad(g::text, 3, '0'), '250655', 'Savings', 'pk_test_seed_group_' || g, v_group_balance,
            v_admin_id, CASE WHEN g % 4 = 0 THEN 'warning' ELSE 'paid' END, TRUE, FALSE, 'verified',
            -34.0 + (g * 0.7), 18.0 + (g * 1.1), CASE WHEN v_types[g] = 'burial_society' THEN 4 ELSE 0 END,
            CASE WHEN v_types[g] = 'burial_society' THEN 5 ELSE 0 END, 100000.00, 24, 12.5, 12000.00, 12, now(), now()
        ) ON CONFLICT (name) DO UPDATE
            SET updated_at = now(), balance = EXCLUDED.balance;

        SELECT id INTO v_group_id FROM public.groups WHERE name = v_group_name LIMIT 1;

        INSERT INTO public.members (group_id, user_id, full_name, phone, email, notification_pref, status, joined_at, probation_end_at, member_key)
        VALUES (v_group_id, v_admin_id, format('Safe Admin %s', lpad(g::text,2,'0')), format('07900%s', lpad(g::text,5,'0')), format('safe.admin.%s@example.com', lpad(g::text,2,'0')), 'both', 'active', now() - interval '120 days', now() + interval '60 days', format('SAFE_ADMIN_KEY_%s', lpad(g::text,2,'0')))
        ON CONFLICT (group_id, user_id) DO NOTHING;

        INSERT INTO public.group_ledger (id, group_id, transaction_id, amount, balance_after, description, category, created_at)
        VALUES (gen_random_uuid(), v_group_id, NULL, 0.00, v_group_balance, format('SAFE_SEED opening balance %s', lpad(g::text,2,'0')), 'opening_balance', now() - interval '90 days');

        FOR m IN 1..9 LOOP
            INSERT INTO public.members (group_id, user_id, full_name, id_number, phone, email, street, suburb, city, province, notification_pref, status, joined_at, probation_end_at, beneficiary_count, beneficiary_over_65_count, total_contributions, total_paid, member_key)
            VALUES (
                v_group_id, NULL, CASE WHEN m IN (1,2) THEN format('Safe Extra Admin %s-%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')) ELSE format('Safe Member %s-%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')) END,
                lpad((9200000000000 + (g * 100) + m)::text,13,'0'), format('07%s', lpad((g*100 + m)::text,8,'0')),
                CASE WHEN m IN (1,2) THEN format('safe.groupadmin.%s.%s@example.com', lpad(g::text,2,'0'), lpad(m::text,2,'0')) ELSE format('safe.member.%s.%s@example.com', lpad(g::text,2,'0'), lpad(m::text,2,'0')) END,
                format('%s Safe Street', m), v_townships[g], v_cities[g], v_provinces[g], CASE WHEN m % 3 = 0 THEN 'email' WHEN m % 3 = 1 THEN 'whatsapp' ELSE 'both' END,
                CASE WHEN m <= 7 THEN 'active' WHEN m = 8 THEN 'probation' ELSE 'pending_payment' END, now() - ((m+g) || ' days')::interval, now() + interval '60 days', CASE WHEN v_types[g] = 'burial_society' THEN 2 ELSE 0 END, 0, 0, 0,
                CASE WHEN m IN (1,2) THEN format('SAFE_EXTRA_ADMIN_KEY_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')) ELSE format('SAFE_KEY_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')) END
            ) RETURNING id INTO v_member_id;

            IF m IN (1,2) THEN
                SELECT id INTO v_extra_admin_id FROM auth.users WHERE lower(email) = lower(CASE WHEN m=1 THEN format('safe.groupadmin.%s.01@example.com', lpad(g::text,2,'0')) ELSE format('safe.groupadmin.%s.02@example.com', lpad(g::text,2,'0')) END) LIMIT 1;
                IF v_extra_admin_id IS NULL THEN
                    v_extra_admin_id := gen_random_uuid();
                    INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
                    VALUES (v_extra_admin_id, 'authenticated', 'authenticated', CASE WHEN m=1 THEN format('safe.groupadmin.%s.01@example.com', lpad(g::text,2,'0')) ELSE format('safe.groupadmin.%s.02@example.com', lpad(g::text,2,'0')) END, crypt(v_extra_admin_password, gen_salt('bf')), now(), '{"provider":"email","providers":["email"],"role":"group_admin"}'::jsonb, jsonb_build_object('full_name', CASE WHEN m=1 THEN format('Safe Extra Admin %s-01', lpad(g::text,2,'0')) ELSE format('Safe Extra Admin %s-02', lpad(g::text,2,'0')) END, 'role', 'group_admin'), now(), now());
                ELSE
                    UPDATE auth.users SET encrypted_password = crypt(v_extra_admin_password, gen_salt('bf')), email_confirmed_at = COALESCE(email_confirmed_at, now()), raw_app_meta_data = COALESCE(raw_app_meta_data, '{}'::jsonb) || '{"provider":"email","providers":["email"],"role":"group_admin"}'::jsonb, raw_user_meta_data = COALESCE(raw_user_meta_data, '{}'::jsonb) || jsonb_build_object('full_name', CASE WHEN m=1 THEN format('Safe Extra Admin %s-01', lpad(g::text,2,'0')) ELSE format('Safe Extra Admin %s-02', lpad(g::text,2,'0')) END, 'role', 'group_admin'), updated_at = now() WHERE id = v_extra_admin_id;
                END IF;

                INSERT INTO auth.identities (id, user_id, provider, provider_id, identity_data, created_at, updated_at)
                VALUES (gen_random_uuid(), v_extra_admin_id, 'email', CASE WHEN m=1 THEN format('safe.groupadmin.%s.01@example.com', lpad(g::text,2,'0')) ELSE format('safe.groupadmin.%s.02@example.com', lpad(g::text,2,'0')) END, jsonb_build_object('sub', v_extra_admin_id::text, 'email', CASE WHEN m=1 THEN format('safe.groupadmin.%s.01@example.com', lpad(g::text,2,'0')) ELSE format('safe.groupadmin.%s.02@example.com', lpad(g::text,2,'0')) END), now(), now())
                ON CONFLICT (provider, provider_id) DO UPDATE SET user_id = EXCLUDED.user_id, identity_data = EXCLUDED.identity_data, updated_at = now();

                INSERT INTO public.profiles (id, full_name, email, role) VALUES (v_extra_admin_id, CASE WHEN m=1 THEN format('Safe Extra Admin %s-01', lpad(g::text,2,'0')) ELSE format('Safe Extra Admin %s-02', lpad(g::text,2,'0')) END, CASE WHEN m=1 THEN format('safe.groupadmin.%s.01@example.com', lpad(g::text,2,'0')) ELSE format('safe.groupadmin.%s.02@example.com', lpad(g::text,2,'0')) END, 'group_admin') ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, email = EXCLUDED.email, role = 'group_admin';

                UPDATE public.members SET user_id = v_extra_admin_id WHERE id = v_member_id;
            END IF;

            INSERT INTO public.contributions (id, member_id, group_id, amount, type, due_date, paid_at, payment_method, transaction_id, status, late_fees_applied, created_at)
            VALUES (gen_random_uuid(), v_member_id, v_group_id, v_contribution_amount, 'contribution', current_date - 30, now() - interval '21 days', 'yoco', format('safe_tx_paid_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), 'paid', FALSE, now() - interval '30 days')
            ON CONFLICT (id) DO NOTHING;

            INSERT INTO public.payments (id, member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at, created_at)
            VALUES (gen_random_uuid(), v_member_id, v_group_id, v_contribution_amount, 'contribution', 'yoco', format('safe_pay_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), 'completed', now() - interval '21 days', now() - interval '21 days')
            ON CONFLICT (id) DO NOTHING;
        END LOOP;

        v_disbursement_status := CASE WHEN g % 4 = 0 THEN 'completed' WHEN g % 3 = 0 THEN 'processing' WHEN g % 5 = 0 THEN 'failed' ELSE 'pending' END;
        INSERT INTO public.payouts (id, group_id, amount, bank_name, account_no, branch_code, status, processed_by, processed_at, payout_reference, created_at)
        VALUES (gen_random_uuid(), v_group_id, v_disbursement_amount, 'Standard Bank', '1234500' || lpad(g::text,3,'0'), '051001', v_disbursement_status, CASE WHEN v_disbursement_status = 'pending' THEN NULL ELSE v_admin_id END, CASE WHEN v_disbursement_status = 'pending' THEN NULL ELSE now() - interval '2 days' END, CASE WHEN v_disbursement_status IN ('processing','completed') THEN format('safe_payout_%s', lpad(g::text,2,'0')) ELSE NULL END, now() - interval '3 days') ON CONFLICT (id) DO NOTHING;

        IF v_disbursement_status = 'completed' THEN
            PERFORM public.increment_group_balance(v_group_id, -v_disbursement_amount);
        END IF;

        UPDATE public.groups SET current_members = 10 WHERE id = v_group_id;

        INSERT INTO public.audit_logs (id, actor_id, target_group_id, action, details, created_at) VALUES (gen_random_uuid(), v_admin_id, v_group_id, 'SAFE_SEED_GROUP_CREATED', jsonb_build_object('seed', true, 'group_number', g, 'members', 10), now()) ON CONFLICT (id) DO NOTHING;
    END LOOP;

    RAISE NOTICE 'SAFE_SEED completed: 10 groups + members + transactions + payouts + ledger entries.';
END$$;

-- End of file

