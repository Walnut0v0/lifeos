$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
$privateDir = Join-Path $projectRoot 'work\private\exchange'
foreach ($role in @('server','tunnel')) {
    $pidPath = Join-Path $privateDir ($role + '.pid')
    if (!(Test-Path -LiteralPath $pidPath)) { continue }
    $taskProcessId = [int](Get-Content -LiteralPath $pidPath)
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$taskProcessId"
    if ($null -ne $process) {
        $expectedPath = if ($role -eq 'server') { Join-Path $projectRoot 'bridge\server.py' } else { Join-Path $projectRoot 'work\toolchain\cloudflared.exe' }
        if (!$process.CommandLine.Contains($expectedPath)) { throw 'Process identity changed; refusing to stop unrelated process.' }
        Stop-Process -Id $taskProcessId
    }
    Remove-Item -LiteralPath $pidPath
}
Write-Output 'Stopped the project exchange and tunnel processes.'
