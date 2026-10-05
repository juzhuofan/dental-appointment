. (Join-Path $PSScriptRoot 'Common.ps1')
$statePath = Join-Path $ProjectRoot '.local\running.json'
if (-not (Test-Path -LiteralPath $statePath)) { Write-Host '没有项目进程记录。'; return }
$processState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
foreach ($savedProcess in $processState) {
    $runningProcess = Get-Process -Id $savedProcess.pid -ErrorAction SilentlyContinue
    if ($runningProcess -and $runningProcess.StartTime.ToUniversalTime().Ticks -eq [long]$savedProcess.startedAtTicks) {
        Stop-Process -Id $savedProcess.pid
        Write-Host ('已停止：' + $savedProcess.name)
    }
}
Remove-Item -LiteralPath $statePath
