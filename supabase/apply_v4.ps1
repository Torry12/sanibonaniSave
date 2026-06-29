<#
apply_v4.ps1 — applies the v4_canonical Supabase schema and functions
Usage:
  $env:PG_CONN = "host=... dbname=... user=... password=..."; .\supabase\apply_v4.ps1
#>

param(
    [string]$Conn = $env:PG_CONN
)

if (-not $Conn) {
    Write-Error "PG_CONN not set. Set env var or pass -Conn"; exit 2
}

$driver = 'supabase/v4_canonical/SUPABASE_COMPLETE_RESET_AND_REBUILD.sql'
.\supabase\apply_in_order.ps1 -Driver $driver -Conn $Conn
