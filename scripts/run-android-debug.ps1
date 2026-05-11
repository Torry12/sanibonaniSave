param(
    [string]$PackageName = "com.sanibonani.save",
    [string]$SdkDir = "$env:LOCALAPPDATA\Android\Sdk"
)

$ErrorActionPreference = "Stop"

$adb = Join-Path $SdkDir "platform-tools\adb.exe"
if (!(Test-Path -LiteralPath $adb)) {
    throw "adb.exe not found at $adb. Check sdk.dir in local.properties or install Android SDK Platform Tools."
}

Write-Host "Building debug APK..."
& .\gradlew.bat :app:assembleDebug

$apk = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"
if (!(Test-Path -LiteralPath $apk)) {
    throw "Debug APK not found at $apk"
}

Write-Host "Connected devices:"
& $adb devices

Write-Host "Installing $apk..."
& $adb install -r $apk

Write-Host "Launching $PackageName..."
& $adb shell monkey -p $PackageName -c android.intent.category.LAUNCHER 1

Write-Host "Recent AndroidRuntime errors:"
& $adb logcat -d -t 100 AndroidRuntime:E SanibonaniSave:E "*:S"

