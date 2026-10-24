$srcDir = "app/src/main/java/com/sanibonani/save"
$testDir = "app/src/test/java/com/sanibonani/save"
$androidTestDir = "app/src/androidTest/java/com/sanibonani/save"

New-Item -ItemType Directory -Force -Path $testDir | Out-Null
New-Item -ItemType Directory -Force -Path $androidTestDir | Out-Null

$androidTestFiles = @(
    "AdminNavigationSimulationTest.kt",
    "AppFlowTest.kt",
    "BusinessProcessSimulationTest.kt",
    "DatabaseConnectionTest.kt",
    "DatabaseInitializationTest.kt",
    "LiveFinancialFlowSimulation.kt",
    "LiveSeeder.kt",
    "LiveSimulationRunner.kt",
    "MasterDataGenerator.kt",
    "MigrationTest.kt",
    "SupabaseIntegrationTest.kt",
    "simulation/DeepSimulationAndroidTest.kt",
    "simulation/InternetTest.kt"
)

Get-ChildItem -Path $srcDir -Recurse -Filter "*.kt" | Where-Object {
    $_.Name -like "*Test.kt" -or $_.Name -like "*Seeder.kt" -or $_.Name -like "*Simulation*.kt" -or $_.Name -like "*Generator.kt" -or $_.Name -like "*Utility.kt"
} | ForEach-Object {
    $fullPath = $_.FullName
    $basePath = (Resolve-Path $srcDir).Path
    $relPath = $fullPath.Substring($basePath.Length + 1).Replace("\", "/")

    $isAndroidTest = $false
    foreach ($f in $androidTestFiles) {
        if ($relPath -eq $f -or $relPath.EndsWith("/$f")) {
            $isAndroidTest = $true
            break
        }
    }

    $dest = if ($isAndroidTest) {
        Join-Path $androidTestDir $relPath
    } else {
        Join-Path $testDir $relPath
    }

    $destParent = Split-Path $dest -Parent
    New-Item -ItemType Directory -Force -Path $destParent | Out-Null
    Move-Item -Path $fullPath -Destination $dest -Force
    Write-Host "Moved $relPath to $(if ($isAndroidTest) { 'androidTest' } else { 'test' })"
}
