-- -----------------------------------------------------------------------------
-- SanibonaniSave - Full App E2E Seed
-- Purpose: Seed broad, realistic data across all major features and group types.
-- Idempotent scope: records tied to groups named E2E-SEED-% and E2E_SEED_* logs.
-- Prerequisite: run 01_DATABASE_SCHEMA.sql and 03_PLATFORM_ADMIN_SETUP.sql first.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    v_admin_id UUID;
    v_group_id UUID;
    v_member_id UUID;
    v_loan_id UUID;

    v_group_index INT;
    v_member_index INT;

    v_group_type TEXT;
    v_group_name TEXT;
    v_group_city TEXT;
    v_group_province TEXT;

    v_member_status TEXT;
    v_fee_status TEXT;

    v_member_ids UUID[];

    v_payout_status_constraint TEXT := '';
    v_platform_fee_status_constraint TEXT := '';

    v_status_group_approved TEXT := 'group_approved';
    v_status_processing TEXT := 'processing';
    v_status_failed TEXT := 'failed';

    v_platform_fee_overdue TEXT := 'overdue';

    v_group_types TEXT[] := ARRAY[
        'burial_society',
        'stokvel',
        'rosca',
        'investment_club',
        'emergency_fund',
        'community_savings',
        'tontine',
        'other'
    ];

    v_provinces TEXT[] := ARRAY[
        'Gauteng',
        'Western Cape',
        'KwaZulu-Natal',
        'Eastern Cape',
        'Free State',
        'Limpopo',
        'Mpumalanga',
        'North West'
    ];

    v_cities TEXT[] := ARRAY[
        'Johannesburg',
        'Cape Town',
        'Durban',
        'Gqeberha',
        'Bloemfontein',
        'Polokwane',
        'Mbombela',
        'Mahikeng'
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

    -- Detect environment-specific payouts status constraint differences.
    SELECT COALESCE(pg_get_constraintdef(c.oid), '')
    INTO v_payout_status_constraint
    FROM pg_constraint c
    WHERE c.conrelid = 'public.payouts'::regclass
      AND c.conname = 'payouts_status_check'
    LIMIT 1;

    IF position('group_approved' IN lower(v_payout_status_constraint)) = 0 THEN
        IF position('pending' IN lower(v_payout_status_constraint)) > 0 THEN
            v_status_group_approved := 'pending';
        ELSE
            v_status_group_approved := 'processing';
        END IF;
    END IF;

    IF position('processing' IN lower(v_payout_status_constraint)) = 0 THEN
        v_status_processing := v_status_group_approved;
    END IF;

    IF position('failed' IN lower(v_payout_status_constraint)) = 0 THEN
        v_status_failed := v_status_processing;
    END IF;

    -- Detect environment-specific platform_fees status differences.
    SELECT COALESCE(pg_get_constraintdef(c.oid), '')
    INTO v_platform_fee_status_constraint
    FROM pg_constraint c
    WHERE c.conrelid = 'public.platform_fees'::regclass
      AND c.conname = 'platform_fees_status_check'
    LIMIT 1;

    IF position('overdue' IN lower(v_platform_fee_status_constraint)) = 0 THEN
        v_platform_fee_overdue := 'warning';
    END IF;

    -- Cleanup previous seed scope.
    DELETE FROM public.audit_logs
    WHERE action LIKE 'E2E_SEED_%'
       OR target_group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%');

    DELETE FROM public.loan_repayments
    WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%');

    DELETE FROM public.beneficiary_payout_claims
    WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%');

    DELETE FROM public.loans
    WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%');

    DELETE FROM public.platform_fees
    WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%');

    DELETE FROM public.payouts
    WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%');

    DELETE FROM public.group_actuarial_metrics
    WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%');

    DELETE FROM public.group_ledger
    WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%');

    DELETE FROM public.notifications
    WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%');

    DELETE FROM public.payments
    WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%');

    DELETE FROM public.contributions
    WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%');

    DELETE FROM public.member_documents
    WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%');

    DELETE FROM public.beneficiaries
    WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%');

    DELETE FROM public.members
    WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%');

    DELETE FROM public.policies
    WHERE group_id IN (SELECT id FROM public.groups WHERE name LIKE 'E2E-SEED-%');

    DELETE FROM public.platform_ledger
    WHERE description LIKE 'E2E seed%';

    DELETE FROM public.groups
    WHERE name LIKE 'E2E-SEED-%';

    -- Keep platform settings deterministic for fee-related tests.
    INSERT INTO public.platform_settings (key, value)
    VALUES ('monthly_per_member', 12.0)
    ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW();

    INSERT INTO public.platform_settings (key, value)
    VALUES ('registration_fee', 700.0)
    ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW();

    -- Seed 8 groups across all group types.
    FOR v_group_index IN 1..array_length(v_group_types, 1) LOOP
        v_group_type := v_group_types[v_group_index];
        v_group_province := v_provinces[v_group_index];
        v_group_city := v_cities[v_group_index];
        v_group_name := format('E2E-SEED-%s %s', v_group_index, replace(initcap(replace(v_group_type, '_', ' ')), '  ', ' '));

        v_fee_status := CASE (v_group_index % 5)
            WHEN 1 THEN 'paid'
            WHEN 2 THEN 'due'
            WHEN 3 THEN 'warning'
            WHEN 4 THEN 'suspended'
            ELSE 'pending_activation'
        END;

        INSERT INTO public.groups (
            id,
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
            yoco_public_key,
            balance,
            goal_amount,
            period_months,
            admin_user_id,
            fee_status,
            registration_paid,
            is_platform_suspended,
            constitution_url,
            constitution_status,
            max_beneficiaries,
            beneficiary_increase_pct,
            loan_interest_rate,
            loan_max_amount,
            loan_max_months,
            latitude,
            longitude,
            geohash
        ) VALUES (
            gen_random_uuid(),
            v_group_name,
            v_group_type,
            v_group_province,
            v_group_city,
            'Seed Township ' || v_group_index,
            'E2E seeded group for end-to-end testing across app features.',
            'group',
            100 + (v_group_index * 10),
            150 + (v_group_index * 50),
            25 + (v_group_index * 5),
            5,
            3,
            25,
            80,
            TRUE,
            (v_group_index % 2 = 0),
            2,
            'FNB',
            '620000000' || v_group_index,
            '250655',
            'Savings',
            'pk_test_seed_group_' || v_group_index,
            20000 + (v_group_index * 2500),
            150000 + (v_group_index * 20000),
            12 + v_group_index,
            v_admin_id,
            v_fee_status,
            (v_group_index % 2 = 0),
            (v_group_index = 8),
            'https://example.com/constitution/e2e-seed-' || v_group_index || '.pdf',
            CASE WHEN v_group_index % 3 = 0 THEN 'verified' ELSE 'pending' END,
            CASE WHEN v_group_type = 'burial_society' THEN 8 ELSE 0 END,
            CASE WHEN v_group_type = 'burial_society' THEN 20 ELSE 0 END,
            CASE WHEN v_group_type IN ('stokvel', 'rosca', 'community_savings', 'other') THEN 8 + v_group_index ELSE 0 END,
            CASE WHEN v_group_type IN ('stokvel', 'rosca', 'community_savings', 'other') THEN 3000 + (v_group_index * 1000) ELSE 0 END,
            CASE WHEN v_group_type IN ('stokvel', 'rosca', 'community_savings', 'other') THEN 12 ELSE 0 END,
            -26.2 + (v_group_index * 0.1),
            28.0 + (v_group_index * 0.1),
            'kek' || v_group_index
        ) RETURNING id INTO v_group_id;

        INSERT INTO public.group_actuarial_metrics (
            group_id,
            pure_premium,
            gross_premium,
            reserve_adequacy_pct,
            solvency_margin_pct,
            loss_ratio_pct,
            contribution_sufficiency_pct,
            break_even_members,
            actuarial_present_value,
            funding_ratio_pct,
            payment_rate_pct,
            composite_risk_score,
            insolvency_months,
            expected_annual_claims
        ) VALUES (
            v_group_id,
            180 + (v_group_index * 10),
            250 + (v_group_index * 15),
            65 + (v_group_index * 3),
            45 + (v_group_index * 2),
            30 + (v_group_index * 4),
            80 + v_group_index,
            10 + v_group_index,
            25000 + (v_group_index * 5000),
            75 + v_group_index,
            85 - v_group_index,
            20 + (v_group_index * 8),
            6 + v_group_index,
            12000 + (v_group_index * 3000)
        );

        INSERT INTO public.policies (
            group_id,
            name,
            description,
            required_amount,
            status
        ) VALUES (
            v_group_id,
            'Base Policy ' || v_group_index,
            'Seed policy for policy listing and contribution policy linkage.',
            5000 + (v_group_index * 1000),
            CASE WHEN v_group_index % 2 = 0 THEN 'active' ELSE 'partial' END
        );

        v_member_ids := ARRAY[]::UUID[];

        -- 8 members per group, spread over member/document/payment states.
        FOR v_member_index IN 1..8 LOOP
            v_member_status := CASE (v_member_index % 4)
                WHEN 1 THEN 'active'
                WHEN 2 THEN 'probation'
                WHEN 3 THEN 'pending_payment'
                ELSE 'suspended'
            END;

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
                profile_photo_url,
                document_1_url,
                document_1_type,
                document_1_status,
                document_2_url,
                document_2_type,
                document_2_status,
                beneficiary_count,
                beneficiary_over_65_count,
                monthly_contribution_override,
                total_contributions,
                total_paid,
                fcm_token,
                member_key
            ) VALUES (
                v_group_id,
                CASE WHEN v_member_index = 1 THEN v_admin_id ELSE NULL END,
                format('E2E Member %s-%s', v_group_index, v_member_index),
                lpad((9001010000000 + (v_group_index * 1000) + v_member_index)::TEXT, 13, '0'),
                '07' || lpad((v_group_index * 100 + v_member_index)::TEXT, 8, '0'),
                format('e2e.member.%s.%s@example.com', v_group_index, v_member_index),
                format('%s Seed Street', v_member_index),
                'Seed Suburb',
                v_group_city,
                v_group_province,
                CASE (v_member_index % 3)
                    WHEN 1 THEN 'both'
                    WHEN 2 THEN 'whatsapp'
                    ELSE 'email'
                END,
                v_member_status,
                NOW() - make_interval(months => v_member_index),
                CASE WHEN v_member_status = 'probation' THEN NOW() + INTERVAL '30 days' ELSE NOW() - INTERVAL '10 days' END,
                format('https://example.com/profiles/e2e-%s-%s.jpg', v_group_index, v_member_index),
                format('https://example.com/docs/e2e-id-%s-%s.pdf', v_group_index, v_member_index),
                'ID',
                CASE (v_member_index % 3)
                    WHEN 1 THEN 'verified'
                    WHEN 2 THEN 'pending'
                    ELSE 'rejected'
                END,
                format('https://example.com/docs/e2e-proof-%s-%s.pdf', v_group_index, v_member_index),
                'Proof of Address',
                CASE WHEN v_member_index % 2 = 0 THEN 'verified' ELSE 'pending' END,
                CASE WHEN v_group_type = 'burial_society' THEN 2 ELSE 0 END,
                CASE WHEN v_group_type = 'burial_society' AND v_member_index % 2 = 0 THEN 1 ELSE 0 END,
                CASE WHEN v_member_index % 3 = 0 THEN 400 + (v_group_index * 10) ELSE NULL END,
                3 + v_member_index,
                1200 + (v_member_index * 250),
                format('fcm_e2e_%s_%s', v_group_index, v_member_index),
                format('MK-E2E-%s-%s', v_group_index, v_member_index)
            ) RETURNING id INTO v_member_id;

            v_member_ids := array_append(v_member_ids, v_member_id);

            INSERT INTO public.member_documents (
                member_id,
                group_id,
                label,
                document_url,
                document_type,
                status
            ) VALUES (
                v_member_id,
                v_group_id,
                'ID/Passport',
                format('https://example.com/memberdocs/e2e-%s-%s-id.pdf', v_group_index, v_member_index),
                'ID',
                CASE
                    WHEN v_member_index % 3 = 0 THEN 'rejected'
                    WHEN v_member_index % 2 = 0 THEN 'verified'
                    ELSE 'pending'
                END
            );

            -- Contributions: paid + due + overdue/partial for each member.
            INSERT INTO public.contributions (
                member_id,
                group_id,
                amount,
                type,
                due_date,
                paid_at,
                payment_method,
                yoco_transaction_id,
                status,
                late_fees_applied
            ) VALUES
            (
                v_member_id,
                v_group_id,
                200 + (v_group_index * 10),
                'contribution',
                CURRENT_DATE - INTERVAL '35 days',
                NOW() - INTERVAL '34 days',
                'yoco',
                format('tx-e2e-paid-%s-%s', v_group_index, v_member_index),
                'paid',
                FALSE
            ),
            (
                v_member_id,
                v_group_id,
                200 + (v_group_index * 10),
                'contribution',
                CURRENT_DATE + INTERVAL '5 days',
                NULL,
                'bank',
                NULL,
                'due',
                FALSE
            ),
            (
                v_member_id,
                v_group_id,
                200 + (v_group_index * 10),
                CASE WHEN v_member_index % 2 = 0 THEN 'late_fee' ELSE 'contribution' END,
                CURRENT_DATE - INTERVAL '10 days',
                CASE WHEN v_member_index % 2 = 0 THEN NOW() - INTERVAL '8 days' ELSE NULL END,
                'cash',
                NULL,
                CASE WHEN v_member_index % 2 = 0 THEN 'partial' ELSE 'overdue' END,
                TRUE
            );

            INSERT INTO public.payments (
                member_id,
                group_id,
                amount,
                payment_type,
                payment_method,
                transaction_id,
                status,
                processed_at
            ) VALUES
            (
                v_member_id,
                v_group_id,
                200 + (v_group_index * 10),
                'contribution',
                'yoco',
                format('pay-e2e-ok-%s-%s', v_group_index, v_member_index),
                'completed',
                NOW() - INTERVAL '2 days'
            ),
            (
                v_member_id,
                v_group_id,
                50,
                'late_fee',
                'bank',
                format('pay-e2e-late-%s-%s', v_group_index, v_member_index),
                CASE WHEN v_member_index % 2 = 0 THEN 'failed' ELSE 'pending' END,
                CASE WHEN v_member_index % 2 = 0 THEN NOW() - INTERVAL '1 day' ELSE NULL END
            );
        END LOOP;

        -- Beneficiaries for burial-society group with varied document status.
        IF v_group_type = 'burial_society' THEN
            INSERT INTO public.beneficiaries (
                group_id,
                member_id,
                full_name,
                id_number,
                relationship,
                date_of_birth,
                is_over_65,
                document_url,
                document_status
            )
            SELECT
                v_group_id,
                v_member_ids[idx],
                format('E2E Beneficiary %s-%s', v_group_index, idx),
                lpad((7001010000000 + idx)::TEXT, 13, '0'),
                CASE WHEN idx % 2 = 0 THEN 'Parent' ELSE 'Sibling' END,
                CURRENT_DATE - make_interval(years => (30 + idx * 7)),
                (idx % 2 = 0),
                format('https://example.com/beneficiaries/e2e-%s-%s.pdf', v_group_index, idx),
                CASE WHEN idx = 1 THEN 'verified' WHEN idx = 2 THEN 'pending' ELSE 'rejected' END
            FROM generate_series(1, 3) AS idx;

            -- Burial claims across workflow states.
            INSERT INTO public.beneficiary_payout_claims (
                group_id,
                member_id,
                beneficiary_id,
                beneficiary_name,
                cause_of_death,
                date_of_death,
                claim_amount,
                bank_name,
                account_no,
                branch_code,
                account_holder,
                notes,
                status,
                reviewed_by,
                reviewed_at,
                admin_notes,
                rejection_reason
            ) VALUES
            (
                v_group_id,
                v_member_ids[1],
                gen_random_uuid(),
                'E2E Claim Beneficiary A',
                'Natural causes',
                CURRENT_DATE - INTERVAL '14 days',
                25000,
                'FNB',
                '62000000111',
                '250655',
                'E2E Claimant A',
                'Seed submitted claim',
                'submitted',
                NULL,
                NULL,
                NULL,
                NULL
            ),
            (
                v_group_id,
                v_member_ids[2],
                gen_random_uuid(),
                'E2E Claim Beneficiary B',
                'Accident',
                CURRENT_DATE - INTERVAL '45 days',
                30000,
                'ABSA',
                '40500000111',
                '632005',
                'E2E Claimant B',
                'Seed escalated claim',
                'escalated',
                v_admin_id,
                NOW() - INTERVAL '3 days',
                'Requires urgent review',
                NULL
            ),
            (
                v_group_id,
                v_member_ids[3],
                gen_random_uuid(),
                'E2E Claim Beneficiary C',
                'Illness',
                CURRENT_DATE - INTERVAL '60 days',
                28000,
                'Nedbank',
                '19800000111',
                '198765',
                'E2E Claimant C',
                'Seed paid claim',
                'paid',
                v_admin_id,
                NOW() - INTERVAL '10 days',
                'Paid successfully',
                NULL
            );
        END IF;

        -- Loans covering all statuses used by admin/member flows.
        INSERT INTO public.loans (
            member_id,
            group_id,
            amount,
            interest_rate,
            total_to_repay,
            total_repaid,
            monthly_repayment,
            start_date,
            end_date,
            next_payment_date,
            status,
            purpose,
            contract_url,
            surety_amount,
            reviewed_by,
            reviewed_at,
            admin_notes,
            rejection_reason
        ) VALUES
        (
            v_member_ids[1],
            v_group_id,
            3000,
            10,
            3300,
            0,
            550,
            CURRENT_DATE,
            CURRENT_DATE + INTERVAL '6 months',
            CURRENT_DATE + INTERVAL '30 days',
            'pending',
            'Business inventory',
            NULL,
            300,
            NULL,
            NULL,
            NULL,
            NULL
        ),
        (
            v_member_ids[2],
            v_group_id,
            4500,
            12,
            5040,
            0,
            840,
            CURRENT_DATE,
            CURRENT_DATE + INTERVAL '6 months',
            CURRENT_DATE + INTERVAL '30 days',
            'approved',
            'School fees',
            'https://example.com/contracts/e2e-approved-' || v_group_index || '.pdf',
            500,
            v_admin_id,
            NOW() - INTERVAL '2 days',
            'Approved in seed',
            NULL
        ),
        (
            v_member_ids[3],
            v_group_id,
            6000,
            15,
            6900,
            2300,
            1150,
            CURRENT_DATE - INTERVAL '90 days',
            CURRENT_DATE + INTERVAL '90 days',
            CURRENT_DATE + INTERVAL '15 days',
            'partially_paid',
            'Home repairs',
            'https://example.com/contracts/e2e-partial-' || v_group_index || '.pdf',
            700,
            v_admin_id,
            NOW() - INTERVAL '70 days',
            'Partially paid seed case',
            NULL
        ),
        (
            v_member_ids[4],
            v_group_id,
            2500,
            8,
            2700,
            0,
            450,
            CURRENT_DATE - INTERVAL '60 days',
            CURRENT_DATE + INTERVAL '120 days',
            CURRENT_DATE - INTERVAL '5 days',
            'overdue',
            'Medical emergency',
            NULL,
            250,
            v_admin_id,
            NOW() - INTERVAL '50 days',
            'Missed repayment',
            NULL
        ),
        (
            v_member_ids[5],
            v_group_id,
            1800,
            5,
            1890,
            1890,
            315,
            CURRENT_DATE - INTERVAL '180 days',
            CURRENT_DATE - INTERVAL '30 days',
            NULL,
            'completed',
            'Short-term cash flow',
            'https://example.com/contracts/e2e-complete-' || v_group_index || '.pdf',
            200,
            v_admin_id,
            NOW() - INTERVAL '170 days',
            'Completed loan',
            NULL
        ),
        (
            v_member_ids[6],
            v_group_id,
            2200,
            9,
            2398,
            0,
            400,
            CURRENT_DATE,
            CURRENT_DATE + INTERVAL '180 days',
            NULL,
            'rejected',
            'Personal use',
            NULL,
            0,
            v_admin_id,
            NOW() - INTERVAL '1 day',
            'Rejected in seed',
            'Contribution history below threshold'
        ),
        (
            v_member_ids[7],
            v_group_id,
            2800,
            11,
            3108,
            0,
            518,
            CURRENT_DATE - INTERVAL '10 days',
            CURRENT_DATE + INTERVAL '170 days',
            NULL,
            'cancelled',
            'Appliance purchase',
            NULL,
            0,
            v_admin_id,
            NOW() - INTERVAL '2 days',
            'Cancelled request',
            'Cancelled by member'
        );

        -- Repayments for partially_paid and completed loans.
        SELECT id INTO v_loan_id
        FROM public.loans
        WHERE group_id = v_group_id
          AND member_id = v_member_ids[3]
          AND status = 'partially_paid'
        LIMIT 1;

        IF v_loan_id IS NOT NULL THEN
            INSERT INTO public.loan_repayments (
                loan_id,
                member_id,
                group_id,
                amount,
                paid_at,
                payment_method,
                transaction_id
            ) VALUES
            (
                v_loan_id,
                v_member_ids[3],
                v_group_id,
                1150,
                NOW() - INTERVAL '45 days',
                'yoco',
                format('repay-e2e-%s-a', v_group_index)
            ),
            (
                v_loan_id,
                v_member_ids[3],
                v_group_id,
                1150,
                NOW() - INTERVAL '15 days',
                'bank',
                format('repay-e2e-%s-b', v_group_index)
            );
        END IF;

        SELECT id INTO v_loan_id
        FROM public.loans
        WHERE group_id = v_group_id
          AND member_id = v_member_ids[5]
          AND status = 'completed'
        LIMIT 1;

        IF v_loan_id IS NOT NULL THEN
            INSERT INTO public.loan_repayments (
                loan_id,
                member_id,
                group_id,
                amount,
                paid_at,
                payment_method,
                transaction_id
            ) VALUES
            (
                v_loan_id,
                v_member_ids[5],
                v_group_id,
                945,
                NOW() - INTERVAL '120 days',
                'cash',
                format('repay-e2e-%s-c', v_group_index)
            ),
            (
                v_loan_id,
                v_member_ids[5],
                v_group_id,
                945,
                NOW() - INTERVAL '60 days',
                'bank',
                format('repay-e2e-%s-d', v_group_index)
            );
        END IF;

        -- Payouts to exercise disbursement states in platform admin.
        INSERT INTO public.payouts (
            group_id,
            amount,
            bank_name,
            account_no,
            branch_code,
            status,
            processed_by,
            processed_at,
            yoco_payout_id
        ) VALUES
        (
            v_group_id,
            7000 + (v_group_index * 250),
            'FNB',
            '620000900' || v_group_index,
            '250655',
            CASE
                WHEN v_group_index = 1 THEN v_status_group_approved
                WHEN v_group_index = 2 THEN v_status_processing
                WHEN v_group_index = 3 THEN 'completed'
                WHEN v_group_index = 4 THEN v_status_failed
                ELSE 'cancelled'
            END,
            v_admin_id,
            NOW() - make_interval(days => (2 + v_group_index)),
            format('payout-e2e-%s', v_group_index)
        );

        -- Notifications covering common trigger events/channels.
        INSERT INTO public.notifications (
            group_id,
            member_id,
            message,
            channel,
            trigger_event
        ) VALUES
        (
            v_group_id,
            v_member_ids[1],
            'Seed reminder: contribution due soon.',
            'whatsapp',
            'payment_due'
        ),
        (
            v_group_id,
            v_member_ids[2],
            'Seed confirmation: payment received.',
            'email',
            'payment_confirmed'
        ),
        (
            v_group_id,
            v_member_ids[3],
            'Seed alert: loan request under review.',
            'both',
            'loan_requested'
        ),
        (
            v_group_id,
            NULL,
            'Seed broadcast: monthly meeting this weekend.',
            'both',
            'custom'
        );

        -- Platform fees across statuses (with safe fallback for overdue).
        INSERT INTO public.platform_fees (
            group_id,
            fee_type,
            amount,
            due_date,
            status,
            paid_at,
            transaction_id
        ) VALUES
        (
            v_group_id,
            'registration',
            700,
            to_char(CURRENT_DATE - INTERVAL '180 days', 'YYYY-MM-DD'),
            'paid',
            NOW() - INTERVAL '170 days',
            format('pf-reg-e2e-%s', v_group_index)
        ),
        (
            v_group_id,
            'monthly',
            12 * 8,
            to_char(CURRENT_DATE + INTERVAL '10 days', 'YYYY-MM-DD'),
            CASE
                WHEN v_group_index % 3 = 0 THEN v_platform_fee_overdue
                WHEN v_group_index % 3 = 1 THEN 'warning'
                ELSE 'due'
            END,
            NULL,
            NULL
        );

        -- Lightweight ledger lines for list rendering.
        INSERT INTO public.group_ledger (
            group_id,
            transaction_id,
            amount,
            balance_after,
            description,
            category
        ) VALUES
        (
            v_group_id,
            gen_random_uuid(),
            1500 + (v_group_index * 100),
            21000 + (v_group_index * 2500),
            'E2E seed opening balance adjustment',
            'seed_adjustment'
        );

        -- Audit events for platform maintenance/impersonation tabs.
        INSERT INTO public.audit_logs (
            actor_id,
            target_member_id,
            target_group_id,
            action,
            details,
            created_at
        ) VALUES
        (
            v_admin_id,
            v_member_ids[1],
            v_group_id,
            'E2E_SEED_IMPERSONATE_MEMBER',
            jsonb_build_object('groupIndex', v_group_index, 'memberIndex', 1, 'reason', 'seed coverage'),
            NOW() - make_interval(hours => v_group_index)
        ),
        (
            v_admin_id,
            NULL,
            v_group_id,
            'E2E_SEED_PLATFORM_REVIEW',
            jsonb_build_object('groupType', v_group_type, 'note', 'seeded full app scenario pack'),
            NOW() - make_interval(hours => (v_group_index + 1))
        );
    END LOOP;

    -- Platform ledger rows used by platform finance screens.
    INSERT INTO public.platform_ledger (
        transaction_id,
        amount,
        balance_after,
        description,
        category
    ) VALUES
    (
        gen_random_uuid(),
        700,
        700,
        'E2E seed registration fee income',
        'registration'
    ),
    (
        gen_random_uuid(),
        960,
        1660,
        'E2E seed monthly fee income',
        'monthly'
    ),
    (
        gen_random_uuid(),
        -120,
        1540,
        'E2E seed fee reversal test entry',
        'adjustment'
    );

    RAISE NOTICE 'E2E full app seed completed successfully.';
END $$;


