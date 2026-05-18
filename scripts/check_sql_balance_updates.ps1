<#
PowerShell script: check_sql_balance_updates.ps1
Purpose: scan the repository SQL files for direct updates to `groups.balance` and flag files that perform such UPDATEs without also inserting into `group_ledger`.

Usage:
  # run locally and print findings
  .\scripts\check_sql_balance_updates.ps1

  # run in CI to fail the job if any risky matches are found
  .\scripts\check_sql_balance_updates.ps1 -CI

Notes / heuristic:
 - This is a conservative heuristic to find potentially unsafe direct balance updates.
 - It searches for UPDATE statements touching a `groups` table and the token `balance` within the SET clause.
 - If the same file also contains `group_ledger` or an INSERT into `group_ledger` it's less likely an unsafe direct update, but still worth review.
 - The script is intentionally conservative and will output file/line snippets for manual review.
#>
param(
    [switch]$CI
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
# assume the repo root is the parent of the scripts directory (works when script lives in ./scripts)
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..')).Path
Write-Host "Repository root: $repoRoot"

# directories to search - adjust if you keep SQL elsewhere
$searchDirs = @(
    (Join-Path $repoRoot 'supabase'),
    (Join-Path $repoRoot 'supabase\migrations'),
    $repoRoot
) | Where-Object { Test-Path $_ }

$updatePattern = '(?is)\bUPDATE\s+(?:public\.)?groups\b[\s\S]{0,200}?\bSET\b[^;]*?\bbalance\b'
$insertLedgerPattern = '(?is)\bINSERT\s+INTO\s+(?:public\.)?group_ledger\b|\bgroup_ledger\b'

$matches = @()

foreach ($dir in $searchDirs | Select-Object -Unique) {
    Write-Host "Scanning directory: $dir"
    Get-ChildItem -Path $dir -Recurse -Filter '*.sql' -File -ErrorAction SilentlyContinue | ForEach-Object {
        $path = $_.FullName
        $content = Get-Content -Raw -ErrorAction SilentlyContinue -Path $path
        if (-not $content) { return }

        $hasUpdate = [regex]::Matches($content, $updatePattern).Count -gt 0
        if ($hasUpdate) {
            $hasLedger = [regex]::IsMatch($content, $insertLedgerPattern)

            # extract snippets with the UPDATE lines for context
            $updateMatches = [regex]::Matches($content, $updatePattern)
            foreach ($m in $updateMatches) {
                $snippet = $m.Value -replace "\r\n", " " -replace "\s+", " "
                $matches += [PSCustomObject]@{
                    File = $path
                    Snippet = $snippet
                    ContainsLedger = $hasLedger
                }
            }
        }
    }
}

if ($matches.Count -eq 0) {
    Write-Host "No direct groups.balance UPDATE patterns found in scanned SQL files." -ForegroundColor Green
    exit 0
}

Write-Host "Potential direct balance updates found: $($matches.Count)" -ForegroundColor Yellow
foreach ($m in $matches) {
    if ($m.ContainsLedger) {
        Write-Host "[REVIEW] File: $($m.File) -- UPDATE found but file contains 'group_ledger' (may be safe); please review." -ForegroundColor Cyan
    } else {
        Write-Host "[ALERT] File: $($m.File) -- UPDATE found and NO group_ledger insert detected; manual review recommended." -ForegroundColor Red
    }
    Write-Host "    Snippet: $($m.Snippet)" -ForegroundColor DarkGray
}

if ($CI) {
    Write-Host "CI mode: failing because potential direct balance updates were detected." -ForegroundColor Red
    exit 1
}

exit 0

