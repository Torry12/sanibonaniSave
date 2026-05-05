-- seed_30_groups_300_members.sql
-- REAL WORLD SIMULATION SEED SCRIPT v2.0
-- Seeds 30 groups and 300 members with fully realistic SA savings group patterns.
-- Covers: contributions, late fees, joining fees, loans, repayments, beneficiaries,
--         payouts, platform fees, notifications, actuarial metrics, audit logs.

-- ─── 1. PUBLIC CLEAN SLATE ────────────────────────────────────────────────────
TRUNCATE TABLE
  public.audit_logs,
  public.payments,
  public.platform_fees,
  public.payouts,
  public.notifications,
  public.beneficiaries,
  public.member_documents,
  public.contributions,
  public.loan_repayments,
  public.loans,
  public.group_actuarial_metrics,
  public.policies,
  public.members,
  public.groups,
  public.profiles
RESTART IDENTITY CASCADE;

-- ─── 2. AUTH CLEANUP (after public truncate to avoid FK errors) ──────────────
DO $$
DECLARE
    v_seed_user_ids UUID[] := ARRAY[
        'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1'::UUID,
        'b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2'::UUID,
        'c3c3c3c3-c3c3-c3c3-c3c3-c3c3c3c3c3c3'::UUID,
        'd4d4d4d4-d4d4-d4d4-d4d4-d4d4d4d4d4d4'::UUID,
        'e5e5e5e5-e5e5-e5e5-e5e5-e5e5e5e5e5e5'::UUID,
        'f6f6f6f6-f6f6-f6f6-f6f6-f6f6f6f6f6f6'::UUID,
        'a7a7a7a7-a7a7-a7a7-a7a7-a7a7a7a7a7a7'::UUID,
        'b8b8b8b8-b8b8-b8b8-b8b8-b8b8b8b8b8b8'::UUID,
        'c9c9c9c9-c9c9-c9c9-c9c9-c9c9c9c9c9c9'::UUID
    ];
    v_seed_emails TEXT[] := ARRAY[
        'torryymsimango@gmail.com',
        'torrymsimango@gmail.com',
        'admin1@test.com',
        'admin2@test.com',
        'member1@test.com',
        'member2@test.com',
        'member3@test.com',
        'member4@test.com',
        'member5@test.com',
        'member6@test.com'
    ];
BEGIN
    BEGIN
        DELETE FROM auth.identities
        WHERE user_id = ANY(v_seed_user_ids)
           OR lower(provider_id) = ANY(v_seed_emails)
           OR lower(COALESCE(identity_data->>'email', '')) = ANY(v_seed_emails);
    EXCEPTION WHEN undefined_table THEN
        RAISE NOTICE 'auth.identities table not found, skipping auth identity cleanup';
    END;

    DELETE FROM auth.users
    WHERE id = ANY(v_seed_user_ids)
       OR lower(email) = ANY(v_seed_emails);
END $$;


-- ─── 3. PERSONA USERS ────────────────────────────────────────────────────────
DO $$
DECLARE
    v_users JSONB := '[
        {"id":"a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1","email":"torryymsimango@gmail.com","name":"Torry Msimango","role":"platform_admin"},
        {"id":"b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2","email":"admin1@test.com","name":"Sizwe Dhlomo","role":"group_admin"},
        {"id":"c3c3c3c3-c3c3-c3c3-c3c3-c3c3c3c3c3c3","email":"admin2@test.com","name":"Thandiwe Khumalo","role":"group_admin"},
        {"id":"d4d4d4d4-d4d4-d4d4-d4d4-d4d4d4d4d4d4","email":"member1@test.com","name":"Jabu Buthelezi","role":"member"},
        {"id":"e5e5e5e5-e5e5-e5e5-e5e5-e5e5e5e5e5e5","email":"member2@test.com","name":"Lerato Mokoena","role":"member"},
        {"id":"f6f6f6f6-f6f6-f6f6-f6f6-f6f6f6f6f6f6","email":"member3@test.com","name":"Nompumelelo Dlamini","role":"member"},
        {"id":"a7a7a7a7-a7a7-a7a7-a7a7-a7a7a7a7a7a7","email":"member4@test.com","name":"Sipho Radebe","role":"member"},
        {"id":"b8b8b8b8-b8b8-b8b8-b8b8-b8b8b8b8b8b8","email":"member5@test.com","name":"Busisiwe Mthembu","role":"member"},
        {"id":"c9c9c9c9-c9c9-c9c9-c9c9-c9c9c9c9c9c9","email":"member6@test.com","name":"Mandla Sithole","role":"member"}
    ]';
    v_u RECORD;
    v_existing_user_id UUID;
BEGIN
    FOR v_u IN SELECT * FROM jsonb_to_recordset(v_users) AS x(id UUID, email TEXT, name TEXT, role TEXT) LOOP
        SELECT id
        INTO v_existing_user_id
        FROM auth.users
        WHERE lower(email) = lower(v_u.email)
        LIMIT 1;

        -- Defensive cleanup: if this block is run by itself, remove any stale row using the
        -- same email under a different UUID before inserting the canonical seeded UUID.
        IF v_existing_user_id IS NOT NULL AND v_existing_user_id <> v_u.id THEN
            BEGIN
                DELETE FROM auth.identities
                WHERE user_id = v_existing_user_id
                   OR lower(provider_id) = lower(v_u.email)
                   OR lower(COALESCE(identity_data->>'email', '')) = lower(v_u.email);
            EXCEPTION WHEN undefined_table THEN
                RAISE NOTICE 'auth.identities table not found, skipping duplicate cleanup for %', v_u.email;
            END;

            DELETE FROM public.profiles
            WHERE (id = v_existing_user_id OR lower(email) = lower(v_u.email))
              AND id <> v_u.id;

            DELETE FROM auth.users
            WHERE id = v_existing_user_id;
        END IF;

        IF EXISTS (SELECT 1 FROM auth.users WHERE id = v_u.id) THEN
            UPDATE auth.users
            SET
                email = v_u.email,
                encrypted_password = extensions.crypt(
                    CASE WHEN v_u.role = 'platform_admin' THEN 'torry123M' ELSE 'password123' END,
                    extensions.gen_salt('bf')
                ),
                email_confirmed_at = COALESCE(email_confirmed_at, NOW()),
                raw_app_meta_data = '{"provider":"email","providers":["email"]}'::jsonb,
                raw_user_meta_data = jsonb_build_object('full_name', v_u.name, 'role', v_u.role),
                updated_at = NOW()
            WHERE id = v_u.id;
        ELSE
            INSERT INTO auth.users (
                id, aud, role, email, encrypted_password, email_confirmed_at,
                raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token
            ) VALUES (
                v_u.id, 'authenticated', 'authenticated', v_u.email,
                extensions.crypt(
                    CASE WHEN v_u.role = 'platform_admin' THEN 'torry123M' ELSE 'password123' END,
                    extensions.gen_salt('bf')
                ), NOW(),
                '{"provider":"email","providers":["email"]}',
                jsonb_build_object('full_name', v_u.name, 'role', v_u.role),
                NOW(), NOW(), ''
            );
        END IF;

        BEGIN
            INSERT INTO auth.identities (
                id,
                user_id,
                provider,
                provider_id,
                identity_data,
                created_at,
                updated_at,
                last_sign_in_at
            ) VALUES (
                gen_random_uuid(),
                v_u.id,
                'email',
                lower(v_u.email),
                jsonb_build_object('sub', v_u.id::text, 'email', v_u.email),
                NOW(),
                NOW(),
                NOW()
            )
            ON CONFLICT (provider, provider_id) DO UPDATE
            SET
                user_id = EXCLUDED.user_id,
                identity_data = EXCLUDED.identity_data,
                updated_at = NOW();
        EXCEPTION WHEN undefined_table THEN
            RAISE NOTICE 'auth.identities table not found, skipping identity upsert for %', v_u.email;
        END;

        INSERT INTO public.profiles (id, full_name, email, role)
        VALUES (v_u.id, v_u.name, v_u.email, v_u.role)
        ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, role = EXCLUDED.role;
    END LOOP;
END $$;

-- ─── 3. SEED 30 GROUPS ────────────────────────────────────────────────────────
DO $$
DECLARE
    i       INT;
    v_id    UUID;
    v_type  TEXT;
    v_admin UUID;

    -- Real SA location grid: province, city, township, lat, lng
    v_provinces  TEXT[] := ARRAY['Gauteng','KwaZulu-Natal','Western Cape','Eastern Cape','Limpopo','Mpumalanga','North West','Free State','Northern Cape'];
    v_cities     TEXT[] := ARRAY['Johannesburg','Durban','Cape Town','Gqeberha','Polokwane','Nelspruit','Rustenburg','Bloemfontein','Kimberley'];
    v_townships  TEXT[] := ARRAY['Soweto','Umlazi','Khayelitsha','Motherwell','Seshego','KwaMhlanga','Tlhabane','Botshabelo','Galeshewe'];
    v_lats       FLOAT8[] := ARRAY[-26.2041,-29.8587,-33.9249,-33.9608,-23.9045,-25.4745,-25.6670,-29.0852,-28.7282];
    v_lngs       FLOAT8[] := ARRAY[28.0473,31.0218,18.4241,25.6022,29.4689,30.9703,27.2427,26.1596,24.7499];

    -- Group names per type
    v_burial_names TEXT[] := ARRAY[
        'Siyakhula Burial Society','Thuthukani Burial Society','Ubuntu Life Society',
        'Masakhane Burial Fund','Sizabantu Society','Thandeka Memorial Fund',
        'Ilanga Burial Society','Nokwazi Burial Fund'
    ];
    v_stokvel_names TEXT[] := ARRAY[
        'Unity Savings Stokvel','Batho Pele Stokvel','Ikhaya Savings Club',
        'Malunde Grocery Stokvel','Thrive Together Savings','Bona Bafazi Stokvel'
    ];
    v_invest_names  TEXT[] := ARRAY['Elite Investment Club','Mzansi Growth Fund','Ubuntu Capital Club'];
    v_rosca_names   TEXT[] := ARRAY['Mahala Rotation Circle','Impilo ROSCA','Joyful Cycle Savings'];

    v_loc_idx INT;
    v_name    TEXT;
    v_lat     FLOAT8;
    v_lng     FLOAT8;
    v_bank    TEXT;
    v_acc     TEXT;
    v_branch  TEXT;
    m         INT;
