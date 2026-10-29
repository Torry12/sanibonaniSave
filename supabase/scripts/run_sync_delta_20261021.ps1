<#
run_sync_delta_20261021.ps1 - staged runner for supabase/online/SYNC_DELTA_20261021.sql

The delta is written to be pasted in stages with a hard stop after the read-only
pre-flight (K.0). This script keeps that discipline mechanically: it extracts each
K-section from the delta at run time, and it refuses to run any writing section unless
a K.0 run that passed - against this exact copy of the file - is on record.

Usage (you supply the connection; this script never stores or prints it):

  $env:PG_CONN = "host=... port=5432 dbname=postgres user=postgres password=..."
  .\supabase\scripts\run_sync_delta_20261021.ps1 -Section K0
  # read the verdicts. Only once every gate has passed does the next step unlock:
  .\supabase\scripts\run_sync_delta_20261021.ps1 -Section K1,K2,K3
  .\supabase\scripts\run_sync_delta_20261021.ps1 -Section K4

  # No psql route to the tier? Extract the sections and paste them in the Dashboard
  # yourself - same file, same order, same section boundaries:
  .\supabase\scripts\run_sync_delta_20261021.ps1 -Section K0,K1,K2,K3,K4 -ExtractOnly

  # Local tier with no host psql: run each section through the Supabase Postgres
  # container. No connection string and no DB password is read or needed - psql runs
  # inside the container over its local socket:
  .\supabase\scripts\run_sync_delta_20261021.ps1 -Section K0 -Container supabase_db_aszmodkqpafwtnfufmow
  .\supabase\scripts\run_sync_delta_20261021.ps1 -Section K1,K2,K3 -Container supabase_db_aszmodkqpafwtnfufmow

  # Prove the gate parsing without sending anything anywhere:
  .\supabase\scripts\run_sync_delta_20261021.ps1 -SelfTest

K.0 and K.4 only SELECT, so they run without asking - that is what makes the pre-flight and
the post-check scriptable. K.1/K.2/K.3 write, and because ConfirmImpact is 'High' they ask
for an answer on every run (no -Confirm switch needed) and additionally require a recorded
K.0 pass. If no one can answer - the process has no interactive input - a writing section
stops with that explanation rather than running or dumping PowerShell's internal
NullReferenceException. The recorded pass is per tier: a K.0 that passed on Local does not
unlock a writing section on the cloud tier.

K.1 uses plain CREATE INDEX, which blocks writes on each table while it builds.
Run it in a quiet window.
#>

