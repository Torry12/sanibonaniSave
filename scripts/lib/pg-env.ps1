# Shared libpq environment handling for the Docker-based remote SQL helpers.
# Splitting the URL into PG* variables keeps the password out of every argv (the
# host process list included); libpq reads them the same way it reads a conninfo.

function Split-PostgresUrl {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [string]$SslMode = ""
    )

    $Pattern = '^(?i)postgres(?:ql)?://(?<user>[^:@/]+):(?<pass>[^@]*)@(?<host>[^:/?]+):(?<port>\d+)/(?<db>[^?]+)(?:\?(?<query>.*))?$'
    if ($Url -notmatch $Pattern) {
        throw "Unrecognised connection string. Use Dashboard -> Connect -> Direct connection (postgresql://user:password@host:5432/postgres)."
    }

    $Mode = $SslMode
    if (-not $Mode) {
        if ($Matches.query -match 'sslmode=([a-z]+)') { $Mode = $Matches[1] } else { $Mode = 'require' }
    }

    [pscustomobject]@{
        Host     = $Matches.host
        Port     = $Matches.port
        User     = [uri]::UnescapeDataString($Matches.user)
        Password = [uri]::UnescapeDataString($Matches.pass)
        Database = [uri]::UnescapeDataString($Matches.db)
        SslMode  = $Mode
    }
}

function Set-PostgresEnv {
    param(
        [Parameter(Mandatory = $true)]$Connection,
        [switch]$ReadOnly
    )

    $Names = 'PGHOST','PGPORT','PGUSER','PGPASSWORD','PGDATABASE','PGSSLMODE','PGOPTIONS'
    $Saved = @{}
    foreach ($Name in $Names) { $Saved[$Name] = [Environment]::GetEnvironmentVariable($Name) }

    $env:PGHOST = $Connection.Host
    $env:PGPORT = $Connection.Port
    $env:PGUSER = $Connection.User
    $env:PGPASSWORD = $Connection.Password
    $env:PGDATABASE = $Connection.Database
    $env:PGSSLMODE = $Connection.SslMode
    if ($ReadOnly) { $env:PGOPTIONS = "-c default_transaction_read_only=on" }

    ,$Saved
}

function Restore-PostgresEnv {
    param([Parameter(Mandatory = $true)]$Saved)

    foreach ($Name in $Saved.Keys) {
        if ($null -eq $Saved[$Name]) {
            Remove-Item "Env:$Name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($Name, $Saved[$Name])
        }
    }
}

function Read-DatabaseUrl {
    param([Parameter(Mandatory = $true)][string]$Prompt)

    $Secure = Read-Host -AsSecureString $Prompt
    $Ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($Ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($Ptr)
    }
}
