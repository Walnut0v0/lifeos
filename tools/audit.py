"""Fail closed on unexpected merged/APK permissions and prohibited source APIs."""
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--online', action='store_true')
parser.add_argument('target', nargs='?')
parser.add_argument('aapt', nargs='?')
args = parser.parse_args()

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
ONLINE_ALLOWED = ALLOWED | {'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE', 'android.permission.WAKE_LOCK'}
def check_manifest(path, online=False):
    root = ET.parse(path).getroot()
    permissions = {x.attrib[ns + 'name'] for x in root.findall('uses-permission')}
    allowed = ONLINE_ALLOWED if online else ALLOWED
    assert permissions <= allowed, permissions - allowed
    if online:
        assert 'android.permission.INTERNET' in permissions
        assert root.find('application').attrib[ns + 'usesCleartextTraffic'] == 'false'
    else:
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
for source in (ROOT / 'android/app/src').rglob('*.kt'):
    text = source.read_text(encoding='utf-8')
    assert not re.search(r'\b(getRootInActiveWindow|rootInActiveWindow|takeScreenshot|dispatchGesture)\b|event\??\.(text|source)\b', text), source
if args.aapt:
    apk, aapt = args.target, args.aapt
    result = subprocess.check_output([aapt, 'dump', 'permissions', apk], text=True)
    permissions = set(re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", result))
    assert permissions and permissions <= (ONLINE_ALLOWED if args.online else ALLOWED), result
    assert ('android.permission.INTERNET' in permissions) == args.online
    print('APK permission audit PASS:', sorted(permissions))
elif args.target:
    check_manifest(args.target, args.online)
print('Accessibility/source privacy audit PASS')
