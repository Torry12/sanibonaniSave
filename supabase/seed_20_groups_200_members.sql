-- seed_20_groups_200_members.sql
-- Ensures a platform admin user exists in both auth.users and public.profiles, and seeds 20 groups and 200 members for robust scenario testing.

-- 1. Create platform admin in auth.users if not exists
INSERT INTO auth.users (
    id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token
) VALUES (
    'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1',
    'authenticated',
    'platform_admin',
    'torrymsimango@gmail.com',
    extensions.crypt('torry123M', extensions.gen_salt('bf')),
    NOW(),
    '{"provider": "email", "providers": ["email"]}',
    jsonb_build_object('full_name', 'Platform Admin', 'role', 'platform_admin'),
    NOW(),
    NOW(),
    ''
) ON CONFLICT (id) DO NOTHING;

-- 2. Create platform admin profile if not exists
INSERT INTO public.profiles (id, full_name, email, role)
VALUES ('a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'Platform Admin', 'torrymsimango@gmail.com', 'platform_admin')
ON CONFLICT (id) DO NOTHING;

-- 3. Insert 20 groups, each with the platform admin as admin
DO $$
DECLARE
  i INT;
  group_id UUID;
BEGIN
  FOR i IN 1..20 LOOP
    group_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', i::text);
    INSERT INTO public.groups (
      id, name, type, province, city, township, admin_user_id, is_public, joining_fee, monthly_contribution, created_at,
      current_members, balance, fee_status, registration_paid
    ) VALUES (
      group_id,
      'Test Group ' || i,
      'burial_society',
      CASE WHEN i % 3 = 0 THEN 'Gauteng' WHEN i % 3 = 1 THEN 'KZN' ELSE 'Western Cape' END,
      CASE WHEN i % 3 = 0 THEN 'Johannesburg' WHEN i % 3 = 1 THEN 'Durban' ELSE 'Cape Town' END,
      CASE WHEN i % 3 = 0 THEN 'Soweto' WHEN i % 3 = 1 THEN 'Umlazi' ELSE 'Khayelitsha' END,
      'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1',
      TRUE,
      100 + (i * 5),
      200 + (i * 10),
      NOW(),
      10, 2000, 'paid', TRUE
    ) ON CONFLICT (id) DO NOTHING;
  END LOOP;
END $$;

-- 4. Insert 200 members, 10 per group
DO $$
DECLARE
  i INT;
  j INT;
  group_id UUID;
  member_id UUID;
BEGIN
  FOR i IN 1..20 LOOP
    group_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', i::text);
    -- Add admin as member of each group (active)
    INSERT INTO public.members (
      id, group_id, user_id, full_name, id_number, phone, email, notification_pref, status, joined_at, probation_end_at
    ) VALUES (
      uuid_generate_v5(group_id, 'admin'),
      group_id,
      'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1',
      'Platform Admin',
      '900101580000' || i,
      '08100000' || LPAD(i::text, 3, '0'),
      'torrymsimango@gmail.com',
      'whatsapp',
      'active',
      NOW() - INTERVAL '12 months',
      NOW() - INTERVAL '6 months'
    ) ON CONFLICT (id) DO NOTHING;
    -- Add a sample contribution for admin
    INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status)
    VALUES (
      uuid_generate_v5(group_id, 'admin'),
      group_id,
      200,
      'contribution',
      NOW() - INTERVAL '1 month',
      NOW() - INTERVAL '1 month',
      'yoco',
      'paid'
    ) ON CONFLICT DO NOTHING;
    -- Add 9 more members per group
    FOR j IN 1..9 LOOP
      member_id := uuid_generate_v5(group_id, j::text);
      INSERT INTO public.members (
        id, group_id, user_id, full_name, id_number, phone, email, notification_pref, status, joined_at, probation_end_at
      ) VALUES (
        member_id,
        group_id,
        NULL,
        'Member ' || ((i-1)*10 + j),
        '90010158' || LPAD(((i-1)*10 + j)::text, 4, '0'),
        '081' || LPAD(((i-1)*10 + j)::text, 7, '0'),
        'member' || ((i-1)*10 + j) || '@test.com',
        CASE WHEN j % 4 = 0 THEN 'email' WHEN j % 4 = 1 THEN 'whatsapp' ELSE 'both' END,
        CASE WHEN j % 5 = 0 THEN 'probation' WHEN j % 5 = 1 THEN 'pending_payment' WHEN j % 5 = 2 THEN 'suspended' ELSE 'active' END,
        NOW() - (j * INTERVAL '5 days'),
        NOW() + (j * INTERVAL '2 days')
      ) ON CONFLICT (id) DO NOTHING;
    END LOOP;
  END LOOP;
END $$;

-- 5. Contributions for all members (History)
DO $$
DECLARE
  i INT;
  j INT;
  k INT;
  group_id UUID;
  member_id UUID;
  due_date DATE;
  amount NUMERIC;