BEGIN
    FOR i IN 1..30 LOOP
        v_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', i::text);
        v_type := CASE
            WHEN i <= 12 THEN 'burial_society'
            WHEN i <= 21 THEN 'stokvel'
            WHEN i <= 26 THEN 'investment_club'
            ELSE              'rosca'
        END;
        v_admin    := CASE WHEN i % 2 = 0 THEN 'b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2'::UUID
                                           ELSE 'c3c3c3c3-c3c3-c3c3-c3c3-c3c3c3c3c3c3'::UUID END;
        v_loc_idx  := (i % 9) + 1;
        v_lat      := v_lats[v_loc_idx]  + (i * 0.003);
        v_lng      := v_lngs[v_loc_idx]  + (i * 0.003);

        v_name := CASE v_type
            WHEN 'burial_society'  THEN v_burial_names[((i - 1) % 8) + 1]
            WHEN 'stokvel'         THEN v_stokvel_names[((i - 13) % 6) + 1]
            WHEN 'investment_club' THEN v_invest_names[((i - 22) % 3) + 1]
            ELSE                        v_rosca_names[((i - 27) % 3) + 1]
        END;

        v_bank   := (ARRAY['Capitec','FNB','Standard Bank','Absa','Nedbank'])[(i % 5) + 1];
        v_acc    := LPAD((((i::BIGINT * 123456789) % 9999999999)::BIGINT)::text, 10, '0');
        v_branch := CASE v_bank
            WHEN 'Capitec'       THEN '470010'
            WHEN 'FNB'           THEN '250655'
            WHEN 'Standard Bank' THEN '051001'
            WHEN 'Absa'          THEN '632005'
            ELSE                      '198765'
        END;

        INSERT INTO public.groups (
            id, name, type, province, city, township, description, logo_emoji,
            joining_fee, monthly_contribution, late_fee, late_fee_grace_days, probation_months,
            payment_due_day, max_members, current_members, balance,
            admin_user_id, fee_status, registration_paid,
            constitution_url, constitution_status,
            latitude, longitude, geohash,
            bank_name, account_number, branch_code, account_type,
            max_beneficiaries, beneficiary_increase_pct, goal_amount, period_months,
            is_public, allow_partial_payment, auto_suspend_after,
            is_platform_suspended
        ) VALUES (
            v_id, v_name, v_type,
            v_provinces[v_loc_idx], v_cities[v_loc_idx], v_townships[v_loc_idx],
            CASE v_type
                WHEN 'burial_society'  THEN 'Dignified burial cover for members and their families across ' || v_cities[v_loc_idx] || '.'
                WHEN 'stokvel'         THEN 'A trusted savings circle helping families in ' || v_townships[v_loc_idx] || ' reach financial goals.'
                WHEN 'investment_club' THEN 'Pooling resources for collective investment growth in ' || v_provinces[v_loc_idx] || '.'
                ELSE                        'Monthly rotation savings — every member gets their turn.'
            END,
            CASE v_type WHEN 'burial_society' THEN '🕊️' WHEN 'stokvel' THEN '💰' WHEN 'investment_club' THEN '📈' ELSE '🔄' END,
            CASE v_type WHEN 'burial_society'  THEN 300 + (i * 20)
                         WHEN 'stokvel'         THEN 150 + (i * 15)
                         WHEN 'investment_club' THEN 500 + (i * 25)
                         ELSE 100 END,
            CASE v_type WHEN 'burial_society'  THEN 150 + (i * 10)
                         WHEN 'stokvel'         THEN 300 + (i * 20)
                         WHEN 'investment_club' THEN 500 + (i * 30)
                         ELSE 250 + (i * 15) END,
            CASE v_type WHEN 'burial_society' THEN 50 WHEN 'investment_club' THEN 100 ELSE 30 END,
            5, 3,
            CASE WHEN i % 4 = 0 THEN 1 WHEN i % 4 = 1 THEN 15 WHEN i % 4 = 2 THEN 25 ELSE 28 END,
            CASE v_type WHEN 'burial_society' THEN 80 WHEN 'investment_club' THEN 30 ELSE 50 END,
            0, 0,
            v_admin, 'paid', TRUE,
            'https://storage.example.com/constitutions/const_' || i || '.pdf', 'verified',
            v_lat, v_lng,
            substr(md5(v_lat::text || v_lng::text), 1, 8),
            v_bank, v_acc, v_branch, 'Savings',
            CASE WHEN v_type = 'burial_society' THEN 8 ELSE 0 END,
            15.0,
            CASE v_type WHEN 'investment_club' THEN 200000 WHEN 'stokvel' THEN 50000 ELSE 30000 END,
            CASE v_type WHEN 'investment_club' THEN 24 ELSE 12 END,
            TRUE,
            CASE WHEN v_type = 'stokvel' THEN TRUE ELSE FALSE END,
            2,
            CASE WHEN i = 7 THEN TRUE ELSE FALSE END  -- 1 group suspended for admin testing
        );

        IF v_type = 'burial_society' THEN
            INSERT INTO public.policies (group_id, name, description, required_amount, status)
            VALUES
                (v_id, 'Standard Cover',   'Main member funeral cover up to R15,000.', 15000, 'active'),
                (v_id, 'Family Extension', 'Extends cover to spouse and up to 3 children.', 25000, 'active');
        END IF;

        -- Platform registration fee
        INSERT INTO public.platform_fees (group_id, fee_type, amount, status, due_date, paid_at, transaction_id)
        VALUES (v_id, 'registration', 700, 'paid',
                to_char(NOW() - INTERVAL '6 months', 'YYYY-MM-DD'),
                NOW() - INTERVAL '6 months', 'TXN-REG-' || i);

        -- Monthly platform fees (last 6 months)
        FOR m IN 1..6 LOOP
            INSERT INTO public.platform_fees (group_id, fee_type, amount, status, due_date, paid_at, transaction_id)
            VALUES (
                v_id, 'monthly', 375,
                CASE WHEN m <= 4 THEN 'paid' WHEN m = 5 THEN 'warning' ELSE 'due' END,
                to_char(NOW() - ((6 - m) || ' months')::INTERVAL, 'YYYY-MM'),
                CASE WHEN m <= 4 THEN NOW() - ((6 - m) || ' months')::INTERVAL ELSE NULL END,
                CASE WHEN m <= 4 THEN 'TXN-MTH-' || i || '-' || m ELSE NULL END
            );
        END LOOP;
    END LOOP;
END $$;

-- ─── 4. MEMBERSHIPS ───────────────────────────────────────────────────────────
DO $$
DECLARE
    k          INT;
    v_g_idx    INT;
    v_g_id     UUID;
    v_m_id     UUID;
    v_user_id  UUID;
    v_status   TEXT;
    v_joined   TIMESTAMPTZ;
    v_first    TEXT;
    v_last     TEXT;
    v_prov     TEXT;
    v_city     TEXT;
    v_notif    TEXT;

    v_firsts TEXT[] := ARRAY[
        'Lindiwe','Sipho','Zanele','Bongani','Thandi','Musa','Nomsa','Jabu','Lerato','Themba',
        'Nompumelelo','Mandla','Ayanda','Sifiso','Nokwanda','Tebogo','Refilwe','Kagiso','Palesa','Ntombi',
        'Dineo','Lethiwe','Siyanda','Nhlanhla','Busisiwe','Phindile','Mpho','Lungelo','Sindisiwe','Vuyo'
    ];
    v_lasts TEXT[] := ARRAY[
        'Dlamini','Mokoena','Zulu','Ndou','Gumede','Smit','Naidoo','Pillay','Radebe','Modise',
        'Mthembu','Khumalo','Sithole','Ntuli','Mkhize','Cele','Mahlangu','Nkosi','Dube','Shabalala'
    ];
    v_provs  TEXT[] := ARRAY['Gauteng','KwaZulu-Natal','Western Cape','Eastern Cape','Limpopo','Mpumalanga','North West','Free State'];
    v_cities TEXT[] := ARRAY['Johannesburg','Durban','Cape Town','Gqeberha','Polokwane','Nelspruit','Rustenburg','Bloemfontein'];
    v_streets TEXT[] := ARRAY['Main Rd','Church St','Station Rd','Market St','Freedom Ave','Nelson Mandela Dr','Voortrekker Rd','Jan Smuts Ave','Commissioner St','Victoria Rd'];
    v_suburbs TEXT[] := ARRAY['Ext 1','Township B','Section C','Block D','Phase 2','Unit 7','Sector 3','Area F'];
