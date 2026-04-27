param(
    [string]$TestClass = "com.sanibonani.save.ui.navigation.NavGraphRoleTransitionIntegrationTest",
    [string]$TestMethod,
    [string]$TestPackage,
    [switch]$CheckOnly
)

$ErrorActionPreference = "Stop"

function Resolve-AdbPath {
    $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCmd -and $adbCmd.Source) {
        return $adbCmd.Source
    }

    $repoRoot = $PSScriptRoot
    $localProps = Join-Path $repoRoot "local.properties"
    if (Test-Path $localProps) {
        $line = Get-Content $localProps | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($line) {
            $sdkDir = ($line -replace '^sdk\.dir=', '').Trim()
            # local.properties on Windows often stores: C\:\\Users\\...; normalize to C:\Users\...
            $sdkDir = $sdkDir.Replace('\:', ':')
            while ($sdkDir.Contains('\\')) {
                $sdkDir = $sdkDir.Replace('\\', '\')
            }
            $adbFromLocalProps = Join-Path $sdkDir "platform-tools\adb.exe"
            if (Test-Path $adbFromLocalProps) {
                return $adbFromLocalProps
            }
        }
    }

    foreach ($sdkVar in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, (Join-Path $env:LOCALAPPDATA "Android\Sdk"))) {
        if ([string]::IsNullOrWhiteSpace($sdkVar)) { continue }
        $candidate = Join-Path $sdkVar "platform-tools\adb.exe"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    return $null
}

$adbPath = Resolve-AdbPath
if (-not $adbPath) {
    throw "adb not found. Ensure Android SDK is installed and set sdk.dir in local.properties or ANDROID_SDK_ROOT."
}

$platformToolsDir = Split-Path $adbPath -Parent
if (-not ($env:PATH -split ';' | Where-Object { $_ -eq $platformToolsDir })) {
    $env:PATH = "$platformToolsDir;$env:PATH"
}

$env:ANDROID_SDK_ROOT = Split-Path $platformToolsDir -Parent

Write-Host "Using adb: $adbPath"
& $adbPath start-server | Out-Null
$devicesRaw = & $adbPath devices
$onlineDevices = $devicesRaw | Where-Object { $_ -match '\sdevice$' -and $_ -notmatch '^List of devices' }

if (-not $onlineDevices) {
    throw "No online emulator/device found. Start an emulator or connect a device, then rerun."
}

Write-Host "Connected device(s):"
$onlineDevices | ForEach-Object { Write-Host "  $_" }

if ($CheckOnly) {
    Write-Host "CheckOnly complete. Environment is ready to run the instrumentation test."
    exit 0
}

Push-Location $PSScriptRoot
try {
    $hasExplicitClass = $PSBoundParameters.ContainsKey("TestClass")
    $hasMethod = -not [string]::IsNullOrWhiteSpace($TestMethod)
    $hasPackage = -not [string]::IsNullOrWhiteSpace($TestPackage)

    if ($hasMethod -and [string]::IsNullOrWhiteSpace($TestClass)) {
        throw "-TestMethod requires -TestClass."
    }

    $runnerArg = if ($hasMethod) {
        if ($hasPackage) {
            Write-Host "Note: -TestPackage is ignored because -TestMethod was provided."
        }
        $target = "$TestClass#$TestMethod"
        Write-Host "Running instrumentation target (class#method): $target"
        "-Pandroid.testInstrumentationRunnerArguments.class=$target"
    } elseif ($hasExplicitClass) {
        if ($hasPackage) {
            Write-Host "Note: -TestPackage is ignored because -TestClass was provided explicitly."
        }
        Write-Host "Running instrumentation target (class): $TestClass"
        "-Pandroid.testInstrumentationRunnerArguments.class=$TestClass"
    } elseif ($hasPackage) {
        Write-Host "Running instrumentation target (package): $TestPackage"
        "-Pandroid.testInstrumentationRunnerArguments.package=$TestPackage"
    } else {
        Write-Host "Running instrumentation target (default class): $TestClass"
        "-Pandroid.testInstrumentationRunnerArguments.class=$TestClass"
    }

    & .\gradlew.bat :app:connectedDebugAndroidTest $runnerArg --console=plain --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Instrumentation test failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Write-Host "Instrumentation run completed."
Write-Host "Report: app/build/reports/androidTests/connected/index.html"
Write-Host "Results: app/build/outputs/androidTest-results/connected/"

