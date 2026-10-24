SELECT set_config('request.jwt.claims', '{"sub": "5e289789-df5f-4e52-942b-5e3790540098", "email": "torrymsimango@gmail.com", "app_metadata": {"role": "platform_admin"}}', true);

SELECT auth.jwt() as jwt;
SELECT auth.jwt() ->> 'email' as email;
SELECT auth.jwt() -> 'app_metadata' as app_metadata;
SELECT auth.jwt() -> 'app_metadata' ->> 'role' as role;