BEGIN
    -- A. Admins as members in their own groups
    FOR v_g_idx IN 1..30 LOOP
        v_g_id    := uuid_generate_v5('11111111-1111-1111-1111-111111111111', v_g_idx::text);
        v_user_id := CASE WHEN v_g_idx % 2 = 0 THEN 'b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2'::UUID
                                                ELSE 'c3c3c3c3-c3c3-c3c3-c3c3-c3c3c3c3c3c3'::UUID END;
        v_m_id    := uuid_generate_v5(v_g_id, v_user_id::text);

        INSERT INTO public.members (
            id, group_id, user_id, full_name, status, joined_at, probation_end_at, member_key,
            phone, email, city, province, notification_pref,
            document_1_url, document_1_type, document_1_status,
            document_2_url, document_2_type, document_2_status
        ) VALUES (
            v_m_id, v_g_id, v_user_id,
            CASE WHEN v_g_idx % 2 = 0 THEN 'Sizwe Dhlomo' ELSE 'Thandiwe Khumalo' END,
            'active', NOW() - INTERVAL '18 months', NOW() - INTERVAL '15 months',
            'ADM-' || LPAD(v_g_idx::text, 3, '0'),
            CASE WHEN v_g_idx % 2 = 0 THEN '0831234567' ELSE '0827654321' END,
            CASE WHEN v_g_idx % 2 = 0 THEN 'admin1@test.com' ELSE 'admin2@test.com' END,
            'Johannesburg', 'Gauteng', 'both',
            'https://storage.example.com/docs/admin_id.jpg', 'ID', 'verified',
            'https://storage.example.com/docs/admin_res.jpg', 'Proof of Residence', 'verified'
        ) ON CONFLICT DO NOTHING;
    END LOOP;

    -- B. Persona: member1 (Jabu) in groups 1–3, member2 (Lerato) in groups 4–6
    FOR v_g_idx IN 1..3 LOOP
        v_g_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', v_g_idx::text);
        v_m_id := uuid_generate_v5(v_g_id, 'd4d4d4d4-d4d4-d4d4-d4d4-d4d4d4d4d4d4');
        INSERT INTO public.members (
            id, group_id, user_id, full_name, status, joined_at, probation_end_at, member_key,
            phone, email, street, suburb, city, province, notification_pref,
            document_1_url, document_1_type, document_1_status,
            document_2_url, document_2_type, document_2_status
        ) VALUES (
            v_m_id, v_g_id, 'd4d4d4d4-d4d4-d4d4-d4d4-d4d4d4d4d4d4',
            'Jabu Buthelezi', 'active',
            NOW() - INTERVAL '8 months', NOW() - INTERVAL '5 months',
            'M1-G' || v_g_idx,
            '0761234567', 'member1@test.com',
            '14 Ntombi Street', 'Umlazi D', 'Durban', 'KwaZulu-Natal', 'whatsapp',
            'https://storage.example.com/docs/jabu_id.jpg', 'ID', 'verified',
            'https://storage.example.com/docs/jabu_res.jpg', 'Proof of Residence', 'verified'
        ) ON CONFLICT DO NOTHING;
    END LOOP;

    FOR v_g_idx IN 4..6 LOOP
        v_g_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', v_g_idx::text);
        v_m_id := uuid_generate_v5(v_g_id, 'e5e5e5e5-e5e5-e5e5-e5e5-e5e5e5e5e5e5');
        INSERT INTO public.members (
            id, group_id, user_id, full_name, status, joined_at, probation_end_at, member_key,
            phone, email, street, suburb, city, province, notification_pref,
            document_1_url, document_1_type, document_1_status,
            document_2_url, document_2_type, document_2_status
        ) VALUES (
            v_m_id, v_g_id, 'e5e5e5e5-e5e5-e5e5-e5e5-e5e5e5e5e5e5',
            'Lerato Mokoena', 'active',
            NOW() - INTERVAL '4 months', NOW() - INTERVAL '1 month',
            'M2-G' || v_g_idx,
            '0729876543', 'member2@test.com',
            '7 Ubuntu Close', 'Soweto Ext 3', 'Johannesburg', 'Gauteng', 'both',
            'https://storage.example.com/docs/lerato_id.jpg', 'ID', 'verified',
            'https://storage.example.com/docs/lerato_res.jpg', 'Proof of Residence', 'pending'
        ) ON CONFLICT DO NOTHING;
    END LOOP;

    -- C. member3 (Nompumelelo Dlamini — senior active, long history, loan holder)
    --    Groups 7, 8, 9 — burial society + stokvel + investment club
    FOR v_g_idx IN 7..9 LOOP
        v_g_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', v_g_idx::text);
        v_m_id := uuid_generate_v5(v_g_id, 'f6f6f6f6-f6f6-f6f6-f6f6-f6f6f6f6f6f6');
        INSERT INTO public.members (
            id, group_id, user_id, full_name, id_number, status, joined_at, probation_end_at, member_key,
            phone, email, street, suburb, city, province, notification_pref,
            total_contributions, total_paid,
            document_1_url, document_1_type, document_1_status,
            document_2_url, document_2_type, document_2_status,
            document_3_url, document_3_type, document_3_status,
            document_4_url, document_4_type, document_4_status
        ) VALUES (
            v_m_id, v_g_id, 'f6f6f6f6-f6f6-f6f6-f6f6-f6f6f6f6f6f6',
            'Nompumelelo Dlamini', '8803015800082',
            'active',
            NOW() - INTERVAL '22 months', NOW() - INTERVAL '19 months',
            'M3-G' || v_g_idx,
            '0731234560', 'member3@test.com',
            '3 Khulekani Street', 'Umlazi P', 'Durban', 'KwaZulu-Natal', 'whatsapp',
            18, 0.0,  -- contributions set by explicit section below
            'https://storage.example.com/docs/nompumelelo_id.jpg', 'ID', 'verified',
            'https://storage.example.com/docs/nompumelelo_por.jpg', 'Proof of Residence', 'verified',
            'https://storage.example.com/docs/nompumelelo_benef.pdf', 'Beneficiary Form', 'verified',
            'https://storage.example.com/docs/nompumelelo_marriage.pdf', 'Marriage Certificate', 'pending'
        ) ON CONFLICT DO NOTHING;
    END LOOP;

    -- D. member4 (Sipho Radebe — new probation member, joined recently)
    --    Groups 10, 11 — stokvel
    FOR v_g_idx IN 10..11 LOOP
        v_g_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', v_g_idx::text);
        v_m_id := uuid_generate_v5(v_g_id, 'a7a7a7a7-a7a7-a7a7-a7a7-a7a7a7a7a7a7');
        INSERT INTO public.members (
            id, group_id, user_id, full_name, id_number, status, joined_at, probation_end_at, member_key,
            phone, email, street, suburb, city, province, notification_pref,
            total_contributions, total_paid,
            document_1_url, document_1_type, document_1_status,
            document_2_url, document_2_type, document_2_status
        ) VALUES (
            v_m_id, v_g_id, 'a7a7a7a7-a7a7-a7a7-a7a7-a7a7a7a7a7a7',
            'Sipho Radebe', '9507285800083',
            'probation',
            NOW() - INTERVAL '2 months', NOW() + INTERVAL '1 month',
            'M4-G' || v_g_idx,
            '0746543210', 'member4@test.com',
            '22 Thabo Nkosi Avenue', 'Soweto East', 'Johannesburg', 'Gauteng', 'both',
            2, 0.0,
            'https://storage.example.com/docs/sipho_id.jpg', 'ID', 'verified',
            'https://storage.example.com/docs/sipho_por.jpg', 'Proof of Residence', 'pending'
        ) ON CONFLICT DO NOTHING;
    END LOOP;

    -- E. member5 (Busisiwe Mthembu — suspended, missed last 3 contributions)
    --    Groups 1, 5 — burial society
    FOR v_g_idx IN 1..1 LOOP
        v_g_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', v_g_idx::text);
        v_m_id := uuid_generate_v5(v_g_id, 'b8b8b8b8-b8b8-b8b8-b8b8-b8b8b8b8b8b8');
        INSERT INTO public.members (
            id, group_id, user_id, full_name, id_number, status, joined_at, probation_end_at, member_key,
            phone, email, street, suburb, city, province, notification_pref,
            total_contributions, total_paid,
            document_1_url, document_1_type, document_1_status,
            document_2_url, document_2_type, document_2_status
        ) VALUES (
            v_m_id, v_g_id, 'b8b8b8b8-b8b8-b8b8-b8b8-b8b8b8b8b8b8',
            'Busisiwe Mthembu', '9204130800084',
            'suspended',
            NOW() - INTERVAL '15 months', NOW() - INTERVAL '12 months',
            'M5-G' || v_g_idx,
            '0829876543', 'member5@test.com',
            '9 Mahlangu Road', 'Khayelitsha Block L', 'Cape Town', 'Western Cape', 'email',
            9, 0.0,
            'https://storage.example.com/docs/busisiwe_id.jpg', 'ID', 'verified',
            'https://storage.example.com/docs/busisiwe_por.jpg', 'Proof of Residence', 'rejected'
        ) ON CONFLICT DO NOTHING;
    END LOOP;
    FOR v_g_idx IN 5..5 LOOP
        v_g_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', v_g_idx::text);
        v_m_id := uuid_generate_v5(v_g_id, 'b8b8b8b8-b8b8-b8b8-b8b8-b8b8b8b8b8b8');
        INSERT INTO public.members (
            id, group_id, user_id, full_name, id_number, status, joined_at, probation_end_at, member_key,
            phone, email, street, suburb, city, province, notification_pref,
            total_contributions, total_paid,
            document_1_url, document_1_type, document_1_status,
            document_2_url, document_2_type, document_2_status
        ) VALUES (
            v_m_id, v_g_id, 'b8b8b8b8-b8b8-b8b8-b8b8-b8b8b8b8b8b8',
            'Busisiwe Mthembu', '9204130800084',
            'suspended',
            NOW() - INTERVAL '15 months', NOW() - INTERVAL '12 months',
            'M5-G5',
            '0829876543', 'member5@test.com',
            '9 Mahlangu Road', 'Khayelitsha Block L', 'Cape Town', 'Western Cape', 'email',
            9, 0.0,
            'https://storage.example.com/docs/busisiwe_id.jpg', 'ID', 'verified',
            'https://storage.example.com/docs/busisiwe_por.jpg', 'Proof of Residence', 'rejected'
        ) ON CONFLICT DO NOTHING;
    END LOOP;

    -- F. member6 (Mandla Sithole — brand new, PENDING_PAYMENT, no contributions yet)
    --    Group 2 — burial society
    v_g_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', '2');
    v_m_id := uuid_generate_v5(v_g_id, 'c9c9c9c9-c9c9-c9c9-c9c9-c9c9c9c9c9c9');
    INSERT INTO public.members (
        id, group_id, user_id, full_name, id_number, status, joined_at, member_key,
        phone, email, street, suburb, city, province, notification_pref,
        total_contributions, total_paid,
        document_1_url, document_1_type, document_1_status
    ) VALUES (
        v_m_id, v_g_id, 'c9c9c9c9-c9c9-c9c9-c9c9-c9c9c9c9c9c9',
        'Mandla Sithole', '0003055800085',
        'pending_payment',
        NOW() - INTERVAL '5 days', 'M6-G2',
        '0671234567', 'member6@test.com',
        '55 Freedom Way', 'Galeshewe Block C', 'Kimberley', 'Northern Cape', 'whatsapp',
        0, 0.0,
        'https://storage.example.com/docs/mandla_id.jpg', 'ID', 'pending'
    ) ON CONFLICT DO NOTHING;

    -- C. Bulk generic members (264 + 30 admins + 6 personas = 300 total)
    FOR k IN 1..264 LOOP
        v_g_idx := (k % 30) + 1;
        v_g_id  := uuid_generate_v5('11111111-1111-1111-1111-111111111111', v_g_idx::text);
        v_m_id  := uuid_generate_v5(v_g_id, 'GEN-' || k);

        v_first := v_firsts[(k % 30) + 1];
        v_last  := v_lasts[(k % 20) + 1];
        v_prov  := v_provs[(k % 8) + 1];
        v_city  := v_cities[(k % 8) + 1];
        v_notif := (ARRAY['whatsapp','email','both'])[(k % 3) + 1];

        v_status := CASE
            WHEN k % 18 = 0 THEN 'suspended'
            WHEN k % 13 = 0 THEN 'pending_payment'
            WHEN k % 9  = 0 THEN 'probation'
            ELSE                  'active'
        END;

        v_joined := NOW() - ((k % 700) * INTERVAL '1 day');

        INSERT INTO public.members (
            id, group_id, full_name, status, joined_at, probation_end_at, member_key,
            street, suburb, city, province, phone, email, notification_pref,
            document_1_url, document_1_type, document_1_status,
            document_2_url, document_2_type, document_2_status,
            document_3_url, document_3_type, document_3_status
        ) VALUES (
            v_m_id, v_g_id,
            v_first || ' ' || v_last,
            v_status, v_joined, v_joined + INTERVAL '3 months',
            'MEM-' || LPAD(k::text, 4, '0'),
            (k % 900 + 1)::text || ' ' || v_streets[(k % 10) + 1],
            v_suburbs[(k % 8) + 1],
            v_city, v_prov,
            '0' || (ARRAY['61','72','73','74','76','78','81','82','83','84'])[(k % 10) + 1]
              || LPAD((k * 7 % 9999999)::text, 7, '0'),
            lower(v_first) || '.' || lower(v_last) || k::text || '@email.co.za',
            v_notif,
            'https://storage.example.com/members/mem' || k || '/id.jpg', 'ID',
            CASE WHEN k % 5 = 0 THEN 'pending' WHEN k % 7 = 0 THEN 'rejected' ELSE 'verified' END,
            'https://storage.example.com/members/mem' || k || '/res.jpg', 'Proof of Residence',
            CASE WHEN k % 6 = 0 THEN 'pending' ELSE 'verified' END,
            CASE WHEN v_g_idx <= 12 THEN 'https://storage.example.com/members/mem' || k || '/benef.pdf' ELSE NULL END,
            CASE WHEN v_g_idx <= 12 THEN 'Beneficiary Form' ELSE NULL END,
            CASE WHEN v_g_idx <= 12 THEN (ARRAY['pending','verified','verified'])[(k % 3) + 1] ELSE NULL END
        ) ON CONFLICT DO NOTHING;
    END LOOP;
