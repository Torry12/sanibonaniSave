param(
    [string]$SeedFile,
    [switch]$NoSeed,
    [switch]$SkipStart,
    [switch]$CheckOnly,
    [ValidateSet("Light", "Extensive", "None")]
    [string]$SeedingMode = "Light"
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
    Invoke-Step "supabase db reset --local --no-seed --yes"
}

# Find DB container name
$dbContainer = docker ps --format "{{.Names}}" | Select-String "supabase_db" | Select-Object -First 1
if (-not $dbContainer) {
    throw "Supabase DB container not found. Is Supabase running?"
}
Write-Host "Using DB Container: $dbContainer" -ForegroundColor Yellow

# ── 1. BOOTSTRAP IDENTITY ──────────────────────────────────────────
# This ensures Platform Admin is established before RLS hardening is applied.
# The masters read the credential from current_setting('sanibonani.platform_admin_password'), because
# the value that used to be committed here was published on GitHub. So running 04 without that setting
# leaves auth.users.encrypted_password NULL and the admin cannot sign in - it is not an error, only a
# NOTICE. Supply the value from local.properties when it is present.
Write-Host "Bootstrapping Platform Admin..." -ForegroundColor Yellow
$adminScript = "supabase/04_PLATFORM_ADMIN_SETUP.sql"
if (Test-Path $adminScript) {
    $adminPw = $null
    $localProps = Join-Path $repoRoot "local.properties"
    if (Test-Path $localProps) {
        $pwLine = Get-Content $localProps -Encoding UTF8 |
            Where-Object { $_ -match '^\s*PLATFORM_ADMIN_PASSWORD\s*=' } | Select-Object -Last 1
        if ($pwLine) { $adminPw = ($pwLine -replace '^\s*PLATFORM_ADMIN_PASSWORD\s*=', '').Trim() }
    }

    if ([string]::IsNullOrWhiteSpace($adminPw)) {
        Write-Host "PLATFORM_ADMIN_PASSWORD is absent from local.properties: the admin row keeps its current password, or gets none. Set it later with 04_PLATFORM_ADMIN_SETUP.sql inside BEGIN; SET LOCAL sanibonani.platform_admin_password = '<password>'; ... COMMIT;" -ForegroundColor Yellow
        Get-Content $adminScript -Raw | docker exec -i $dbContainer psql -U postgres -d postgres -q
    } else {
        # Each SQL line is built on its own with -f. Do NOT reassemble them inside @(): PowerShell's
        # array-literal parser splits "SET ... = '" + $pw + "';" into three separate elements, so the
        # joined payload puts the raw password on a line of its own, Postgres accepts the multi-line
        # literal, and the admin ends up with a bcrypt hash of "\n<password>\n" - a credential that
        # verifies for nobody while the script still reports success. -f also keeps a password
        # containing $ or backtick out of the parser's way, which interpolation would not.
        # The payload goes to a file rather than the pipeline because PowerShell 5.1 re-encodes
        # pipeline stdin in the console codepage, which would silently change a non-ASCII credential.
        $quotedPw = $adminPw.Replace("'", "''")
        $setPwLine = "SET LOCAL sanibonani.platform_admin_password = '{0}';" -f $quotedPw
        $adminSql = "BEGIN;`n$setPwLine`n" + (Get-Content $adminScript -Raw) + "`nCOMMIT;"
        $adminSqlFile = Join-Path $env:TEMP ("admin_bootstrap_" + [guid]::NewGuid().ToString("N") + ".sql")
        [System.IO.File]::WriteAllText($adminSqlFile, $adminSql, (New-Object System.Text.UTF8Encoding($false)))
        $containerSql = "/tmp/admin_bootstrap.sql"
        $verifySql = "BEGIN;`n" + ("SET LOCAL sanibonani.verify_pw = '{0}';" -f $quotedPw) + "`n" +
            "select 'ADMIN_PW_VERIFIES=' || coalesce((select extensions.crypt(current_setting('sanibonani.verify_pw', true), encrypted_password) = encrypted_password from auth.users where lower(email) = 'torrymsimango@gmail.com'), false);`nROLLBACK;"
        $verifySqlFile = Join-Path $env:TEMP ("admin_verify_" + [guid]::NewGuid().ToString("N") + ".sql")
        [System.IO.File]::WriteAllText($verifySqlFile, $verifySql, (New-Object System.Text.UTF8Encoding($false)))
        $containerVerify = "/tmp/admin_verify.sql"
        try {
            docker cp $adminSqlFile "${dbContainer}:${containerSql}"
            if ($LASTEXITCODE -ne 0) { throw "Could not copy the admin bootstrap into $dbContainer (exit $LASTEXITCODE)." }
            docker exec -i $dbContainer psql -U postgres -d postgres -q -v ON_ERROR_STOP=1 -f $containerSql
            if ($LASTEXITCODE -ne 0) { throw "Platform admin bootstrap failed (exit $LASTEXITCODE)." }
            docker exec -i $dbContainer rm -f $containerSql

            # Proof from a session that never wrote anything: the stored hash must authenticate the
            # value local.properties holds. Checking inside the applying transaction would be
            # circular - it compares the same string it just hashed.
            docker cp $verifySqlFile "${dbContainer}:${containerVerify}"
            if ($LASTEXITCODE -ne 0) { throw "Could not copy the admin verification into $dbContainer (exit $LASTEXITCODE)." }
            # Out-String first: psql returns one line per result, and on an array -match/-notmatch are
            # *filters*, not booleans, so "$out -notmatch 'x'" is truthy even when a line says x - a
            # passing check would throw. The captured text is deliberately never echoed into a message,
            # because a Postgres error quotes the failing line and that line holds the credential.
            $verifyOut = (docker exec -i $dbContainer psql -U postgres -d postgres -X -A -v ON_ERROR_STOP=1 -f $containerVerify | Out-String)
            $verifyExit = $LASTEXITCODE
            docker exec -i $dbContainer rm -f $containerVerify
            if ($verifyExit -ne 0 -or $verifyOut -notmatch 'ADMIN_PW_VERIFIES=true') {
                throw "Platform admin credential was written but does not verify against auth.users. Check how PLATFORM_ADMIN_PASSWORD is stored in local.properties."
            }
            Write-Host "Platform Admin credential verified against auth.users." -ForegroundColor Green
        } finally {
            Remove-Item $adminSqlFile -Force -ErrorAction SilentlyContinue
            Remove-Item $verifySqlFile -Force -ErrorAction SilentlyContinue
        }
    }
}

