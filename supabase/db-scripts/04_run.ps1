<#
04_run.ps1
PowerShell runner to recreate the database from scratch and seed it.

Scripts (execution order):
  1) 01_create_schema.sql   -- creates / recreates public schema using consolidated schema
  2) numbered migrations    -- runs numbered migration files found in the supabase folder (ordered by filename)
  3) 03_seed.sql            -- applies consolidated safe seed

Usage examples (PowerShell):
  # using psql with explicit connection parameters
  .\04_run.ps1 -Host localhost -Port 5432 -User postgres -Password mypass -Database sanibonani

  # using environment variables (PGPASSWORD will be set for this session)
  $env:PGPASSWORD='mypass'; .\04_run.ps1 -Host localhost -Port 5432 -User postgres -Database sanibonani

Notes:
- This script requires `psql` available on PATH. If you use the Supabase CLI or a GUI, you can run the SQL files directly.
- The script will stop at the first error.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Host,
    [Parameter(Mandatory=$false)][int]$Port = 5432,
    [Parameter(Mandatory=$true)][string]$User,
    [Parameter(Mandatory=$true)][string]$Password,
    [Parameter(Mandatory=$true)][string]$Database,
    [switch]$DryRun
)

function Run-PSQLFile([string]$FilePath){
    Write-Host "-> Executing: $FilePath"
    if($DryRun){ return }
    $env:PGPASSWORD = $Password
    $cmd = "psql -h $Host -p $Port -U $User -d $Database -f `"$FilePath`""
    $proc = Start-Process -FilePath psql -ArgumentList "-h", $Host, "-p", $Port.ToString(), "-U", $User, "-d", $Database, "-f", $FilePath -NoNewWindow -Wait -PassThru -ErrorAction Stop
    if($proc.ExitCode -ne 0){ throw "psql returned exit code $($proc.ExitCode) while executing $FilePath" }
}

Write-Host "Starting DB rebuild + seed (dryrun=$DryRun)"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootSupabase = Join-Path $scriptDir ".."

# 1) Create schema using consolidated schema file
$schemaScript = Join-Path $scriptDir "01_create_schema.sql"
if(Test-Path $schemaScript){
    Run-PSQLFile $schemaScript
} else { throw "Missing $schemaScript" }

# 2) Run all migration files in supabase folder in lexical order.
#    We intentionally skip files that look like consolidated dumps or seeds.
$blacklistPatterns = @(
    'CONSOLIDATED',
    'CONSOLIDATED_SCHEMA',
    'CONSOLIDATED_FOR_DASHBOARD',
    'CONSOLIDATED_FULL',
    'CONSOLIDATED_APPLY',
    'CONSOLIDATED_FOR_DASHBOARD_SCHEMA_PLUS_SAFE_SEED',
    'CONSOLIDATED_FOR_DASHBOARD.sql',
    '01_DATABASE_SCHEMA.sql',
    '01_create_schema.sql'
)

Get-ChildItem -Path $rootSupabase -Filter "??_*.sql" | Sort-Object Name | ForEach-Object {
    $name = $_.Name
    $path = $_.FullName
    # skip the scripts in db-scripts itself
    if($path -like "*/db-scripts/*") { return }
    # skip consolidated/seed files by pattern
    foreach($p in $blacklistPatterns){ if($name -like "*$p*"){ return } }
    # skip explicit seed files (we'll run the consolidated seed at the end)
    if($name -match '(_SEED_|SEED_)'){
        Write-Host "Skipping seed file (will be applied later): $name"; return
    }
    Write-Host "Applying migration: $name"
    Run-PSQLFile $path
}

# 3) Seed
$seedScript = Join-Path $scriptDir "03_seed.sql"
if(Test-Path $seedScript){
    Run-PSQLFile $seedScript
} else { throw "Missing $seedScript" }

Write-Host "Database rebuild + seed complete."