END $$;

-- ─── 4.5. EXPLICIT PERSONA FINANCIAL HISTORIES ───────────────────────────────
-- Gives each named test member a distinct, realistic financial journey.
-- This runs BEFORE the bulk contribution loop in section 5 so the
-- generic loop's ON CONFLICT DO NOTHING will skip any duplicates.
DO $$
DECLARE
    v_g_id   UUID;
    v_m_id   UUID;
    v_month  INT;
    v_due    DATE;
    v_amount NUMERIC;
    v_total  NUMERIC;
BEGIN

-- ══════════════════════════════════════════════════════════════════════════════
-- PERSONA: Nompumelelo Dlamini (member3) — active, 22 months, loan holder
-- Groups 7, 8, 9 — fully paid up, diverse payment methods
-- ══════════════════════════════════════════════════════════════════════════════
<<nompumelelo_loop>>
FOR v_g_id IN
    SELECT uuid_generate_v5('11111111-1111-1111-1111-111111111111', g::text)
    FROM unnest(ARRAY[7, 8, 9]) g
LOOP
    v_m_id := uuid_generate_v5(v_g_id, 'f6f6f6f6-f6f6-f6f6-f6f6-f6f6f6f6f6f6');
    SELECT monthly_contribution, joining_fee INTO v_amount, v_total FROM public.groups WHERE id = v_g_id;

    -- Joining fee
    INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, status, payment_method)
    VALUES (v_m_id, v_g_id, v_total, 'joining_fee',
            (NOW() - INTERVAL '22 months')::DATE,
            NOW() - INTERVAL '22 months', 'paid', 'yoco')
    ON CONFLICT DO NOTHING;

    UPDATE public.groups SET balance = balance + v_total WHERE id = v_g_id;

    -- 18 paid monthly contributions (months 1–18), 1 partial (month 19), 3 future/upcoming
    FOR v_month IN 0..20 LOOP
        v_due := (date_trunc('month', NOW() - INTERVAL '22 months') + (v_month || ' month')::INTERVAL)::DATE;
        CONTINUE WHEN v_due > CURRENT_DATE;
        INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, status, payment_method)
        VALUES (
            v_m_id, v_g_id,
            CASE WHEN v_month = 19 THEN round(v_amount * 0.5, 2) ELSE v_amount END,
            'contribution', v_due,
            CASE WHEN v_month < 20 THEN (v_due + INTERVAL '2 days')::TIMESTAMPTZ ELSE NULL END,
            CASE WHEN v_month = 19 THEN 'partial' WHEN v_month < 20 THEN 'paid' ELSE 'due' END,
            (ARRAY['yoco','bank','cash','yoco','bank'])[(v_month % 5) + 1]
        ) ON CONFLICT DO NOTHING;

        IF v_month < 20 THEN
            v_total := CASE WHEN v_month = 19 THEN round(v_amount * 0.5, 2) ELSE v_amount END;
            UPDATE public.members
            SET total_contributions = total_contributions + 1,
                total_paid = total_paid + v_total
            WHERE id = v_m_id;
            UPDATE public.groups SET balance = balance + v_total WHERE id = v_g_id;
            INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, status, processed_at)
            VALUES (v_m_id, v_g_id, v_total, 'contribution',
                    (ARRAY['yoco','bank','cash','yoco','bank'])[(v_month % 5) + 1],
                    'completed', (v_due + INTERVAL '2 days')::TIMESTAMPTZ);
        END IF;
    END LOOP;
END LOOP nompumelelo_loop;

-- Nompumelelo beneficiaries (burial society group 7)
v_g_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', '7');
v_m_id := uuid_generate_v5(v_g_id, 'f6f6f6f6-f6f6-f6f6-f6f6-f6f6f6f6f6f6');
INSERT INTO public.beneficiaries (group_id, member_id, full_name, relationship, date_of_birth, is_over_65, document_url, document_status)
VALUES
    (v_g_id, v_m_id, 'Sibusiso Dlamini',   'Spouse', '1986-06-14', FALSE, 'https://storage.example.com/beneficiaries/nom_b1.pdf', 'verified'),
    (v_g_id, v_m_id, 'Ayanda Dlamini',     'Child',  '2012-03-22', FALSE, 'https://storage.example.com/beneficiaries/nom_b2.pdf', 'verified'),
    (v_g_id, v_m_id, 'Nomazizi Dlamini',   'Mother', '1958-11-05', TRUE, 'https://storage.example.com/beneficiaries/nom_b3.pdf', 'pending')