BEGIN
  FOR i IN 1..20 LOOP
    group_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', i::text);
    amount := 200 + (i * 10);

    FOR j IN 1..9 LOOP
      member_id := uuid_generate_v5(group_id, j::text);
      -- Add 6 months of contributions for each member
      FOR k IN 1..6 LOOP
        due_date := (NOW() - (k || ' month')::INTERVAL)::DATE;
        -- Most members have paid
        IF (j % 5 != 0) THEN
           INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status)
           VALUES (member_id, group_id, amount, 'contribution', due_date, (due_date + INTERVAL '2 days'), 'yoco', 'paid')
           ON CONFLICT DO NOTHING;

           -- Update member totals
           UPDATE public.members
           SET total_contributions = total_contributions + 1,
               total_paid = total_paid + amount
           WHERE id = member_id;

           -- Update group balance
           UPDATE public.groups SET balance = balance + amount WHERE id = group_id;
        ELSE
           -- Some missed payments
           INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, status)
           VALUES (member_id, group_id, amount, 'contribution', due_date, 'overdue')
           ON CONFLICT DO NOTHING;
        END IF;
      END LOOP;
    END LOOP;
  END LOOP;
END $$;

-- 6. Payout Requests
DO $$
DECLARE
  i INT;
  group_id UUID;
BEGIN
  FOR i IN 1..20 LOOP
    group_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', i::text);

    -- One completed payout
    INSERT INTO public.payouts (group_id, amount, bank_name, account_no, branch_code, status, processed_at)
    VALUES (group_id, 1000, 'Standard Bank', '123456789', '051001', 'completed', NOW() - INTERVAL '1 month')
    ON CONFLICT DO NOTHING;

    UPDATE public.groups SET balance = balance - 1000 WHERE id = group_id;

    -- One pending payout
    INSERT INTO public.payouts (group_id, amount, bank_name, account_no, branch_code, status)
    VALUES (group_id, 500, 'FNB', '987654321', '250655', 'pending')
    ON CONFLICT DO NOTHING;
  END LOOP;
END $$;

-- 7. Loans and Repayments
DO $$
DECLARE
  i INT;
  group_id UUID;
  member_id UUID;
  loan_id UUID;
BEGIN
  FOR i IN 1..20 LOOP
    group_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', i::text);
    -- Member 1 in each group takes a loan
    member_id := uuid_generate_v5(group_id, '1');

    INSERT INTO public.loans (
        member_id, group_id, amount, interest_rate, total_to_repay, monthly_repayment,
        start_date, end_date, status, purpose
    ) VALUES (
        member_id, group_id, 2000, 5, 2100, 350,
        (NOW() - INTERVAL '2 months')::DATE, (NOW() + INTERVAL '4 months')::DATE, 'active', 'Education'
    ) RETURNING id INTO loan_id;

    UPDATE public.groups SET balance = balance - 2000 WHERE id = group_id;

    -- Add one repayment
    INSERT INTO public.loan_repayments (loan_id, member_id, group_id, amount, paid_at)
    VALUES (loan_id, member_id, group_id, 350, NOW() - INTERVAL '1 month');

    UPDATE public.loans SET total_repaid = 350 WHERE id = loan_id;
  END LOOP;
END $$;

-- 8. Notifications
DO $$
DECLARE
  i INT;
  group_id UUID;
BEGIN
  FOR i IN 1..20 LOOP
    group_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', i::text);
    INSERT INTO public.notifications (group_id, message, channel, trigger_event)
    VALUES (group_id, 'Welcome to Test Group ' || i || '! Please remember to pay your joining fee.', 'both', 'group_welcome');
  END LOOP;
END $$;

-- 9. Beneficiaries (for Burial Societies)
DO $$
DECLARE
  i INT;
  j INT;
  group_id UUID;
  member_id UUID;
BEGIN
  FOR i IN 1..20 LOOP
    IF (i % 3 = 0) THEN -- Burial societies
      group_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', i::text);
      FOR j IN 1..3 LOOP
        member_id := uuid_generate_v5(group_id, j::text);
        INSERT INTO public.beneficiaries (group_id, member_id, full_name, relationship, is_over_65)
        VALUES (group_id, member_id, 'Beneficiary ' || j || ' for Member ' || j, 'Spouse', FALSE);
      END LOOP;
    END IF;
  END LOOP;
END $$;

-- 10. Documents (Sample slots)
DO $$
DECLARE
  i INT;
  group_id UUID;
  member_id UUID;
BEGIN
  FOR i IN 1..20 LOOP
    group_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', i::text);
    FOR j IN 1..3 LOOP
      member_id := uuid_generate_v5(group_id, j::text);
      INSERT INTO public.member_documents (member_id, group_id, label, document_url, status)
      VALUES (member_id, group_id, 'ID Document', 'https://example.com/docs/id_' || member_id || '.pdf', 'verified');
    END LOOP;
  END LOOP;
END $$;

-- Platform admin credentials:
-- Email: torrymsimango@gmail.com
-- Password: torry123M
