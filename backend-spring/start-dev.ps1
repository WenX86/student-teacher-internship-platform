param(
    [switch]$UseMySql,
    [switch]$Detached
)

$ErrorActionPreference = "Stop"

function Resolve-CommandPath {
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

$backendDir = $PSScriptRoot
$projectRoot = Split-Path -Parent $backendDir
$logsDir = Join-Path $backendDir "logs"
$stateFile = Join-Path $logsDir "backend-dev.state.json"
$stdoutLog = Join-Path $logsDir "backend-dev.stdout.log"
$stderrLog = Join-Path $logsDir "backend-dev.stderr.log"
$mavenPath = Resolve-CommandPath -CommandName "mvn.cmd" -FallbackPaths @(
    (Join-Path $projectRoot "tools\apache-maven-3.9.11\bin\mvn.cmd")
)

if (-not $mavenPath) {
    throw "未找到 mvn.cmd，请先安装 Maven 或确认 tools/apache-maven-3.9.11 已存在。"
}

$tempRoot = if ($env:TEMP) { $env:TEMP } else { "C:\Temp" }
if (-not (Test-Path $tempRoot)) {
    New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
}

New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

$junctionPath = Join-Path $tempRoot "internship-platform-backend-spring"

if (Test-Path $junctionPath) {
    try {
        $item = Get-Item -LiteralPath $junctionPath -Force
        if ($item.LinkType -eq "Junction") {
            $resolved = [System.IO.Path]::GetFullPath($item.Target)
            $expected = [System.IO.Path]::GetFullPath($backendDir)
            if ($resolved -ne $expected) {
                Remove-Item -LiteralPath $junctionPath -Force -Recurse
            }
        } else {
            throw "路径 $junctionPath 已存在且不是 junction，请手动处理后重试。"
        }
    } catch {
        Remove-Item -LiteralPath $junctionPath -Force -Recurse -ErrorAction Stop
    }
}

if (-not (Test-Path $junctionPath)) {
    New-Item -ItemType Junction -Path $junctionPath -Target $backendDir | Out-Null
}

$mavenArgs = @("spring-boot:run", "-DskipTests")
if ($UseMySql) {
    $mavenArgs += "-Dspring-boot.run.arguments=--spring.profiles.active=dev-mysql"
}

Write-Host "Using backend source:" $backendDir
Write-Host "Using ASCII junction :" $junctionPath
Write-Host "Running command      : mvn spring-boot:run -DskipTests" -NoNewline
if ($UseMySql) {
    Write-Host " --spring.profiles.active=dev-mysql"
} else {
    Write-Host ""
}

if ($Detached) {
    Remove-Item $stdoutLog, $stderrLog, $stateFile -Force -ErrorAction SilentlyContinue

    $proc = Start-Process `
        -FilePath $mavenPath `
        -ArgumentList $mavenArgs `
        -WorkingDirectory $junctionPath `
        -PassThru `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog

    $ready = Wait-PortListening -Port 8080 -TimeoutSeconds 60
    $state = [ordered]@{
        pid = $proc.Id
        port = 8080
        useMySql = [bool]$UseMySql
        backendDir = $backendDir
        junctionPath = $junctionPath
        startedAt = (Get-Date).ToString("o")
    }
    $state | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 $stateFile

    Write-Host ""
    if ($ready) {
        Write-Host "Backend: http://localhost:8080"
    } else {
        Write-Host "Backend port 8080 not confirmed. See $stdoutLog and $stderrLog"
    }
    Write-Host ("Backend PID: {0}" -f $proc.Id)
    Write-Host ("Logs: {0}" -f $logsDir)
    exit 0
}

Push-Location $junctionPath
try {
    & $mavenPath @mavenArgs
} finally {
    Pop-Location
}
