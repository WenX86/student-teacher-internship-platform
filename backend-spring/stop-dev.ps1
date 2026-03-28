param()

$ErrorActionPreference = "Stop"

$backendDir = $PSScriptRoot
$logsDir = Join-Path $backendDir "logs"
$stateFile = Join-Path $logsDir "backend-dev.state.json"

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
    Write-Host "No backend-dev.state.json found. Nothing to stop."
    exit 0
}

$state = Get-Content $stateFile -Raw | ConvertFrom-Json
$stopped = $false

if ($state.pid) {
    $stopped = Stop-PidTree -ProcessId ([int]$state.pid)
}

Remove-Item $stateFile -ErrorAction SilentlyContinue

Write-Host "Stop result:"
Write-Host ("Backend stopped: {0}" -f $stopped)



