param([switch]$WithoutPatientPreview)
. (Join-Path $PSScriptRoot 'Common.ps1')
$activeEnvironment = Get-ActiveEnvironment -Component 'backend'
if ($activeEnvironment -ne 'dev') {
    throw 'Start-Local.ps1 仅启动本机开发后端。请将 backend/src/main/resources/application.yml 的 spring.profiles.active 设为 dev。'
}
Import-LocalEnvironment
$runtimeDir = Join-Path $ProjectRoot '.local'
New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
$statePath = Join-Path $runtimeDir 'running.json'
if (Test-Path -LiteralPath $statePath) {
    $previousState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    foreach ($previousProcess in $previousState) {
        $runningProcess = Get-Process -Id $previousProcess.pid -ErrorAction SilentlyContinue
        if ($runningProcess -and $runningProcess.StartTime.ToUniversalTime().Ticks -eq [long]$previousProcess.startedAtTicks) {
            throw '项目进程已运行。请先执行 scripts/Stop-Local.ps1。'
        }
    }
}
$requiredPorts = @([int]$env:SERVER_PORT, 5173)
if (-not $WithoutPatientPreview) { $requiredPorts += 5174 }
foreach ($portNumber in $requiredPorts) {
    $portProbe = [System.Net.Sockets.TcpClient]::new()
    try {
        $pendingProbe = $portProbe.ConnectAsync('127.0.0.1', $portNumber)
        if ($pendingProbe.Wait(500) -and $portProbe.Connected) { throw "端口 $portNumber 已占用，请先确认服务。" }
    } catch [System.AggregateException] {
        # 本机连接被拒绝表示端口空闲。
    } finally { $portProbe.Dispose() }
}
$backendJar = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'backend\target') -Filter '*.jar' |
    Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*javadoc*' } | Select-Object -First 1
if (-not $backendJar) { throw '请先执行 scripts/Build-Local.ps1 构建后端。' }
$viteScript = Join-Path $ProjectRoot 'admin-web\node_modules\vite\bin\vite.js'
if (-not (Test-Path -LiteralPath $viteScript)) { throw '管理端依赖尚未安装，请先构建项目。' }
$patientScript = Join-Path $ProjectRoot 'miniapp\node_modules\@dcloudio\vite-plugin-uni\bin\uni.js'
if (-not $WithoutPatientPreview -and -not (Test-Path -LiteralPath $patientScript)) { throw '小程序依赖尚未安装，请先构建项目。' }
$javaCommand = Join-Path $env:JAVA_HOME 'bin\java.exe'
$nodeCommand = (Get-Command node.exe).Source
$backendProcess = Start-Process -FilePath $javaCommand -ArgumentList @('-jar', ('"' + $backendJar.FullName + '"')) `
    -WorkingDirectory (Join-Path $ProjectRoot 'backend') -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $runtimeDir 'backend.out.log') -RedirectStandardError (Join-Path $runtimeDir 'backend.err.log')
try {
    $adminEnvironment = Get-ActiveEnvironment -Component 'admin-web'
    $adminConfig = Get-FrontendEnvironmentSettings -Component 'admin-web' -Environment $adminEnvironment
    $env:VITE_API_BASE_URL = $adminConfig['VITE_API_BASE_URL']
    $env:VITE_DEV_PROXY_TARGET = $adminConfig['VITE_DEV_PROXY_TARGET']
    $adminProcess = Start-Process -FilePath $nodeCommand -ArgumentList @(('"' + $viteScript + '"'), '--mode', $adminEnvironment, '--host', '127.0.0.1', '--port', '5173', '--strictPort') `
        -WorkingDirectory (Join-Path $ProjectRoot 'admin-web') -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $runtimeDir 'admin.out.log') -RedirectStandardError (Join-Path $runtimeDir 'admin.err.log')
    if (-not $WithoutPatientPreview) {
        $miniappEnvironment = Get-ActiveEnvironment -Component 'miniapp'
        $miniappConfig = Get-FrontendEnvironmentSettings -Component 'miniapp' -Environment $miniappEnvironment
        $env:VITE_API_BASE_URL = $miniappConfig['VITE_API_BASE_URL']
        $patientProcess = Start-Process -FilePath $nodeCommand -ArgumentList @(('"' + $patientScript + '"'), '--mode', $miniappEnvironment) `
            -WorkingDirectory (Join-Path $ProjectRoot 'miniapp') -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $runtimeDir 'patient.out.log') -RedirectStandardError (Join-Path $runtimeDir 'patient.err.log')
    }
} catch {
    Stop-Process -Id $backendProcess.Id -ErrorAction SilentlyContinue
    if (Get-Variable adminProcess -ErrorAction SilentlyContinue) { Stop-Process -Id $adminProcess.Id -ErrorAction SilentlyContinue }
    throw
}
$processState = @(
    @{name='backend'; pid=$backendProcess.Id; startedAtTicks=$backendProcess.StartTime.ToUniversalTime().Ticks},
    @{name='admin'; pid=$adminProcess.Id; startedAtTicks=$adminProcess.StartTime.ToUniversalTime().Ticks}
)
if (-not $WithoutPatientPreview) {
    $processState += @{name='patient-preview'; pid=$patientProcess.Id; startedAtTicks=$patientProcess.StartTime.ToUniversalTime().Ticks}
}
$processState | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
$backendReady = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    if ($backendProcess.HasExited) {
        & (Join-Path $PSScriptRoot 'Stop-Local.ps1')
        throw '后端启动失败，请查看 .local/backend.err.log 和 backend.out.log。'
    }
    try {
        $healthResponse = Invoke-RestMethod -Uri ('http://127.0.0.1:' + $env:SERVER_PORT + '/actuator/health') -TimeoutSec 2
        if ($healthResponse.status -eq 'UP') { $backendReady = $true; break }
    } catch { }
    Start-Sleep -Milliseconds 500
}
if (-not $backendReady) { Write-Warning '后端仍在启动，请查看 .local/backend.out.log。' }
Write-Host ('管理后台：http://127.0.0.1:5173/；后端：http://127.0.0.1:' + $env:SERVER_PORT)
Write-Host ("运行环境：后端 dev；管理端 $adminEnvironment。")
if (-not $WithoutPatientPreview) {
    Write-Host '患者端 H5 预览：http://127.0.0.1:5174/；正式小程序请导入微信开发者工具。'
    Write-Host ("小程序预览环境：$miniappEnvironment。")
}
Write-Host '演示账号密码见 backend/src/main/resources/application-private-dev.yml；停止服务执行 scripts/Stop-Local.ps1。'
