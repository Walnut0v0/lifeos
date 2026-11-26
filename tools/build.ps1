param([string[]]$Tasks = @('testLabDebugUnitTest', 'testOnlineDebugUnitTest', 'lintLabDebug', 'lintOnlineDebug', 'assembleLabDebug', 'assembleOnlineDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
if (!(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { throw 'Install JDK 21 or update tools/build.ps1 JAVA_HOME.' }
$env:GRADLE_USER_HOME = Join-Path $projectRoot 'work\toolchain\gradle-home'
$sdkPath = (Join-Path $projectRoot 'work\toolchain\sdk').Replace('\','/')
Set-Content -LiteralPath (Join-Path $projectRoot 'android\local.properties') -Encoding ascii -Value "sdk.dir=$sdkPath"
& (Join-Path $projectRoot 'work\toolchain\gradle-8.13\bin\gradle.bat') -p (Join-Path $projectRoot 'android') @Tasks --console plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