ON CONFLICT DO NOTHING;

-- Nompumelelo active loan (group 7)
v_amount := (SELECT total_paid FROM public.members WHERE id = v_m_id LIMIT 1);
INSERT INTO public.loans (
    id, member_id, group_id, amount, interest_rate, total_to_repay,
    total_repaid, monthly_repayment, start_date, end_date, next_payment_date, status, purpose
) VALUES (
    uuid_generate_v5(v_m_id, 'LOAN-NOMPUMELELO'),
    v_m_id, v_g_id,
    5000.00, 10.0, 5500.00,
    2750.00, 458.33,
    (NOW() - INTERVAL '6 months')::DATE,
    (NOW() + INTERVAL '6 months')::DATE,
    (NOW() + INTERVAL '1 month')::DATE,
    'active', 'Home renovations — kitchen and bathroom upgrade'
) ON CONFLICT DO NOTHING;
-- 6 repayments already made
INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, paid_at, payment_method)
SELECT uuid_generate_v5(v_m_id, 'LOAN-NOMPUMELELO'), v_m_id, v_g_id,
       458.33,
       NOW() - ((6 - s) || ' months')::INTERVAL,
       'bank'
FROM generate_series(1, 6) s
ON CONFLICT DO NOTHING;


-- ══════════════════════════════════════════════════════════════════════════════
-- PERSONA: Sipho Radebe (member4) — probation, just 2 months in
-- Groups 10, 11 — paid joining fee + 2 monthly contributions
-- ══════════════════════════════════════════════════════════════════════════════
<<sipho_loop>>
FOR v_g_id IN
    SELECT uuid_generate_v5('11111111-1111-1111-1111-111111111111', g::text)
    FROM unnest(ARRAY[10, 11]) g
LOOP
    v_m_id := uuid_generate_v5(v_g_id, 'a7a7a7a7-a7a7-a7a7-a7a7-a7a7a7a7a7a7');
    SELECT monthly_contribution, joining_fee INTO v_amount, v_total FROM public.groups WHERE id = v_g_id;

    -- Joining fee paid at join time
    INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, status, payment_method)
    VALUES (v_m_id, v_g_id, v_total, 'joining_fee',
            (NOW() - INTERVAL '2 months')::DATE,
            NOW() - INTERVAL '2 months', 'paid', 'yoco')
    ON CONFLICT DO NOTHING;
    UPDATE public.groups SET balance = balance + v_total WHERE id = v_g_id;
    INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, status, processed_at)
    VALUES (v_m_id, v_g_id, v_total, 'joining_fee', 'yoco', 'completed', NOW() - INTERVAL '2 months');

    -- 2 monthly contributions paid
    FOR v_month IN 0..1 LOOP
        v_due := (date_trunc('month', NOW() - INTERVAL '2 months') + (v_month || ' month')::INTERVAL)::DATE;
        INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, status, payment_method)
        VALUES (v_m_id, v_g_id, v_amount, 'contribution', v_due,
                (v_due + INTERVAL '1 day')::TIMESTAMPTZ, 'paid', 'yoco')
        ON CONFLICT DO NOTHING;
        UPDATE public.members
        SET total_contributions = total_contributions + 1, total_paid = total_paid + v_amount
        WHERE id = v_m_id;
        UPDATE public.groups SET balance = balance + v_amount WHERE id = v_g_id;
        INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, status, processed_at)
        VALUES (v_m_id, v_g_id, v_amount, 'contribution', 'yoco', 'completed', (v_due + INTERVAL '1 day')::TIMESTAMPTZ);
    END LOOP;

    -- Current month due but not yet paid
    v_due := date_trunc('month', NOW())::DATE;
    INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, status, payment_method)
    VALUES (v_m_id, v_g_id, v_amount, 'contribution', v_due, NULL, 'due', 'yoco')
    ON CONFLICT DO NOTHING;
END LOOP sipho_loop;


-- ══════════════════════════════════════════════════════════════════════════════
-- PERSONA: Busisiwe Mthembu (member5) — suspended after 9 paid months
-- Groups 1, 5 — 9 paid, 3 overdue, 1 late fee outstanding
-- ══════════════════════════════════════════════════════════════════════════════
<<busisiwe_loop>>
FOR v_g_id IN
    SELECT uuid_generate_v5('11111111-1111-1111-1111-111111111111', g::text)
    FROM unnest(ARRAY[1, 5]) g
LOOP
    v_m_id := uuid_generate_v5(v_g_id, 'b8b8b8b8-b8b8-b8b8-b8b8-b8b8b8b8b8b8');
    SELECT monthly_contribution, joining_fee, late_fee INTO v_amount, v_total, v_total FROM public.groups WHERE id = v_g_id;
    SELECT monthly_contribution INTO v_amount FROM public.groups WHERE id = v_g_id;
    SELECT joining_fee INTO v_total FROM public.groups WHERE id = v_g_id;

    -- Joining fee
    INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, status, payment_method)
    VALUES (v_m_id, v_g_id, v_total, 'joining_fee',
            (NOW() - INTERVAL '15 months')::DATE,
            NOW() - INTERVAL '15 months', 'paid', 'cash')
    ON CONFLICT DO NOTHING;
    UPDATE public.groups SET balance = balance + v_total WHERE id = v_g_id;

    -- 9 paid months
    FOR v_month IN 0..8 LOOP
        v_due := (date_trunc('month', NOW() - INTERVAL '15 months') + (v_month || ' month')::INTERVAL)::DATE;
        INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, status, payment_method)
        VALUES (v_m_id, v_g_id, v_amount, 'contribution', v_due,
                (v_due + INTERVAL '3 days')::TIMESTAMPTZ, 'paid', 'bank')
        ON CONFLICT DO NOTHING;
        UPDATE public.members
        SET total_contributions = total_contributions + 1, total_paid = total_paid + v_amount
        WHERE id = v_m_id;
        UPDATE public.groups SET balance = balance + v_amount WHERE id = v_g_id;
        INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, status, processed_at)
        VALUES (v_m_id, v_g_id, v_amount, 'contribution', 'bank', 'completed', (v_due + INTERVAL '3 days')::TIMESTAMPTZ);
    END LOOP;

    -- 3 overdue months (months 9–11)
    FOR v_month IN 9..11 LOOP
        v_due := (date_trunc('month', NOW() - INTERVAL '15 months') + (v_month || ' month')::INTERVAL)::DATE;
        INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, status, payment_method, late_fees_applied)
        VALUES (v_m_id, v_g_id, v_amount, 'contribution', v_due, NULL, 'overdue', 'bank', TRUE)
        ON CONFLICT DO NOTHING;
    END LOOP;

    -- 1 late fee outstanding
    SELECT late_fee INTO v_total FROM public.groups WHERE id = v_g_id;
    INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, status, payment_method)
    VALUES (v_m_id, v_g_id, v_total, 'late_fee',
            (date_trunc('month', NOW() - INTERVAL '6 months'))::DATE + 10,
            NULL, 'due', 'yoco')
    ON CONFLICT DO NOTHING;

    -- Suspension notification
    INSERT INTO public.notifications (group_id, member_id, message, channel, trigger_event)
    VALUES (v_g_id, v_m_id,
            'Your account has been suspended due to 3 consecutive missed contributions. Please contact your group administrator.',
            'both', 'account_suspended')
    ON CONFLICT DO NOTHING;
END LOOP busisiwe_loop;

-- Audit log for Busisiwe's suspension
INSERT INTO public.audit_logs (actor_id, target_member_id, target_group_id, action, details)
SELECT 'c3c3c3c3-c3c3-c3c3-c3c3-c3c3c3c3c3c3',
       uuid_generate_v5(
           uuid_generate_v5('11111111-1111-1111-1111-111111111111', g::text),
           'b8b8b8b8-b8b8-b8b8-b8b8-b8b8b8b8b8b8'
       ),
       uuid_generate_v5('11111111-1111-1111-1111-111111111111', g::text),
       'MEMBER_SUSPENDED',
       jsonb_build_object('reason', 'Missed 3 consecutive monthly contributions', 'suspended_at', NOW())
FROM unnest(ARRAY[1, 5]) g
ON CONFLICT DO NOTHING;


-- ══════════════════════════════════════════════════════════════════════════════
-- PERSONA: Mandla Sithole (member6) — brand new, PENDING_PAYMENT
-- Group 2 — registered but joining fee not yet paid, no contributions
-- ══════════════════════════════════════════════════════════════════════════════
v_g_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', '2');
v_m_id := uuid_generate_v5(v_g_id, 'c9c9c9c9-c9c9-c9c9-c9c9-c9c9c9c9c9c9');

-- Welcome notification already queued
INSERT INTO public.notifications (group_id, member_id, message, channel, trigger_event)
VALUES (v_g_id, v_m_id,
        'Welcome to SanibonaniSave! Your registration is complete. Please pay your joining fee to activate your membership.',
        'whatsapp', 'new_member')
ON CONFLICT DO NOTHING;

END $$;

-- ─── 5. CONTRIBUTIONS & FINANCIAL HISTORY ─────────────────────────────────────
DO $$
DECLARE
    v_m        RECORD;
    v_month    INT;
    v_due_date DATE;
    v_amount   NUMERIC;
    v_late_amt NUMERIC;
    v_status   TEXT;
    v_paid_at  TIMESTAMPTZ;
    v_hash     INT;
    v_method   TEXT;
