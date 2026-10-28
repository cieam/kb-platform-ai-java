<#
.SYNOPSIS
    一键启动 kb-platform（单体 AI 知识库问答系统）。

.DESCRIPTION
    · 数据库默认连本机 MySQL（localhost:3306 / root / 123456），库名 kb_platform。
      先执行一次建库脚本（幂等，可重复跑）：
        & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -uroot -p123456 `
            --default-character-set=utf8mb4 -e "source <本目录>/sql/init.sql"
    · Redis 和 PostgreSQL 用容器起（mysql 服务默认不启动，避免和本机 MySQL 抢 3306）。
    · 默认端口 8085：8080 留给微服务项目的网关，两个项目可以同时跑。

.EXAMPLE
    .\start-local.ps1
    .\start-local.ps1 -Port 8080
    .\start-local.ps1 -SkipBuild
    .\start-local.ps1 -SkipMiddleware
    .\start-local.ps1 -Stop
#>
param(
    [int]$Port = 8085,
    [string]$MysqlHost = 'localhost',
    [string]$MysqlPort = '3306',
    [string]$MysqlUser = 'root',
    [string]$MysqlPassword = '123456',
    [string]$RedisPort = '16380',
    [string]$RedisPassword = 'redis123',
    [string]$PostgresPort = '15432',
    [switch]$SkipMiddleware,
    [switch]$SkipBuild,
    [switch]$Stop
)

# 注意：这里必须是 Continue 而不是 Stop。
# PowerShell 5.1 下 $ErrorActionPreference='Stop' 会因为原生命令（docker / mvn）
# 往 stderr 写进度信息而抛出 NativeCommandError，把正常输出当成致命错误。
# 所以对原生命令改成 Continue，靠 $LASTEXITCODE 判断成败。
$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$logs = Join-Path $root 'run-logs'
$dockerBin = 'C:\Users\' + $env:USERNAME + '\AppData\Local\Programs\DockerDesktop\resources\bin'
if (Test-Path $dockerBin) { $env:PATH = "$dockerBin;$env:PATH" }


if ($Stop) {
    Write-Host "停止 $Port 端口的应用…" -ForegroundColor Yellow
    Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
    Write-Host '停止 Redis / PostgreSQL 容器…' -ForegroundColor Yellow
    & docker compose -f (Join-Path $root 'docker-compose.yml') stop 2>&1 | Out-Host
    Write-Host '已停止。' -ForegroundColor Green
    return
}

if (-not $SkipMiddleware) {
    Write-Host '=== 1/3 启动 Redis 与 PostgreSQL ===' -ForegroundColor Cyan
    $env:REDIS_PORT = $RedisPort
    $env:POSTGRES_PORT = $PostgresPort
    $env:MYSQL_PORT = $MysqlPort
    & docker compose -f (Join-Path $root 'docker-compose.yml') up -d redis postgres 2>&1 | Out-Host
    Start-Sleep -Seconds 12
    & docker compose -f (Join-Path $root 'docker-compose.yml') ps --format '{{.Name}}  {{.Status}}' 2>&1 | Out-Host
} else {
    Write-Host '=== 1/3 跳过中间件 ===' -ForegroundColor DarkGray
}

if (-not $SkipBuild) {
    Write-Host '=== 2/3 打包 ===' -ForegroundColor Cyan
    & mvn -B -q package -DskipTests -f (Join-Path $root 'pom.xml') 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Maven 打包失败' }
}

Write-Host "=== 3/3 启动应用（端口 $Port）===" -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $logs | Out-Null
Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 2

$env:MYSQL_HOST = $MysqlHost
$env:MYSQL_PORT = $MysqlPort
$env:MYSQL_USER = $MysqlUser
$env:MYSQL_PASSWORD = $MysqlPassword
$env:REDIS_HOST = 'localhost'
$env:REDIS_PORT = $RedisPort
$env:REDIS_PASSWORD = $RedisPassword
$env:POSTGRES_HOST = 'localhost'
$env:POSTGRES_PORT = $PostgresPort

$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$jar = Join-Path $root 'target\kb-platform-1.0.0.jar'
if (-not (Test-Path $jar)) { throw "找不到 $jar" }

Start-Process -FilePath $java `
    -ArgumentList '-Xmx320m', '-jar', "`"$jar`"", "--server.port=$Port" `
    -WorkingDirectory $root `
    -RedirectStandardOutput (Join-Path $logs 'kb-platform.out.log') `
    -RedirectStandardError (Join-Path $logs 'kb-platform.err.log') `
    -WindowStyle Hidden

Write-Host '等待应用就绪…' -ForegroundColor Cyan
$ready = $false
foreach ($i in 1..40) {
    Start-Sleep -Seconds 3
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:$Port/" -UseBasicParsing -TimeoutSec 5
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch { }
}
if (-not $ready) { throw "应用没在 http://localhost:$Port 上就绪，看 run-logs\kb-platform.out.log" }

Write-Host ''
Write-Host '=== kb-platform 启动完成 ===' -ForegroundColor Green
Write-Host "  前端页面     http://localhost:$Port/"
Write-Host "  接口文档     http://localhost:$Port/doc.html"
Write-Host '  演示账号     admin / 123456'
Write-Host "  日志         $logs"
Write-Host ''
Write-Host '停止：.\start-local.ps1 -Stop' -ForegroundColor DarkGray
