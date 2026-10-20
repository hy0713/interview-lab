param([switch]$Build)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
Set-Location $workspace
$envFile = Join-Path $workspace '.env'
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in [IO.File]::ReadAllLines($envFile)) {
        if ($line -match '^([A-Z_]+)=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
        }
    }
}
if ($env:BACKEND_PORT) { $env:SERVER_PORT = $env:BACKEND_PORT }
if ($Build) {
    & mvn.cmd -f ai-mock-interview/pom.xml -B -ntp package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw '后端打包失败' }
}
$jar = Join-Path $workspace 'ai-mock-interview/target/ai-mock-interview-2.0.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw '未找到 JAR，请先执行 scripts/run-backend.ps1 -Build' }
& java '-Dfile.encoding=UTF-8' '-Duser.timezone=Asia/Shanghai' -jar $jar
exit $LASTEXITCODE
