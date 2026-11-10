param(
    [string]$PackageName = "com.sanibonani.save",
    [string]$SdkDir = "$env:LOCALAPPDATA\Android\Sdk",
    [int]$SupabaseLocalPort = 45421,
    # Holds the screen awake through a long run; only takes effect while the phone is charging.
    [switch]$KeepAwake,
    [switch]$PreflightOnly
)

$ErrorActionPreference = "Stop"

$adb = Join-Path $SdkDir "platform-tools\adb.exe"
if (!(Test-Path -LiteralPath $adb)) {
    throw "adb.exe not found at $adb. Check sdk.dir in local.properties or install Android SDK Platform Tools."
}

function Get-AttachedSerials {
    (& $adb devices) |
        Select-Object -Skip 1 |
        Where-Object { $_ -match "`tdevice`r?$" } |
        ForEach-Object { ($_ -split "`t")[0] }
}

function Get-MdnsName {
    # A wireless device is advertised as a service name and as ip:port. The name is the serial to
    # attach under because AGP builds the test-result file path from it, and a Windows path cannot
    # contain the ':' of "192.168.18.90:40773" - that run dies with "Invalid file path".
    & $adb mdns services 2>$null |
        Where-Object { $_ -match "adb-tls-connect" } |
        ForEach-Object { ($_ -split "\s+")[0] } |
        Select-Object -First 1
}

function Get-MdnsAddress {
    & $adb mdns services 2>$null |
        Where-Object { $_ -match "adb-tls-connect" } |
        ForEach-Object { ($_ -split "\s+")[-1] } |
        Where-Object { $_ -match "^\d+\.\d+\.\d+\.\d+:\d+$" } |
        Select-Object -First 1
}

function Select-ColonFreeSerial {
    Get-AttachedSerials | Where-Object { $_ -notmatch ":" } | Select-Object -First 1
}

function Connect-WirelessAdb {
    # The advertised port changes on every re-association, so a remembered address is worthless and
    # the only reliable source is a fresh mDNS lookup. Measured behaviour: adbd stops publishing the
    # service record while an ip:port session is live, so releasing that session is what makes the
    # colon-free name discoverable again. The release is paired with the reconnect at the end, so a
    # promotion that fails never leaves the phone off adb.
    $released = $null
    for ($attempt = 1; $attempt -le 8; $attempt++) {
        $colonFree = Select-ColonFreeSerial
        if ($colonFree) { return $colonFree }

        $name = Get-MdnsName
        if ($name) {
            Get-AttachedSerials | Where-Object { $_ -match ":" } |
                ForEach-Object { & $adb disconnect $_ | Out-Null }
            & $adb connect $name | Out-Null
        } else {
            $address = Get-MdnsAddress
            if ($address) {
                & $adb connect $address | Out-Null
            } elseif (-not $released) {
                $held = Get-AttachedSerials | Select-Object -First 1
                if ($held) { $released = $held; & $adb disconnect $held | Out-Null }
            }
        }
        Start-Sleep -Milliseconds 2000
    }
    if ($released -and -not (Get-AttachedSerials)) {
        & $adb connect $released | Out-Null
    }
    return (Get-AttachedSerials | Select-Object -First 1)
}

