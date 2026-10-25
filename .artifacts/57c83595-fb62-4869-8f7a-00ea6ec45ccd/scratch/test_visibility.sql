-- Test visibility as torrymsimango@gmail.com
SET ROLE authenticated;
SET auth.uid = '2785fcff-62aa-41c9-9792-b2e939c0e17f';
-- Simulate the JWT claim
SELECT set_config('request.jwt.claims', '{"sub": "2785fcff-62aa-41c9-9792-b2e939c0e17f", "email": "torrymsimango@gmail.com", "app_metadata": {"role": "platform_admin"}}', true);

SELECT * FROM public.check_rls_visibility_v1() WHERE table_name = 'members';
