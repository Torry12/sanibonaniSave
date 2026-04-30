-- DATA RESET: Truncate all relevant tables before seeding
TRUNCATE TABLE
  public.payments,
  public.platform_fees,
  public.payouts,
  public.notifications,
  public.beneficiaries,
  public.member_documents,
  public.contributions,
  public.members,
  public.groups,
  public.profiles
RESTART IDENTITY CASCADE;

-- seed_members_state_scenarios.sql
-- SanibonaniSave: Focused seed for all member state scenarios (for UI/logic testing)
-- Safe to run after schema.sql. Will not affect main mock data.

-- 0. Ensure admin user exists for group foreign key
INSERT INTO auth.users (
    id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token
) VALUES (
    'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1',
    'authenticated',
    'authenticated',
    'admin1@test.com',
    extensions.crypt('password123', extensions.gen_salt('bf')),
    NOW(),
    '{"provider": "email", "providers": ["email"]}',
    '{"full_name": "Test Admin", "role": "group_admin"}',
    NOW(),
    NOW(),
    ''
) ON CONFLICT (id) DO NOTHING;

INSERT INTO public.profiles (id, full_name, email, role)
VALUES ('a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'Test Admin', 'admin1@test.com', 'group_admin')
ON CONFLICT (id) DO NOTHING;

-- 1. Create a test group
-- 1a. Ensure admin is a member of every group for visibility
-- This guarantees that admin1@test.com will see all groups in the app

-- Ensure admin is a member of every group for visibility (run after all group insertions)
DO $$
DECLARE
  group_rec RECORD;
BEGIN
  FOR group_rec IN SELECT id FROM public.groups LOOP
    IF NOT EXISTS (
      SELECT 1 FROM public.members WHERE group_id = group_rec.id AND user_id = 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1'
    ) THEN
      INSERT INTO public.members (
        id, group_id, user_id, full_name, id_number, phone, email, notification_pref, status, joined_at, probation_end_at
      ) VALUES (
        gen_random_uuid(), group_rec.id, 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'Test Admin', '9001015800001', '0810000001', 'admin1@test.com', 'whatsapp', 'active', NOW() - INTERVAL '12 months', NOW() - INTERVAL '6 months'
      ) ON CONFLICT (id) DO NOTHING;
    END IF;
  END LOOP;
END $$;
INSERT INTO public.groups (id, name, type, province, city, township, admin_user_id, is_public, joining_fee, monthly_contribution, created_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'State Scenario Group', 'burial_society', 'Gauteng', 'Johannesburg', 'Soweto', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', TRUE, 100, 200, NOW())
ON CONFLICT (id) DO NOTHING;

-- 2. Insert members with every possible status
INSERT INTO public.members (id, group_id, user_id, full_name, id_number, phone, email, notification_pref, status, joined_at, probation_end_at)
VALUES
  ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', NULL, 'Active Member', '9001015800085', '0810000001', NULL, 'whatsapp', 'active', NOW() - INTERVAL '12 months', NOW() - INTERVAL '6 months'),
  ('22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111111', NULL, 'Probation Member', '9001015800086', '0810000002', NULL, 'email', 'probation', NOW() - INTERVAL '1 month', NOW() + INTERVAL '2 months'),
  ('22222222-2222-2222-2222-222222222203', '11111111-1111-1111-1111-111111111111', NULL, 'Pending Payment', '9001015800087', '0810000003', NULL, 'both', 'pending_payment', NOW() - INTERVAL '2 weeks', NOW() + INTERVAL '1 month'),
  ('22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111111', NULL, 'Suspended Member', '9001015800088', '0810000004', NULL, 'whatsapp', 'suspended', NOW() - INTERVAL '8 months', NOW() - INTERVAL '7 months'),
  ('22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111111', NULL, 'Suspended/Former Member', '9001015800089', '0810000005', NULL, 'email', 'suspended', NOW() - INTERVAL '18 months', NOW() - INTERVAL '17 months');

-- 3. Add contributions for each scenario
INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status)
VALUES
  ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 200, 'contribution', NOW() - INTERVAL '1 month', NOW() - INTERVAL '1 month', 'yoco', 'paid'),
  ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 200, 'contribution', NOW(), NULL, 'yoco', 'due'),
  ('22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111111', 200, 'contribution', NOW(), NULL, 'yoco', 'due'),
  ('22222222-2222-2222-2222-222222222203', '11111111-1111-1111-1111-111111111111', 100, 'joining_fee', NOW() - INTERVAL '2 weeks', NULL, 'yoco', 'due'),
  ('22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111111', 200, 'contribution', NOW() - INTERVAL '2 months', NULL, 'yoco', 'overdue'),
  ('22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111111', 200, 'contribution', NOW() - INTERVAL '12 months', NOW() - INTERVAL '12 months', 'yoco', 'paid');

