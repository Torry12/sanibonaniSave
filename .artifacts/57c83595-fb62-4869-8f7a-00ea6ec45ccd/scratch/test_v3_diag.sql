-- Test visibility as torrymsimango@gmail.com
SET auth.uid = '5e289789-df5f-4e52-942b-5e3790540098';
SELECT set_config('request.jwt.claims', '{"sub": "5e289789-df5f-4e52-942b-5e3790540098", "email": "torrymsimango@gmail.com", "app_metadata": {"role": "platform_admin"}}', true);

SELECT * FROM public.check_rls_visibility_v1() WHERE table_name = 'members';
