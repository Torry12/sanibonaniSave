SELECT set_config('request.jwt.claims', '{"sub": "5e289789-df5f-4e52-942b-5e3790540098"}', true);
SELECT auth.uid() as uid;
SELECT current_setting('request.jwt.claims', true) as claims;
