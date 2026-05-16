param(
    [string]$SeedFile,
    [switch]$NoSeed,
    [switch]$SkipStart,
    [switch]$CheckOnly
)

$ErrorActionPreference = "Stop"

function Invoke-Step {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command
    )

    Write-Host "> $Command" -ForegroundColor Cyan
    Invoke-Expression $Command
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $Command"
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host "Repo: $repoRoot" -ForegroundColor Green

if (-not (Get-Command supabase -ErrorAction SilentlyContinue)) {
    throw "Supabase CLI not found. Install from https://github.com/supabase/cli"
}

Invoke-Step "supabase --version"

if ($CheckOnly) {
    Write-Host "Check-only mode complete." -ForegroundColor Yellow
    exit 0
}

if (-not $SkipStart) {
    Invoke-Step "supabase start"
}

if ($NoSeed) {
    Invoke-Step "supabase db reset --local --no-seed --yes"
} else {
    # Keep reset deterministic; explicit seed is applied below if provided.
    Invoke-Step "supabase db reset --local --no-seed --yes"
}

if ($SeedFile) {
    $resolvedSeed = Resolve-Path $SeedFile -ErrorAction Stop
    Invoke-Step "supabase db query --local --file `"$resolvedSeed`""
}

Write-Host "Local migrations applied successfully." -ForegroundColor Green
Invoke-Step "supabase migration list --local"

