$ErrorActionPreference = "Stop"

Write-Host "Running live-readiness verification..."

& .\gradlew.bat `
    :app:assembleDebug `
    :app:testDebugUnitTest `
    :domain:testDebugUnitTest `
    :app:compileDebugAndroidTestKotlin `
    :app:lintDebug

Write-Host "Verification complete."

