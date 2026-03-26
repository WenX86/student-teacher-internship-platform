param(
    [switch]$NoPause
)

$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$stateFile = Join-Path $root "logs\start-all.state.json"

function Stop-PidTree {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Pid
    )

    try {
        & taskkill.exe /PID $Pid /T /F | Out-Null
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

if ($state.backendPid) {
    $backendStopped = Stop-PidTree -Pid [int]$state.backendPid
}

if ($state.frontendPid) {
    $frontendStopped = Stop-PidTree -Pid [int]$state.frontendPid
}

Remove-Item $stateFile -ErrorAction SilentlyContinue

Write-Host "Stop result:"
Write-Host ("Backend stopped: {0}" -f $backendStopped)
Write-Host ("Frontend stopped: {0}" -f $frontendStopped)

if (-not $NoPause) {
    Write-Host "Press any key to close this window."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}
