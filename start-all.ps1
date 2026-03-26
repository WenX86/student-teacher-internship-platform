param(
    [switch]$OpenBrowser,
    [switch]$NoPause
)

$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$backendDir = Join-Path $root "backend-spring"
$frontendDir = Join-Path $root "frontend"
$backendJar = Join-Path $backendDir "target\internship-platform-backend-0.0.1-SNAPSHOT.jar"
$logsDir = Join-Path $root "logs"
$backendStdout = Join-Path $logsDir "backend.stdout.log"
$backendStderr = Join-Path $logsDir "backend.stderr.log"
$frontendStdout = Join-Path $logsDir "frontend.stdout.log"
$frontendStderr = Join-Path $logsDir "frontend.stderr.log"
$stateFile = Join-Path $logsDir "start-all.state.json"

function Resolve-ToolPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CommandName,
        [string[]]$FallbackPaths = @()
    )

    $command = Get-Command $CommandName -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    foreach ($fallback in $FallbackPaths) {
        if ($fallback -and (Test-Path $fallback)) {
            return $fallback
        }
    }

    return $null
}

function Test-PortListening {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    try {
        return @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction Stop).Count -gt 0
    } catch {
        return [bool](netstat -ano | Select-String -Pattern ":$Port\s+.*LISTENING")
    }
}

function Wait-PortListening {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port,
        [int]$TimeoutSeconds = 60
    )

    for ($i = 0; $i -lt ($TimeoutSeconds * 2); $i++) {
        if (Test-PortListening -Port $Port) {
            return $true
        }
        Start-Sleep -Milliseconds 500
    }

    return $false
}

if (-not (Test-Path $backendJar)) {
    throw "Backend JAR not found: $backendJar. Please build backend-spring first."
}

$javaPath = Resolve-ToolPath -CommandName "java.exe" -FallbackPaths @(
    (Join-Path $env:JAVA_HOME "bin\java.exe"),
    "C:\Program Files\Java\jdk-21\bin\java.exe",
    "D:\Web\Java\jdk-21\bin\java.exe"
)

if (-not $javaPath) {
    throw "java.exe not found. Please install JDK 21 and ensure JAVA_HOME / PATH are set."
}

$npmPath = Resolve-ToolPath -CommandName "npm.cmd" -FallbackPaths @(
    "C:\Program Files\nodejs\npm.cmd"
)

if (-not $npmPath) {
    throw "npm.cmd not found. Please install Node.js and ensure PATH is set."
}

New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
Remove-Item $backendStdout, $backendStderr, $frontendStdout, $frontendStderr, $stateFile -ErrorAction SilentlyContinue

# Frontend reads VITE_API_BASE at startup, so set it before spawning Vite.
$env:VITE_API_BASE = "http://localhost:8080/api"
$nodeDir = Split-Path -Parent $npmPath
if ($nodeDir -and -not ($env:Path -split ";" | Where-Object { $_ -eq $nodeDir })) {
    $env:Path = "$nodeDir;$env:Path"
}

Write-Host "Starting backend..."
$backendProc = Start-Process `
    -FilePath $javaPath `
    -ArgumentList @("-jar", $backendJar) `
    -WorkingDirectory $backendDir `
    -PassThru `
    -WindowStyle Hidden `
    -RedirectStandardOutput $backendStdout `
    -RedirectStandardError $backendStderr

Write-Host "Starting frontend..."
$frontendProc = Start-Process `
    -FilePath $npmPath `
    -ArgumentList @("run", "dev", "--", "--host", "0.0.0.0") `
    -WorkingDirectory $frontendDir `
    -PassThru `
    -WindowStyle Hidden `
    -RedirectStandardOutput $frontendStdout `
    -RedirectStandardError $frontendStderr

$backendReady = Wait-PortListening -Port 8080 -TimeoutSeconds 60
$frontendReady = Wait-PortListening -Port 5173 -TimeoutSeconds 60

Write-Host ""
Write-Host "Startup result:"

if ($backendReady) {
    Write-Host "Backend: http://localhost:8080"
} else {
    Write-Host "Backend: port 8080 not confirmed. See $backendStdout and $backendStderr"
}

if ($frontendReady) {
    Write-Host "Frontend: http://localhost:5173"
} else {
    Write-Host "Frontend: port 5173 not confirmed. See $frontendStdout and $frontendStderr"
}

Write-Host ("Backend PID: {0}" -f $backendProc.Id)
Write-Host ("Frontend PID: {0}" -f $frontendProc.Id)
Write-Host ("Logs: {0}" -f $logsDir)

$state = [ordered]@{
    backendPid = $backendProc.Id
    frontendPid = $frontendProc.Id
    backendPort = 8080
    frontendPort = 5173
    startedAt = (Get-Date).ToString("o")
}
$state | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 $stateFile

if ($OpenBrowser -and $frontendReady) {
    Start-Process "http://localhost:5173"
}

if (-not $NoPause) {
    Write-Host "Press any key to close this window."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}
