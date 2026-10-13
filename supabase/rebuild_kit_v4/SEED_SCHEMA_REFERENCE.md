# SanibonaniSave Seed & Schema Table Reference

This table documents all tables and columns referenced by the seed file (`05_SEED_DATA.sql`) and cross-checked against the canonical schema (`01_TABLES_AND_INDEXES.sql`).

| Table                | Columns Referenced in Seed File                                                                                   |
|----------------------|------------------------------------------------------------------------------------------------------------------|
| platform_settings    | key, value                                                                                                        |
| profiles             | id, full_name, email, role, created_at, updated_at                                                               |
| groups               | id, name, type, province, city, township, description, logo_emoji, joining_fee, monthly_contribution, late_fee, late_fee_grace_days, probation_months, payment_due_day, max_members, is_public, allow_partial_payment, auto_suspend_after, bank_name, account_number, branch_code, account_type, gateway_public_key, balance, admin_user_id, fee_status, registration_paid, is_platform_suspended, constitution_status, latitude, longitude, max_beneficiaries, beneficiary_increase_pct, goal_amount, period_months, loan_interest_rate, loan_max_amount, loan_max_months, created_at, updated_at |
| policies             | id, group_id, name, description, required_amount, status, created_at, updated_at                                  |
| group_polls          | id, group_id, created_by_member_id, title, description, status, starts_at, ends_at, created_at, updated_at        |
| group_poll_options   | id, poll_id, label, position, created_at                                                                         |
| members              | id, group_id, user_id, full_name, phone, email, notification_pref, status, joined_at, probation_end_at, beneficiary_count, total_contributions, total_paid, member_key, created_at, updated_at |
| group_ledger         | group_id, description                                                                                            |
| contributions        | id, member_id, group_id, amount, type, due_date, paid_at, payment_method, transaction_id, status, created_at      |
| payments             | id, member_id, group_id, amount, payment_type, payment_method, transaction_id, status, processed_at, created_at   |
| beneficiaries        | id, group_id, member_id, full_name, id_number, relationship, date_of_birth, is_over_65, document_status, created_at, updated_at |
| member_documents     | id, member_id, group_id, label, document_url, document_type, status, created_at, updated_at                      |
| notifications        | id, group_id, member_id, message, channel, trigger_event, created_at                                             |
| loans                | id, member_id, group_id, amount, interest_rate, total_to_repay, monthly_repayment, status, created_at, updated_at, purpose |
| loan_repayments      | id, loan_id, member_id, group_id, amount, paid_at, payment_method, transaction_id, created_at                    |
| platform_fees        | id, group_id, fee_type, amount, status, due_date, paid_at, transaction_id, created_at, updated_at                |

**Note:**
- Only columns referenced in the seed file are listed. Some tables may have additional columns in the schema.
- `auth.users` is referenced but managed by Supabase and not included here.
- All columns were verified to exist in the schema as of 2026-05-27.