BEGIN
    FOR v_m IN
        SELECT m.id, m.group_id, m.joined_at, m.status
        FROM public.members m
        WHERE m.status <> 'pending_payment'
    LOOP
        SELECT monthly_contribution, late_fee
        INTO v_amount, v_late_amt
        FROM public.groups WHERE id = v_m.group_id;

        v_hash  := ((hashtext(v_m.id::text)::BIGINT) & 2147483647)::INT;
        v_method := (ARRAY['yoco','bank','cash','other'])[(v_hash % 4) + 1];

        -- Joining fee record
        INSERT INTO public.contributions (
            member_id, group_id, amount, type, due_date, paid_at, status, payment_method
        )
        SELECT v_m.id, v_m.group_id, g.joining_fee, 'joining_fee',
               v_m.joined_at::DATE,
               v_m.joined_at + INTERVAL '2 hours',
               'paid', v_method
        FROM public.groups g
        WHERE g.id = v_m.group_id AND g.joining_fee > 0
        ON CONFLICT DO NOTHING;

        -- Monthly contributions
        FOR v_month IN 0..24 LOOP
            v_due_date := (date_trunc('month', v_m.joined_at) + (v_month || ' month')::INTERVAL)::DATE;
            EXIT WHEN v_due_date > CURRENT_DATE;

            v_status := CASE
                WHEN (v_hash % 20) = 0 AND v_month > 0 THEN 'overdue'
                WHEN (v_hash % 33) = 0 AND v_month >= 22 THEN 'partial'
                ELSE 'paid'
            END;

            v_paid_at := CASE
                WHEN v_status = 'paid'    THEN v_due_date::TIMESTAMPTZ + ((v_hash % 5 + 1) || ' days')::INTERVAL
                WHEN v_status = 'partial' THEN v_due_date::TIMESTAMPTZ + INTERVAL '3 days'
                ELSE NULL
            END;

            INSERT INTO public.contributions (
                member_id, group_id, amount, type, due_date, paid_at, status,
                payment_method, late_fees_applied
            ) VALUES (
                v_m.id, v_m.group_id,
                CASE WHEN v_status = 'partial' THEN round(v_amount * 0.5, 2) ELSE v_amount END,
                'contribution', v_due_date, v_paid_at, v_status, v_method,
                CASE WHEN v_status = 'overdue' AND v_month > 1 THEN TRUE ELSE FALSE END
            ) ON CONFLICT DO NOTHING;

            IF v_status IN ('paid','partial') THEN
                UPDATE public.members
                SET total_contributions = total_contributions + 1,
                    total_paid = total_paid + CASE WHEN v_status = 'partial' THEN round(v_amount * 0.5, 2) ELSE v_amount END
                WHERE id = v_m.id;

                UPDATE public.groups
                SET balance = balance + CASE WHEN v_status = 'partial' THEN round(v_amount * 0.5, 2) ELSE v_amount END
                WHERE id = v_m.group_id;

                INSERT INTO public.payments (
                    member_id, group_id, amount, payment_type, payment_method, status, processed_at
                ) VALUES (
                    v_m.id, v_m.group_id,
                    CASE WHEN v_status = 'partial' THEN round(v_amount * 0.5, 2) ELSE v_amount END,
                    'contribution', v_method, 'completed', v_paid_at
                );
            END IF;

            -- Late fee record for overdue after grace period
            IF v_status = 'overdue' AND v_month > 1 AND v_late_amt > 0 THEN
                INSERT INTO public.contributions (
                    member_id, group_id, amount, type, due_date, paid_at, status, payment_method
                ) VALUES (
                    v_m.id, v_m.group_id, v_late_amt, 'late_fee',
                    v_due_date + 10, NULL, 'due', 'yoco'
                ) ON CONFLICT DO NOTHING;
            END IF;
        END LOOP;
    END LOOP;
END $$;

-- ─── 6. BENEFICIARIES (Burial society members only) ───────────────────────────
DO $$
DECLARE
    v_m      RECORD;
    v_hash   INT;
    v_count  INT;
    j        INT;
    v_b_firsts TEXT[] := ARRAY['Nomvula','Siphamandla','Thabo','Zandile','Siphokazi','Mthokozisi','Nokwanda','Luyanda'];
    v_b_lasts  TEXT[] := ARRAY['Dlamini','Mthembu','Cele','Khumalo','Ndlovu','Shabalala','Nkosi','Buthelezi'];
    v_rels     TEXT[] := ARRAY['Spouse','Child','Parent','Sibling','Child','Spouse','Parent','Child'];
    v_dobs     DATE[] := ARRAY[
        '1990-03-15','2005-11-20','1958-07-01','1995-04-12',
        '2010-09-30','1988-01-25','1963-12-10','2015-06-05'
    ];
BEGIN
    FOR v_m IN
        SELECT m.id, m.group_id
        FROM public.members m
        JOIN public.groups g ON g.id = m.group_id
        WHERE g.type = 'burial_society'
          AND m.status IN ('active','probation')
    LOOP
        v_hash  := ((hashtext(v_m.id::text)::BIGINT) & 2147483647)::INT;
        v_count := (v_hash % 4) + 1;

        FOR j IN 1..v_count LOOP
            INSERT INTO public.beneficiaries (
                group_id, member_id, full_name, relationship,
                date_of_birth, is_over_65, document_url, document_status
            ) VALUES (
                v_m.group_id, v_m.id,
                v_b_firsts[((((v_hash::BIGINT + j) % 8)::INT) + 1)] || ' ' || v_b_lasts[((((v_hash::BIGINT * j) % 8)::INT) + 1)],
                v_rels[((((v_hash::BIGINT + j) % 8)::INT) + 1)],
                v_dobs[((((v_hash::BIGINT * j) % 8)::INT) + 1)],
                v_dobs[((((v_hash::BIGINT * j) % 8)::INT) + 1)] < '1961-01-01'::DATE,
                'https://storage.example.com/beneficiaries/benef_' || v_m.id || '_' || j || '.pdf',
                (ARRAY['verified','verified','pending','rejected'])[(v_hash % 4) + 1]
            );
        END LOOP;
    END LOOP;
END $$;

-- ─── 7. SMART LOANS ───────────────────────────────────────────────────────────
-- Eligibility: active, joined >6 months ago, has total_paid > 0, ~20% selected
DO $$
DECLARE
    v_m           RECORD;
    v_loan_id     UUID;
    v_hash        INT;
    v_principal   NUMERIC;
    v_rate        NUMERIC;
    v_total       NUMERIC;
    v_monthly_rep NUMERIC;
    v_months      INT;
    v_start       DATE;
    v_status      TEXT;
    v_repaid      NUMERIC;
    v_rep_months  INT;
    v_rep_amt     NUMERIC;
    m             INT;
    v_purposes    TEXT[] := ARRAY[
        'School fees for children','Home renovations','Medical expenses',
        'Small business start-up','Vehicle repairs','Grocery stock-up',
        'Funeral expenses','Farm equipment','Wedding costs','Debt consolidation'
    ];
BEGIN
    FOR v_m IN
        SELECT m.id, m.group_id, m.joined_at, m.total_paid
        FROM public.members m
        WHERE m.status = 'active'
          AND m.joined_at < NOW() - INTERVAL '6 months'
          AND m.total_paid > 0
          AND (((hashtext(m.id::text)::BIGINT) & 2147483647) % 5) = 0
    LOOP
        v_hash      := ((hashtext(v_m.id::text)::BIGINT) & 2147483647)::INT;
        v_principal := round(LEAST(GREATEST(v_m.total_paid * 0.5, 500), 50000)::NUMERIC, 2);
        v_rate      := (ARRAY[5.0, 7.5, 10.0, 12.5, 15.0])[(v_hash % 5) + 1];
        v_months    := (ARRAY[3, 6, 9, 12, 18])[(v_hash % 5) + 1];
        v_total     := round(v_principal * (1 + v_rate / 100), 2);
        v_monthly_rep := round(v_total / v_months, 2);
        v_start     := (v_m.joined_at + INTERVAL '7 months')::DATE;

        v_status := CASE
            WHEN v_hash % 10 = 0 THEN 'completed'
            WHEN v_hash % 7  = 0 THEN 'overdue'
            WHEN v_hash % 15 = 0 THEN 'rejected'
            WHEN v_hash % 20 = 0 THEN 'pending'
            ELSE                       'active'
        END;

        INSERT INTO public.loans (
            id, member_id, group_id, amount, interest_rate, total_to_repay,
            total_repaid, monthly_repayment, start_date, end_date, next_payment_date,
            status, purpose
        ) VALUES (
            uuid_generate_v5(v_m.id, 'LOAN-1'),
            v_m.id, v_m.group_id,
            v_principal, v_rate, v_total,
            0, v_monthly_rep,
            v_start, v_start + (v_months || ' months')::INTERVAL,
            v_start + INTERVAL '1 month',
            v_status,
            v_purposes[(v_hash % 10) + 1]
        ) RETURNING id INTO v_loan_id;

        IF v_status IN ('active','completed','overdue') THEN
            v_repaid    := 0;
            v_rep_months := CASE
                WHEN v_status = 'completed' THEN v_months
                WHEN v_status = 'overdue'   THEN GREATEST(1, v_months - 2)
                ELSE                              GREATEST(1, v_months / 2)
            END;

            FOR m IN 1..v_rep_months LOOP
                v_rep_amt := CASE
                    WHEN m = v_rep_months AND v_status = 'completed' THEN v_total - v_repaid
                    ELSE v_monthly_rep
                END;
                v_repaid := v_repaid + v_rep_amt;

                INSERT INTO public.loan_repayments (
                    loan_id, member_id, group_id, amount, paid_at, payment_method
                ) VALUES (
                    v_loan_id, v_m.id, v_m.group_id, v_rep_amt,
                    (v_start + (m || ' months')::INTERVAL)::TIMESTAMPTZ,
                    (ARRAY['yoco','bank','cash'])[(v_hash % 3) + 1]
                );
            END LOOP;

            UPDATE public.loans SET total_repaid = v_repaid WHERE id = v_loan_id;
        END IF;
    END LOOP;
