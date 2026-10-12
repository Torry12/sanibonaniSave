-- Test visibility for torrymsimango@gmail.com
SET ROLE authenticated;
SET auth.uid = '5e289789-df5f-4e52-942b-5e3790540098';
SELECT set_config('request.jwt.claims', '{"sub": "5e289789-df5f-4e52-942b-5e3790540098", "email": "torrymsimango@gmail.com", "app_metadata": {"role": "platform_admin"}}', true);

SELECT count(*) as visible_members FROM public.members;
SELECT count(*) as visible_groups FROM public.groups;