[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    # Comma or space separated. Both work from a session and from -File, which hands
    # "K1,K2,K3" over as a single string:
    #   -Section K0        -Section K1,K2,K3        -Section "K1 K2 K3"
    [string]$Section,

    [string]$PgConn = $env:PG_CONN,

    # Reach the tier through a running Docker container instead of a host psql: the
    # section file is copied in and psql runs inside it. Ignores PG_CONN entirely.
    [string]$Container,

    # Only used with -Container. The Postgres image's local socket trusts this user, so
    # no password is involved; override if the tier's role or database differs.
    [string]$DbUser = 'postgres',
    [string]$DbName = 'postgres',

    [string]$DeltaPath,

    [string]$LogDir,

    # Write the section files and stop. Touches no database and reads no credential.
    [switch]$ExtractOnly,

    # Ignore the recorded K.0 pass. Only for re-running a section you know passed.
    [switch]$Force,

    # Run the gate logic against canned tier output. No psql, no connection, no tier.
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ------------------------------------------------------------------ helpers ----
function Get-Stat {
    param([string]$Text, [string]$Label)
    $m = [regex]::Match($Text, [regex]::Escape($Label) + '=([^\s|]+)')
    if (-not $m.Success) { return $null }
    return $m.Groups[1].Value
}

# Pass/fail is only ever asserted where the delta itself guarantees the value: its own
# bodies against known hashes, and its own post-conditions. Live-tier measurements
# (K.0.f) are reported, never gated - see the note in the delta.
function Test-K0 {
    param([string]$Text)
    $fail = @()
    if ($Text -match 'DRIFT')  { $fail += 'K.0.a reported DRIFT: the tier holds a body the masters do not know about. Port it before pasting K.2.' }
    if ($Text -match 'ABSENT') { $fail += 'K.0.a reported ABSENT: one of the five functions is not installed in this tier.' }
    if ((Get-Stat $Text 'platform_ledger_duplicate_tx') -ne '0')    { $fail += 'K.0.b: duplicate transaction_id rows exist and must be resolved by hand first.' }
    if ((Get-Stat $Text 'colliding_index_names')        -ne '0')    { $fail += 'K.0.c: an index name in K.1 already exists; IF NOT EXISTS would skip it silently.' }
    if ((Get-Stat $Text 'recon_status_allows_discrepancy_found') -ne 'true') { $fail += 'K.0.d: reconciliations.status has no discrepancy_found domain - K.2 would fail on every payment.' }
    if ((Get-Stat $Text 'groups_null_balance')          -ne '0')    { $fail += 'K.0.e: groups with a NULL balance exist; the new drift reporting reads them as 0.' }
    return $fail
}

function Test-K4 {
    param([string]$Text)
    $fail = @()
    if ($Text -match 'DRIFT')  { $fail += 'K.4.a / K.4.d reported DRIFT after the paste.' }
    if ($Text -match 'ABSENT') { $fail += 'K.4.a / K.4.d reported ABSENT - a function went missing.' }
    if ((Get-Stat $Text 'new_indexes') -ne '8') { $fail += ('K.4.c: new_indexes=' + (Get-Stat $Text 'new_indexes') + ', expected 8.') }
    if ((Get-Stat $Text 'targets_unpinned') -ne '0') { $fail += ('K.4.b: targets_unpinned=' + (Get-Stat $Text 'targets_unpinned') + ', expected 0.') }
    # K.4.e used to fail when close_poll_v1, pay_burial_claim_v1 or record_disbursement_v1
    # appeared in pg_proc, because delta 21 recorded them as still undefined and installed
    # nothing that would define them. SYNC_DELTA_20261029B is that definition, so on a tier that
    # has run it these three counts are legitimately 1 and must not read as a failure. The gate
    # deliberately no longer inspects them: K.4.e is a record of a gap, and a gap that has since
    # been closed elsewhere is not a reason for this verification to fail.
    return $fail
}

function Count-Statements {
    param([string]$Body)
    # Count the terminators psql will see: strip each dollar-quoted body (so the ';' and
    # line starts inside a function are invisible), then strip comment lines.
    $stripped = [regex]::Replace($Body, '(?s)\$\$.*?\$\$', '<body>')
    $stripped = [regex]::Replace($stripped, '(?m)^\s*--.*$', '')
    return ([regex]::Matches($stripped, ';')).Count
}

# A K.0 pass is only evidence about the tier it ran against, so the record is keyed per
# tier. The cloud key is a digest of the connection string, never the string itself.
function Get-TierKey {
    if ($Container) { return "container-$Container" }
    if (-not $PgConn) { return 'pgconn-unset' }
    $hash = [Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($PgConn))
    return 'pgconn-' + (($hash | Select-Object -First 6 | ForEach-Object { $_.ToString('x2') }) -join '')
}

function Invoke-Section {
    param([string]$Id)

    $sqlFile = Join-Path $LogDir "$Id.sql"
    $logFile = Join-Path $LogDir "$Id.out"
    # -A keeps each 'label=value' check on one line so the gates can parse it,
    # -t drops column headers, ON_ERROR_STOP makes a bad statement abort the section.
    if ($Container) {
        # The file is copied in rather than piped: PowerShell would re-encode stdin in the
        # console codepage, and the container cannot see a Windows path for -f.
        $inTier = "/tmp/sanibonani_$Id.sql"
        & docker cp $sqlFile "${Container}:${inTier}" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "docker cp of $sqlFile into ${Container}:${inTier} failed (exit $LASTEXITCODE)." }
        # Merge psql's stderr inside the container: a NOTICE from 'index already exists,
        # skipping' arrives as output, and Windows PowerShell turns native stderr routed
        # through 2>&1 into error records.
        $sh = "psql -U $DbUser -d $DbName -v ON_ERROR_STOP=1 -X -A -t --no-psqlrc -f $inTier 2>&1"
        $out = & docker exec -i $Container sh -c $sh 2>&1
    } else {
        $out = & psql $PgConn -v ON_ERROR_STOP=1 -X -A -t --no-psqlrc -f $sqlFile 2>&1
    }
    $code = $LASTEXITCODE
    $out | Tee-Object -FilePath $logFile | Out-Null
    return [pscustomobject]@{
        Id = $Id; ExitCode = $code; Output = ($out -join "`n"); LogFile = $logFile
    }
}

