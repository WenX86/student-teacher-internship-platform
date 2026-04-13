param(
    [switch]$NoPause
)

$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$stateFiles = @(
    (Join-Path $root "logs\start-all.state.json"),
    (Join-Path $root "logs\start-mysql.state.json")
)
$backendStopScript = Join-Path $root "backend-spring\stop-dev.ps1"

function Stop-PidTree {
    param(
        [Parameter(Mandatory = $true)]
        [int]$ProcessId
    )

    try {
        & taskkill.exe /PID $ProcessId /T /F 2>$null | Out-Null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Read-State {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path $Path)) {
        return $null
    }

    try {
        return Get-Content $Path -Raw | ConvertFrom-Json
    } catch {
        return $null
    }
}

$states = @()
foreach ($path in $stateFiles) {
    $state = Read-State -Path $path
    if ($state) {
        $states += [pscustomobject]@{
            Path = $path
            State = $state
        }
    }
}

if (-not $states.Count) {
    Write-Host "No start-all.state.json or start-mysql.state.json found. Nothing to stop."
    if (-not $NoPause) {
        Write-Host "Press any key to close this window."
        $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    }
    exit 0
}

$backendStopped = $false
$frontendStopped = $false
$stoppedBackendPids = New-Object System.Collections.Generic.HashSet[int]
$stoppedFrontendPids = New-Object System.Collections.Generic.HashSet[int]

if (Test-Path $backendStopScript) {
    try {
        & "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" -ExecutionPolicy Bypass -File $backendStopScript | Out-Null
        $backendStopped = $true
    } catch {
        $backendStopped = $false
    }
}

if (-not $backendStopped) {
    foreach ($item in $states) {
        $state = $item.State
        foreach ($pidProperty in @("backendPid", "backendLauncherPid")) {
            $pid = $state.$pidProperty
            if ($pid) {
                $pidInt = [int]$pid
                if ($stoppedBackendPids.Add($pidInt)) {
                    if (Stop-PidTree -ProcessId $pidInt) {
                        $backendStopped = $true
                    }
                }
            }
        }
    }
}

foreach ($item in $states) {
    $state = $item.State
    if ($state.frontendPid) {
        $pidInt = [int]$state.frontendPid
        if ($stoppedFrontendPids.Add($pidInt)) {
            if (Stop-PidTree -ProcessId $pidInt) {
                $frontendStopped = $true
            }
        }
    }
}

foreach ($path in $stateFiles) {
    Remove-Item $path -ErrorAction SilentlyContinue
}

Write-Host "Stop result:"
Write-Host ("Backend stopped: {0}" -f $backendStopped)
Write-Host ("Frontend stopped: {0}" -f $frontendStopped)
Write-Host ("State files checked: {0}" -f (($stateFiles | ForEach-Object { Split-Path $_ -Leaf }) -join ", "))

if (-not $NoPause) {
    Write-Host "Press any key to close this window."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}
