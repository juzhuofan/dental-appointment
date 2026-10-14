param(
    [string]$MySqlHome = 'D:\itApps\MySQL',
    [string]$JavaHome = 'C:\jdk\jdk17',
    [string]$Maven = 'D:\itApps\maven\apache-maven-3.10.0\bin\mvn.cmd',
    [string]$ByteBuddyAgent = 'D:\itApps\.m2\repository\net\bytebuddy\byte-buddy-agent\1.17.8\byte-buddy-agent-1.17.8.jar'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$weeklyRoot = Split-Path -Parent $PSScriptRoot
$weeklyPort = 13316
if (Get-NetTCPConnection -State Listen -LocalPort $weeklyPort -ErrorAction SilentlyContinue) {
    throw 'Dedicated MySQL test port 13316 is already in use. No existing server will be reused or stopped.'
}
$weeklyRun = Join-Path $weeklyRoot ('.local\weekly-mysql-' + [Guid]::NewGuid().ToString('N'))
$weeklyData = Join-Path $weeklyRun 'data'
New-Item -ItemType Directory -Path $weeklyData -Force | Out-Null
$weeklyServer = Join-Path $MySqlHome 'bin\mysqld.exe'
$weeklyClient = Join-Path $MySqlHome 'bin\mysql.exe'
$weeklyProcess = $null
$weeklyWorkerId = $null
$weeklyExit = 1
try {
    # A new disposable datadir. Never read installed MySQL configuration or use business data.
    & $weeklyServer --no-defaults --initialize-insecure "--basedir=$MySqlHome" "--datadir=$weeklyData" "--log-error=$weeklyRun\initialize.log" --innodb-buffer-pool-size=64M
    if ($LASTEXITCODE -ne 0) { throw "Disposable MySQL initialization failed. See $weeklyRun" }
    $weeklyArgs = @('--no-defaults', ('--basedir="{0}"' -f $MySqlHome), ('--datadir="{0}"' -f $weeklyData),
        '--port=13316', '--bind-address=127.0.0.1', '--mysqlx=0', '--skip-log-bin',
        '--innodb-buffer-pool-size=64M', '--max-connections=20', '--performance-schema=OFF',
        ('--log-error="{0}\server.log"' -f $weeklyRun))
    $weeklyProcess = Start-Process -FilePath $weeklyServer -ArgumentList $weeklyArgs -WindowStyle Hidden -PassThru
    $weeklyReady = $false
    for ($weeklyAttempt=0; $weeklyAttempt -lt 60; $weeklyAttempt++) {
        if ($weeklyProcess.HasExited) { throw "Disposable MySQL exited. See $weeklyRun\server.log" }
        $weeklyListening = Get-NetTCPConnection -State Listen -LocalPort $weeklyPort -ErrorAction SilentlyContinue
        if ($weeklyListening) {
            # MySQL on Windows may fork a monitored child. Verify both ancestry and exact datadir.
            $weeklyWorker = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $weeklyListening.OwningProcess)
            if (-not $weeklyWorker -or $weeklyWorker.ExecutablePath -ne $weeklyServer -or
                -not $weeklyWorker.CommandLine.Contains($weeklyData) -or
                ($weeklyWorker.ProcessId -ne $weeklyProcess.Id -and $weeklyWorker.ParentProcessId -ne $weeklyProcess.Id)) {
                throw 'Unexpected owner of test port 13316.'
            }
            $weeklyWorkerId = $weeklyWorker.ProcessId
            $weeklyReady = $true
            break
        }
        Start-Sleep -Milliseconds 300
    }
    if (-not $weeklyReady) { throw 'Disposable MySQL did not become ready.' }
    & $weeklyClient --no-defaults --host=127.0.0.1 --port=13316 --user=root --execute='CREATE DATABASE dental_weekly_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create dedicated test database.' }
    $env:JAVA_HOME = $JavaHome
    $env:Path = (Join-Path $JavaHome 'bin') + ';' + $env:Path
    $env:MAVEN_OPTS = '-Xms24m -Xmx192m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC'
    $weeklyVm = '-Xms24m -Xmx192m -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC -javaagent:' + $ByteBuddyAgent
    Push-Location (Join-Path $weeklyRoot 'backend')
    try {
        & $Maven -o '-Dweekly.database=true' '-Dai.live=false' '-Dai.database=false' '-Dtest=WeeklyScheduleDatabaseTest' "-DargLine=$weeklyVm" test -B -ntp
        $weeklyExit = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    # Stop only the disposable server created above. Retain its datadir/logs for diagnosis.
    if ($weeklyWorkerId) {
        $weeklyWorker = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $weeklyWorkerId)
        if ($weeklyWorker -and $weeklyWorker.ExecutablePath -eq $weeklyServer -and $weeklyWorker.CommandLine.Contains($weeklyData)) {
            Stop-Process -Id $weeklyWorkerId -Force
        }
    }
    if ($weeklyProcess -and -not $weeklyProcess.HasExited) { Stop-Process -Id $weeklyProcess.Id -Force }
}
exit $weeklyExit
