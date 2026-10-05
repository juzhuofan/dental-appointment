. (Join-Path $PSScriptRoot 'Common.ps1')
Import-LocalEnvironment
Push-Location $ProjectRoot
try {
    if (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot 'node_modules\@playwright\test'))) {
        Invoke-CheckedCommand 'npm.cmd' @('ci', '--no-audit', '--no-fund')
    }
    Invoke-CheckedCommand 'npm.cmd' @('run', 'test:api')
    Invoke-CheckedCommand 'npm.cmd' @('run', 'test:ui')
} finally { Pop-Location }