# ── 2. APPLY ENHANCEMENTS ──────────────────────────────────────────
Write-Host "Applying core functions and enhancements via psql..." -ForegroundColor Yellow
$coreScripts = @(
    "33_LEDGER_CONSOLIDATION_V1.sql",
    "34_AUDIT_REPORT_FACILITIES_V1.sql"
)

foreach ($script in $coreScripts) {
    $scriptPath = "supabase/$script"
    if (Test-Path $scriptPath) {
        Write-Host "Applying $script..." -ForegroundColor Cyan
        # Continue on notices/warnings but fail on actual errors
        Get-Content $scriptPath -Raw | docker exec -i $dbContainer psql -U postgres -d postgres -q
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Non-fatal error in $script. Check logs if data is missing."
        }
    }
}

# ── 3. SYNC SCHEMA CACHE ─────────────────────────────────────────────
Write-Host "Syncing PostgREST schema cache..." -ForegroundColor Yellow
docker exec -i $dbContainer psql -U postgres -d postgres -c "NOTIFY pgrst, 'reload schema';"

if ($SeedingMode -ne "None") {
    Write-Host "Applying integrated seed data..." -ForegroundColor Yellow
    # SEED_ALL_DATA.sql carries 10_SEED_HELPERS.sql first, so the money modules find the pg_temp
    # helper they post contributions through. MASTER_SEED_DATA.sql alone would not.
    #
    # The seed's platform_admin fixture reads its credential from
    # current_setting('sanibonani.seed_platform_admin_password'); unset means the fixture is created
    # with NULL encrypted_password (a row that already exists keeps its password - the seed only
    # creates, never re-hashes). Supply the value from local.properties when it is present so a
    # rebuild does not leave that admin un-signable.
    $seedAdminPw = $null
    $localProps = Join-Path $repoRoot "local.properties"
    if (Test-Path $localProps) {
        $seedPwLine = Get-Content $localProps -Encoding UTF8 |
            Where-Object { $_ -match '^\s*SEED_PLATFORM_ADMIN_PASSWORD\s*=' } | Select-Object -Last 1
        if ($seedPwLine) { $seedAdminPw = ($seedPwLine -replace '^\s*SEED_PLATFORM_ADMIN_PASSWORD\s*=', '').Trim() }
    }

    $quotedSeedPw = $null
    $seedSql = [System.IO.File]::ReadAllText((Join-Path $repoRoot "supabase/SEED_ALL_DATA.sql"), (New-Object System.Text.UTF8Encoding($false)))
    if ([string]::IsNullOrWhiteSpace($seedAdminPw)) {
        Write-Host "SEED_PLATFORM_ADMIN_PASSWORD is absent from local.properties: the seeded platform_admin fixture keeps its current password, or is created with none." -ForegroundColor Yellow
    } else {
        # Session-level SET, not SET LOCAL: SEED_ALL_DATA.sql opens its own BEGIN/COMMIT (line 5 and
        # the last line), so a transaction-local setting would be cleared by that inner COMMIT and
        # the seed DO blocks would read NULL. The connection closes when psql exits.
        # Line built with -f, never inside @() - see the admin bootstrap above for the split-array
        # hazard that hashes "\n<password>\n".
        $quotedSeedPw = $seedAdminPw.Replace("'", "''")
        $seedSql = ("SET sanibonani.seed_platform_admin_password = '{0}';" -f $quotedSeedPw) + "`n" + $seedSql
    }

    # File + docker cp instead of piping: PowerShell 5.1 re-encodes pipeline stdin in the console
    # codepage, and this seed is UTF-8 with non-ASCII comment text.
    $seedFile = Join-Path $env:TEMP ("seed_all_" + [guid]::NewGuid().ToString("N") + ".sql")
    [System.IO.File]::WriteAllText($seedFile, $seedSql, (New-Object System.Text.UTF8Encoding($false)))
    $containerSeed = "/tmp/seed_all.sql"
    try {
        docker cp $seedFile "${dbContainer}:${containerSeed}"
        if ($LASTEXITCODE -ne 0) { throw "Could not copy the seed into $dbContainer (exit $LASTEXITCODE)." }
        # ON_ERROR_STOP: the file runs in one transaction and ends at 90_SEED_INTEGRITY_GATE.sql, so
        # an error means the COMMIT at the bottom rolls the whole seed back anyway. Stop at the first
        # one and report it instead of printing a green "applied successfully" over a no-op.
        docker exec -i $dbContainer psql -U postgres -d postgres -q -v ON_ERROR_STOP=1 -f $containerSeed
        if ($LASTEXITCODE -ne 0) { throw "Seeding failed (exit $LASTEXITCODE)." }
        docker exec -i $dbContainer rm -f $containerSeed

        if ($quotedSeedPw) {
            # Proof from a session that wrote nothing: the stored hash must authenticate the value
            # local.properties holds. Checking in the seeding session would be circular.
            $seedVerify = "BEGIN;`n" + ("SET LOCAL sanibonani.verify_pw = '{0}';" -f $quotedSeedPw) + "`n" +
                "select 'SEED_ADMIN_PW_VERIFIES=' || coalesce((select extensions.crypt(current_setting('sanibonani.verify_pw', true), encrypted_password) = encrypted_password from auth.users where lower(email) = 'platform.admin@master.example.com'), false);`nROLLBACK;"
            $seedVerifyFile = Join-Path $env:TEMP ("seed_verify_" + [guid]::NewGuid().ToString("N") + ".sql")
            [System.IO.File]::WriteAllText($seedVerifyFile, $seedVerify, (New-Object System.Text.UTF8Encoding($false)))
            $containerSeedVerify = "/tmp/seed_verify.sql"
            try {
                docker cp $seedVerifyFile "${dbContainer}:${containerSeedVerify}"
                if ($LASTEXITCODE -ne 0) { throw "Could not copy the seed admin verification into $dbContainer (exit $LASTEXITCODE)." }
                # Out-String first: on an array -match/-notmatch are filters, not booleans. The
                # captured text is never echoed - a Postgres error quotes the failing line.
                $seedVerifyOut = (docker exec -i $dbContainer psql -U postgres -d postgres -X -A -v ON_ERROR_STOP=1 -f $containerSeedVerify | Out-String)
                $seedVerifyExit = $LASTEXITCODE
                docker exec -i $dbContainer rm -f $containerSeedVerify
                if ($seedVerifyExit -ne 0 -or $seedVerifyOut -notmatch 'SEED_ADMIN_PW_VERIFIES=true') {
                    throw "The seeded platform_admin credential was written but does not verify against auth.users. Check how SEED_PLATFORM_ADMIN_PASSWORD is stored in local.properties."
                }
                Write-Host "Seeded Platform Admin credential verified against auth.users." -ForegroundColor Green
            } finally {
                Remove-Item $seedVerifyFile -Force -ErrorAction SilentlyContinue
            }
        }
    } finally {
        Remove-Item $seedFile -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Local migrations and seeding applied successfully." -ForegroundColor Green
Invoke-Step "supabase migration list --local"
