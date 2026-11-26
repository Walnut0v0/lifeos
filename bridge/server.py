"""Private exchange development server. Put behind authenticated HTTPS; bind loopback only.

No OpenAI key, telemetry, public plan URL, or request/body logging. Phone and planner
use separate random credentials. Runtime files belong in ignored work/private/.
"""
import argparse
import datetime as dt
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import threading
from urllib.parse import urlsplit, parse_qs
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from zoneinfo import ZoneInfo

MAX_BODY = 262144
UTC = dt.timezone.utc


def instant(value):
    if not isinstance(value, str):
        raise ValueError('Timestamp must be a string')
    result = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
    if result.tzinfo is None:
        raise ValueError('Offset required')
    return result


def validate_plan(plan):
    if type(plan['schema_version']) is not int or plan['schema_version'] != 1 or type(plan['revision']) is not int or not 1 <= plan['revision'] <= 2**63-1:
        raise ValueError('Invalid schema/revision')
    generated, expiry = instant(plan['generated_at']), instant(plan['valid_until'])
    if generated.timestamp() <= 0 or generated > dt.datetime.now(UTC) + dt.timedelta(minutes=5):
        raise ValueError('Invalid generated time')
    if expiry <= dt.datetime.now(UTC) or not 0 < (expiry - generated).total_seconds() <= 8 * 86400:
        raise ValueError('Invalid validity')
    ZoneInfo(plan['timezone'])
    tasks = plan['tasks']
    if not isinstance(tasks, list) or len(tasks) > 512:
        raise ValueError('Invalid tasks')
    import re
    end, ids = None, set()
    for task in sorted(tasks, key=lambda x: instant(x['start_at'])):
        start, stop = instant(task['start_at']), instant(task['end_at'])
        if not re.fullmatch(r'[A-Za-z0-9_.-]{1,80}', task['task_id']) or task['task_id'] in ids:
            raise ValueError('Invalid ID')
        if not isinstance(task.get('title', ''), str) or not isinstance(task['focus_mode'], str) or len(task.get('title', '')) > 120 or not 1 <= len(task['focus_mode']) <= 40:
            raise ValueError('Invalid task fields')
        if not 0 < (stop - start).total_seconds() <= 86400 or stop > expiry or start < generated - dt.timedelta(days=8):
            raise ValueError('Invalid interval')
        if end is not None and start < end:
            raise ValueError('Overlap')
        end = stop
        ids.add(task['task_id'])
    return plan


def atomic_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix('.tmp')
    with tmp.open('w', encoding='utf-8') as stream:
        json.dump(data, stream, ensure_ascii=False, separators=(',', ':'))
        stream.flush()
        os.fsync(stream.fileno())
    tmp.replace(path)


def initialize(root):
    root.mkdir(parents=True, exist_ok=True)
    config_path = root / 'config.json'
    if not config_path.exists():
        atomic_write(config_path, {'phone_token': secrets.token_urlsafe(32), 'planner_token': secrets.token_urlsafe(32)})
    if not (root / 'plan.json').exists():
        now = dt.datetime.now(UTC)
        atomic_write(root / 'plan.json', {'schema_version': 1, 'revision': 1, 'generated_at': now.isoformat(),
                     'timezone': 'Asia/Manila', 'valid_until': (now + dt.timedelta(days=2)).isoformat(), 'tasks': []})
    return json.loads(config_path.read_text(encoding='utf-8'))


