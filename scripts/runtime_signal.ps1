param(
    [string]$WorkspaceRoot = "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full",
    [string]$OutputPath = "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full\runtime_signal.json",
    [switch]$CheckLocalFunction,
    [string]$FunctionName = "architecture-read",
    [string]$FunctionAction = "blueprint",
    [string]$FunctionsBaseUrl = "http://127.0.0.1:54321/functions/v1",
    [switch]$CiExitOnNotReady,
    [switch]$FailOnYellow,
    [ValidateSet("Default", "Utf8NoBom")]
    [string]$OutputEncodingMode = "Default",
    [int]$TimeoutSec = 8,
    [switch]$Verbose
)

$ErrorActionPreference = "Stop"
$VerbosePreference = if ($Verbose) { "Continue" } else { "SilentlyContinue" }

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
if ($OutputEncodingMode -eq "Utf8NoBom") {
    [Console]::OutputEncoding = $utf8NoBom
}

# ============================================================================
# Diagnostic & Logging Functions
# ============================================================================

function Write-Verbose-Log {
    param([string]$Message)
    if ($Verbose) {
        $timestamp = (Get-Date).ToString("HH:mm:ss.fff")
        Write-Host "[$timestamp] [DIAG] $Message" -ForegroundColor Gray
    }
}

