-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — ALIGN CONTRIBUTION TYPES
-- Version: 1.0 (July 2026)
-- ─────────────────────────────────────────────────────────────────────────────

-- Update the check constraint to include all used types for high-fidelity ledgering.
ALTER TABLE public.contributions DROP CONSTRAINT IF EXISTS contributions_type_check;

ALTER TABLE public.contributions ADD CONSTRAINT contributions_type_check
CHECK (type IN (
    'contribution',
    'joining_fee',
    'registration_contribution',
    'late_fee',
    'member_fee',
    'member_fee_ledger',
    'loan_settlement_extraction'
));