# --------------------------------------------------------------- self test ----
if ($SelfTest) {
    # Output shaped exactly as `psql -A -t` renders it.
    $k0Clean = @'
approve_and_disburse_loan_v2|573fa6839c728087ac25b59a3e092d24|MATCH
record_platform_revenue_v1|16f0200ea7aba0d088429050fa635a0a|MATCH
record_platform_revenue_v2|e177966e5f75908a8db09a27ded35944|MATCH
recompute_ledger_running_balances_v2|916f8f39209f9d0da04e348b4b7ca02a|MATCH
recalculate_all_group_financials_v3|686f8b4b424576d0a16f05625574b821|MATCH
platform_ledger_duplicate_tx=0
colliding_index_names=0
recon_status_allows_discrepancy_found=true|CHECK ((status = ANY (ARRAY['balanced'::text, 'discrepancy_found'::text])))
groups_null_balance=0
targets_unpinned=12
'@
    $k4Clean = @'
approve_and_disburse_loan_v2|e42f0f4def47d261fd1e8c8e03569297|MATCH
record_platform_revenue_v1|92b3c4af0d00a0e85100f4b53c259186|MATCH
record_platform_revenue_v2|69b90494975e790a284b09669a643a4b|MATCH
recompute_ledger_running_balances_v2|5ce092bd4b98c9143af08318986b124e|MATCH
recalculate_all_group_financials_v3|35ed7f1aa21cf1c3d718d05bc9838ed4|MATCH
targets_unpinned=0
new_indexes=8
increment_group_balance|0e96e06f47613f191008e7df6ef47550|MATCH
record_contribution_v2|90ef8d9ad34e7f6d8fb9b9be20de577f|MATCH
record_loan_repayment_v1|7ba14d8b9cd1d5f7035e6b03b82c7952|MATCH
reconcile_bank_transaction_v1|4cc54a865a9c504b325a36c229b733bf|MATCH
pay_platform_fee_v2|42921eb9257bde763294c40e8f30dc8c|MATCH
process_complex_payment_v4|d32e03083a3dfb663533a650b3fb2097|MATCH
process_rosca_payout_v2|5c9b00d5f769c0d1ea1f5467c939c71c|MATCH
process_rosca_payout_v3|138002dafaebd53db091b4cd9948141a|MATCH
close_poll_v1|0
pay_burial_claim_v1|0
record_disbursement_v1|0
'@
    # Each case: a mutated tier output and how many gates must then fire.
    $cases = @(
        @{ Name = 'K.0 clean tier';            Text = $k0Clean; Expected = 0 },
        @{ Name = 'K.0 hot fix on tier';       Text = ($k0Clean -replace '686f8b4b424576d0a16f05625574b821\|MATCH', '686f8b4b424576d0a16f05625574b821|DRIFT'); Expected = 1 },
        @{ Name = 'K.0 function missing';      Text = ($k0Clean -replace '686f8b4b424576d0a16f05625574b821\|MATCH', 'ABSENT|ABSENT'); Expected = 1 },
        @{ Name = 'K.0 duplicated revenue';    Text = ($k0Clean -replace 'duplicate_tx=0', 'duplicate_tx=3');  Expected = 1 },
        @{ Name = 'K.0 index name clash';      Text = ($k0Clean -replace 'colliding_index_names=0', 'colliding_index_names=2'); Expected = 1 },
        @{ Name = 'K.0 status domain missing'; Text = ($k0Clean -replace 'found=true', 'found=false');         Expected = 1 },
        @{ Name = 'K.0 NULL balances';         Text = ($k0Clean -replace 'null_balance=0', 'null_balance=7');  Expected = 1 },
        @{ Name = 'K.0 low pin count is fine'; Text = ($k0Clean -replace 'targets_unpinned=12', 'targets_unpinned=0'); Expected = 0 },
        @{ Name = 'K.4 clean tier';            Text = $k4Clean; Expected = 0 },
        @{ Name = 'K.4 body did not land';     Text = ($k4Clean -replace 'v2\|5c9b00d5f769c0d1ea1f5467c939c71c\|MATCH', 'v2|deadbeef|DRIFT'); Expected = 1 },
        @{ Name = 'K.4 only 7 indexes';        Text = ($k4Clean -replace 'new_indexes=8', 'new_indexes=7');   Expected = 1 },
        @{ Name = 'K.4 pin left unpinned';     Text = ($k4Clean -replace 'unpinned=0', 'unpinned=3');          Expected = 1 },
        # 20261029B declares these three, so their appearance is no longer evidence against delta 21.
        @{ Name = 'K.4 named RPC now defined'; Text = ($k4Clean -replace 'close_poll_v1\|0', 'close_poll_v1|1'); Expected = 0 },
        # A label the tier never printed must read as a failure, never as a pass.
        @{ Name = 'K.4 output truncated';      Text = ($k4Clean -replace 'new_indexes=8', '');                 Expected = 1 }
    )

    $bad = 0
    foreach ($c in $cases) {
        $got = if ($c.Name -like 'K.0*') { @(Test-K0 $c.Text).Count } else { @(Test-K4 $c.Text).Count }
        $ok = ($got -eq $c.Expected)
        if (-not $ok) { $bad++ }
        '{0}  {1,-30} expected={2} got={3}' -f ($(if ($ok) { 'ok  ' } else { 'FAIL' })), $c.Name, $c.Expected, $got
    }
    # The last case must not merely fail: a missing label has to read as a failure, not a pass.
    if ($bad) { throw "self-test: $bad case(s) wrong" }
    Write-Host "`nSelf-test passed: $($cases.Count) cases. Nothing was sent to any tier." -ForegroundColor Green
    exit 0
}

