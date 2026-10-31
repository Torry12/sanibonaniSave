# -----------------------------------------------------------------------------
# SanibonaniSave - CLOUD SEEDING SCRIPT
# Version: 1.0 (High-Fidelity Production Seeding)
# -----------------------------------------------------------------------------

$ErrorActionPreference = "Stop"

. "$PSScriptRoot/lib/pg-env.ps1"

# 1. SETUP: Configuration
$ProjectRef = "aszmodkqpafwtnfufmow"
$SeedFile = "supabase/SEED_ALL_DATA.sql"

Write-Host ">> STARTING PRODUCTION SEEDING" -ForegroundColor Cyan
Write-Host "Project: $ProjectRef" -ForegroundColor Yellow
Write-Host "Source: $SeedFile" -ForegroundColor Yellow

# 2. VERIFICATION: Ensure we are in the right environment
if (-not (Test-Path "supabase")) {
    throw "Error: Must be run from the repository root."
}

if (-not (Get-Command npx -ErrorAction SilentlyContinue)) {
    throw "Error: Node.js (npx) is required."
}

# 3. SECURITY: Prompt for confirmation
# The seed is destructive: it deletes the LIGHT-* groups and persona.%@master.example.com members
# before recreating them, so it must never be pointed at a project holding real data.
# Skip prompt if automated input is provided
if ($env:SUPABASE_AUTO_CONFIRM -eq "YES") {
    $Confirmation = "YES"
} else {
    $Confirmation = Read-Host "!! WARNING: This DELETES the LIGHT-* groups and persona.* members, then injects MOCK DATA into the ONLINE project. Are you sure? (YES/no)"
}

if ($Confirmation -ne "YES") {
    Write-Host "Seeding aborted by user." -ForegroundColor Red
    exit 0
}

# 4. EXECUTION: Run the remote query
# The seed's platform_admin fixture (platform.admin@master.example.com, role='platform_admin') reads
# its credential from current_setting('sanibonani.seed_platform_admin_password'), because the value
# that used to be committed here was published on GitHub. Unset means the fixture is created with a
# NULL encrypted_password, so a Cloud re-seed without it leaves that admin un-signable while the same
# fixture on Local - which run-supabase-local-reset.ps1 does supply - can sign in. That is a
# tier-state difference, so read the value from local.properties (or the environment) and prefix it.
# Session-level SET, not SET LOCAL: SEED_ALL_DATA.sql opens its own BEGIN/COMMIT, and an inner
# COMMIT would clear a transaction-local setting before the DO blocks run.
$EffectiveSeedFile = $SeedFile
$PrefixedSeedFile = $null
$SeedAdminPw = $env:SEED_PLATFORM_ADMIN_PASSWORD
if ([string]::IsNullOrWhiteSpace($SeedAdminPw)) {
    $LocalProps = Join-Path (Get-Location) "local.properties"
    if (Test-Path $LocalProps) {
        $SeedPwLine = Get-Content $LocalProps -Encoding UTF8 |
            Where-Object { $_ -match '^\s*SEED_PLATFORM_ADMIN_PASSWORD\s*=' } | Select-Object -Last 1
        if ($SeedPwLine) { $SeedAdminPw = ($SeedPwLine -replace '^\s*SEED_PLATFORM_ADMIN_PASSWORD\s*=', '').Trim() }
    }
}