function Write-TextFile {
    param(
        [string]$Path,
        [string]$Content,
        [ValidateSet("Default", "Utf8NoBom")]
        [string]$Mode
    )

    if ($Mode -eq "Utf8NoBom") {
        [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
    } else {
        $Content | Out-File -FilePath $Path -Encoding utf8
    }
}

function Get-DiagnosticInfo {
    param([string]$Detail)

    # Trim verbose output and provide max 500 chars
    if ($Detail.Length -gt 500) {
        return $Detail.Substring(0, 500) + "..."
    }
    return $Detail
}

function Test-CommandExists {
    param([string]$Name)
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Invoke-Capture {
    param([string]$Command)
    try {
        $text = Invoke-Expression $Command 2>&1 | Out-String
        return @{ ok = $true; output = $text.Trim() }
    } catch {
        return @{ ok = $false; output = ($_ | Out-String).Trim() }
    }
}

$denoExists = Test-CommandExists "deno"
$supabaseExists = Test-CommandExists "supabase"
$dockerExists = Test-CommandExists "docker"

$denoVersion = if ($denoExists) { (Invoke-Capture "deno --version").output } else { "not_found" }
$supabaseVersion = if ($supabaseExists) { (Invoke-Capture "supabase --version").output } else { "not_found" }

$dockerServerVersion = "not_available"
$dockerDaemonHealthy = $false
if ($dockerExists) {
    $dockerProbe = Invoke-Capture "docker version --format '{{.Server.Version}}'"
    if ($dockerProbe.ok -and $dockerProbe.output -and $dockerProbe.output -ne "<no value>") {
        $dockerDaemonHealthy = $true
        $dockerServerVersion = $dockerProbe.output
    } else {
        $dockerServerVersion = if ($dockerProbe.output) { $dockerProbe.output } else { "daemon_unreachable" }
    }
}

$supabaseStatusOutput = "not_checked"
$supabaseLocalRunning = $false
if ($supabaseExists) {
    Push-Location $WorkspaceRoot
    try {
        $statusProbe = Invoke-Capture "supabase status -o json"
        $supabaseStatusOutput = $statusProbe.output
        if ($statusProbe.ok -and $supabaseStatusOutput) {
            # Best-effort parse: if JSON includes API URL, local stack is up.
            if ($supabaseStatusOutput -match '"API URL"' -or $supabaseStatusOutput -match 'apiUrl' -or $supabaseStatusOutput -match 'http://127.0.0.1') {
                $supabaseLocalRunning = $true
            }
        }
    } finally {
        Pop-Location
    }
}

$requirements = @(
    @{ key = "deno_cli"; ok = $denoExists; detail = $denoVersion },
    @{ key = "supabase_cli"; ok = $supabaseExists; detail = $supabaseVersion },
    @{ key = "docker_cli"; ok = $dockerExists; detail = if ($dockerExists) { "present" } else { "not_found" } },
    @{ key = "docker_daemon"; ok = $dockerDaemonHealthy; detail = $dockerServerVersion },
    @{ key = "supabase_local_stack"; ok = $supabaseLocalRunning; detail = $supabaseStatusOutput }
)

$localFunctionProbe = @{
    enabled = [bool]$CheckLocalFunction
    key = "local_function_http"
    ok = $false
    detail = "not_checked"
    url = "${FunctionsBaseUrl}/${FunctionName}?action=${FunctionAction}"
}

if ($CheckLocalFunction) {
    Write-Verbose-Log "Probing local function at: $($localFunctionProbe.url)"
    try {
        $probeUrl = $localFunctionProbe.url
        $resp = Invoke-WebRequest -Method Get -Uri $probeUrl -TimeoutSec $TimeoutSec -ErrorAction Stop
        $localFunctionProbe.ok = ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 300)
        $localFunctionProbe.detail = "http_$($resp.StatusCode)"
        Write-Verbose-Log "Local function probe: $($resp.StatusCode)"
    } catch {
        $localFunctionProbe.ok = $false
        $errorDetail = Get-DiagnosticInfo (($_ | Out-String).Trim())
        $localFunctionProbe.detail = $errorDetail
        Write-Verbose-Log "Local function probe failed: $errorDetail"
    }

    $requirements += @{
        key = $localFunctionProbe.key
        ok = $localFunctionProbe.ok
        detail = $localFunctionProbe.detail
    }
}

$blockingFailures = @($requirements | Where-Object { -not $_.ok })

$signal = if (-not $denoExists -or -not $supabaseExists -or -not $dockerExists -or -not $dockerDaemonHealthy) {
    "RED"
} elseif (-not $supabaseLocalRunning -or ($CheckLocalFunction -and -not $localFunctionProbe.ok)) {
    "YELLOW"
} else {
    "GREEN"
}

$result = [ordered]@{
    generated_at = (Get-Date).ToString("o")
    workspace = $WorkspaceRoot
    signal = $signal
    runtime_ready = ($signal -eq "GREEN")
    summary = switch ($signal) {
        "GREEN" { "Local Deno/Supabase runtime is ready." }
        "YELLOW" { "Toolchain is present, but local Supabase stack or local function endpoint is not confirmed running." }
        default { "Blocking runtime prerequisites are missing." }
    }
    requirements = $requirements
    blocking_failures = $blockingFailures
    local_function_probe = $localFunctionProbe
    diagnostics = @{
        probe_version = "2.0"
        timeout_seconds = $TimeoutSec
        check_local_function = [bool]$CheckLocalFunction
        function_base_url = $FunctionsBaseUrl
        total_requirements = $requirements.Count
        failed_count = $blockingFailures.Count
    }
}

$resultJson = $result | ConvertTo-Json -Depth 8
Write-TextFile -Path $OutputPath -Content $resultJson -Mode $OutputEncodingMode

$topBlockers = @($blockingFailures | ForEach-Object { $_["key"] } | Select-Object -First 3)
$blockerText = if ($topBlockers.Count -gt 0) { $topBlockers -join "," } else { "none" }
$summaryLine = "runtime_summary=signal:$signal;ready:$($result.runtime_ready);blockers:$blockerText"

Write-Output ("runtime_signal=" + $signal)
Write-Output ("report=" + $OutputPath)
Write-Output $summaryLine

Write-Verbose-Log "Report written to: $OutputPath"
Write-Verbose-Log "Summary: $summaryLine"

if ($CiExitOnNotReady) {
    # CI semantics:
    # - RED always fails (blocking prerequisites missing)
    # - YELLOW fails only when explicitly requested
    if ($signal -eq "RED") {
        exit 2
    }
    if ($signal -eq "YELLOW" -and $FailOnYellow) {
        exit 3
    }
}

