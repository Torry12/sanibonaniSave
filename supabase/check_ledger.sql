SELECT table_name, column_name, data_type FROM information_schema.columns
WHERE table_name IN ('group_ledger', 'platform_ledger') AND table_schema = 'public'
ORDER BY table_name, column_name;
