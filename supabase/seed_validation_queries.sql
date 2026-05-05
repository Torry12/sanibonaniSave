-- SanibonaniSave — Seed Validation + Portal Isolation Probes
-- Run this file as one command after seeding.
-- It validates:
-- 1) Seed integrity and admin/member invariants
-- 2) Admin-vs-member access boundaries used by the group admin portal

-- 0) High-level snapshot
SELECT 'auth.users' AS entity, COUNT(*)::int AS count FROM auth.users
UNION ALL SELECT 'profiles', COUNT(*)::int FROM public.profiles
UNION ALL SELECT 'groups', COUNT(*)::int FROM public.groups
UNION ALL SELECT 'members', COUNT(*)::int FROM public.members
UNION ALL SELECT 'policies', COUNT(*)::int FROM public.policies
UNION ALL SELECT 'beneficiaries', COUNT(*)::int FROM public.beneficiaries
UNION ALL SELECT 'contributions', COUNT(*)::int FROM public.contributions
UNION ALL SELECT 'payments', COUNT(*)::int FROM public.payments
UNION ALL SELECT 'member_documents', COUNT(*)::int FROM public.member_documents
UNION ALL SELECT 'notifications', COUNT(*)::int FROM public.notifications
UNION ALL SELECT 'payouts', COUNT(*)::int FROM public.payouts
UNION ALL SELECT 'platform_fees', COUNT(*)::int FROM public.platform_fees;

-- 1) One-command validation harness
DO $$
DECLARE
	v_platform_uid UUID;
	v_admin_uid UUID;
	v_member_uid UUID;
	v_admin_group UUID;
	v_foreign_group UUID;
	v_member_group UUID;

	v_count INT;
	v_bool BOOLEAN;

	v_total INT := 0;
	v_failed INT := 0;

	v_actor TEXT;
	v_check TEXT;
	v_expected TEXT;
	v_actual TEXT;
	v_pass BOOLEAN;