function Invoke-DevicePreflight {
    $script:Serial = Select-ColonFreeSerial
    if (-not $script:Serial) {
        Write-Host "No colon-free adb transport attached - rediscovering over mDNS..."
        $script:Serial = Connect-WirelessAdb
        if (-not $script:Serial) {
            throw "No device on adb. Enable wireless debugging on the phone (Developer options > Wireless debugging) and join it to this Wi-Fi."
        }
    }
    if ($script:Serial -match ":") {
        Write-Warning "Serial '$($script:Serial)' carries a colon, so connectedDebugAndroidTest dies with 'Invalid file path' when it merges results on Windows. Re-run this pre-flight to release the ip:port session and let the mDNS service name re-advertise, or use a USB transport."
    }
    Write-Host "Using device: $script:Serial"

    if ($script:Serial -notmatch ":") {
        # The daemon can attach the same phone twice - once as ip:port, once as the service name -
        # and then every unqualified adb command fails with "more than one device/emulator". Keep
        # only the colon-free transport.
        Get-AttachedSerials | Where-Object { $_ -match ":" -and $_ -ne $script:Serial } |
            ForEach-Object {
                Write-Host "Releasing duplicate ip:port transport: $_"
                & $adb disconnect $_ | Out-Null
            }
    }

    $charging = & $adb -s $script:Serial shell dumpsys battery |
        Select-String -Pattern "^\s+(AC|USB|Wireless) powered: true"

    if ($KeepAwake) {
        # PowerManager reads system/screen_off_timeout and caps it at 30 minutes. The secure
        # sleep_timeout key this device also exposes is a decoy - writing 1800000 there left
        # mScreenOffTimeoutSetting at 300000 - and stayon only applies while plugged in.
        & $adb -s $script:Serial shell "settings put system screen_off_timeout 1800000" | Out-Null
        & $adb -s $script:Serial shell "svc power stayon true" | Out-Null
        # Honor suspends the instrumented process inside ~75s of idle regardless of the timeout, so
        # doze has to be off too. Re-enable it after the run: dumpsys deviceidle enable.
        & $adb -s $script:Serial shell "dumpsys deviceidle disable" | Out-Null
        if (-not $charging) {
            Write-Warning "-KeepAwake set but the phone is on battery: stayon needs power, and screen sleep still caps at 30 minutes."
        }
        Write-Host "Doze disabled for this run - restore afterwards with: adb shell dumpsys deviceidle enable"
    }
    $screenOffSetting = (& $adb -s $script:Serial shell "settings get system screen_off_timeout").Trim()
    $effective = & $adb -s $script:Serial shell dumpsys power |
        Select-String -Pattern "mScreenOffTimeoutSetting=\d+" | Select-Object -First 1
    Write-Host "Power: charging=$([bool]$charging) screen_off_timeout=$screenOffSetting effective=$((($effective -replace '\s', '')))"
    if (-not $KeepAwake) {
        Write-Host "Not touching power settings (pass -KeepAwake for screen_off_timeout=1800000 + stayon)."
    }
    if (-not $charging) {
        Write-Warning "Phone is on battery: stayon cannot hold, so the screen sleeps at the timeout above and that drops the wireless transport and the reverse tunnel. Plug it in for any run longer than that."
    }

    # A reconnect wipes reverse forwarding, and the app talks to the Local tier at 127.0.0.1 on the
    # phone. Re-assert it every run and prove both ends exist before anything depends on it.
    & $adb -s $script:Serial reverse ("tcp:{0}" -f $SupabaseLocalPort) ("tcp:{0}" -f $SupabaseLocalPort) | Out-Null
    $tunnel = & $adb -s $script:Serial reverse --list
    if ($tunnel -notmatch ("tcp:{0} tcp:{0}" -f $SupabaseLocalPort)) {
        throw "Reverse tunnel for port $SupabaseLocalPort is not listed: $tunnel"
    }
    $hostListening = Test-NetConnection -ComputerName 127.0.0.1 -Port $SupabaseLocalPort -InformationLevel Quiet -WarningAction SilentlyContinue
    if (-not $hostListening) {
        throw "Nothing is listening on host port $SupabaseLocalPort. Start the Local Supabase stack (scripts/connect-supabase-docker.ps1) before running on-device tests."
    }
    Write-Host "Tunnel ready: device 127.0.0.1:$SupabaseLocalPort -> host 127.0.0.1:$SupabaseLocalPort (listening)"

    if ($PreflightOnly) {
        Write-Host ("Preflight OK. Serial: {0}" -f $script:Serial)
        exit 0
    }
}

Invoke-DevicePreflight

Write-Host "Building debug APK..."
& (Join-Path $PSScriptRoot "..\gradlew.bat") :app:assembleDebug

$apk = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"
if (!(Test-Path -LiteralPath $apk)) {
    throw "Debug APK not found at $apk"
}

Write-Host "Installing $apk..."
& $adb -s $script:Serial install -r $apk

Write-Host "Launching $PackageName..."
& $adb -s $script:Serial shell monkey -p $PackageName -c android.intent.category.LAUNCHER 1

Write-Host "Recent AndroidRuntime errors:"
& $adb -s $script:Serial logcat -d -t 100 AndroidRuntime:E SanibonaniSave:E "*:S"
