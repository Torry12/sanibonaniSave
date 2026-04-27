-- Migration: Add login audit and 2FA columns to auth.users
-- Run in Supabase SQL Editor or migration tool

ALTER TABLE auth.users
ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS login_attempts INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS two_factor_secret TEXT;

-- Index for faster lookup on role
CREATE INDEX IF NOT EXISTS idx_auth_users_role ON auth.users(role);

-- Optionally, update RLS policies to restrict platform_admin access
-- (Assumes RBAC functions already exist)

