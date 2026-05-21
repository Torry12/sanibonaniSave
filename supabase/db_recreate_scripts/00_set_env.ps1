<#
00_set_env.ps1

Usage (recommended):
    .\00_set_env.ps1 -Host localhost -Port 5432 -Database postgres -User postgres -Password secret

This script writes a local `db.env.ps1` in this folder that the other scripts will dot-source
to pick up connection values. It does NOT persist secrets outside this folder unless you
explicitly save the file.
#>

param(
    [string]$Host = "localhost",
    [int]$Port = 5432,
    [string]$Database = "postgres",
    [string]$User = "postgres",
    [string]$Password = "",
    [string]$DatabaseUrl = ""
)

$envFile = Join-Path $PSScriptRoot "db.env.ps1"

if ($DatabaseUrl -ne "") {
    Write-Host "Using DATABASE_URL provided; writing env file..."
    $content = "`$env:DATABASE_URL = '$DatabaseUrl'`n"
    $content += "# psql also respects PG* vars if present."
} else {
    if ($Password -eq "") {
        Write-Host "No password provided — you will be prompted to enter it now (input will be hidden)."
        $pw = Read-Host -AsSecureString "Postgres password for $User@$Host`:$Port (secure)"
        $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($pw))
    }

    $content = "`$env:PGHOST = '$Host'`n"
    $content += "`$env:PGPORT = '$Port'`n"
    $content += "`$env:PGUSER = '$User'`n"
    $content += "`$env:PGPASSWORD = '$Password'`n"
    $content += "`$env:PGDATABASE = '$Database'`n"
}

$content += "# Helper: optionally set ADMIN_USER_ID for seeding (set before running 02_seed_data.ps1)\n"
$content += "# `$env:ADMIN_USER_ID = '<admin-uuid>'\n"

Set-Content -Path $envFile -Value $content -Encoding UTF8 -Force
Write-Host "Wrote environment bootstrap to: $envFile"
Write-Host "Dot-source it in PowerShell to set variables for this session: `. $envFile`"
