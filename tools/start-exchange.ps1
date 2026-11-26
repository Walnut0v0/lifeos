param([switch]$Tunnel)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$privateDir = Join-Path $projectRoot 'work\private\exchange'
New-Item -ItemType Directory -Force -Path $privateDir | Out-Null
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
$acl = [System.Security.AccessControl.DirectorySecurity]::new()
$acl.SetOwner($identity)
$acl.SetAccessRuleProtection($true, $false)
$acl.AddAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
$acl.AddAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new([System.Security.Principal.SecurityIdentifier]::new('S-1-5-18'), 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
Set-Acl -LiteralPath $privateDir -AclObject $acl
if (Get-NetTCPConnection -LocalPort 8787 -State Listen -ErrorAction SilentlyContinue) { throw 'Port 8787 is already in use; do not start a second exchange server.' }
$pythonPath = (Get-Command python.exe).Source
$serverPath = Join-Path $projectRoot 'bridge\server.py'
$serverArguments = '"' + $serverPath + '" --data-dir "' + $privateDir + '"'
$server = Start-Process -FilePath $pythonPath -ArgumentList $serverArguments -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $privateDir 'server.log') -RedirectStandardError (Join-Path $privateDir 'server-error.log')
Set-Content -LiteralPath (Join-Path $privateDir 'server.pid') -Value $server.Id
if ($Tunnel) {
    $cloudflared = Join-Path $projectRoot 'work\toolchain\cloudflared.exe'
    if (!(Test-Path -LiteralPath $cloudflared)) { throw 'Download cloudflared from the official Cloudflare site first.' }
    $tunnelProcess = Start-Process -FilePath $cloudflared -ArgumentList @('tunnel','--url','http://127.0.0.1:8787','--no-autoupdate') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $privateDir 'tunnel-out.log') -RedirectStandardError (Join-Path $privateDir 'tunnel.log')
    Set-Content -LiteralPath (Join-Path $privateDir 'tunnel.pid') -Value $tunnelProcess.Id
    Write-Output 'Development HTTPS tunnel started. It is temporary, has no uptime guarantee, and requires this PC to remain on.'
} else {
    Write-Output 'Private exchange started on loopback port 8787. Use a trusted HTTPS reverse proxy for a phone connection.'
}
Write-Output 'Credentials and process records stay in work/private/exchange; never commit or share them publicly.'
