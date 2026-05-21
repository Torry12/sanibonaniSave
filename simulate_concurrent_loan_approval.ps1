# PowerShell script to simulate high-concurrency loan approval/disbursement in PostgreSQL
# This script launches multiple parallel jobs, each calling the approve_and_disburse_loan_v1 function
# Replace the placeholders with your actual DB credentials and test UUIDs

$psqlPath = "psql"  # Ensure psql is in your PATH or provide the full path
$dbUser = "your_db_user"
$dbPass = "your_db_password"
$dbHost = "localhost"
$dbPort = "5432"
$dbName = "your_db_name"
$loanId = "your-loan-uuid"
$adminId = "your-admin-uuid"
$jobs = @()

# Set the PGPASSWORD environment variable for non-interactive password passing
$env:PGPASSWORD = $dbPass

for ($i=0; $i -lt 50; $i++) {
    $jobs += Start-Job -ScriptBlock {
        param($psqlPath, $dbUser, $dbHost, $dbPort, $dbName, $loanId, $adminId)
        $cmd = "\"$psqlPath\" -U $dbUser -h $dbHost -p $dbPort -d $dbName -c \"SELECT public.approve_and_disburse_loan_v1('$loanId', '$adminId', 'bank');\""
        Invoke-Expression $cmd
    } -ArgumentList $psqlPath, $dbUser, $dbHost, $dbPort, $dbName, $loanId, $adminId
}

$jobs | Wait-Job
$jobs | Receive-Job

# Clean up jobs
disable-job $jobs | Remove-Job

Write-Host "High-concurrency simulation complete. Check your database for results and error logs."

