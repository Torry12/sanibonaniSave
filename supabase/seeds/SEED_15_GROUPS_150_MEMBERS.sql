-- SEED_15_GROUPS_150_MEMBERS.sql
-- Purpose: Create 15 groups and 150 members (10 members per group) with
-- related data (profiles, members, contributions, payments, group_ledger, audit_logs).
-- Safe to run multiple times (idempotent via ON CONFLICT / checks).
-- Intended for development/testing only.

DO $$
DECLARE
    g INT;
    m INT;
    v_group_id UUID;
    v_admin_id UUID;
    v_member_id UUID;
    v_extra_id UUID;
    v_group_name TEXT;
    v_group_balance NUMERIC(12,2);
    v_contribution_amount NUMERIC(10,2);
    v_payment_id UUID;
BEGIN
    -- Ensure extensions used by seeds exist
    PERFORM 1 FROM pg_extension WHERE extname = 'pgcrypto';

    FOR g IN 1..15 LOOP
        v_group_name := format('SEED15-G%s %s', lpad(g::text,2,'0'), 'Community');
        v_group_balance := (1000.00 + (g * 500))::numeric(12,2);

        -- create or update group
        INSERT INTO public.groups (
            id, name, type, province, city, township, description, logo_emoji, joining_fee, monthly_contribution,
            late_fee, late_fee_grace_days, probation_months, payment_due_day, max_members, is_public, allow_partial_payment,
            auto_suspend_after, bank_name, account_number, branch_code, account_type, gateway_public_key, balance,
            admin_user_id, fee_status, registration_paid, is_platform_suspended, constitution_status, latitude, longitude,
            max_beneficiaries, beneficiary_increase_pct, goal_amount, period_months, loan_interest_rate, loan_max_amount, loan_max_months, created_at, updated_at
        ) VALUES (
            gen_random_uuid(), v_group_name, 'community_savings', 'Gauteng', 'Johannesburg', 'Soweto',
            'Seeded test group for integration testing', '🤝', 50.00, 200.00,
            10.00, 5, 3, 28, 120, TRUE, FALSE,
            2, 'Test Bank', lpad((100000 + g)::text,10,'0'), '000001', 'Savings', 'pk_test_seed_group_' || g, v_group_balance,
            NULL, 'paid', TRUE, FALSE, 'verified', -26.0 + (g * 0.1), 28.0 + (g * 0.1), 5, 5.00, 100000.00, 12, 12, now(), now()
        ) ON CONFLICT (name) DO UPDATE SET updated_at = now(), balance = EXCLUDED.balance
        RETURNING id INTO v_group_id;

        -- create a deterministic admin profile for the group (idempotent)
        -- Seed group policy
        INSERT INTO public.policies (id, group_id, name, description, required_amount, status, created_at, updated_at)
        VALUES (gen_random_uuid(), v_group_id, format('SEED15_POLICY_%s', lpad(g::text,2,'0')), 'Seeded policy for group', 5000.00 + (g * 1000), 'active', now() - interval '60 days', now() - interval '30 days')
        ON CONFLICT (name) DO NOTHING;
        -- Seed group poll
        DO $$
        DECLARE v_poll_id UUID := gen_random_uuid();
        BEGIN
        INSERT INTO public.group_polls (id, group_id, created_by_member_id, title, description, status, starts_at, ends_at, created_at, updated_at)
        VALUES (v_poll_id, v_group_id, NULL, format('SEED15_POLL_%s', lpad(g::text,2,'0')), 'Seeded poll for group', 'open', now() - interval '10 days', now() + interval '10 days', now() - interval '10 days', now())
        ON CONFLICT (id) DO NOTHING;
        INSERT INTO public.group_poll_options (id, poll_id, label, position, created_at)
        VALUES (gen_random_uuid(), v_poll_id, 'Option A', 1, now()), (gen_random_uuid(), v_poll_id, 'Option B', 2, now())
        ON CONFLICT (id) DO NOTHING;
        END $$;
        v_admin_id := gen_random_uuid();
        INSERT INTO public.profiles (id, full_name, email, role, created_at, updated_at)
        VALUES (v_admin_id, format('Seed Admin %s', lpad(g::text,2,'0')), format('seed.admin.%s@example.com', lpad(g::text,2,'0')), 'group_admin', now(), now())
        ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, email = EXCLUDED.email, role = EXCLUDED.role, updated_at = now();

        -- create admin member entry and link to group
        INSERT INTO public.members (id, group_id, user_id, full_name, phone, email, notification_pref, status, joined_at, probation_end_at, beneficiary_count, total_contributions, total_paid, member_key, created_at, updated_at)
        VALUES (gen_random_uuid(), v_group_id, v_admin_id, format('Seed Group Admin %s', lpad(g::text,2,'0')), format('079%05s', lpad(g::text,5,'0')), format('seed.admin.%s@example.com', lpad(g::text,2,'0')), 'both', 'active', now() - interval '100 days', now() + interval '30 days', 0, 0, 0, format('SEED_ADMIN_KEY_%s', lpad(g::text,2,'0')), now(), now())
        ON CONFLICT (group_id, user_id) DO NOTHING
        RETURNING id INTO v_member_id;

        -- opening ledger entry for the group
        INSERT INTO public.group_ledger (id, group_id, transaction_id, amount, balance_after, description, category, created_at)
        VALUES (gen_random_uuid(), v_group_id, NULL, v_group_balance, v_group_balance, format('SEED opening balance %s', lpad(g::text,2,'0')), 'opening_balance', now())
        ON CONFLICT (id) DO NOTHING;

        -- Ensure group's stored balance and member count reflect seeded values
        UPDATE public.groups SET balance = v_group_balance, current_members = 10 WHERE id = v_group_id;

        -- create 9 additional members (total 10 per group => 150 members)
        FOR m IN 1..9 LOOP
            v_extra_id := gen_random_uuid();
            INSERT INTO public.profiles (id, full_name, email, role, created_at, updated_at)
            VALUES (v_extra_id, format('Seed Member %s-%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), format('seed.member.%s.%s@example.com', lpad(g::text,2,'0'), lpad(m::text,2,'0')), 'member', now(), now())
            ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name, email = EXCLUDED.email, updated_at = now();

            INSERT INTO public.members (id, group_id, user_id, full_name, phone, email, notification_pref, status, joined_at, probation_end_at, beneficiary_count, total_contributions, total_paid, member_key, created_at, updated_at)
            VALUES (gen_random_uuid(), v_group_id, v_extra_id, format('Seed Member %s-%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), format('071%06s', lpad((g*100 + m)::text,6,'0')), format('seed.member.%s.%s@example.com', lpad(g::text,2,'0'), lpad(m::text,2,'0')), 'both', CASE WHEN m < 8 THEN 'active' ELSE 'probation' END, now() - ((m+g) || ' days')::interval, now() + interval '60 days', 0, 0, 0, format('SEED_KEY_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), now(), now())
            ON CONFLICT (id) DO NOTHING
            RETURNING id INTO v_member_id;

            -- create a contribution and a matching payment for each member to reflect activity
            v_contribution_amount := (100.00 + (g * 5) + m)::numeric(10,2);

            INSERT INTO public.contributions (id, member_id, group_id, amount, type, due_date, paid_at, payment_method, transaction_id, status, created_at)
            VALUES (gen_random_uuid(), v_member_id, v_group_id, v_contribution_amount, 'contribution', current_date - 10, now() - interval '7 days', 'seed_gateway', gen_random_uuid()::text, 'paid', now() - interval '7 days')
            ON CONFLICT (id) DO NOTHING;

            INSERT INTO public.payments (id, member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at, created_at)
            VALUES (gen_random_uuid(), v_member_id, v_group_id, v_contribution_amount, 'contribution', 'seed_gateway', gen_random_uuid()::text, 'completed', now() - interval '7 days', now() - interval '7 days')
            ON CONFLICT (id) DO NOTHING
            RETURNING id INTO v_payment_id;

            -- append ledger entry for the payment (increase group balance)
            INSERT INTO public.group_ledger (id, group_id, transaction_id, amount, balance_after, description, category, created_at)
            VALUES (gen_random_uuid(), v_group_id, v_payment_id, v_contribution_amount, (SELECT COALESCE(balance,0) + v_contribution_amount FROM public.groups WHERE id = v_group_id), format('Member payment %s', v_member_id), 'contribution', now())
            ON CONFLICT (id) DO NOTHING;

            -- update group's balance by adding contribution amount
            UPDATE public.groups SET balance = COALESCE(balance,0) + v_contribution_amount WHERE id = v_group_id;

            -- update member totals
            UPDATE public.members SET total_contributions = COALESCE(total_contributions,0) + 1, total_paid = COALESCE(total_paid,0) + v_contribution_amount WHERE id = v_member_id;

            -- audit log
            INSERT INTO public.audit_logs (id, actor_id, target_member_id, target_group_id, action, details, created_at)
            VALUES (gen_random_uuid(), v_admin_id, v_member_id, v_group_id, 'SEED_MEMBER_ADDED', jsonb_build_object('seed', true, 'member_index', m), now())
            ON CONFLICT (id) DO NOTHING;

            -- Add beneficiary for each member
            INSERT INTO public.beneficiaries (id, group_id, member_id, full_name, id_number, relationship, date_of_birth, is_over_65, document_status, created_at, updated_at)
            VALUES (gen_random_uuid(), v_group_id, v_member_id, format('SEED15_BENEFICIARY_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), lpad((9900000000000 + (g * 100) + m)::text,13,'0'), 'child', '2010-01-01', FALSE, 'verified', now() - interval '10 days', now())
            ON CONFLICT (id) DO NOTHING;

            -- Add member document
            INSERT INTO public.member_documents (id, member_id, group_id, label, document_url, document_type, status, created_at, updated_at)
            VALUES (gen_random_uuid(), v_member_id, v_group_id, format('SEED15_DOC_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), 'https://example.com/doc.pdf', 'id_card', 'verified', now() - interval '10 days', now())
            ON CONFLICT (id) DO NOTHING;

            -- Add notification for member
            INSERT INTO public.notifications (id, group_id, member_id, message, channel, trigger_event, created_at)
            VALUES (gen_random_uuid(), v_group_id, v_member_id, format('SEED15 notification for member %s-%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), 'both', 'seed', now() - interval '5 days')
            ON CONFLICT (id) DO NOTHING;

            -- Add loan and repayment for some members
            IF m = 3 OR m = 7 THEN
                DO $$
                DECLARE v_loan_id UUID := gen_random_uuid();
                BEGIN
                INSERT INTO public.loans (id, member_id, group_id, amount, interest_rate, total_to_repay, monthly_repayment, status, created_at, updated_at, purpose)
                VALUES (v_loan_id, v_member_id, v_group_id, 2000.00 + (g * 100), 10.0, 2200.00 + (g * 100), 200.00, CASE WHEN m = 3 THEN 'active' ELSE 'overdue' END, now() - interval '40 days', now(), format('SEED15_LOAN_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')))
                ON CONFLICT (id) DO NOTHING;
                -- Add repayment
                INSERT INTO public.loan_repayments (id, loan_id, member_id, group_id, amount, paid_at, payment_method, transaction_id, created_at)
                VALUES (gen_random_uuid(), v_loan_id, v_member_id, v_group_id, 500.00, now() - interval '10 days', 'bank', format('seed15_loanrepay_%s_%s', lpad(g::text,2,'0'), lpad(m::text,2,'0')), now() - interval '10 days')
                ON CONFLICT (id) DO NOTHING;
                END $$;
            END IF;
                        -- Add platform fee for group
                        INSERT INTO public.platform_fees (id, group_id, fee_type, amount, status, due_date, paid_at, transaction_id, created_at, updated_at)
                        VALUES (gen_random_uuid(), v_group_id, 'registration', 700.00, 'paid', to_char(now() - interval '100 days', 'YYYY-MM-DD'), now() - interval '99 days', format('SEED15_FEE_%s', lpad(g::text,2,'0')), now() - interval '100 days', now() - interval '99 days')
                        ON CONFLICT (id) DO NOTHING;
        END LOOP;

    END LOOP;

    RAISE NOTICE 'SEED_15_GROUPS_150_MEMBERS completed: 15 groups + 150 members seeded.';
END
$$ LANGUAGE plpgsql;

-- End of seed file