END $$;

-- ─── 8. PAYOUTS ───────────────────────────────────────────────────────────────
DO $$
DECLARE
    v_g     RECORD;
    v_hash  INT;
    v_banks TEXT[] := ARRAY['Capitec','FNB','Standard Bank','Absa','Nedbank'];
    v_brchs TEXT[] := ARRAY['470010','250655','051001','632005','198765'];
    j       INT;
BEGIN
    FOR v_g IN SELECT id, balance, bank_name, account_number, branch_code FROM public.groups LOOP
        v_hash := ((hashtext(v_g.id::text)::BIGINT) & 2147483647)::INT;

        -- 2–4 historical payouts per group
        FOR j IN 1..((v_hash % 3) + 2) LOOP
            INSERT INTO public.payouts (
                group_id, amount, bank_name, account_no, branch_code,
                status, processed_by, processed_at, yoco_payout_id
            ) VALUES (
                v_g.id,
                GREATEST(round((v_g.balance * (0.1 + (v_hash % 5) * 0.05) / j)::NUMERIC, 2), 100),
                COALESCE(v_g.bank_name, v_banks[(v_hash % 5) + 1]),
                COALESCE(v_g.account_number, '0000000001'),
                COALESCE(v_g.branch_code, v_brchs[(v_hash % 5) + 1]),
                (ARRAY['completed','completed','completed','failed','cancelled'])[((((v_hash::BIGINT * j) % 5)::INT) + 1)],
                'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1',
                NOW() - ((j * 45) || ' days')::INTERVAL,
                'YOCO-PO-' || substr(v_g.id::text, 1, 8) || '-' || j
            );
        END LOOP;

        -- 1 pending payout per 3rd group for admin portal testing
        IF v_hash % 3 = 0 THEN
            INSERT INTO public.payouts (
                group_id, amount, bank_name, account_no, branch_code, status
            ) VALUES (
                v_g.id,
                GREATEST(round(v_g.balance * 0.15, 2), 100),
                COALESCE(v_g.bank_name, v_banks[(v_hash % 5) + 1]),
                COALESCE(v_g.account_number, '0000000001'),
                COALESCE(v_g.branch_code, v_brchs[(v_hash % 5) + 1]),
                'pending'
            );
        END IF;
    END LOOP;
END $$;

-- ─── 9. NOTIFICATIONS ─────────────────────────────────────────────────────────
DO $$
DECLARE
    v_g    RECORD;
    v_m    RECORD;
    v_hash INT;
    j      INT;
    v_msgs TEXT[] := ARRAY[
        'Your monthly contribution of R%s is due on the 28th.',
        'Payment received — thank you! Your account is up to date.',
        'Reminder: Your contribution is overdue. Please pay to avoid suspension.',
        'A payout has been processed to your group account.',
        'Welcome to the group! Your membership is now active.',
        'Your beneficiary documents have been approved.',
        'Your loan application has been approved. Funds will be disbursed shortly.',
        'Important: Group meeting scheduled for next Saturday at 10:00 AM.',
        'Your late fee of R50 has been applied to your account.',
        'Congratulations! You have completed your probation period.'
    ];
    v_events TEXT[] := ARRAY[
        'payment_due','payment_received','payment_overdue','payout_processed',
        'member_welcome','document_verified','loan_approved','meeting_reminder',
        'late_fee_applied','probation_complete'
    ];
BEGIN
    -- Group-wide broadcast notifications
    FOR v_g IN SELECT id, monthly_contribution FROM public.groups LOOP
        v_hash := ((hashtext(v_g.id::text)::BIGINT) & 2147483647)::INT;
        FOR j IN 1..3 LOOP
            INSERT INTO public.notifications (group_id, message, channel, trigger_event)
            VALUES (
                v_g.id,
                replace(v_msgs[((((v_hash::BIGINT * j) % 10)::INT) + 1)], '%s', v_g.monthly_contribution::text),
                (ARRAY['email','whatsapp','both'])[(v_hash % 3) + 1],
                v_events[((((v_hash::BIGINT * j) % 10)::INT) + 1)]
            );
        END LOOP;
    END LOOP;

    -- Member-specific notifications
    FOR v_m IN
        SELECT m.id, m.group_id, m.status, g.monthly_contribution
        FROM public.members m
        JOIN public.groups g ON g.id = m.group_id
        WHERE m.status IN ('active','probation','suspended')
        LIMIT 300
    LOOP
        v_hash := ((hashtext(v_m.id::text)::BIGINT) & 2147483647)::INT;
        INSERT INTO public.notifications (group_id, member_id, message, channel, trigger_event)
        VALUES (
            v_m.group_id, v_m.id,
            CASE v_m.status
                WHEN 'suspended' THEN 'Your account has been suspended due to missed payments. Please contact your group admin.'
                WHEN 'probation' THEN 'You are currently on probation. Keep up with your contributions to qualify for loans.'
                ELSE replace(v_msgs[(v_hash % 10) + 1], '%s', v_m.monthly_contribution::text)
            END,
            (ARRAY['whatsapp','email','both'])[(v_hash % 3) + 1],
            CASE v_m.status
                WHEN 'suspended' THEN 'account_suspended'
                WHEN 'probation' THEN 'probation_reminder'
                ELSE v_events[(v_hash % 10) + 1]
            END
        );
    END LOOP;
END $$;

-- ─── 10. ACTUARIAL METRICS ────────────────────────────────────────────────────
DO $$
DECLARE
    v_g           RECORD;
    v_members_cnt INT;
    v_total_paid  NUMERIC;
    v_pmt_rate    NUMERIC;
    v_monthly_inc NUMERIC;
    v_loss_ratio  NUMERIC;
    v_reserve     NUMERIC;
    v_solvency    NUMERIC;
BEGIN
    FOR v_g IN SELECT id, balance, monthly_contribution, current_members, type FROM public.groups LOOP
        SELECT COUNT(*), COALESCE(SUM(total_paid), 0)
        INTO v_members_cnt, v_total_paid
        FROM public.members WHERE group_id = v_g.id AND status = 'active';

        SELECT COALESCE(
            COUNT(*) FILTER (WHERE status IN ('paid','partial')) * 100.0
              / NULLIF(COUNT(*), 0),
            95
        ) INTO v_pmt_rate
        FROM public.contributions WHERE group_id = v_g.id;

        v_monthly_inc := v_g.monthly_contribution * GREATEST(v_members_cnt, 1);
        v_loss_ratio  := CASE WHEN v_g.type = 'burial_society' THEN 62 + (((hashtext(v_g.id::text)::BIGINT) & 2147483647) % 16)
                              WHEN v_g.type = 'investment_club' THEN 20 + (((hashtext(v_g.id::text)::BIGINT) & 2147483647) % 15)
                              ELSE 35 + (((hashtext(v_g.id::text)::BIGINT) & 2147483647) % 20) END;
        v_reserve     := CASE WHEN v_monthly_inc > 0 THEN LEAST(v_g.balance / v_monthly_inc * 100, 300) ELSE 0 END;
        v_solvency    := LEAST(150 - v_loss_ratio + (((hashtext(v_g.id::text)::BIGINT) & 2147483647) % 20), 200);

        INSERT INTO public.group_actuarial_metrics (
            group_id,
            pure_premium, gross_premium,
            reserve_adequacy_pct, solvency_margin_pct, loss_ratio_pct,
            contribution_sufficiency_pct, break_even_members,
            actuarial_present_value, funding_ratio_pct,
            payment_rate_pct, composite_risk_score, insolvency_months,
            expected_annual_claims
        ) VALUES (
            v_g.id,
            round((v_monthly_inc * 0.6)::NUMERIC, 2),
            round((v_monthly_inc * 0.85)::NUMERIC, 2),
            round(v_reserve::NUMERIC, 2),
            round(v_solvency::NUMERIC, 2),
            round(v_loss_ratio::NUMERIC, 2),
            CASE WHEN v_monthly_inc > 0 THEN LEAST(round((v_g.balance / v_monthly_inc * 100)::NUMERIC, 2), 300) ELSE 0 END,
            CASE WHEN v_g.monthly_contribution > 0
                 THEN CEIL(v_monthly_inc * v_loss_ratio / 100 / v_g.monthly_contribution)::INT
                 ELSE 0 END,
            round((v_g.balance * 0.95)::NUMERIC, 2),
            round(v_reserve::NUMERIC, 2),
            round(v_pmt_rate::NUMERIC, 2),
            CASE WHEN v_loss_ratio > 75 THEN 4 WHEN v_loss_ratio > 60 THEN 3 WHEN v_loss_ratio > 45 THEN 2 ELSE 1 END,
            CASE WHEN v_monthly_inc > 0 THEN FLOOR(v_g.balance / v_monthly_inc)::INT ELSE 12 END,
            round((v_monthly_inc * v_loss_ratio / 100 * 12)::NUMERIC, 2)
        );
    END LOOP;
