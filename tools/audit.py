"""Fail closed on unexpected merged/APK permissions and prohibited source APIs."""
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ALLOWED = {
    'android.permission.RECEIVE_BOOT_COMPLETED',
    'android.permission.SCHEDULE_EXACT_ALARM',
    'android.permission.POST_NOTIFICATIONS',
    'android.permission.PACKAGE_USAGE_STATS',
    'android.permission.SYSTEM_ALERT_WINDOW',
    'android.permission.FOREGROUND_SERVICE',
    'io.lifeos.focuslab.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION',
}
ns = '{http://schemas.android.com/apk/res/android}'
def check_manifest(path):
    root = ET.parse(path).getroot()
    permissions = {x.attrib[ns + 'name'] for x in root.findall('uses-permission')}
    assert permissions <= ALLOWED, permissions - ALLOWED
    assert not any(x.endswith('INTERNET') for x in permissions)
    print('Manifest permission audit PASS:', sorted(permissions))

check_manifest(ROOT / 'android/app/src/main/AndroidManifest.xml')
config = ET.parse(ROOT / 'android/app/src/main/res/xml/accessibility_service.xml').getroot()
for name in ('canRetrieveWindowContent', 'canPerformGestures', 'canTakeScreenshot'):
    assert config.attrib[ns + name] == 'false'
assert config.attrib[ns + 'accessibilityEventTypes'] == 'typeWindowStateChanged'
debug = ET.parse(ROOT / 'android/app/src/debug/AndroidManifest.xml').getroot()
receiver = debug.find('application/receiver')
assert receiver.attrib[ns + 'permission'] == 'android.permission.DUMP'
assert receiver.attrib[ns + 'name'] == '.DebugReceiver'
assert not any(x.attrib.get(ns + 'name') == '.DebugReceiver' for x in ET.parse(ROOT / 'android/app/src/main/AndroidManifest.xml').getroot().findall('application/receiver'))
for source in (ROOT / 'android/app/src/main/java').rglob('*.kt'):
    text = source.read_text(encoding='utf-8')
    assert not re.search(r'\b(getRootInActiveWindow|rootInActiveWindow|takeScreenshot|dispatchGesture)\b|event\??\.(text|source)\b', text), source
if len(sys.argv) == 3:
    apk, aapt = sys.argv[1:]
    result = subprocess.check_output([aapt, 'dump', 'permissions', apk], text=True)
    permissions = set(re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", result))
    assert permissions and permissions <= ALLOWED, result
    print('APK permission audit PASS:', sorted(permissions))
elif len(sys.argv) == 2:
    check_manifest(sys.argv[1])
print('Accessibility/source privacy audit PASS')
