# PowerShell script to delete users from Supabase
# Email: torrymsimango@gmail.com (exact email as specified)

$SupabaseUrl = "https://prosbbknupoexgzjwrwr.supabase.co"
$ServiceRoleKey = "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InByb3NiYmtudXBvZXhnemp3cndyIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3MzU0MTgwNSwiZXhwIjoyMDg5MTE3ODA1fQ.EDVweeVevFlnIo-9xfwc80zZ93KM3tY-GTmWsUmPCLA"
$Email = "torrymsimango@gmail.com"

# First, get all users with this email to see if they exist
Write-Host "Fetching users with email: $Email"

$headers = @{
    "Authorization" = "Bearer $ServiceRoleKey"
    "Content-Type" = "application/json"
    "apikey" = $ServiceRoleKey
}

try {
    # List all users (requires service role)
    $response = Invoke-RestMethod `
        -Uri "$SupabaseUrl/rest/v1/auth.users" `
        -Method GET `
        -Headers $headers `
        -ErrorAction Stop

    Write-Host "Users found: $($response.Count)"
    $response | Format-Table
} catch {
    Write-Host "Error fetching users (this may require direct database access): $_"
    Write-Host ""
    Write-Host "Since direct REST API access may be limited, please use the following SQL command"
    Write-Host "in your Supabase SQL Editor (Query Editor in the Dashboard):"
    Write-Host ""
    Write-Host "DELETE FROM auth.users WHERE email = 'torrymsimango@gmail.com';"
    Write-Host ""
    Write-Host "Or use psql if you have database access configured."
}