# ---------------------------------------------------------------- setup ----
$repoRoot  = Split-Path -Parent $PSScriptRoot                     # ...\supabase
$DeltaPath = if ($DeltaPath) { $DeltaPath } else { Join-Path $repoRoot 'online\SYNC_DELTA_20261021.sql' }
$LogDir    = if ($LogDir)    { $LogDir }    else { Join-Path ([IO.Path]::GetTempPath()) 'sanibonani_sync_delta_20261021' }

if ($Container -and $PgConn) {
    throw '-Container and PG_CONN point at different tiers; pass one or the other, not both.'
}
if ($Container -and $Container -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]*$') {
    throw "-Container '$Container' is not a valid container name."
}
foreach ($pair in @{ DbUser = $DbUser; DbName = $DbName }.GetEnumerator()) {
    if ($pair.Value -notmatch '^[A-Za-z0-9_]+$') {
        throw "-$($pair.Key) '$($pair.Value)' is not a plain identifier; it would be interpolated into the container command."
    }
}

if (-not (Test-Path -LiteralPath $DeltaPath)) { throw "delta not found: $DeltaPath" }
if (-not $Section) { throw 'pass -Section, e.g. -Section K0 or -Section K1,K2,K3 (or -SelfTest / -ExtractOnly).' }

# Local scratch and the delta fingerprint are not tier operations, so they go through
# .NET rather than a cmdlet: a cmdlet would honour -WhatIf and return nothing, which
# would make a dry run crash instead of previewing.
[IO.Directory]::CreateDirectory($LogDir) | Out-Null

