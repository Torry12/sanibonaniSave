ALTER TABLE public.members ADD COLUMN IF NOT EXISTS contract_url TEXT;
ALTER TABLE public.members ADD COLUMN IF NOT EXISTS contract_status TEXT DEFAULT 'pending' CHECK (contract_status IN ('pending', 'verified', 'rejected'));
