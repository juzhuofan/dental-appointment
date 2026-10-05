param([switch]$SkipInstall)
. (Join-Path $PSScriptRoot 'Common.ps1')
Import-LocalEnvironment
$statePath = Join-Path $ProjectRoot '.local\running.json'
if (Test-Path -LiteralPath $statePath) {
    $savedProcesses = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    foreach ($savedProcess in $savedProcesses) {
        if ($savedProcess.name -ne 'backend') { continue }
        $runningProcess = Get-Process -Id $savedProcess.pid -ErrorAction SilentlyContinue
        if ($runningProcess -and $runningProcess.StartTime.ToUniversalTime().Ticks -eq [long]$savedProcess.startedAtTicks) {
            throw '后端正在运行，Windows 会锁定 JAR 文件。请先执行 scripts/Stop-Local.ps1，再构建和启动。'
        }
    }
}
$mavenCommand = Resolve-MavenCommand
Push-Location (Join-Path $ProjectRoot 'backend')
try { Invoke-CheckedCommand $mavenCommand @('verify', '-B', '-ntp', ('-Dmaven.repo.local=' + (Join-Path $ProjectRoot '.local\m2'))) } finally { Pop-Location }
foreach ($clientProject in @('admin-web', 'miniapp')) {
    Push-Location (Join-Path $ProjectRoot $clientProject)
    try {
        if (-not $SkipInstall) { Invoke-CheckedCommand 'npm.cmd' @('ci', '--no-audit', '--no-fund') }
        if ($clientProject -eq 'admin-web') { Invoke-CheckedCommand 'npm.cmd' @('run', 'build') }
        else {
            Invoke-CheckedCommand 'npm.cmd' @('run', 'build:mp-weixin')
            Invoke-CheckedCommand 'npm.cmd' @('run', 'build:h5')
        }
    } finally { Pop-Location }
}
Write-Host '构建完成。微信开发者工具请导入 miniapp/dist/build/mp-weixin。'
