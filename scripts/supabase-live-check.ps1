param(
    [string]$ProjectRef = "",
    [switch]$DeployFunctions
)

$ErrorActionPreference = "Stop"

$supabase = Get-Command supabase -ErrorAction SilentlyContinue
if ($null -eq $supabase) {
    throw "Supabase CLI is not installed or not on PATH. Install it, then run this script again."
}

Write-Host "Supabase CLI:"
supabase --version

if ($ProjectRef.Trim().Length -gt 0) {
    Write-Host "Linking Supabase project $ProjectRef..."
    supabase link --project-ref $ProjectRef
}

Write-Host "Checking migration status..."
supabase migration list

if ($DeployFunctions) {
    Write-Host "Deploying Edge Functions..."
    supabase functions deploy send-whatsapp
    supabase functions deploy yoco-webhook
    supabase functions deploy mobile-admin-actions
    supabase functions deploy agent-orchestrator
}

Write-Host "Backend check complete."

