"""Configure only our DUMP-protected debug app; no secret in terminal output/history."""
import base64
import json
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
private = root / 'work/private/exchange'
config = json.loads((private / 'config.json').read_text(encoding='utf-8'))
if 'endpoint' not in config or config['endpoint'].endswith('.trycloudflare.com'):
    matches = re.findall(r'https://[a-z0-9-]+\.trycloudflare\.com', (private / 'tunnel.log').read_text(encoding='utf-8'))
    if not matches:
        raise SystemExit('No HTTPS endpoint found; configure a stable endpoint or start a development tunnel.')
    config['endpoint'] = matches[-1]
    (private / 'config.json').write_text(json.dumps(config), encoding='utf-8')
payload = base64.b64encode(json.dumps({'endpoint':config['endpoint'], 'token':config['phone_token']}).encode()).decode()
adb = root / 'work/toolchain/sdk/platform-tools/adb.exe'
command = [str(adb), '-d', 'shell', 'am', 'broadcast', '--user', 'current', '--receiver-foreground', '-a', 'io.lifeos.focuslab.DEBUG', '-n', 'io.lifeos.focuslab/.DebugReceiver', '--es', 'operation', 'online_configure', '--es', 'configuration', payload]
result = subprocess.run(command, capture_output=True, text=True, encoding='utf-8')
if 'ONLINE_CONFIGURED' not in result.stdout:
    raise SystemExit('Configuration not confirmed. Connect an authorized device with the online debug APK installed.')
print('Private HTTPS configuration saved using Android Keystore. Automatic execution and statistics have not been enabled by this command.')
