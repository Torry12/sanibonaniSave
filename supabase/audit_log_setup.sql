-- 1. Create the audit_logs table for platform admin activity logging
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id uuid NOT NULL,         -- The admin's user id
    target_member_id uuid,          -- The member being assisted (nullable)
    target_group_id uuid,           -- The group being assisted (nullable)
    action text NOT NULL,           -- e.g. "VIEW_MEMBER_DASHBOARD", "UPDATE_MEMBER_STATUS"
    details jsonb,                  -- Optional: extra info
    created_at timestamptz NOT NULL DEFAULT now()
);

-- 2. Enable RLS and allow authenticated users to insert logs
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Allow Insert Audit Logs" ON public.audit_logs;
CREATE POLICY "Allow Insert Audit Logs" ON public.audit_logs
FOR INSERT TO authenticated
WITH CHECK (true);

