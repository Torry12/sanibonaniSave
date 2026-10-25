SELECT ('{"app_metadata": {"role": "platform_admin"}}'::jsonb -> 'app_metadata' ->> 'role') = 'platform_admin' as match;
SELECT ('{"app_metadata": {"role": "platform_admin"}}'::jsonb -> 'app_metadata' ->> 'role') as role_val;