class ExchangeServer(ThreadingHTTPServer):
    def __init__(self, address, root):
        self.root = Path(root)
        self.credentials = initialize(self.root)
        self.lock = threading.Lock()
        self.last_expiry = 0
        super().__init__(address, Handler)

    def service_actions(self):
        import time
        if time.monotonic() - self.last_expiry < 60:
            return
        self.last_expiry = time.monotonic()
        with self.lock:
            for path in (self.root / 'usage').glob('*.json'):
                data = json.loads(path.read_text(encoding='utf-8'))
                today = dt.datetime.now(ZoneInfo(data['timezone'])).date()
                days = data.get('retention_days', 7)
                cutoff = today - dt.timedelta(days=days-1)
                if dt.date.fromisoformat(data['date']) < cutoff:
                    for folder in ('usage', 'intervals', 'interval-staging'):
                        (self.root / folder / path.name).unlink(missing_ok=True)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def role(self):
        bearer = self.headers.get('Authorization', '')
        if not bearer.startswith('Bearer '):
            return None
        token = bearer[7:]
        for role in ('phone', 'planner'):
            if hmac.compare_digest(token.encode(), self.server.credentials[f'{role}_token'].encode()):
                return role
        return None

    def reply(self, code, data):
        raw = json.dumps(data, ensure_ascii=False, separators=(',', ':')).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def body(self):
        size = int(self.headers.get('Content-Length', '0'))
        if not 0 < size <= MAX_BODY:
            raise ValueError('Invalid size')
        self.connection.settimeout(15)
        return json.loads(self.rfile.read(size))

    def do_GET(self):
        role = self.role()
        if role is None:
            return self.reply(401, {'error': 'unauthorized'})
        if self.path == '/v1/plan':
            with self.server.lock:
                return self.reply(200, json.loads((self.server.root / 'plan.json').read_text(encoding='utf-8')))
        parsed = urlsplit(self.path)
        query = parse_qs(parsed.query)
        if parsed.path == '/v1/usage' and role == 'planner':
            with self.server.lock:
                values = [json.loads(p.read_text(encoding='utf-8')) for p in sorted((self.server.root / 'usage').glob('*.json'))]
                date = query.get('date', [None])[0]
                if date is not None:
                    values = [v for v in values if v['date'] == date]
                else:
                    values = sorted(values, key=lambda x: x['date'], reverse=True)[:3]
            return self.reply(200, {'days': values})
        if parsed.path == '/v1/intervals' and role == 'planner':
            try:
                date = query['date'][0]
                dt.date.fromisoformat(date)
                offset = int(query.get('offset', ['0'])[0])
                if offset < 0:
                    raise ValueError('Invalid offset')
                with self.server.lock:
                    records = [json.loads(p.read_text(encoding='utf-8')) for p in sorted((self.server.root / 'intervals').glob('*.json'))]
                    spans = [span for record in records if record['date'] == date for span in record['intervals']]
                end = offset + 200
                return self.reply(200, {'intervals':spans[offset:end], 'next_offset':end if end < len(spans) else None})
            except (ValueError, KeyError, TypeError):
                return self.reply(400, {'error':'invalid_query'})
        self.reply(404, {'error': 'not_found'})

    def do_PUT(self):
        role = self.role()
        if role is None:
            return self.reply(401, {'error': 'unauthorized'})
        try:
            data = self.body()
            with self.server.lock:
                if self.path == '/v1/plan' and role == 'planner':
                    validate_plan(data)
                    old = json.loads((self.server.root / 'plan.json').read_text(encoding='utf-8'))
                    if data['revision'] < old['revision'] or (data['revision'] == old['revision'] and data != old):
                        return self.reply(409, {'error': 'revision_conflict'})
                    atomic_write(self.server.root / 'plan.json', data)
                    return self.reply(200, {'revision': data['revision']})
                if self.path == '/v1/usage' and role == 'phone':
                    # Bound fields and enforce a content-addressed identity, never accept paths.
                    if not isinstance(data, dict) or data.get('schema_version') != 1:
                        raise ValueError('Invalid usage')
                    key = self.identity(data)
                    dt.date.fromisoformat(data['date'])
                    ZoneInfo(data['timezone'])
                    days = data.get('retention_days', 7)
                    if type(days) is not int or not 1 <= days <= 30:
                        raise ValueError('Invalid retention')
                    atomic_write(self.server.root / 'usage' / f'{key}.json', data)
                    if not data.get('has_details', False):
                        for folder in ('intervals', 'interval-staging'):
                            (self.server.root / folder / f'{key}.json').unlink(missing_ok=True)
                    return self.reply(200, {'accepted': True})
                if self.path == '/v1/intervals' and role == 'phone':
                    key = self.identity(data)
                    if not (self.server.root / 'usage' / f'{key}.json').exists():
                        raise ValueError('Missing summary')
                    total, index, batch = data['total_chunks'], data['chunk_index'], data['batch_id']
                    if type(total) is not int or type(index) is not int or not 1 <= total <= 512 or not 0 <= index < total:
                        raise ValueError('Invalid chunk')
                    if not isinstance(batch, str) or len(batch) != 64 or not all(c in '0123456789abcdef' for c in batch):
                        raise ValueError('Invalid batch')
                    intervals = data['intervals']
                    if not isinstance(intervals, list) or len(intervals) > 200:
                        raise ValueError('Invalid intervals')
                    for span in intervals:
                        if not isinstance(span['event_id'], str) or len(span['event_id']) > 512:
                            raise ValueError('Invalid interval ID')
                        if not isinstance(span['app_package'], str) or len(span['app_package']) > 255:
                            raise ValueError('Invalid package')
                        if instant(span['end_at']) <= instant(span['start_at']):
                            raise ValueError('Invalid interval')
                    staging = self.server.root / 'interval-staging' / f'{key}.json'
                    state = json.loads(staging.read_text(encoding='utf-8')) if staging.exists() else {}
                    if state.get('batch_id') != batch or state.get('total_chunks') != total:
                        state = {k: data[k] for k in ('date', 'timezone', 'device_alias', 'batch_id', 'total_chunks')}
                        state['parts'] = {}
                    state['parts'][str(index)] = intervals
                    atomic_write(staging, state)
                    if len(state['parts']) == total:
                        all_spans = [span for i in range(total) for span in state['parts'][str(i)]]
                        if len({x['event_id'] for x in all_spans}) != len(all_spans):
                            raise ValueError('Duplicate intervals')
                        result = {k: data[k] for k in ('date', 'timezone', 'device_alias')}
                        result['intervals'] = all_spans
                        atomic_write(self.server.root / 'intervals' / f'{key}.json', result)
                        staging.unlink(missing_ok=True)
                    return self.reply(200, {'accepted': True, 'complete': not staging.exists()})
            self.reply(403, {'error': 'forbidden'})
        except (ValueError, KeyError, TypeError, OverflowError):
            self.reply(400, {'error': 'invalid_payload'})

    @staticmethod
    def identity(data):
        identity = [data['date'], data['timezone'], data['device_alias']]
        if not all(isinstance(x, str) and 0 < len(x) <= 100 for x in identity):
            raise ValueError('Invalid identity')
        return hashlib.sha256(json.dumps(identity).encode()).hexdigest()

    def do_DELETE(self):
        if self.role() != 'phone':
            return self.reply(401, {'error': 'unauthorized'})
        if self.path != '/v1/usage':
            return self.reply(404, {'error': 'not_found'})
        with self.server.lock:
            for folder in ('usage', 'intervals', 'interval-staging'):
                for path in (self.server.root / folder).glob('*.json'):
                    path.unlink()
        self.reply(200, {'deleted': True})


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--data-dir', type=Path, required=True)
    parser.add_argument('--port', type=int, default=8787)
    args = parser.parse_args()
    server = ExchangeServer(('127.0.0.1', args.port), args.data_dir)
    print('Private exchange listening on loopback; credentials are in the private data directory.', flush=True)
    server.serve_forever()
