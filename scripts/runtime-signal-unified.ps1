<#
.SYNOPSIS
    Unified Runtime Signal Probe - Orchestrates environment readiness checks for CI/CD pipelines.

.DESCRIPTION
    This wrapper consolidates platform-specific logic, provides debugging capabilities,
    and manages the execution of the core runtime probe with enhanced logging and error handling.

.PARAMETER WorkspaceRoot
    Root workspace path (defaults to environment or current directory)

.PARAMETER CheckLocalFunction
    Enable HTTP probe for local Edge Functions

.PARAMETER FunctionName
    Edge Function name to probe (default: architecture-read)

.PARAMETER FunctionAction
    Edge Function action parameter (default: events)

.PARAMETER Strict
    Exit with code 3 on YELLOW signals (not just RED)

.PARAMETER Debug
    Enable verbose debug output with timestamps and detailed diagnostics

.PARAMETER Retry
    Number of retry attempts for transient failures (default: 1)

.PARAMETER TimeoutSeconds
    HTTP timeout for function probe (default: 8)

.EXAMPLE
    .\runtime-signal-unified.ps1 -CheckLocalFunction -Strict -Debug

.NOTES
    Author: SanibonaniSave Deployment Team
    Version: 2.0 (Consolidated)
    Replaces: runtime_signal_ci_example.ps1 and runtime_signal_ci_example.sh
#>

param(
    [string]$WorkspaceRoot,
    [switch]$CheckLocalFunction,
    [string]$FunctionName = "architecture-read",
    [string]$FunctionAction = "events",
    [switch]$Strict,
    [switch]$Debug,
    [int]$Retry = 0,
    [int]$TimeoutSeconds = 8
)

# ============================================================================
# Initialize Settings
# ============================================================================

$ErrorActionPreference = "Stop"
$DebugPreference = if ($Debug) { "Continue" } else { "SilentlyContinue" }

# Resolve workspace root
if (-not $WorkspaceRoot) {
    if ($env:GITHUB_WORKSPACE) {
        $WorkspaceRoot = $env:GITHUB_WORKSPACE
    } elseif ($env:CI_PROJECT_DIR) {
        $WorkspaceRoot = $env:CI_PROJECT_DIR
    } else {
        $WorkspaceRoot = (Get-Location).Path
    }
}

$WorkspaceRoot = (Resolve-Path $WorkspaceRoot -ErrorAction Stop).Path
$ScriptPath = Join-Path $WorkspaceRoot "scripts\runtime_signal.ps1"
$ReportPath = Join-Path $WorkspaceRoot "runtime_signal.json"

# Non-interactive encoding
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $utf8NoBom

# ============================================================================
# Logging Functions
# ============================================================================

function Write-DebugLog {
    param([string]$Message)
    if ($Debug) {
        $timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss.fff")
        Write-Host "[$timestamp] [DEBUG] $Message" -ForegroundColor Cyan
    }
}

function Write-InfoLog {
    param([string]$Message)
    $timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss.fff")
    Write-Host "[$timestamp] [INFO] $Message" -ForegroundColor Green
}

function Write-WarnLog {
    param([string]$Message)
    $timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss.fff")
    Write-Host "[$timestamp] [WARN] $Message" -ForegroundColor Yellow
}

function Write-ErrorLog {
    param([string]$Message, [int]$ExitCode = 1)
    $timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss.fff")
    Write-Error "[$timestamp] [ERROR] $Message" -ErrorAction Continue

    if ($ExitCode -gt 0) {
        exit $ExitCode
    }
}

# ============================================================================
# Validation & Pre-flight Checks
# ============================================================================

Write-DebugLog "Workspace root: $WorkspaceRoot"
Write-DebugLog "Probe script path: $ScriptPath"

if (-not (Test-Path $ScriptPath)) {
    Write-ErrorLog "Core probe script not found: $ScriptPath" 10
}

Write-InfoLog "✓ Pre-flight validation passed"

# ============================================================================
# Probe Execution with Retry Logic
# ============================================================================

$maxAttempts = $Retry + 1
$attempt = 1
$exitCode = -1