-- 4. Add documents for all document statuses
INSERT INTO public.member_documents (member_id, group_id, label, document_url, document_type, status)
VALUES
  ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 'ID Document', 'https://example.com/docs/active_id.pdf', 'pdf', 'verified'),
  ('22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111111', 'ID Document', 'https://example.com/docs/probation_id.pdf', 'pdf', 'pending'),
  ('22222222-2222-2222-2222-222222222203', '11111111-1111-1111-1111-111111111111', 'ID Document', 'https://example.com/docs/pending_id.pdf', 'pdf', 'rejected');

-- 5. Add beneficiaries for some, none for others
INSERT INTO public.beneficiaries (group_id, member_id, id, full_name, relationship, date_of_birth, is_over_65, document_status)
VALUES
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222201', gen_random_uuid(), 'Active Beneficiary', 'Spouse', '1985-01-01', FALSE, 'verified'),
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222202', gen_random_uuid(), 'Probation Beneficiary', 'Child', '2010-05-10', FALSE, 'pending');

-- 6. Add notifications for some members
INSERT INTO public.notifications (group_id, member_id, message, channel, trigger_event)
VALUES
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222201', 'Welcome Active!', 'whatsapp', 'custom'),
  ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222204', 'Account suspended.', 'email', 'status_change');

-- 7. Add payouts for the group
INSERT INTO public.payouts (group_id, amount, bank_name, account_no, branch_code, status, created_at)
VALUES
  ('11111111-1111-1111-1111-111111111111', 1000, 'FNB', '62123456789', '250655', 'pending', NOW() - INTERVAL '1 week'),
  ('11111111-1111-1111-1111-111111111111', 2000, 'FNB', '62123456789', '250655', 'completed', NOW() - INTERVAL '2 months');

-- 8. Add platform fees for all states
INSERT INTO public.platform_fees (group_id, fee_type, amount, status, due_date, paid_at)
VALUES
  ('11111111-1111-1111-1111-111111111111', 'registration', 700, 'paid', (NOW() - INTERVAL '12 months')::text, NOW() - INTERVAL '12 months'),
  ('11111111-1111-1111-1111-111111111111', 'monthly', 250, 'due', (NOW() - INTERVAL '1 month')::text, NULL),
  ('11111111-1111-1111-1111-111111111111', 'monthly', 250, 'warning', (NOW() - INTERVAL '2 months')::text, NULL),
  ('11111111-1111-1111-1111-111111111111', 'monthly', 250, 'suspended', (NOW() - INTERVAL '3 months')::text, NULL);

-- 9. Add payment history for all payment states
INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at)
VALUES
  ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 200, 'contribution', 'yoco', 'tx_active_paid', 'completed', NOW() - INTERVAL '1 month'),
  ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 200, 'contribution', 'yoco', 'tx_active_due', 'pending', NOW()),
  ('22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111111', 200, 'contribution', 'yoco', 'tx_suspended_overdue', 'failed', NOW() - INTERVAL '2 months');

-- 10. Add a member with no contributions, no documents, no beneficiaries, no notifications
INSERT INTO public.members (id, group_id, user_id, full_name, id_number, phone, email, notification_pref, status, joined_at, probation_end_at)
VALUES ('22222222-2222-2222-2222-222222222206', '11111111-1111-1111-1111-111111111111', NULL, 'No Data Member', '9001015800090', '0810000006', NULL, 'email', 'active', NOW() - INTERVAL '1 month', NOW());

-- 11. Add 13 more groups with unique admins
-- (UUIDs and emails are deterministic for clarity)
DO $$
DECLARE
  i INT;
  group_id UUID;
  admin_id UUID;
  admin_email TEXT;
BEGIN
  FOR i IN 2..14 LOOP
    group_id := uuid_generate_v5('11111111-1111-1111-1111-111111111111', i::text);
    admin_id := uuid_generate_v5('a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', i::text);
    admin_email := 'admin' || i || '@test.com';
    -- Insert admin user
    INSERT INTO auth.users (id, aud, role, email, encrypted_password, email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token)
    VALUES (
      admin_id, 'authenticated', 'authenticated', admin_email,
      extensions.crypt('password123', extensions.gen_salt('bf')),
      NOW(), '{"provider": "email", "providers": ["email"]}', jsonb_build_object('full_name', 'Test Admin ' || i, 'role', 'group_admin'), NOW(), NOW(), ''
    ) ON CONFLICT (id) DO NOTHING;
    -- Insert admin profile
    INSERT INTO public.profiles (id, full_name, email, role)
    VALUES (admin_id, 'Test Admin ' || i, admin_email, 'group_admin')
    ON CONFLICT (id) DO NOTHING;
    -- Insert group
    INSERT INTO public.groups (id, name, type, province, city, township, admin_user_id, is_public, joining_fee, monthly_contribution, created_at)
    VALUES (
      group_id, 'Scenario Group ' || i, 'burial_society', 'Gauteng', 'City' || i, 'Township' || i, admin_id, TRUE, 100 + i * 10, 200 + i * 10, NOW()
    ) ON CONFLICT (id) DO NOTHING;
  END LOOP;
END $$;

