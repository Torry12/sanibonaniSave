-- Truncate all relevant tables before reseeding for a clean development/test environment.
-- WARNING: This will delete ALL data in these tables!

BEGIN;

TRUNCATE TABLE
    public.members,
    public.profiles,
    public.groups,

    public.policies,
    public.group_polls,
    public.group_poll_options,
    public.platform_fees,
    public.contributions,
    public.payments,
    public.beneficiaries,
    public.member_documents,
    public.notifications,
    public.loans,
    public.loan_repayments,
    public.beneficiary_payout_claims,
    public.virtual_bank_accounts,
    public.virtual_bank_transactions
RESTART IDENTITY CASCADE;

COMMIT;

-- Now run your seed script.

