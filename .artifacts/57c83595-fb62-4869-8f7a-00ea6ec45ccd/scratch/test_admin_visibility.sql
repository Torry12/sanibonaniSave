-- 1. Identify a Platform Admin
SELECT id, email FROM public.profiles WHERE role = 'platform_admin' LIMIT 1;

-- 2. Test visibility for torrymsimango@gmail.com
SET ROLE authenticated;
SET auth.uid = '2785fcff-62aa-41c9-9792-b2e939c0e17f'; -- Assuming this is Torry's ID
SELECT set_config('request.jwt.claims', '{"sub": "2785fcff-62aa-41c9-9792-b2e939c0e17f", "email": "torrymsimango@gmail.com", "app_metadata": {"role": "platform_admin"}}', true);

SELECT count(*) as visible_members FROM public.members;
SELECT count(*) as visible_groups FROM public.groups;
