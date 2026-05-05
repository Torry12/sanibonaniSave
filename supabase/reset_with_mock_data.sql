-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — COMPLETE DATABASE RESET WITH COMPREHENSIVE MOCK DATA
-- Version: 3.0 (Updated April 29, 2026)
-- ─────────────────────────────────────────────────────────────────────────────
-- This script populates the database with realistic scenarios for testing:
-- 1. Multiple Group Types (Burial Society, Stokvel, Investment Club)
-- 2. Diverse Member Statuses (Active, Probation, Suspended, Pending Payment)
-- 3. Complex Financial States (Overdue contributions, Partial payments, Loans)
-- 4. Administrative Scenarios (Pending payouts, Document verification, Audit logs)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. CLEAR AUTH USERS & PUBLIC SCHEMA DATA
DELETE FROM auth.users;

-- 2. CREATE PLATFORM ADMIN
INSERT INTO auth.users (
    id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data, created_at, updated_at, confirmation_token
) VALUES (
    '1b8aca84-c136-4c1b-b024-902584ae80d8',
    'authenticated',
    'authenticated',
    'torrymsimango@gmail.com',
    extensions.crypt('torry123M', extensions.gen_salt('bf')),
    NOW(),
    '{"provider": "email", "providers": ["email"]}',
    '{"full_name": "Torry Msimango", "role": "platform_admin"}',
    NOW(),
    NOW(),
    ''
);

-- Profiles is handled by trigger, but we'll ensure it's correct
UPDATE public.profiles SET role = 'platform_admin' WHERE id = '1b8aca84-c136-4c1b-b024-902584ae80d8';

