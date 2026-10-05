Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot

function Import-LocalEnvironment {
    $configPath = Join-Path $ProjectRoot '.env.local'
    if (-not (Test-Path -LiteralPath $configPath)) {
        throw '请先复制 .env.example 为 .env.local 并填写本地连接配置。'
    }
    foreach ($configLine in [System.IO.File]::ReadAllLines($configPath)) {
        if ([string]::IsNullOrWhiteSpace($configLine) -or $configLine.TrimStart().StartsWith('#')) { continue }
        $configPair = $configLine.Split('=', 2)
        if ($configPair.Count -ne 2 -or $configPair[0] -notmatch '^[A-Z_][A-Z0-9_]*$') { continue }
        [System.Environment]::SetEnvironmentVariable($configPair[0], $configPair[1], 'Process')
    }
    if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        throw 'JAVA_HOME 必须指向 JDK 17 安装目录。'
    }
    $env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
    $env:npm_config_cache = Join-Path $ProjectRoot '.local\npm-cache'
    $env:MAVEN_USER_HOME = Join-Path $ProjectRoot '.local\maven-wrapper'
    $javaInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $javaInfo.FileName = Join-Path $env:JAVA_HOME 'bin\java.exe'
    $javaInfo.Arguments = '-version'
    $javaInfo.UseShellExecute = $false
    $javaInfo.RedirectStandardError = $true
    $javaInfo.CreateNoWindow = $true
    $javaProcess = [System.Diagnostics.Process]::Start($javaInfo)
    $javaVersion = $javaProcess.StandardError.ReadToEnd()
    $javaProcess.WaitForExit()
    $javaProcess.Dispose()
    if ($javaVersion -notmatch 'version "17\.') { throw '项目必须使用 JDK 17。请修改 .env.local 的 JAVA_HOME。' }
    $nodeVersion = & node --version
    if ($nodeVersion.Trim() -ne 'v24.18.0') { throw '项目必须使用 Node.js 24.18.0。请检查 PATH。' }
}

function Resolve-MavenCommand {
    if ($env:MAVEN_HOME) {
        $configuredPath = Join-Path $env:MAVEN_HOME 'bin\mvn.cmd'
        if (Test-Path -LiteralPath $configuredPath) { return $configuredPath }
    }
    $foundMaven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($foundMaven) { return $foundMaven.Source }
    $wrapperPath = Join-Path $ProjectRoot 'backend\mvnw.cmd'
    if (Test-Path -LiteralPath $wrapperPath) { return $wrapperPath }
    throw '未找到 Maven。请在 .env.local 设置 MAVEN_HOME，或使用项目 Maven Wrapper。'
}

function Invoke-CheckedCommand {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "命令执行失败（退出码 $LASTEXITCODE）：$Command" }
}