while ($attempt -le $maxAttempts) {
    if ($attempt -gt 1) {
        Write-WarnLog "Retry attempt $attempt/$maxAttempts..."
        Start-Sleep -Seconds 2
    }

    Write-InfoLog "Running runtime probe (attempt $attempt/$maxAttempts)..."
    Write-DebugLog "Parameters: FunctionName=$FunctionName, FunctionAction=$FunctionAction, Strict=$Strict"

    $probeArgs = @(
        "-WorkspaceRoot", $WorkspaceRoot,
        "-OutputEncodingMode", "Utf8NoBom",
        "-CiExitOnNotReady"
    )

    if ($CheckLocalFunction) {
        $probeArgs += @(
            "-CheckLocalFunction",
            "-FunctionName", $FunctionName,
            "-FunctionAction", $FunctionAction,
            "-TimeoutSec", $TimeoutSeconds
        )
    }

    if ($Strict) {
        $probeArgs += "-FailOnYellow"
    }

    # Execute probe
    try {
        Write-DebugLog "Invoking: powershell -NoProfile -ExecutionPolicy Bypass -File '$ScriptPath' with parameters"
        & powershell -NoProfile -ExecutionPolicy Bypass -File $ScriptPath @probeArgs
        $exitCode = $LASTEXITCODE

        if ($exitCode -eq 0 -or ($exitCode -eq 3 -and $Strict)) {
            break  # Success or expected failure
        }

        if ($attempt -lt $maxAttempts -and $exitCode -eq 2) {
            Write-WarnLog "Transient failure detected (exit $exitCode), will retry..."
            continue
        }
    } catch {
        $errorMsg = $_ | Out-String
        Write-ErrorLog "Probe execution failed: $errorMsg" 11
    }

    $attempt++
}

# ============================================================================
# Report Generation & Output
# ============================================================================

Write-DebugLog "Probe completed with exit code: $exitCode"

# Emit structured output
try {
    if (Test-Path $ReportPath) {
        $report = Get-Content $ReportPath -Raw | ConvertFrom-Json

        Write-InfoLog "=========================================================="
        Write-InfoLog "RUNTIME SIGNAL REPORT"
        Write-InfoLog "=========================================================="

        $signal = $report.signal
        $ready = $report.runtime_ready
        $summary = $report.summary

        # Color-coded signal
        $signalColor = switch ($signal) {
            "RED" { "Red" }
            "YELLOW" { "Yellow" }
            "GREEN" { "Green" }
            default { "Gray" }
        }

        Write-Host "Signal:  " -NoNewline
        Write-Host $signal -ForegroundColor $signalColor
        Write-Host "Ready:   $ready"
        Write-Host "Summary: $summary"

        if ($Debug -or $report.blocking_failures.Count -gt 0) {
            Write-Host ""
            Write-Host "Requirements:" -ForegroundColor Cyan
            $report.requirements | ForEach-Object {
                $status = if ($_.ok) { "✓" } else { "✗" }
                $color = if ($_.ok) { "Green" } else { "Red" }
                Write-Host "  [$status] $($_.key)" -ForegroundColor $color
                Write-DebugLog "    Detail: $($_.detail)"
            }

            if ($report.blocking_failures.Count -gt 0) {
                Write-Host ""
                Write-Host "Blocking Failures:" -ForegroundColor Red
                $report.blocking_failures | ForEach-Object {
                    Write-Host "  • $($_.key): $($_.detail)" -ForegroundColor Red
                }
            }
        }

        Write-InfoLog "=========================================================="
    }
} catch {
    Write-WarnLog "Could not parse report: $_"
}

# ============================================================================
# Finalization
# ============================================================================

Write-DebugLog "Exit code: $exitCode"

if ($exitCode -eq 0) {
    Write-InfoLog "✓ Runtime environment ready for deployment"
} elseif ($exitCode -eq 2) {
    Write-ErrorLog "✗ FAILED: Critical runtime prerequisites missing (RED signal)" -ExitCode 0
    exit 2
} elseif ($exitCode -eq 3) {
    Write-ErrorLog "✗ FAILED: Runtime environment degraded (YELLOW signal with -Strict)" -ExitCode 0
    exit 3
}

exit $exitCode