if ([string]::IsNullOrWhiteSpace($SeedAdminPw)) {
    Write-Host ">> SEED_PLATFORM_ADMIN_PASSWORD is absent: the seeded platform_admin fixture will keep its current password, or be created with none." -ForegroundColor Yellow
} else {
    # One line built with -f, never inside @(): PowerShell's array-literal parser splits
    # "SET ... = '" + $pw + "';" into three elements, so the joined file puts the raw password on a
    # line of its own and Postgres hashes "\n<password>\n".
    $QuotedSeedPw = $SeedAdminPw.Replace("'", "''")
    $SeedText = [System.IO.File]::ReadAllText((Resolve-Path $SeedFile).Path, (New-Object System.Text.UTF8Encoding($false)))
    $Prefixed = ("SET sanibonani.seed_platform_admin_password = '{0}';" -f $QuotedSeedPw) + "`n" + $SeedText
    $PrefixedSeedFile = Join-Path $env:TEMP ("seed_online_" + [guid]::NewGuid().ToString("N") + ".sql")
    [System.IO.File]::WriteAllText($PrefixedSeedFile, $Prefixed, (New-Object System.Text.UTF8Encoding($false)))
    $EffectiveSeedFile = $PrefixedSeedFile
    Write-Host ">> Seeding with the platform_admin fixture credential from local.properties." -ForegroundColor Gray
}

Write-Host ">> Initializing remote connection..." -ForegroundColor Gray
try {
    # `supabase db query --file` prepares every statement, so it rejects this script outright
    # ("cannot insert multiple commands into a prepared statement"). psql runs the whole file in
    # one session, which is what the pg_temp helper and the BEGIN/COMMIT wrapper need.
    # `supabase db query --file` sends the whole file as one session, which is what the pg_temp
    # helper and the BEGIN/COMMIT wrapper need - and the linked route carries its own credentials,
    # so a pasted connection string is only required for the psql/docker paths below.
    if (-not $env:DATABASE_URL) {
        Write-Host ">> No DATABASE_URL; seeding through the linked project via the Supabase CLI." -ForegroundColor Yellow
        npx.cmd -y supabase db query --linked --file $EffectiveSeedFile
        if ($LASTEXITCODE -ne 0) {
            throw "Linked seed failed with exit code $LASTEXITCODE."
        }
        Write-Host ">> SEEDING SUCCESSFUL: Cloud project seeded from $SeedFile." -ForegroundColor Green
        Write-Host ">> Process Complete." -ForegroundColor Cyan
        exit 0
    }

    Write-Host ">> Executing remote SQL Seeding..." -ForegroundColor Cyan

    if (Get-Command psql -ErrorAction SilentlyContinue) {
        psql $env:DATABASE_URL -X -v ON_ERROR_STOP=1 -f $EffectiveSeedFile
    } elseif (Get-Command docker -ErrorAction SilentlyContinue) {
        # No psql on this host, but the Supabase database image already ships one. Mount the seed
        # read-only rather than piping it through stdin, which would re-encode the UTF-8 file.
        $Image = "public.ecr.aws/supabase/postgres:17.6.1.111"
        docker image inspect $Image *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "psql not found on PATH and image $Image is not cached locally. Install the PostgreSQL client, or paste $SeedFile into the project's SQL editor."
        }
        $Connection = Split-PostgresUrl -Url $env:DATABASE_URL
        $SeedDir = (Resolve-Path (Split-Path $EffectiveSeedFile -Parent)).Path
        $SavedPgVars = Set-PostgresEnv -Connection $Connection
        try {
            docker run --rm -e PGHOST -e PGPORT -e PGUSER -e PGPASSWORD -e PGDATABASE -e PGSSLMODE `
                -v "${SeedDir}:/seed:ro" $Image `
                psql -X -v ON_ERROR_STOP=1 -f "/seed/$(Split-Path $EffectiveSeedFile -Leaf)"
        } finally {
            Restore-PostgresEnv -Saved $SavedPgVars
        }
    } else {
        throw "Neither psql nor docker is available. Install the PostgreSQL client, or paste $SeedFile into the project's SQL editor."
    }

    if ($LASTEXITCODE -ne 0) {
        throw "Remote query failed with exit code $LASTEXITCODE."
    }

    Write-Host ">> SEEDING SUCCESSFUL: Cloud project seeded from $SeedFile." -ForegroundColor Green
} catch {
    Write-Host "!! SEEDING FAILED: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    if ($PrefixedSeedFile) { Remove-Item $PrefixedSeedFile -Force -ErrorAction SilentlyContinue }
}

Write-Host ">> Process Complete." -ForegroundColor Cyan