-- 3. CREATE GROUP ADMINS
INSERT INTO auth.users (id, email, encrypted_password, email_confirmed_at, raw_user_meta_data) VALUES
('a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'admin1@test.com', extensions.crypt('password123', extensions.gen_salt('bf')), NOW(), '{"full_name": "John Admin", "role": "group_admin"}'),
('a2a2a2a2-a2a2-a2a2-a2a2-a2a2a2a2a2a2', 'admin2@test.com', extensions.crypt('password123', extensions.gen_salt('bf')), NOW(), '{"full_name": "Jane Admin", "role": "group_admin"}');

-- 4. CREATE MEMBERS
INSERT INTO auth.users (id, email, encrypted_password, email_confirmed_at, raw_user_meta_data) VALUES
('b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1', 'member1@test.com', extensions.crypt('password123', extensions.gen_salt('bf')), NOW(), '{"full_name": "Sipho Nkosi", "role": "member"}'),
('b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2', 'member2@test.com', extensions.crypt('password123', extensions.gen_salt('bf')), NOW(), '{"full_name": "Thandi Dlamini", "role": "member"}'),
('b3b3b3b3-b3b3-b3b3-b3b3-b3b3b3b3b3b3', 'member3@test.com', extensions.crypt('password123', extensions.gen_salt('bf')), NOW(), '{"full_name": "Bongani Moyo", "role": "member"}'),
('b4b4b4b4-b4b4-b4b4-b4b4-b4b4b4b4b4b4', 'member4@test.com', extensions.crypt('password123', extensions.gen_salt('bf')), NOW(), '{"full_name": "Nomvula Zulu", "role": "member"}');

-- 5. CREATE GROUPS
INSERT INTO public.groups (
    id, name, type, province, city, township, description, logo_emoji,
    joining_fee, monthly_contribution, late_fee, late_fee_grace_days,
    probation_months, payment_due_day, max_members, admin_user_id,
    fee_status, registration_paid, balance, latitude, longitude, geohash,
    max_beneficiaries, beneficiary_increase_pct, goal_amount, period_months
) VALUES
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'Ubuntu Burial Society', 'burial_society', 'Gauteng', 'Johannesburg', 'Soweto', 'Protecting families since 2010.', '🕊️', 250.00, 150.00, 50.00, 5, 3, 28, 100, 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'paid', TRUE, 5250.00, -26.2485, 27.8540, 'ke7fxj6n9', 5, 10.00, 100000.00, 12),
('c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 'Imali Yethu Stokvel', 'stokvel', 'KwaZulu-Natal', 'Durban', 'Umlazi', 'Monthly savings for community growth.', '💰', 100.00, 500.00, 25.00, 7, 1, 15, 12, 'a2a2a2a2-a2a2-a2a2-a2a2-a2a2a2a2a2a2', 'paid', TRUE, 18000.00, -29.9678, 30.8931, 'kd3mfrg1h', 0, 0.00, 72000.00, 12),
('c3c3c3c3-c3c3-c3c3-c3c3-c3c3c3c3c3c3', 'Wealth Builders Club', 'investment_club', 'Western Cape', 'Cape Town', 'Khayelitsha', 'Investing together for a better future.', '📈', 500.00, 1000.00, 100.00, 3, 6, 1, 20, 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'due', FALSE, 0.00, -34.0150, 18.6650, 'k3v9fzhv6', 0, 0.00, 500000.00, 24);

-- 6. CREATE MEMBERS (Admins are also members)
INSERT INTO public.members (id, group_id, user_id, full_name, id_number, phone, email, status, joined_at, probation_end_at, total_contributions, total_paid) VALUES
-- Ubuntu Members
('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d101', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', 'John Admin', '8501015009087', '0821234567', 'admin1@test.com', 'active', NOW() - INTERVAL '6 months', NOW() - INTERVAL '3 months', 6, 900.00),
('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d102', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1', 'Sipho Nkosi', '9001015800085', '0831112233', 'member1@test.com', 'active', NOW() - INTERVAL '4 months', NOW() - INTERVAL '1 month', 4, 600.00),
('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d103', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2', 'Thandi Dlamini', '8506150023087', '0842223344', 'member2@test.com', 'probation', NOW() - INTERVAL '1 month', NOW() + INTERVAL '2 months', 1, 150.00),
-- Imali Yethu Members
('d2d2d2d2-d2d2-d2d2-d2d2-d2d2d2d2d201', 'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 'a2a2a2a2-a2a2-a2a2-a2a2-a2a2a2a2a2a2', 'Jane Admin', '8803015800089', '0711234567', 'admin2@test.com', 'active', NOW() - INTERVAL '8 months', NOW() - INTERVAL '7 months', 8, 4000.00),
('d2d2d2d2-d2d2-d2d2-d2d2-d2d2d2d2d202', 'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 'b3b3b3b3-b3b3-b3b3-b3b3-b3b3b3b3b3b3', 'Bongani Moyo', '9205015800082', '0723334455', 'member3@test.com', 'active', NOW() - INTERVAL '5 months', NOW() - INTERVAL '4 months', 5, 2500.00),
('d2d2d2d2-d2d2-d2d2-d2d2-d2d2d2d2d203', 'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 'b4b4b4b4-b4b4-b4b4-b4b4-b4b4b4b4b4b4', 'Nomvula Zulu', '9510150123088', '0734445566', 'member4@test.com', 'suspended', NOW() - INTERVAL '3 months', NOW(), 1, 500.00);

-- 7. CREATE BENEFICIARIES
INSERT INTO public.beneficiaries (group_id, member_id, full_name, relationship, date_of_birth, is_over_65, document_status) VALUES
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'd1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d101', 'Mary Admin', 'Spouse', '1987-03-15', FALSE, 'verified'),
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'd1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d101', 'James Admin Sr', 'Father', '1955-08-20', TRUE, 'pending'),
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'd1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d102', 'Grace Nkosi', 'Spouse', '1992-05-10', FALSE, 'verified');

-- 8. CREATE CONTRIBUTIONS
INSERT INTO public.contributions (member_id, group_id, amount, type, due_date, paid_at, status, yoco_transaction_id) VALUES
-- John Admin (Last 6 months)
('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d101', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 150.00, 'contribution', '2025-11-28', '2025-11-27', 'paid', 'tx_admin_1'),
('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d101', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 150.00, 'contribution', '2025-12-28', '2025-12-28', 'paid', 'tx_admin_2'),
('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d101', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 150.00, 'contribution', '2026-01-28', '2026-01-29', 'paid', 'tx_admin_3'),
('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d101', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 150.00, 'contribution', '2026-02-28', '2026-02-27', 'paid', 'tx_admin_4'),
('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d101', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 150.00, 'contribution', '2026-03-28', '2026-03-28', 'paid', 'tx_admin_5'),
('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d101', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 150.00, 'contribution', '2026-04-28', '2026-04-27', 'paid', 'tx_admin_6'),
-- Sipho Nkosi (Current month due)
('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d102', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 150.00, 'contribution', '2026-04-28', NULL, 'due', NULL),
-- Thandi Dlamini (Overdue)
('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d103', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 150.00, 'contribution', '2026-03-28', NULL, 'overdue', NULL);

-- 9. CREATE LOANS
INSERT INTO public.loans (member_id, group_id, amount, interest_rate, total_to_repay, monthly_repayment, start_date, end_date, next_payment_date, status, purpose) VALUES
('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d102', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 5000.00, 5.00, 5250.00, 437.50, '2026-01-01', '2026-12-31', '2026-05-01', 'active', 'Home improvements'),
('d2d2d2d2-d2d2-d2d2-d2d2-d2d2d2d2d202', 'c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 2000.00, 0.00, 2000.00, 500.00, '2026-03-15', '2026-06-15', '2026-04-15', 'overdue', 'Emergency medical');

-- 10. CREATE PAYOUTS
INSERT INTO public.payouts (group_id, amount, bank_name, account_no, branch_code, status, created_at) VALUES
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 15000.00, 'FNB', '62123456789', '250655', 'pending', NOW() - INTERVAL '2 days'),
('c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 25000.00, 'Standard Bank', '123456789', '051001', 'completed', NOW() - INTERVAL '1 month');

-- 11. CREATE PLATFORM FEES
INSERT INTO public.platform_fees (group_id, fee_type, amount, status, due_date) VALUES
('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'monthly', 10.00, 'paid', '2026-04-01'),
('c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 'monthly', 120.00, 'due', '2026-05-01'),
('c3c3c3c3-c3c3-c3c3-c3c3-c3c3c3c3c3c3', 'registration', 700.00, 'due', '2026-04-29');

-- 12. CREATE AUDIT LOGS
INSERT INTO public.audit_logs (actor_id, target_group_id, action, details) VALUES
('1b8aca84-c136-4c1b-b024-902584ae80d8', 'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'IMPERSONATE_GROUP_ADMIN', '{"group_name": "Ubuntu Burial Society"}'),
('a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', NULL, 'UPDATE_GROUP_SETTINGS', '{"field": "monthly_contribution", "new_value": 150}');

-- 13. REFRESH PostgREST CACHE
NOTIFY pgrst, 'reload schema';

-- ─────────────────────────────────────────────────────────────────────────────
-- DONE! Database reset with scenario-rich mock data.
-- ─────────────────────────────────────────────────────────────────────────────
