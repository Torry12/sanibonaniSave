<#
check_trigger_backup.ps1 — Invoke the deployed trigger-backup function and list the `backups` bucket.

Prints the function's status and body, then the bucket root, so the "did it store anything"
question is answered from one command. Credentials are read from the environment and never stored:
  $env:SB_ANON  - anon/publishable key (required)
  $env:SB_TOKEN - platform-admin access token (required)
  $env:SB_REF   - project ref (optional, defaults to the deployed project)

Usage:
  $env:SB_ANON  = "<anon key>"
  $env:SB_TOKEN = "<admin access token>"
  .\supabase\scripts\check_trigger_backup.ps1
#>

param(
    [string]$Ref = $env:SB_REF,
    [string]$Anon = $env:SB_ANON,
    [string]$Token = $env:SB_TOKEN
)

if (-not $Ref) { $Ref = "aszmodkqpafwtnfufmow" }
if (-not $Anon) { Write-Error "SB_ANON not set. Set the environment variable or pass -Anon."; exit 2 }
if (-not $Token) { Write-Error "SB_TOKEN not set. Set the environment variable or pass -Token."; exit 2 }

$base = "https://$Ref.supabase.co"
$headers = @{ Authorization = "Bearer $Token"; apikey = $Anon }
$exitCode = 0

function Show-Response($label, $uri, $body) {
    Write-Host "== $label =="
    $params = @{
        UseBasicParsing = $true
        Method          = "Post"
        Uri             = $uri
        Headers         = $headers
    }
    if ($body) {
        $params.ContentType = "application/json"
        $params.Body = $body
    }
    try {
        $response = Invoke-WebRequest @params
        Write-Host ("HTTP {0}" -f $response.StatusCode)
        Write-Host $response.Content
        return $response.StatusCode
    } catch {
        # Invoke-WebRequest throws on 4xx/5xx; the body is the interesting part.
        $response = $_.Exception.Response
        if (-not $response) { throw }
        $status = [int]$response.StatusCode
        $text = $_.ErrorDetails.Message
        if (-not $text -and $response.GetResponseStream) {
            $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
            $text = $reader.ReadToEnd()
        }
        Write-Host ("HTTP {0}" -f $status)
        Write-Host $text
        return $status
    } finally {
        Write-Host ""
    }
}

$invokeStatus = Show-Response "trigger-backup" "$base/functions/v1/trigger-backup" $null
if ($invokeStatus -lt 200 -or $invokeStatus -gt 299) { $exitCode = 1 }

Show-Response "backups bucket root" "$base/storage/v1/object/list/backups" '{"prefix":"","limit":20,"sortBy":{"column":"name","order":"desc"}}' | Out-Null

if ($exitCode -ne 0) {
    Write-Error "trigger-backup did not return 2xx; see the body above."
}

exit $exitCode
