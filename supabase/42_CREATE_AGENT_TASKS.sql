-- Creates the backing table used by supabase/functions/agent-orchestrator.
-- Keep aligned with migrations/20260529001904_create_agent_tasks.sql.

CREATE TABLE IF NOT EXISTS public.agent_tasks (
    id text PRIMARY KEY DEFAULT gen_random_uuid()::text,
    requester_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    task_type text NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'queued'
        CHECK (status IN ('queued', 'processing', 'completed', 'failed')),
    output text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_agent_tasks_requester_id
    ON public.agent_tasks(requester_id);

CREATE INDEX IF NOT EXISTS idx_agent_tasks_status
    ON public.agent_tasks(status);

DROP TRIGGER IF EXISTS trigger_update_agent_tasks_updated_at ON public.agent_tasks;
CREATE TRIGGER trigger_update_agent_tasks_updated_at
BEFORE UPDATE ON public.agent_tasks
FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

ALTER TABLE public.agent_tasks ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Agent task owners can read their tasks" ON public.agent_tasks;
CREATE POLICY "Agent task owners can read their tasks"
ON public.agent_tasks
FOR SELECT
TO authenticated
USING (requester_id = auth.uid());

DROP POLICY IF EXISTS "Agent task owners can insert their tasks" ON public.agent_tasks;
CREATE POLICY "Agent task owners can insert their tasks"
ON public.agent_tasks
FOR INSERT
TO authenticated
WITH CHECK (requester_id = auth.uid());