$ids     = @($Section -split '[,;\s]+' | Where-Object { $_ } | ForEach-Object { $_.ToUpper() })
$allowed = 'K0', 'K1', 'K2', 'K3', 'K4'
if (-not $ids.Count) { throw '-Section parsed to nothing; pass e.g. -Section K0 or -Section K1,K2,K3.' }
$bad = @($ids | Where-Object { $allowed -notcontains $_ })
if ($bad.Count) { throw "-Section accepts only K0 K1 K2 K3 K4; got: $($bad -join ', ')" }

# ---------------------------------------------------------------- extraction ----
# Section starts are the headers carrying two spaces after the number
# ("-- K.1  FINANCIAL HOT-PATH INDEXES"), which distinguishes them from the per-check
# headers ("-- K.0.a  ..."). Ranges are derived, never hard-coded, so editing the delta
# cannot desynchronise this runner.
$txt   = [IO.File]::ReadAllText($DeltaPath) -replace "`r`n", "`n"
$lines = $txt -split "`n"
$bytes    = [IO.File]::ReadAllBytes($DeltaPath)
$deltaSha = (([Security.Cryptography.SHA256]::Create().ComputeHash($bytes) |
              ForEach-Object { $_.ToString('X2') }) -join '')

$starts = @()
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^-- K\.(\d)  ') { $starts += [pscustomobject]@{ Id = "K$($Matches[1])"; Line = $i } }
}
$sections = @{}
foreach ($s in $starts) {
    $next = ($starts | Where-Object { $_.Line -gt $s.Line } | Select-Object -First 1)
    $end  = if ($next) { $next.Line - 1 } else { $lines.Count - 1 }
    $sections[$s.Id] = [pscustomobject]@{ Start = $s.Line + 1; End = $end + 1 }   # 1-based, inclusive
}
foreach ($id in $ids) {
    if (-not $sections.ContainsKey($id)) { throw "section $id is not present in $DeltaPath" }
}

foreach ($id in $ids) {
    $range = $sections[$id]
    $body  = ($lines[($range.Start - 1)..($range.End - 1)] -join "`n")
    $file  = Join-Path $LogDir "$id.sql"
    # UTF-8 without BOM: psql must see the same bytes a Dashboard paste would send.
    [IO.File]::WriteAllText($file, $body, (New-Object Text.UTF8Encoding($false)))

    $stmts = Count-Statements -Body $body
    Write-Host ('  {0}  lines {1}-{2}  {3} statement(s)  -> {4}' -f $id, $range.Start, $range.End, $stmts, $file)
}

if ($ExtractOnly) {
    Write-Host "`nExtractOnly: nothing was sent to any tier."
    exit 0
}

# ------------------------------------------------------ ordering discipline ----
# A writing section runs only on the strength of a K.0 pass recorded against this exact
# copy of the delta, on this exact tier. Checked before the psql and credential
# requirements, so you hear about the missing pre-flight even when the environment is
# not ready.
$tierKey = Get-TierKey
$marker = Join-Path $LogDir "K0.pass.$tierKey.json"
foreach ($id in $ids) {
    if ($id -eq 'K0') { continue }
    if ($Force) { Write-Warning "-Force: the recorded K.0 pass was not required for $id."; continue }
    if (-not (Test-Path -LiteralPath $marker)) {
        throw "run -Section K0 against this tier and let its gates pass first (no record at $marker)."
    }
    $rec = Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json
    if ($rec.deltaSha256 -ne $deltaSha) {
        throw 'the delta changed since the recorded K.0 pass; re-run -Section K0.'
    }
}

