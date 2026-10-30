$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "未找到 docker，请先安装 Docker Desktop"
    exit 1
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker daemon 未运行，请先启动 Docker Desktop"
    exit 1
}

$mysql = New-Object System.Net.Sockets.TcpClient
try {
    $mysql.Connect("127.0.0.1", 3306)
} catch {
    Write-Error "本地 MySQL 未启动或 3306 端口不可用，请先启动 MySQL 服务"
    exit 1
} finally {
    $mysql.Dispose()
}

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    $bytes = New-Object byte[] 64
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $secret = [Convert]::ToBase64String($bytes)
    (Get-Content ".env") -replace '^JWT_SECRET=.*$', "JWT_SECRET=$secret" |
        Set-Content ".env" -Encoding ascii
    Write-Host "已生成 .env（含随机 JWT_SECRET）"
}

docker compose up -d --build
if ($LASTEXITCODE -ne 0) {
    Write-Error "docker compose 启动失败"
    exit 1
}

$port = (Select-String -Path ".env" -Pattern '^OFFERPILOT_HTTP_PORT=(.*)$').Matches[0].Groups[1].Value.Trim()
if (-not $port) { $port = "8088" }

Write-Host "等待后端健康检查..."
$backendHealthy = $false
for ($i = 0; $i -lt 60; $i++) {
    $status = docker inspect --format "{{.State.Health.Status}}" offerpilot-backend 2>$null
    if ($status -eq "healthy") {
        $backendHealthy = $true
        break
    }
    Start-Sleep -Seconds 2
}
if (-not $backendHealthy) {
    Write-Error "后端未在 120 秒内健康，请执行 docker compose logs backend 查看日志"
    exit 1
}

Write-Host "等待前端页面..."
for ($i = 0; $i -lt 30; $i++) {
    try {
        Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:$port/" -TimeoutSec 3 | Out-Null
        Write-Host "OfferPilot 已启动：http://localhost:$port"
        exit 0
    } catch {
        Start-Sleep -Seconds 2
    }
}

Write-Error "前端页面未在 60 秒内可访问，请执行 docker compose logs frontend 查看日志"
exit 1
