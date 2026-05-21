-- ─────────────────────────────────────────────────────────────────────────────
-- SanibonaniSave — EXTENDED VOTING LOGIC & AUTOMATION
-- Date: 2026-05-19
-- Purpose:
--   1) Add type and effect tracking to group_polls
--   2) Implement automated effect application on poll closure
-- ─────────────────────────────────────────────────────────────────────────────

BEGIN;

-- 1. EXTEND group_polls TABLE
ALTER TABLE public.group_polls
ADD COLUMN IF NOT EXISTS type TEXT NOT NULL DEFAULT 'general' CHECK (type IN ('general', 'fee_change', 'rosca_order', 'loan_approval', 'member_removal')),
ADD COLUMN IF NOT EXISTS effect_status TEXT NOT NULL DEFAULT 'none' CHECK (effect_status IN ('none', 'pending', 'applied', 'failed')),
ADD COLUMN IF NOT EXISTS effect_data JSONB;

-- 2. AUTOMATION FUNCTION: apply_poll_effect
CREATE OR REPLACE FUNCTION public.apply_poll_effect()
RETURNS TRIGGER AS $$
DECLARE
    v_winner_option_id UUID;
    v_winner_label TEXT;
    v_group_id UUID;
    v_effect_type TEXT;
BEGIN
    -- Only trigger when status changes to 'closed'
    IF NEW.status = 'closed' AND OLD.status != 'closed' AND NEW.effect_status = 'none' THEN

        -- Identify the winning option (simple majority)
        SELECT option_id INTO v_winner_option_id
        FROM public.group_poll_votes
        WHERE poll_id = NEW.id
        GROUP BY option_id
        ORDER BY count(*) DESC
        LIMIT 1;

        IF v_winner_option_id IS NOT NULL THEN
            SELECT label INTO v_winner_label FROM public.group_poll_options WHERE id = v_winner_option_id;

            NEW.effect_status := 'applied';
            v_effect_type := NEW.type;
            v_group_id := NEW.group_id;

            -- APPLY EFFECTS BASED ON TYPE
            IF v_effect_type = 'fee_change' THEN
                -- Expects effect_data to have {"field": "monthly_contribution" | "joining_fee" | "late_fee"}
                -- The winning label should be the new numeric value
                IF (NEW.effect_data->>'field') = 'monthly_contribution' THEN
                    UPDATE public.groups SET monthly_contribution = v_winner_label::NUMERIC WHERE id = v_group_id;
                ELSIF (NEW.effect_data->>'field') = 'joining_fee' THEN
                    UPDATE public.groups SET joining_fee = v_winner_label::NUMERIC WHERE id = v_group_id;
                ELSIF (NEW.effect_data->>'field') = 'late_fee' THEN
                    UPDATE public.groups SET late_fee = v_winner_label::NUMERIC WHERE id = v_group_id;
                END IF;

            ELSIF v_effect_type = 'loan_approval' THEN
                -- Expects effect_data to have {"loan_id": "..."}
                -- Label should be "Approve" or "Decline"
                IF v_winner_label = 'Approve' THEN
                    UPDATE public.loans SET status = 'approved' WHERE id = (NEW.effect_data->>'loan_id')::UUID;
                ELSIF v_winner_label = 'Decline' THEN
                    UPDATE public.loans SET status = 'declined' WHERE id = (NEW.effect_data->>'loan_id')::UUID;
                END IF;

            ELSIF v_effect_type = 'member_removal' THEN
                -- Expects effect_data to have {"member_id": "..."}
                -- Label should be "Yes" or "No"
                IF v_winner_label = 'Yes' THEN
                    UPDATE public.members SET status = 'suspended' WHERE id = (NEW.effect_data->>'member_id')::UUID;
                END IF;

            ELSE
                NEW.effect_status := 'none'; -- No automated effect for general polls
            END IF;
        END IF;
    END IF;

    RETURN NEW;
EXCEPTION WHEN OTHERS THEN
    -- If automation fails, mark as failed but don't block the poll closure
    NEW.effect_status := 'failed';
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 3. TRIGGER FOR AUTOMATION
DROP TRIGGER IF EXISTS trigger_apply_poll_effect ON public.group_polls;
CREATE TRIGGER trigger_apply_poll_effect
BEFORE UPDATE ON public.group_polls
FOR EACH ROW EXECUTE PROCEDURE public.apply_poll_effect();

COMMIT;
