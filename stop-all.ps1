param(
    [switch]$NoPause
)

$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$stateFile = Join-Path $root "logs\start-all.state.json"
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

if (-not (Test-Path $stateFile)) {
    Write-Host "No start-all.state.json found. Nothing to stop."
    if (-not $NoPause) {
        Write-Host "Press any key to close this window."
        $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    }
    exit 0
}

$state = Get-Content $stateFile -Raw | ConvertFrom-Json
$backendStopped = $false
$frontendStopped = $false

if (Test-Path $backendStopScript) {
    try {
        & "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" -ExecutionPolicy Bypass -File $backendStopScript | Out-Null
        $backendStopped = $true
    } catch {
        $backendStopped = $false
    }
}

if (-not $backendStopped -and $state.backendPid) {
    $backendStopped = Stop-PidTree -ProcessId ([int]$state.backendPid)
}

if (-not $backendStopped -and $state.backendLauncherPid) {
    $backendStopped = Stop-PidTree -ProcessId ([int]$state.backendLauncherPid)
}

if ($state.frontendPid) {
    $frontendStopped = Stop-PidTree -ProcessId ([int]$state.frontendPid)
}

Remove-Item $stateFile -ErrorAction SilentlyContinue

Write-Host "Stop result:"
Write-Host ("Backend stopped: {0}" -f $backendStopped)
Write-Host ("Frontend stopped: {0}" -f $frontendStopped)

if (-not $NoPause) {
    Write-Host "Press any key to close this window."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}

