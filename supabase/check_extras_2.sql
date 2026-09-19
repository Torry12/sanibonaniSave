SELECT table_name, column_name, data_type FROM information_schema.columns
WHERE table_name IN ('beneficiary_payout_claims', 'burial_claims', 'loans') AND table_schema = 'public'
ORDER BY table_name, column_name;
