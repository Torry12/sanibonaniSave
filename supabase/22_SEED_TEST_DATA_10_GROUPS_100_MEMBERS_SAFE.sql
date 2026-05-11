-- -----------------------------------------------------------------------------
-- SanibonaniSave - SAFE TEST SEED (10 Groups / 100 Members)
-- Purpose: create deterministic group/member data for app logic debugging.
-- Scope: groups + members + audit marker only (no payouts/platform_fees/claims).
-- Idempotent: re-running replaces only SEED100-G* groups.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_group_name TEXT;
    v_member_index INT;

    v_types TEXT[] := ARRAY[
        'stokvel',
        'burial_society',
        'investment_club',
        'rosca',
        'community_savings',
        'emergency_fund',
        'tontine',
        'stokvel',
        'burial_society',
        'other'
    ];
    v_provinces TEXT[] := ARRAY[
        'Gauteng',
        'KwaZulu-Natal',
        'Western Cape',
        'Eastern Cape',
        'Limpopo',
        'Mpumalanga',
        'North West',
        'Free State',
        'Northern Cape',
        'Gauteng'
    ];
    v_cities TEXT[] := ARRAY[
        'Johannesburg',
        'Durban',
        'Cape Town',
        'Gqeberha',
        'Polokwane',
        'Mbombela',
        'Mahikeng',
        'Bloemfontein',
        'Kimberley',
        'Pretoria'
    ];
    v_townships TEXT[] := ARRAY[
        'Soweto',
        'Umlazi',
        'Khayelitsha',
        'Motherwell',
        'Seshego',
        'Kanyamazane',
        'Mmabatho',
        'Mangaung',
        'Galeshewe',
        'Mamelodi'
    ];
BEGIN
    SELECT id
    INTO v_admin_id
    FROM auth.users
    WHERE lower(email) = lower('torrymsimango@gmail.com')
    LIMIT 1;

    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'Platform admin user not found. Run 03_PLATFORM_ADMIN_SETUP.sql first.';
    END IF;

    -- Cleanup prior safe-seed records only.
    DELETE FROM public.audit_logs WHERE action = 'SEED100_GROUP_CREATED';
    DELETE FROM public.groups WHERE name LIKE 'SEED100-G%';

    FOR g IN 1..10 LOOP
        v_group_name := format('SEED100-G%02s %s', g, initcap(replace(v_types[g], '_', ' ')));

        INSERT INTO public.groups (
            name,
            type,
            province,
            city,
            township,
            description,
            logo_emoji,
            joining_fee,
            monthly_contribution,
            late_fee,
            late_fee_grace_days,
            probation_months,
            payment_due_day,
            max_members,
            is_public,
            allow_partial_payment,
            auto_suspend_after,
            bank_name,
            account_number,
            branch_code,
            account_type,
            balance,
            admin_user_id,
            fee_status,
            registration_paid,
            is_platform_suspended,
            constitution_status,
            latitude,
            longitude,
            max_beneficiaries,
            beneficiary_increase_pct,
            goal_amount,
            period_months,
            loan_interest_rate,
            loan_max_amount,
            loan_max_months
        ) VALUES (
            v_group_name,
            v_types[g],
            v_provinces[g],
            v_cities[g],
            v_townships[g],
            'SAFE seed dataset for logic testing (groups + members only).',
            CASE WHEN v_types[g] = 'burial_society' THEN '🕊️' ELSE '🤝' END,
            150.00,
            300.00 + (g * 10),
            50.00,
            5,
            3,
            28,
            120,
            TRUE,
            (g % 2 = 0),
            2,
            'FNB',
            format('6200777%03s', g),
            '250655',
            'Savings',
            4500.00 + (g * 1200),
            v_admin_id,
            CASE WHEN g IN (4, 9) THEN 'warning' ELSE 'paid' END,
            TRUE,
            FALSE,
            'verified',
            -34.0 + (g * 0.7),
            18.0 + (g * 1.1),
            CASE WHEN v_types[g] = 'burial_society' THEN 8 ELSE 0 END,
            CASE WHEN v_types[g] = 'burial_society' THEN 10 ELSE 0 END,
            100000.00,
            24,
            12.50,
            12000.00,
            12
        ) RETURNING id INTO v_group_id;

        FOR v_member_index IN 1..10 LOOP
            INSERT INTO public.members (
                group_id,
                user_id,
                full_name,
                id_number,
                phone,
                email,
                street,
                suburb,
                city,
                province,
                notification_pref,
                status,
                joined_at,
                probation_end_at,
                beneficiary_count,
                beneficiary_over_65_count,
                total_contributions,
                total_paid,
                member_key
            ) VALUES (
                v_group_id,
                NULL,
                format('Seed100 Member %s-%s', lpad(g::text, 2, '0'), lpad(v_member_index::text, 2, '0')),
                lpad((9200000000000 + (g * 100) + v_member_index)::text, 13, '0'),
                format('07%s', lpad((g * 100 + v_member_index)::text, 8, '0')),
                format('seed100.member.%s.%s@example.com', lpad(g::text, 2, '0'), lpad(v_member_index::text, 2, '0')),
                format('%s Seed100 Street', v_member_index),
                v_townships[g],
                v_cities[g],
                v_provinces[g],
                CASE WHEN v_member_index % 3 = 0 THEN 'email' WHEN v_member_index % 3 = 1 THEN 'whatsapp' ELSE 'both' END,
                CASE
                    WHEN v_member_index <= 8 THEN 'active'
                    WHEN v_member_index = 9 THEN 'probation'
                    ELSE 'pending_payment'
                END,
                now() - ((v_member_index + g) || ' days')::interval,
                now() + interval '60 days',
                CASE WHEN v_types[g] = 'burial_society' THEN 2 ELSE 0 END,
                CASE WHEN v_types[g] = 'burial_society' AND v_member_index % 4 = 0 THEN 1 ELSE 0 END,
                0,
                0,
                format('SEED100_KEY_%s_%s', lpad(g::text, 2, '0'), lpad(v_member_index::text, 2, '0'))
            );
        END LOOP;

        UPDATE public.groups
        SET current_members = 10
        WHERE id = v_group_id;

        INSERT INTO public.audit_logs (actor_id, target_group_id, action, details)
        VALUES (
            v_admin_id,
            v_group_id,
            'SEED100_GROUP_CREATED',
            jsonb_build_object('seed', true, 'group_number', g, 'members', 10)
        );
    END LOOP;

    RAISE NOTICE 'Safe seed complete: 10 groups + 100 members.';
END $$;

