# 从 Nacos 加载业务配置；只修改当前进程的 JAVA_HOME，不修改 Windows 系统变量。
[CmdletBinding()]
param(
    [ValidateSet('start', 'stop', 'status')]
    [string]$Action = 'start',
    [string[]]$Services = @(),
    [switch]$Build,
    [int]$WaitSeconds = 240,
    [string]$JavaHome = ''
)

$projectRoot = Split-Path -Parent $PSScriptRoot
if ($JavaHome) {
    if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
        throw "Java executable not found under $JavaHome"
    }
    $env:JAVA_HOME = $JavaHome
} else {
    $localJdk = Join-Path $env:USERPROFILE '.jdks/openjdk-26.0.1'
    if (Test-Path -LiteralPath (Join-Path $localJdk 'bin/java.exe')) {
        $env:JAVA_HOME = $localJdk
    }
}

$pythonCommand = Get-Command python -ErrorAction Stop
$launcherArgs = @((Join-Path $projectRoot 'scripts/dev-services.py'), $Action)
if ($Services.Count -gt 0) { $launcherArgs += @('--services') + $Services }
if ($Build) { $launcherArgs += '--build' }
$launcherArgs += @('--wait-seconds', [string]$WaitSeconds)
& $pythonCommand.Source @launcherArgs
exit $LASTEXITCODE