-- 12. Add 200 members with diverse savings scenarios
DO $$
DECLARE
  i INT;
  group_idx INT;
  group_id UUID;
  member_id UUID;
  status_arr TEXT[] := ARRAY['active','probation','pending_payment','suspended'];
  doc_status_arr TEXT[] := ARRAY['verified','pending','rejected'];
  notif_events TEXT[] := ARRAY['custom','status_change','payment_due','fee_warning'];
BEGIN
  FOR i IN 7..200 LOOP
    group_idx := ((i - 1) % 14) + 1;
    group_id := CASE WHEN group_idx = 1 THEN '11111111-1111-1111-1111-111111111111' ELSE uuid_generate_v5('11111111-1111-1111-1111-111111111111', group_idx::text) END;
    member_id := uuid_generate_v5('30000000-0000-0000-0000-000000000000', i::text);
    -- Insert member
    INSERT INTO public.members (id, group_id, user_id, full_name, id_number, phone, email, notification_pref, status, joined_at, probation_end_at)
    VALUES (
      member_id, group_id, NULL, 'Member ' || i, '90010158' || LPAD(i::text, 5, '0'), '081' || LPAD(i::text, 7, '0'), NULL,
      CASE WHEN i % 4 = 0 THEN 'email' WHEN i % 4 = 1 THEN 'whatsapp' ELSE 'both' END,
      status_arr[(i % array_length(status_arr,1)) + 1],
      NOW() - (i * INTERVAL '5 days'),
      NOW() + (i * INTERVAL '2 days')
    ) ON CONFLICT (id) DO NOTHING;
    -- Add contributions: some paid, some overdue, some only joining_fee
    IF i % 5 = 0 THEN
      INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status)
      VALUES (member_id, group_id, 100 + i, 'joining_fee', NOW() - INTERVAL '1 month', NULL, 'yoco', 'due')
      ON CONFLICT DO NOTHING;
    ELSIF i % 5 = 1 THEN
      INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status)
      VALUES (member_id, group_id, 200 + i, 'contribution', NOW() - INTERVAL '2 months', NOW() - INTERVAL '2 months', 'yoco', 'paid')
      ON CONFLICT DO NOTHING;
    ELSIF i % 5 = 2 THEN
      INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status)
      VALUES (member_id, group_id, 200 + i, 'contribution', NOW() - INTERVAL '1 month', NULL, 'yoco', 'overdue')
      ON CONFLICT DO NOTHING;
    ELSIF i % 5 = 3 THEN
      INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, payment_method, status)
      VALUES (member_id, group_id, 200 + i, 'registration_contribution', NOW() - INTERVAL '3 months', NOW() - INTERVAL '3 months', 'yoco', 'paid')
      ON CONFLICT DO NOTHING;
    END IF;
    -- Add documents with mixed statuses
    IF i % 6 = 0 THEN
      INSERT INTO public.member_documents (member_id, group_id, label, document_url, document_type, status)
      VALUES (member_id, group_id, 'ID Document', 'https://example.com/docs/id_'||i||'.pdf', 'pdf', doc_status_arr[(i % 3) + 1])
      ON CONFLICT DO NOTHING;
    END IF;
    -- Add beneficiaries for some
    IF i % 7 = 0 THEN
      INSERT INTO public.beneficiaries (group_id, member_id, id, full_name, relationship, date_of_birth, is_over_65, document_status)
      VALUES (group_id, member_id, gen_random_uuid(), 'Beneficiary '||i, 'Child', '2010-01-01', FALSE, doc_status_arr[(i % 3) + 1])
      ON CONFLICT DO NOTHING;
    END IF;
    -- Add notifications for some
    IF i % 8 = 0 THEN
      INSERT INTO public.notifications (group_id, member_id, message, channel, trigger_event)
      VALUES (group_id, member_id, 'Test notification '||i, 'whatsapp', notif_events[(i % 4) + 1])
      ON CONFLICT DO NOTHING;
    END IF;
    -- Add payouts for some groups
    IF i % 20 = 0 THEN
      INSERT INTO public.payouts (group_id, amount, bank_name, account_no, branch_code, status, created_at)
      VALUES (group_id, 1000 + i, 'FNB', '6212345'||LPAD(i::text,4,'0'), '250655', 'pending', NOW() - INTERVAL '1 week')
      ON CONFLICT DO NOTHING;
    END IF;
    -- Add platform fees for some
    IF i % 15 = 0 THEN
      INSERT INTO public.platform_fees (group_id, fee_type, amount, status, due_date, paid_at)
      VALUES (group_id, 'monthly', 250, 'due', (NOW() - INTERVAL '1 month')::text, NULL)
      ON CONFLICT DO NOTHING;
    END IF;
    -- Add payment history for some
    IF i % 10 = 0 THEN
      INSERT INTO public.payments (member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at)
      VALUES (member_id, group_id, 200 + i, 'contribution', 'yoco', 'tx_'||i, 'completed', NOW() - INTERVAL '1 month')
      ON CONFLICT DO NOTHING;
    END IF;
  END LOOP;
END $$;
