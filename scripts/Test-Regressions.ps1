param([string]$Filter = 'tests/ui/(ai-chat|login-profile|appointment-detail-race|doctor-leaves).spec.mjs')
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$regressionRoot = Split-Path -Parent $PSScriptRoot
$regressionNode = (Get-Command node.exe -ErrorAction Stop).Source
$regressionProcesses = @()
$regressionExit = 1
$env:LOGIN_UI_BASE_URL = 'http://127.0.0.1:6186'
$env:AI_UI_BASE_URL = $env:LOGIN_UI_BASE_URL
$env:ADMIN_FIX_UI_BASE_URL = 'http://127.0.0.1:6190'
New-Item -ItemType Directory -Path (Join-Path $regressionRoot '.local') -Force | Out-Null
try {
    # Serve the real admin build: Vite's development dependency optimizer can stall
    # module loading between Edge contexts, before the application even starts.
    Push-Location $regressionRoot
    try {
        & $regressionNode 'scripts\with-environment.mjs' admin-web build
        if ($LASTEXITCODE -ne 0) { throw 'Admin build failed.' }
    } finally { Pop-Location }
    # Isolated preview ports and mocked APIs; do not stop or reuse the user's running services.
    $regressionProcesses += Start-Process -FilePath $regressionNode -ArgumentList @(('"{0}"' -f "$regressionRoot\miniapp\node_modules\@dcloudio\vite-plugin-uni\bin\uni.js"),'--host','127.0.0.1','--port','6186','--strictPort') -WorkingDirectory "$regressionRoot\miniapp" -WindowStyle Hidden -PassThru -RedirectStandardOutput "$regressionRoot\.local\regression-miniapp.stdout.log" -RedirectStandardError "$regressionRoot\.local\regression-miniapp.stderr.log"
    $regressionProcesses += Start-Process -FilePath $regressionNode -ArgumentList @(('"{0}"' -f "$regressionRoot\admin-web\node_modules\vite\bin\vite.js"),'preview','--host','127.0.0.1','--port','6190','--strictPort') -WorkingDirectory "$regressionRoot\admin-web" -WindowStyle Hidden -PassThru -RedirectStandardOutput "$regressionRoot\.local\regression-admin.stdout.log" -RedirectStandardError "$regressionRoot\.local\regression-admin.stderr.log"
    foreach ($regressionUrl in @($env:LOGIN_UI_BASE_URL, $env:ADMIN_FIX_UI_BASE_URL)) {
        $regressionReady = $false
        $regressionLastError = 'No response'
        for ($regressionAttempt = 0; $regressionAttempt -lt 40; $regressionAttempt++) {
            if ($regressionProcesses | Where-Object { $_.HasExited }) { throw 'An isolated preview exited. Check .local/regression-*.stderr.log and ensure the test ports are free.' }
            try { if ((Invoke-WebRequest -Uri $regressionUrl -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200) { $regressionReady = $true; break } } catch { $regressionLastError = $_.Exception.Message }
            Start-Sleep -Milliseconds 300
        }
        if (-not $regressionReady) { throw "Isolated preview did not become ready: $regressionUrl ($regressionLastError)" }
    }
    Push-Location $regressionRoot
    try {
        & $regressionNode 'node_modules\@playwright\test\cli.js' test $Filter --reporter=line
        $regressionExit = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    foreach ($regressionProcess in $regressionProcesses) {
        if (-not $regressionProcess.HasExited) { Stop-Process -Id $regressionProcess.Id -Force }
    }
}
exit $regressionExit
