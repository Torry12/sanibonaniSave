# -----------------------------------------------------------------------------
# SanibonaniSave - READ-ONLY CLOUD CONNECTION CHECKER (Docker psql client)
# Version: 1.0
#
# Connects to the linked Supabase project with the psql client that already ships
# in the cached Supabase database image, so no local PostgreSQL install is needed.
# The connection string is split into libpq's PG* environment variables and the SQL
# is mounted as a file, so no part of the credential ever reaches an argv; PGOPTIONS
# forces default_transaction_read_only, so this script cannot change the database.
# -----------------------------------------------------------------------------
param(
    [string]$DatabaseUrl = $env:DATABASE_URL,
    [string]$SslMode = "",
    [string]$Image = "public.ecr.aws/supabase/postgres:17.6.1.111"
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot/lib/pg-env.ps1"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is required to run the psql client."
}

docker image inspect $Image *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Image $Image is not cached locally. Run 'docker pull $Image' once, then re-run."
}

if (-not $DatabaseUrl) {
    $DatabaseUrl = Read-DatabaseUrl -Prompt "Paste the connection string from Dashboard -> Connect -> Direct connection (input hidden)"
}

if (-not $DatabaseUrl) {
    throw "No connection string supplied."
}

$Checks = @'
select current_database() as db,
       current_setting('server_version') as server_version,
       current_setting('transaction_read_only') as read_only,
       now() as checked_at;
select (select count(*) from public.groups)                as groups,
       (select count(*) from public.members)               as members,
       (select count(*) from public.group_ledger)          as group_ledger_rows,
       (select count(*) from public.platform_ledger)       as platform_ledger_rows,
       (select count(*) from public.beneficiary_payout_claims) as claims;
select count(*) as balance_drift_groups
from public.groups g
where g.balance <> coalesce((select sum(l.amount) from public.group_ledger l where l.group_id = g.id), 0);
select (select count(*) from public.platform_ledger pl
        where pl.group_id is null and pl.category = 'registration_contribution'
          and not exists (select 1 from public.contributions c where c.id::text = pl.transaction_id))
           as orphan_platform_revenue,
       (select count(*) from public.beneficiary_payout_claims c
        where c.status = 'paid'
          and not exists (select 1 from public.group_ledger l
                          where l.transaction_id = c.id::text and l.category = 'claim'))
           as paid_claims_without_debit;
'@

$Connection = Split-PostgresUrl -Url $DatabaseUrl -SslMode $SslMode

Write-Host ">> Connecting to $($Connection.Host):$($Connection.Port)/$($Connection.Database) as $($Connection.User) (containerised psql)..." -ForegroundColor Cyan

$SqlFile = Join-Path ([IO.Path]::GetTempPath()) ("sanibonani_cloud_check_" + [guid]::NewGuid().ToString("N") + ".sql")
Set-Content -Path $SqlFile -Value $Checks -Encoding ASCII

$SavedPgVars = Set-PostgresEnv -Connection $Connection -ReadOnly
try {
    docker run --rm -e PGHOST -e PGPORT -e PGUSER -e PGPASSWORD -e PGDATABASE -e PGSSLMODE -e PGOPTIONS `
        -v "$(Split-Path $SqlFile -Parent):/sql:ro" $Image `
        psql -X -v ON_ERROR_STOP=1 -f "/sql/$(Split-Path $SqlFile -Leaf)"

    if ($LASTEXITCODE -ne 0) {
        throw "Connection check failed with exit code $LASTEXITCODE."
    }
} finally {
    Restore-PostgresEnv -Saved $SavedPgVars
    Remove-Item $SqlFile -ErrorAction SilentlyContinue
}

Write-Host ">> Connection OK; the checks above ran read-only." -ForegroundColor Green
