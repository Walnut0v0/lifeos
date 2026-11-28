"""Audit the isolated synthetic UI app, independently of the production flavors."""
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
module = root / 'android/ui-prototype'
for path in (module / 'src/debug').rglob('*.kt'):
    source = path.read_text(encoding='utf-8')
    for forbidden in ('io.lifeos.focuslab', 'java.net.', 'android.app.usage', 'WorkManager', 'SyncWork',
                      'AuthorizationClient', 'packageManager', 'contentResolver', 'startService(', 'requestPermissions('):
        assert forbidden not in source, (path.name, forbidden)
assert 'project(":app")' not in (module / 'build.gradle.kts').read_text(encoding='utf-8')
assert 'withBuildType("release")' in (module / 'build.gradle.kts').read_text(encoding='utf-8')
assert not (module / 'src/main/java').exists(), 'Prototype source must be debug-only'
manifest = module / 'build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml'
tree = ET.parse(manifest).getroot()
name = '{http://schemas.android.com/apk/res/android}name'
for permission in tree.findall('uses-permission'):
    assert permission.attrib[name].startswith('io.lifeos.uipreview.'), permission.attrib[name]
app = tree.find('application')
assert not app.findall('service'), 'Prototype must not declare services'
assert [x.attrib[name] for x in app.findall('activity')] == ['io.lifeos.uipreview.PrototypeActivity']
assert not list(tree.findall('queries')), 'Prototype must not scan installed apps'
if len(sys.argv) == 3:
    output = subprocess.check_output([sys.argv[2], 'dump', 'permissions', sys.argv[1]], text=True, encoding='utf-8')
    uses = re.findall(r"uses-permission[^\n]*name='([^']+)'", output)
    assert all(x.startswith('io.lifeos.uipreview.') for x in uses), uses
    badging = subprocess.check_output([sys.argv[2], 'dump', 'badging', sys.argv[1]], text=True, encoding='utf-8')
    assert "package: name='io.lifeos.uipreview'" in badging
print('PASS: isolated debug prototype; no system permissions, app queries, services or business calls')
