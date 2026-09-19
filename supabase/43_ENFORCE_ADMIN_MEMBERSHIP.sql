-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — ENFORCE ADMIN MEMBERSHIP
-- Version: 1.0 (Automatic Registration Trigger)
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. TRIGGER FUNCTION
/**
 * Automatically registers the group creator as the first member.
 * This ensures groups with 0 members cannot exist.
 */
CREATE OR REPLACE FUNCTION public.fn_ensure_admin_is_member()
RETURNS TRIGGER AS $$
DECLARE
    v_profile RECORD;
BEGIN
    -- 1. Fetch admin's profile data
    SELECT * INTO v_profile FROM public.profiles WHERE id = NEW.admin_user_id;

    IF v_profile.id IS NULL THEN
        -- Fallback if profile doesn't exist yet (unlikely due to Auth triggers)
        RAISE WARNING 'Group admin profile % not found. Member registration skipped.', NEW.admin_user_id;
        RETURN NEW;
    END IF;

    -- 2. Register as member
    -- Use ON CONFLICT to avoid errors if the app already handled this.
    INSERT INTO public.members (
        id,
        group_id,
        user_id,
        full_name,
        email,
        phone,
        status,
        joined_at,
        probation_end_at
    )
    VALUES (
        gen_random_uuid(),
        NEW.id,
        NEW.admin_user_id,
        v_profile.full_name,
        v_profile.email,
        v_profile.phone,
        'active', -- Admins are usually active immediately
        NOW(),
        NOW() + (NEW.probation_months || ' months')::INTERVAL
    )
    ON CONFLICT (group_id, user_id) DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 2. ATTACH TRIGGER
DROP TRIGGER IF EXISTS tr_ensure_admin_is_member ON public.groups;
CREATE TRIGGER tr_ensure_admin_is_member
AFTER INSERT ON public.groups
FOR EACH ROW EXECUTE FUNCTION public.fn_ensure_admin_is_member();

-- 3. REPAIR SCRIPT (One-time)
-- Fix any existing groups that currently have 0 members.
INSERT INTO public.members (
    id,
    group_id,
    user_id,
    full_name,
    email,
    phone,
    status,
    joined_at,
    probation_end_at
)
SELECT
    gen_random_uuid(),
    g.id,
    g.admin_user_id,
    p.full_name,
    p.email,
    p.phone,
    'active',
    g.created_at,
    g.created_at + (g.probation_months || ' months')::INTERVAL
FROM public.groups g
JOIN public.profiles p ON g.admin_user_id = p.id
LEFT JOIN public.members m ON g.id = m.group_id AND g.admin_user_id = m.user_id
WHERE m.id IS NULL;
