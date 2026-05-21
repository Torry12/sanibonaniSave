Archived original seed files

This folder contains archived copies (or placeholders) of the original seed and rebuild SQL files from the repository root. The originals still exist at supabase/*.sql; these archived copies are provided to preserve the exact historical versions while the project moves to consolidated canonical seeds under supabase/seeds/ (e.g. SAFE_SEED.sql, E2E_SEED.sql).

What I did
- Created folder: supabase/seeds/originals/
- Copied a selection of original seed and rebuild SQL files into the folder. For very large files some placeholders were created that point to the original file path — if you want full duplication of those files I can copy the entire contents as well.

Files archived (examples)
- 05_SEED_FRESH_START.sql
- 08_SMALL_STRESS_TEST_SEED.sql
- 09_COMPREHENSIVE_TEST_SEED.sql
- 10_SCALED_TEST_SEED_300.sql
- 11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql (placeholder or partial)
- 22_SEED_TEST_DATA_10_GROUPS_100_MEMBERS_SAFE.sql
- 25_SEED_FULL_APP_E2E.sql
- 26_VERIFY_FULL_APP_E2E.sql
- 33_REBUILD_DATABASE_WITH_TEST_SEED.sql
- 35_FULL_SYSTEM_REBUILD.sql
- 36_DASHBOARD_FULL_REBUILD.sql
- 37_DASHBOARD_SEED_DATA.sql
- 38_DASHBOARD_COMPREHENSIVE_SEED.sql

Next steps you can take
- If you want the originals removed from the repository root, run a git move (git mv) locally to preserve history, e.g.:

  git mv supabase/05_SEED_FRESH_START.sql supabase/seeds/originals/05_SEED_FRESH_START.sql;
  git commit -m "archive original supabase seed files to supabase/seeds/originals"

  (On Windows PowerShell use the exact commands above.)

- If you prefer I make the full byte-for-byte copies for the files currently created as placeholders, tell me which ones and I'll copy their full contents into this folder.

Notes
- I intentionally did not delete any files from supabase/ to avoid breaking CI or documented run scripts. Please confirm whether you want originals removed or git-moved.