END $$;

-- ─── 11. AUDIT LOGS ───────────────────────────────────────────────────────────
DO $$
DECLARE
    v_g       RECORD;
    v_m       RECORD;
    v_hash    INT;
    j         INT;
    v_actions TEXT[] := ARRAY[
        'GROUP_CREATED','MEMBER_APPROVED','MEMBER_SUSPENDED','PAYOUT_APPROVED',
        'SETTINGS_UPDATED','CONSTITUTION_VERIFIED','PLATFORM_FEE_PAID','LOAN_APPROVED'
    ];
BEGIN
    FOR v_g IN SELECT id FROM public.groups LOOP
        v_hash := ((hashtext(v_g.id::text)::BIGINT) & 2147483647)::INT;
        FOR j IN 1..3 LOOP
            INSERT INTO public.audit_logs (actor_id, target_group_id, action, details)
            VALUES (
                'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1',
                v_g.id,
                v_actions[((((v_hash::BIGINT * j) % 8)::INT) + 1)],
                jsonb_build_object(
                    'timestamp', NOW() - ((j * 20) || ' days')::INTERVAL,
                    'ip', '196.2.' || (v_hash % 255) || '.1'
                )
            );
        END LOOP;
    END LOOP;

    FOR v_m IN SELECT id, group_id FROM public.members WHERE status = 'suspended' LIMIT 30 LOOP
        INSERT INTO public.audit_logs (actor_id, target_member_id, target_group_id, action, details)
        VALUES (
            CASE WHEN (((hashtext(v_m.id::text)::BIGINT) & 2147483647) % 2) = 0
                 THEN 'b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2'::UUID
                 ELSE 'c3c3c3c3-c3c3-c3c3-c3c3-c3c3c3c3c3c3'::UUID END,
            v_m.id, v_m.group_id, 'MEMBER_SUSPENDED',
            jsonb_build_object('reason', 'Missed 2+ consecutive monthly contributions')
        );
    END LOOP;

    -- Final seed marker
    INSERT INTO public.audit_logs (actor_id, action, details)
    VALUES (
        'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1',
        'SEED_COMPLETE',
        jsonb_build_object(
            'version', '2.1-30x300+personas',
            'personas', jsonb_build_array(
                jsonb_build_object('email','torryymsimango@gmail.com','role','platform_admin','uuid','a1a1...'),
                jsonb_build_object('email','admin1@test.com','role','group_admin','uuid','b2b2...'),
                jsonb_build_object('email','admin2@test.com','role','group_admin','uuid','c3c3...'),
                jsonb_build_object('email','member1@test.com','name','Jabu Buthelezi','status','active','scenario','8mo multi-group','uuid','d4d4...'),
                jsonb_build_object('email','member2@test.com','name','Lerato Mokoena','status','active','scenario','4mo probation','uuid','e5e5...'),
                jsonb_build_object('email','member3@test.com','name','Nompumelelo Dlamini','status','active','scenario','22mo senior+loan','uuid','f6f6...'),
                jsonb_build_object('email','member4@test.com','name','Sipho Radebe','status','probation','scenario','2mo new joiner','uuid','a7a7...'),
                jsonb_build_object('email','member5@test.com','name','Busisiwe Mthembu','status','suspended','scenario','9 paid + 3 overdue','uuid','b8b8...'),
                jsonb_build_object('email','member6@test.com','name','Mandla Sithole','status','pending_payment','scenario','brand new, fee not paid','uuid','c9c9...')
            ),
            'groups',        (SELECT COUNT(*) FROM public.groups),
            'members',       (SELECT COUNT(*) FROM public.members),
            'contributions', (SELECT COUNT(*) FROM public.contributions),
            'loans',         (SELECT COUNT(*) FROM public.loans),
            'beneficiaries', (SELECT COUNT(*) FROM public.beneficiaries),
            'payouts',       (SELECT COUNT(*) FROM public.payouts),
            'notifications', (SELECT COUNT(*) FROM public.notifications),
            'seeded_at',     NOW()
        )
    );
END $$;

-- ─── 12. FINAL AGGREGATE REFRESH ──────────────────────────────────────────────
-- ─── 12A. ADMIN/MEMBER ACCESS INVARIANTS (LATEST RULES) ──────────────────────
-- 1) Every group must have an admin who is also a member of that group.
-- 2) Only users who actually own a group may carry the group_admin profile role.
DO $$
DECLARE
    v_missing_admin_memberships INT;
    v_invalid_admin_roles INT;
    v_groups_without_regular_members INT;
BEGIN
    -- Keep profile roles aligned with actual group ownership.
    UPDATE public.profiles p
    SET role = 'group_admin'
    WHERE p.role <> 'platform_admin'
      AND EXISTS (
          SELECT 1
          FROM public.groups g
          WHERE g.admin_user_id = p.id
      );

    UPDATE public.profiles p
    SET role = 'member'
    WHERE p.role = 'group_admin'
      AND NOT EXISTS (
          SELECT 1
          FROM public.groups g
          WHERE g.admin_user_id = p.id
      );

    -- Auto-heal: ensure each group admin has a same-group member row.
    INSERT INTO public.members (
        id, group_id, user_id, full_name, status, joined_at, probation_end_at, member_key,
        phone, email, city, province, notification_pref,
        document_1_type, document_1_status,
        document_2_type, document_2_status
    )
    SELECT
        uuid_generate_v5(g.id, g.admin_user_id::text),
        g.id,
        g.admin_user_id,
        COALESCE(p.full_name, 'Group Admin'),
        'active',
        NOW() - INTERVAL '12 months',
        NOW() - INTERVAL '9 months',
        'ADM-AUTO-' || substr(g.id::text, 1, 8),
        NULL,
        p.email,
        g.city,
        g.province,
        'both',
        'ID',
        'verified',
        'Proof of Residence',
        'verified'
    FROM public.groups g
    LEFT JOIN public.profiles p ON p.id = g.admin_user_id
    LEFT JOIN public.members m
        ON m.group_id = g.id
       AND m.user_id = g.admin_user_id
    WHERE m.id IS NULL
    ON CONFLICT (group_id, user_id) DO NOTHING;

    -- Hard validation checks.
    SELECT COUNT(*)
    INTO v_missing_admin_memberships
    FROM public.groups g
    LEFT JOIN public.members m
        ON m.group_id = g.id
       AND m.user_id = g.admin_user_id
    WHERE m.id IS NULL;

    IF v_missing_admin_memberships > 0 THEN
        RAISE EXCEPTION 'Seed invariant failed: % groups missing admin-as-member records', v_missing_admin_memberships;
    END IF;

    SELECT COUNT(*)
    INTO v_invalid_admin_roles
    FROM public.profiles p
    WHERE p.role = 'group_admin'
      AND NOT EXISTS (
          SELECT 1
          FROM public.groups g
          WHERE g.admin_user_id = p.id
      );

    IF v_invalid_admin_roles > 0 THEN
        RAISE EXCEPTION 'Seed invariant failed: % users marked group_admin but not group owners', v_invalid_admin_roles;
    END IF;

    -- Ensure each group has at least one non-admin member for portal access testing.
    SELECT COUNT(*)
    INTO v_groups_without_regular_members
    FROM public.groups g
    WHERE NOT EXISTS (
        SELECT 1
        FROM public.members m
        WHERE m.group_id = g.id
          AND (m.user_id IS NULL OR m.user_id <> g.admin_user_id)
    );

    IF v_groups_without_regular_members > 0 THEN
        RAISE EXCEPTION 'Seed invariant failed: % groups have no non-admin member test coverage', v_groups_without_regular_members;
    END IF;
END $$;

UPDATE public.groups g
SET current_members = (SELECT COUNT(*) FROM public.members m WHERE m.group_id = g.id);

-- Summary report
SELECT
    'SEED v2.1 (30x300 + 6 named personas) COMPLETE ✅' AS status,
    (SELECT COUNT(*) FROM public.groups)          AS groups,
    (SELECT COUNT(*) FROM public.members)         AS members,
    (SELECT COUNT(*) FROM public.contributions)   AS contributions,
    (SELECT COUNT(*) FROM public.loans)           AS loans,
    (SELECT COUNT(*) FROM public.loan_repayments) AS repayments,
    (SELECT COUNT(*) FROM public.beneficiaries)   AS beneficiaries,
    (SELECT COUNT(*) FROM public.payouts)         AS payouts,
    (SELECT COUNT(*) FROM public.notifications)   AS notifications,
    (SELECT COUNT(*) FROM public.platform_fees)   AS platform_fees,
    (SELECT COUNT(*) FROM public.audit_logs)      AS audit_logs,
    (SELECT COUNT(*) FROM public.groups g
     JOIN public.members m ON m.group_id = g.id AND m.user_id = g.admin_user_id) AS groups_with_admin_as_member,
    (SELECT COUNT(*) FROM public.profiles p
     WHERE p.role = 'group_admin'
       AND EXISTS (SELECT 1 FROM public.groups g WHERE g.admin_user_id = p.id)) AS valid_group_admin_profiles,
    (SELECT ROUND(SUM(balance), 2) FROM public.groups) AS total_platform_balance_rands;
