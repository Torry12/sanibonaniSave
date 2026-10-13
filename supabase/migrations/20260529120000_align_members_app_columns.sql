-- Brownfield: align public.members with app PostgrestColumns.MEMBERS_SAFE.
-- Fresh installs from rebuild_kit_v4/01_TABLES_AND_INDEXES.sql already include these columns.
-- After apply: NOTIFY pgrst, 'reload schema';

ALTER TABLE public.members ADD COLUMN IF NOT EXISTS street TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS suburb TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS city TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS province TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS id_number TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS profile_photo_url TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS document_1_url TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS document_1_type TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS document_1_status TEXT DEFAULT 'pending';
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS document_2_url TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS document_2_type TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS document_2_status TEXT DEFAULT 'pending';
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS document_3_url TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS document_3_type TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS document_3_status TEXT DEFAULT 'pending';
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS document_4_url TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS document_4_type TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS document_4_status TEXT DEFAULT 'pending';
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS document_5_url TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS document_5_type TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS document_5_status TEXT DEFAULT 'pending';
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS fcm_token TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS monthly_contribution_override NUMERIC(12,2);
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS beneficiary_over_65_count INTEGER DEFAULT 0;

NOTIFY pgrst, 'reload schema';
