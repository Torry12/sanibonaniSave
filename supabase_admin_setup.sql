-- 1. List all users in Supabase Auth (from the dashboard, not SQL)
--    This is managed by Supabase Auth, not a SQL table. Use the dashboard under Authentication > Users.

-- 2. List all members and their platform admin status (members table)
SELECT id, email, is_platform_admin, role FROM members;

-- 3. List all profiles and their platform admin status (profiles table)
SELECT id, email, is_platform_admin, role FROM profiles;

-- 4. Set platform admin for a specific user in members table
UPDATE members
SET is_platform_admin = TRUE, role = 'platform_admin'
WHERE email = 'torrymsimango@gmail.com';

-- 5. Set platform admin for a specific user in profiles table
UPDATE profiles
SET is_platform_admin = TRUE, role = 'platform_admin'
WHERE email = 'torrymsimango@gmail.com';

-- 6. (Optional) Add columns if they do not exist
ALTER TABLE members ADD COLUMN IF NOT EXISTS is_platform_admin BOOLEAN DEFAULT FALSE;
ALTER TABLE members ADD COLUMN IF NOT EXISTS role TEXT DEFAULT 'member';
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS is_platform_admin BOOLEAN DEFAULT FALSE;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS role TEXT DEFAULT 'member';

