-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — BANK INTEGRATION SCHEMA
-- Version: 1.0 (Manual & Automated Bank Transaction Tracking)
-- ─────────────────────────────────────────────────────────────────────────────

/**
 * Tracks raw bank transactions (from manual CSV upload or future API integration).
 * These are reconciled against internal contributions/payouts.
 */
CREATE TABLE IF NOT EXISTS public.bank_transactions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    bank_reference    TEXT NOT NULL,
    amount            NUMERIC(12,2) NOT NULL,
    transaction_date  DATE NOT NULL,
    description       TEXT,
    metadata          JSONB,
    status            TEXT NOT NULL DEFAULT 'unreconciled' CHECK (status IN ('unreconciled', 'reconciled', 'ignored')),
    reconciled_with   UUID, -- References public.group_ledger.id
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    updated_at        TIMESTAMPTZ DEFAULT NOW()
);

-- Indexing for fast reconciliation lookups
CREATE INDEX IF NOT EXISTS idx_bank_tx_group_status ON public.bank_transactions(group_id, status);
CREATE INDEX IF NOT EXISTS idx_bank_tx_reference ON public.bank_transactions(bank_reference);

/**
 * Ensures that the balance of the group is only updated once for a bank transaction.
 * Usually, the app calls process_complex_payment_v1 which adds to ledger.
 * This table is for verifying that physical cash actually landed in the bank.
 */
CREATE OR REPLACE FUNCTION public.reconcile_bank_transaction_v1(
    p_bank_tx_id UUID,
    p_ledger_id UUID
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    UPDATE public.bank_transactions
    SET status = 'reconciled',
        reconciled_with = p_ledger_id,
        updated_at = NOW()
    WHERE id = p_bank_tx_id;

    -- We don't update balances here because the ledger entry (p_ledger_id)
    -- already adjusted the balance. This is purely for audit matching.
END;
$$;