BEGIN
	CREATE TEMP TABLE IF NOT EXISTS tmp_seed_validation_results (
		actor TEXT,
		check_name TEXT,
		expected TEXT,
		actual TEXT,
		passed BOOLEAN,
		details TEXT
	);

	TRUNCATE TABLE tmp_seed_validation_results;

	-- Resolve personas in a way that works across both seeders and email variants.
	SELECT u.id INTO v_platform_uid
	FROM auth.users u
	WHERE lower(u.email) IN ('torrymsimango@gmail.com', 'torryymsimango@gmail.com', 'torrymsimango@hotmail.com')
	ORDER BY CASE lower(u.email)
		WHEN 'torrymsimango@gmail.com' THEN 1
		WHEN 'torryymsimango@gmail.com' THEN 2
		WHEN 'torrymsimango@hotmail.com' THEN 3
		ELSE 4
	END,
	u.created_at NULLS LAST,
	u.id
	LIMIT 1;

	IF v_platform_uid IS NULL THEN
		SELECT p.id INTO v_platform_uid
		FROM public.profiles p
		WHERE p.role = 'platform_admin'
		ORDER BY CASE lower(COALESCE(p.email, ''))
			WHEN 'torrymsimango@gmail.com' THEN 1
			WHEN 'torryymsimango@gmail.com' THEN 2
			WHEN 'torrymsimango@hotmail.com' THEN 3
			ELSE 4
		END,
		p.updated_at NULLS LAST,
		p.id
		LIMIT 1;
	END IF;

	SELECT u.id INTO v_admin_uid
	FROM auth.users u
	WHERE lower(u.email) = 'admin1@test.com'
	ORDER BY u.created_at NULLS LAST, u.id
	LIMIT 1;

	IF v_admin_uid IS NULL THEN
		SELECT p.id INTO v_admin_uid
		FROM public.profiles p
		WHERE p.role = 'group_admin'
		  AND EXISTS (
			  SELECT 1
			  FROM public.groups g
			  WHERE g.admin_user_id = p.id
		  )
		ORDER BY CASE lower(COALESCE(p.email, ''))
			WHEN 'admin1@test.com' THEN 1
			ELSE 2
		END,
		p.updated_at NULLS LAST,
		p.id
		LIMIT 1;
	END IF;

	SELECT u.id INTO v_member_uid
	FROM auth.users u
	WHERE lower(u.email) = 'member1@test.com'
	ORDER BY u.created_at NULLS LAST, u.id
	LIMIT 1;

	IF v_member_uid IS NULL THEN
		SELECT p.id INTO v_member_uid
		FROM public.profiles p
		WHERE p.role = 'member'
		  AND p.id IS DISTINCT FROM v_platform_uid
		  AND p.id IS DISTINCT FROM v_admin_uid
		  AND EXISTS (
			  SELECT 1
			  FROM public.members m
			  JOIN public.groups g ON g.id = m.group_id
			  WHERE m.user_id = p.id
			    AND g.admin_user_id <> p.id
		  )
		ORDER BY CASE lower(COALESCE(p.email, ''))
			WHEN 'member1@test.com' THEN 1
			ELSE 2
		END,
		p.updated_at NULLS LAST,
		p.id
		LIMIT 1;
	END IF;

	SELECT g.id INTO v_admin_group
	FROM public.groups g
	WHERE g.admin_user_id = v_admin_uid
	ORDER BY g.created_at NULLS LAST, g.id
	LIMIT 1;

	SELECT g.id INTO v_foreign_group
	FROM public.groups g
	WHERE g.admin_user_id IS DISTINCT FROM v_admin_uid
	ORDER BY g.created_at NULLS LAST, g.id
	LIMIT 1;

	SELECT m.group_id INTO v_member_group
	FROM public.members m
	JOIN public.groups g ON g.id = m.group_id
	WHERE m.user_id = v_member_uid
	  AND g.admin_user_id <> v_member_uid
	ORDER BY m.created_at NULLS LAST, m.group_id
	LIMIT 1;

	IF v_platform_uid IS NULL OR v_admin_uid IS NULL OR v_member_uid IS NULL THEN
		RAISE EXCEPTION 'Missing required seeded personas (platform/admin/member). platform_uid=%, admin_uid=%, member_uid=%',
			COALESCE(v_platform_uid::text, 'null'),
			COALESCE(v_admin_uid::text, 'null'),
			COALESCE(v_member_uid::text, 'null');
	END IF;
	IF v_admin_group IS NULL OR v_foreign_group IS NULL OR v_member_group IS NULL THEN
		RAISE EXCEPTION 'Missing required seed topology (admin group / foreign group / member group).';
	END IF;

	-- Helper local routine pattern via inline repeated block.
	-- SEED INTEGRITY / INVARIANTS
	v_actor := 'seed';

	SELECT COUNT(*) INTO v_count
	FROM public.groups g
	LEFT JOIN public.members m
	  ON m.group_id = g.id
	 AND m.user_id = g.admin_user_id
	WHERE m.id IS NULL;
	v_check := 'all_groups_have_admin_as_member';
	v_expected := '0';
	v_actual := v_count::text;
	v_pass := (v_count = 0);
	INSERT INTO tmp_seed_validation_results VALUES (v_actor, v_check, v_expected, v_actual, v_pass, 'groups missing admin-as-member rows');

	SELECT COUNT(*) INTO v_count
	FROM public.profiles p
	WHERE p.role = 'group_admin'
	  AND NOT EXISTS (SELECT 1 FROM public.groups g WHERE g.admin_user_id = p.id);
	v_check := 'no_orphan_group_admin_profiles';
	v_expected := '0';
	v_actual := v_count::text;
	v_pass := (v_count = 0);
	INSERT INTO tmp_seed_validation_results VALUES (v_actor, v_check, v_expected, v_actual, v_pass, 'group_admin role must imply actual group ownership');

	SELECT COUNT(*) INTO v_count
	FROM public.groups g
	WHERE NOT EXISTS (
		SELECT 1 FROM public.members m
		WHERE m.group_id = g.id
		  AND (m.user_id IS NULL OR m.user_id <> g.admin_user_id)
	);
	v_check := 'each_group_has_non_admin_member';
	v_expected := '0';
	v_actual := v_count::text;
	v_pass := (v_count = 0);
	INSERT INTO tmp_seed_validation_results VALUES (v_actor, v_check, v_expected, v_actual, v_pass, 'ensures non-admin portal isolation test coverage');

	-- PLATFORM ADMIN PROBES
	v_actor := 'platform_admin';
	PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
	PERFORM set_config('request.jwt.claim.sub', v_platform_uid::text, true);

	SELECT public.is_platform_admin() INTO v_bool;
	INSERT INTO tmp_seed_validation_results
	VALUES (v_actor, 'is_platform_admin_gate', 'true', COALESCE(v_bool::text, 'null'), v_bool IS TRUE, 'platform owner gate must pass');

	SELECT COUNT(*) INTO v_count
	FROM public.groups
	WHERE is_public = true
	   OR admin_user_id = auth.uid()
	   OR public.is_group_member(id)
	   OR public.is_platform_admin();
	INSERT INTO tmp_seed_validation_results
	VALUES (v_actor, 'can_view_groups', '>0', v_count::text, v_count > 0, 'platform admin should view all groups');

	SELECT COUNT(*) INTO v_count
	FROM public.platform_fees
	WHERE public.is_group_admin(group_id)
	   OR public.is_platform_admin();
	INSERT INTO tmp_seed_validation_results
	VALUES (v_actor, 'can_view_platform_fees', '>0', v_count::text, v_count > 0, 'platform admin should view platform fees');

	-- GROUP ADMIN PROBES
	v_actor := 'group_admin';
	PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
	PERFORM set_config('request.jwt.claim.sub', v_admin_uid::text, true);

	SELECT public.is_group_admin(v_admin_group) INTO v_bool;
	INSERT INTO tmp_seed_validation_results
	VALUES (v_actor, 'admin_gate_on_own_group', 'true', COALESCE(v_bool::text, 'null'), v_bool IS TRUE, 'admin1 must be admin for own group');

	SELECT public.is_group_admin(v_foreign_group) INTO v_bool;
	INSERT INTO tmp_seed_validation_results
	VALUES (v_actor, 'admin_gate_on_foreign_group', 'false', COALESCE(v_bool::text, 'null'), v_bool IS FALSE, 'admin1 must not be admin for foreign group');

	SELECT COUNT(*) INTO v_count
	FROM public.members
	WHERE group_id = v_admin_group
	  AND (
		  user_id = auth.uid()
		  OR public.is_group_admin(group_id)
		  OR public.is_platform_admin()
	  );
	INSERT INTO tmp_seed_validation_results
	VALUES (v_actor, 'can_view_members_in_own_group', '>0', v_count::text, v_count > 0, 'group admin portal data should load for own group');

	SELECT COUNT(*) INTO v_count
	FROM public.members
	WHERE group_id = v_foreign_group
	  AND (
		  user_id = auth.uid()
		  OR public.is_group_admin(group_id)
		  OR public.is_platform_admin()
	  );
	INSERT INTO tmp_seed_validation_results
	VALUES (v_actor, 'cannot_view_members_in_foreign_group', '0', v_count::text, v_count = 0, 'group admin must be isolated from foreign group members');

	SELECT COUNT(*) INTO v_count
	FROM public.platform_fees
	WHERE group_id = v_admin_group
	  AND (
		  public.is_group_admin(group_id)
		  OR public.is_platform_admin()
	  );
	INSERT INTO tmp_seed_validation_results
	VALUES (v_actor, 'can_view_own_group_platform_fees', '>0', v_count::text, v_count > 0, 'group admin can inspect own group compliance');

	SELECT COUNT(*) INTO v_count
	FROM public.platform_fees
	WHERE group_id = v_foreign_group
	  AND (
		  public.is_group_admin(group_id)
		  OR public.is_platform_admin()
	  );
	INSERT INTO tmp_seed_validation_results
	VALUES (v_actor, 'cannot_view_foreign_group_platform_fees', '0', v_count::text, v_count = 0, 'group admin cannot inspect foreign group compliance');

	-- MEMBER PROBES (PORTAL ISOLATION)
	v_actor := 'member';
	PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
	PERFORM set_config('request.jwt.claim.sub', v_member_uid::text, true);

	SELECT public.is_group_admin(v_member_group) INTO v_bool;
	INSERT INTO tmp_seed_validation_results
	VALUES (v_actor, 'member_not_admin_gate', 'false', COALESCE(v_bool::text, 'null'), v_bool IS FALSE, 'regular member must not pass group-admin gate');

	SELECT COUNT(*) INTO v_count
	FROM public.members
	WHERE group_id = v_member_group
	  AND (
		  user_id = auth.uid()
		  OR public.is_group_admin(group_id)
		  OR public.is_platform_admin()
	  );
	INSERT INTO tmp_seed_validation_results
	VALUES (v_actor, 'member_cannot_view_full_member_list', '1', v_count::text, v_count = 1, 'member should only see own membership row');

	SELECT COUNT(*) INTO v_count
	FROM public.platform_fees
	WHERE group_id = v_member_group
	  AND (
		  public.is_group_admin(group_id)
		  OR public.is_platform_admin()
	  );
	INSERT INTO tmp_seed_validation_results
	VALUES (v_actor, 'member_cannot_view_platform_fees', '0', v_count::text, v_count = 0, 'non-admin member must not access group admin fee panel');

	SELECT COUNT(*) INTO v_count
	FROM public.payouts
	WHERE group_id = v_member_group
	  AND (
		  public.is_group_admin(group_id)
		  OR public.is_platform_admin()
	  );
	INSERT INTO tmp_seed_validation_results
	VALUES (v_actor, 'member_cannot_view_payouts', '0', v_count::text, v_count = 0, 'non-admin member must not access group admin payouts');

	SELECT COUNT(*) INTO v_count
	FROM public.group_actuarial_metrics
	WHERE group_id = v_member_group
	  AND (
		  public.is_group_admin(group_id)
		  OR public.is_platform_admin()
	  );
	INSERT INTO tmp_seed_validation_results
	VALUES (v_actor, 'member_cannot_view_group_metrics', '0', v_count::text, v_count = 0, 'non-admin member must not access admin actuarial dashboard');

	-- Clear simulated context.
	PERFORM set_config('request.jwt.claim.sub', '', true);
	PERFORM set_config('request.jwt.claim.role', '', true);

	SELECT COUNT(*), COUNT(*) FILTER (WHERE NOT passed)
	INTO v_total, v_failed
	FROM tmp_seed_validation_results;

	RAISE NOTICE 'Seed validation complete: total_checks=%, failed_checks=%', v_total, v_failed;
END $$;

-- 2) Detailed results (all checks)
SELECT actor, check_name, expected, actual, passed, details
FROM tmp_seed_validation_results
ORDER BY passed ASC, actor, check_name;

-- 3) Compact verdict
SELECT
	COUNT(*) AS total_checks,
	COUNT(*) FILTER (WHERE passed) AS passed_checks,
	COUNT(*) FILTER (WHERE NOT passed) AS failed_checks,
	CASE WHEN COUNT(*) FILTER (WHERE NOT passed) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM tmp_seed_validation_results;

