param(
    [string]$WorkspaceRoot = "C:\Users\CRISS\AndroidStudioProjects\SanibonaniSave_Full",
    [switch]$Strict,
    [switch]$CheckLocalFunction,
    [string]$FunctionName = "architecture-read",
    [string]$FunctionAction = "events"
)

$ErrorActionPreference = "Stop"

$scriptPath = Join-Path $WorkspaceRoot "scripts\runtime_signal.ps1"
if (-not (Test-Path $scriptPath)) {
    Write-Error "runtime_signal.ps1 not found at: $scriptPath"
    exit 10
}

$args = @(
    "-WorkspaceRoot", $WorkspaceRoot,
    "-OutputEncodingMode", "Utf8NoBom",
    "-CiExitOnNotReady"
)

if ($CheckLocalFunction) {
    $args += @(
        "-CheckLocalFunction",
        "-FunctionName", $FunctionName,
        "-FunctionAction", $FunctionAction
    )
}

if ($Strict) {
    $args += "-FailOnYellow"
}

& powershell -NoProfile -ExecutionPolicy Bypass -File $scriptPath @args
$exitCode = $LASTEXITCODE

# Print the report body to aid CI debugging on failures.
$reportPath = Join-Path $WorkspaceRoot "runtime_signal.json"
if (Test-Path $reportPath) {
    Write-Output "--- runtime_signal.json ---"
    Get-Content $reportPath
}

exit $exitCode

