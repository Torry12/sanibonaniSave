-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — SUPABASE COMPLETE RESET AND REBUILD (Gold Standard)
-- Version: 16.0 (Unified Hardened Architecture - August 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. RESET: Wipes all tables, functions, triggers, and views
\i '07_RESET_DATABASE.sql'

-- 2. SCHEMA: Definitive table structures and extensions
\i '01_DATABASE_SCHEMA.sql'

-- 3. FUNCTIONS: Authoritative business logic and triggers
\i '02_FUNCTIONS_AND_TRIGGERS.sql'

-- 3.1 ADVANCED RPCs: Complex payment processing v4 (central function)
\i '18_DEPLOY_PROCESS_COMPLEX_PAYMENT_V4.sql'

-- 4. SECURITY: Global RLS policies and storage rules
\i '03_SECURITY_AND_RLS.sql'

-- 5. ADMIN SETUP: Platform administrator bootstrap
\i '04_PLATFORM_ADMIN_SETUP.sql'

-- 6. VIEWS: Analytics and reporting snapshots
\i '05_PERFORMANCE_VIEWS.sql'

-- 7. MASTER SEED: High-fidelity test environment (Version 2.1)
\i 'MASTER_SEED_DATA.sql'

-- 7.1 SEED TEST DATA: 10 Groups / 100 Members with 12 Months History & Banking Info
\i '11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql'

-- 7.2 SEED 50 PAYOUT REQUESTS: Multi-type Payout Requests & Approval Conditions
\i '16_SEED_50_PAYMENT_REQUESTS_AND_APPROVALS.sql'

-- 7.3 SEED FINANCIAL ESCALATIONS: Platform Escalations, Loans & Claims
\i '17_SEED_PLATFORM_FINANCIAL_ESCALATIONS.sql'

-- 7.3.1 SEED BURIAL CLAIM ESCALATIONS: Burial Society Claim Escalations
\i '19_SEED_BURIAL_CLAIM_ESCALATIONS.sql'

-- 7.4 INTEGRITY SEED: Forensic audit scenarios
\i '31_REAL_LIFE_INTEGRITY_SEEDS.sql'

-- 7.5 SEED 12-MONTH TRANSACTIONS & LEDGER: Support & Sim Flows 12-Month History
\i '20_SEED_12_MONTHS_TRANSACTIONS.sql'

-- 7.6 SEED VALID BURIAL CLAIMS: Valid Burial Society Claim Scenarios
\i '21_SEED_VALID_BURIAL_CLAIMS.sql'

-- 8. STORAGE: BRL policies and bucket provisioning
\i '30_STORAGE_RLS.sql'

-- ─────────────────────────────────────────────────────────────────────────────
-- SUCCESS: Ground Zero Established. 100% Logic Parity Confirmed.
-- ─────────────────────────────────────────────────────────────────────────────