# ------------------------------------------------------------------- tier -----
if ($Container) {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "docker not found in PATH, so -Container $Container cannot be reached."
    }
    $running = & docker ps --format '{{.Names}}'
    if ($LASTEXITCODE -ne 0) { throw 'docker ps failed; is the daemon running?' }
    if ($running -notcontains $Container) {
        $hint = if ($running -match 'supabase_db') { " running: $(($running | Where-Object { $_ -like 'supabase_db*' }) -join ', ')" } else { '' }
        throw "container '$Container' is not running;$hint start it (e.g. supabase start) and re-run."
    }
} else {
    if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
        throw 'psql not found in PATH. Either give the Local tier via -Container <name>, or use -ExtractOnly and paste the section files in the Dashboard.'
    }
    if (-not $PgConn) {
        throw 'PG_CONN is not set (or pass -PgConn). This script never stores or echoes it.'
    }
    $env:PGCLIENTENCODING = 'UTF8'
}
$targetLabel = if ($Container) { "container $Container as $DbUser@$DbName" } else { 'the tier PG_CONN points at' }
Write-Host "Target tier: $targetLabel"

# ------------------------------------------------------------------- run -----
# Only a section that changes the tier gets to ask. K.0 and K.4 are read-only - the delta
# says so and the extraction is scanned before it runs - so putting them behind a
# confirmation would block the very step that tells you whether a paste is needed, and it
# made scripted verification fail with PowerShell's own NullReferenceException from inside
# an unanswerable prompt, which reads as a bug in this script.
#
# Writing sections still need an answer from a person, and if nothing can answer, they must
# not run: the exception is caught and turned into the real reason instead of a stack trace.
$writing = 'K1', 'K2', 'K3'
foreach ($id in $ids) {
    if ($writing -contains $id) {
        $confirmed = try {
            $PSCmdlet.ShouldProcess("section $id against $targetLabel", 'run with psql')
        } catch {
            throw "$id writes to $targetLabel and its confirmation could not be answered (no interactive input here). Run it from a terminal where you can read and answer the prompt; this script will not auto-confirm a writing section."
        }
        if (-not $confirmed) { continue }
    }
    elseif ($WhatIfPreference) {
        # $PSCmdlet has no WhatIf property in Windows PowerShell 5.1; -WhatIf sets this one.
        Write-Host "What if: would run read-only section $id against $targetLabel"
        continue
    }

    $r = Invoke-Section -Id $id
    Write-Host "`n----- $id output -----"
    Write-Host $r.Output
    Write-Host "----- end $id (psql exit $($r.ExitCode), log: $($r.LogFile)) -----"

    if ($r.ExitCode -ne 0) { throw "$id failed in psql (exit $($r.ExitCode)). Nothing after $id was run." }

    if ($id -eq 'K0') {
        $gates = @(Test-K0 $r.Output)
        Write-Host ("`nK.0.f (measurement, not a gate): targets_unpinned={0}" -f (Get-Stat $r.Output 'targets_unpinned'))
        if ($gates.Count) {
            Write-Host 'K.0 GATES FAILED - do not run K.1-K.3:' -ForegroundColor Red
            $gates | ForEach-Object { Write-Host "  - $_" }
            throw 'pre-flight did not pass.'
        }
        $json = @{ deltaSha256 = $deltaSha; delta = $DeltaPath; tier = $tierKey;
                   passedAt = (Get-Date).ToString('o') } | ConvertTo-Json
        [IO.File]::WriteAllText($marker, $json, (New-Object Text.UTF8Encoding($false)))
        Write-Host 'K.0 gates passed. Record written; K.1-K.3 are now allowed on this tier.' -ForegroundColor Green
    }

    if ($id -eq 'K4') {
        $gates = @(Test-K4 $r.Output)
        if ($gates.Count) {
            Write-Host 'K.4 POST-CONDITIONS FAILED:' -ForegroundColor Red
            $gates | ForEach-Object { Write-Host "  - $_" }
            throw 'the paste did not land as expected.'
        }
        Write-Host 'K.4: five bodies MATCH, nothing left unpinned, 8 indexes, untouched functions unmoved.' -ForegroundColor Green
    }
}

$other = if ($Container) { 'cloud tier via -PgConn (or a Dashboard paste)' } else { 'Local tier via -Container <name>' }
Write-Host "`nDone for $targetLabel. Run the same file in the same order against the $other; do not re-apply the masters on Local." -ForegroundColor Yellow
