-- -----------------------------------------------------------------------------
-- SanibonaniSave - Align DB Constraints With Latest App Validation Rules
-- Date: 2026-05-14
--
-- Why:
-- - App/domain now enforce:
--   - payment_due_day in 1..28
--   - SA account numbers as 7..13 digits
--   - branch_code as 6 digits
--
-- This migration updates existing environments to match those rules.
-- -----------------------------------------------------------------------------

BEGIN;

-- 1) groups.payment_due_day -> 1..28
-- Cleanup: Clamp values to 1..28
UPDATE public.groups SET payment_due_day = 28 WHERE payment_due_day < 1 OR payment_due_day > 28;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'groups_payment_due_day_check'
          AND conrelid = 'public.groups'::regclass
    ) THEN
        ALTER TABLE public.groups DROP CONSTRAINT groups_payment_due_day_check;
    END IF;
END $$;

ALTER TABLE public.groups
    ADD CONSTRAINT groups_payment_due_day_check
    CHECK (payment_due_day >= 1 AND payment_due_day <= 28);

-- 2) groups bank account constraints (nullable)
-- Cleanup: Nullify invalid account numbers and branch codes
UPDATE public.groups SET account_number = NULL WHERE account_number !~ '^[0-9]{7,13}$';
UPDATE public.groups SET branch_code = NULL WHERE branch_code !~ '^[0-9]{6}$';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'groups_account_number_check'
          AND conrelid = 'public.groups'::regclass
    ) THEN
        ALTER TABLE public.groups DROP CONSTRAINT groups_account_number_check;
    END IF;
END $$;

ALTER TABLE public.groups
    ADD CONSTRAINT groups_account_number_check
    CHECK (account_number IS NULL OR account_number ~ '^[0-9]{7,13}$');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'groups_branch_code_check'
          AND conrelid = 'public.groups'::regclass
    ) THEN
        ALTER TABLE public.groups DROP CONSTRAINT groups_branch_code_check;
    END IF;
END $$;

ALTER TABLE public.groups
    ADD CONSTRAINT groups_branch_code_check
    CHECK (branch_code IS NULL OR branch_code ~ '^[0-9]{6}$');

-- 3) payouts bank fields
-- Cleanup: Update to dummy safe value if invalid
UPDATE public.payouts SET account_no = '0000000' WHERE account_no !~ '^[0-9]{7,13}$';
UPDATE public.payouts SET branch_code = '000000' WHERE branch_code !~ '^[0-9]{6}$';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'payouts_account_no_check'
          AND conrelid = 'public.payouts'::regclass
    ) THEN
        ALTER TABLE public.payouts DROP CONSTRAINT payouts_account_no_check;
    END IF;
END $$;

ALTER TABLE public.payouts
    ADD CONSTRAINT payouts_account_no_check
    CHECK (account_no ~ '^[0-9]{7,13}$');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'payouts_branch_code_check'
          AND conrelid = 'public.payouts'::regclass
    ) THEN
        ALTER TABLE public.payouts DROP CONSTRAINT payouts_branch_code_check;
    END IF;
END $$;

ALTER TABLE public.payouts
    ADD CONSTRAINT payouts_branch_code_check
    CHECK (branch_code ~ '^[0-9]{6}$');

-- 4) beneficiary_payout_claims bank fields
-- Cleanup: Update to dummy safe value if invalid
UPDATE public.beneficiary_payout_claims SET account_no = '0000000' WHERE account_no !~ '^[0-9]{7,13}$';
UPDATE public.beneficiary_payout_claims SET branch_code = '000000' WHERE branch_code !~ '^[0-9]{6}$';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'beneficiary_payout_claims_account_no_check'
          AND conrelid = 'public.beneficiary_payout_claims'::regclass
    ) THEN
        ALTER TABLE public.beneficiary_payout_claims DROP CONSTRAINT beneficiary_payout_claims_account_no_check;
    END IF;
END $$;

ALTER TABLE public.beneficiary_payout_claims
    ADD CONSTRAINT beneficiary_payout_claims_account_no_check
    CHECK (account_no ~ '^[0-9]{7,13}$');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'beneficiary_payout_claims_branch_code_check'
          AND conrelid = 'public.beneficiary_payout_claims'::regclass
    ) THEN
        ALTER TABLE public.beneficiary_payout_claims DROP CONSTRAINT beneficiary_payout_claims_branch_code_check;
    END IF;
END $$;

ALTER TABLE public.beneficiary_payout_claims
    ADD CONSTRAINT beneficiary_payout_claims_branch_code_check
    CHECK (branch_code ~ '^[0-9]{6}$');

COMMIT;

