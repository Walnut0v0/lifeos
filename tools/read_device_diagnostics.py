"""Read only Focus Lab's debug preferences via an already-authorized ADB device.

Raw preferences stay in memory. Output excludes target packages and unknown fields.
No screen capture, UI tree, global logcat, network endpoint or device identifier.
"""
import argparse
import json
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE = 'io.lifeos.focuslab'


def sanitize_preferences(raw):
    root = ET.fromstring(raw)
    strings = {e.attrib.get('name'): e.text for e in root if e.tag == 'string'}
    result = {k: strings.get(k) for k in ('mode', 'overlay_status')}
    result['usage_assist_opt_in'] = any(e.tag == 'boolean' and e.attrib.get('name') == 'usage_assist_opt_in' and e.attrib.get('value') == 'true' for e in root)
    result['target_count'] = sum(len(e) for e in root if e.attrib.get('name') == 'targets')
    session = json.loads(strings.get('session') or 'null')
    result['session'] = {k: session[k] for k in ('start', 'end', 'manual', 'elapsedEnd', 'boot', 'duration') if k in session} if isinstance(session, dict) else None
    result['events'] = []
    for event in json.loads(strings.get('events') or '[]')[-60:]:
        if not isinstance(event, dict) or not re.fullmatch(r'[A-Z_]{1,90}', str(event.get('code', ''))):
            continue
        clean = {'code': event['code']}
        for key in ('at', 'delta_ms'):
            if type(event.get(key)) is int:
                clean[key] = event[key]
        result['events'].append(clean)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default=shutil.which('adb'))
    parser.add_argument('--device', help='Optional device selector; never included in output')
    parser.add_argument('--watch', type=int, default=0, help='Read changes for 1–60 seconds')
    args = parser.parse_args()
    if not args.adb or not 0 <= args.watch <= 60:
        parser.error('Provide --adb and a watch duration between 0 and 60')
    base = [args.adb] + (['-s', args.device] if args.device else ['-d'])
    deadline = time.monotonic() + args.watch
    previous = None
    seen = set()
    while True:
        read = subprocess.run(base + ['exec-out', 'run-as', PACKAGE, 'cat', 'shared_prefs/lab.xml'], capture_output=True, timeout=10)
        if read.returncode:
            print(json.dumps({'error': 'APP_DIAGNOSTICS_UNAVAILABLE_CHECK_DEVICE_AUTH_AND_DEBUG_APK'}))
            return 1
        try:
            snapshot = sanitize_preferences(read.stdout)
            output = json.dumps(snapshot, ensure_ascii=True)
        except (ET.ParseError, ValueError, TypeError, KeyError):
            print(json.dumps({'error': 'APP_DIAGNOSTICS_INVALID'}))
            return 1
        if output != previous:
            if args.watch:
                current = {(e.get('at'), e['code']) for e in snapshot['events']}
                snapshot['events'] = [e for e in snapshot['events'] if (e.get('at'), e['code']) not in seen][-8:]
                seen = current
                print(json.dumps(snapshot, ensure_ascii=True), flush=True)
            else:
                print(output, flush=True)
            previous = output
        if not args.watch or time.monotonic() >= deadline:
            return 0
        time.sleep(1)


if __name__ == '__main__':
    sys.exit(main())
