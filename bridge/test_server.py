import datetime as dt
import json
from pathlib import Path
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from server import ExchangeServer


class ExchangeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.server = ExchangeServer(('127.0.0.1', 0), self.tmp.name)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join(); self.tmp.cleanup()

    def request(self, path, role=None, data=None, method=None):
        headers = {'Authorization': 'Bearer ' + self.server.credentials[f'{role}_token']} if role else {}
        req = urllib.request.Request(f'http://127.0.0.1:{self.server.server_port}{path}', data=json.dumps(data).encode() if data is not None else None, headers=headers, method=method or ('PUT' if data is not None else 'GET'))
        try:
            with urllib.request.urlopen(req, timeout=3) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            return error.code, json.load(error)

    def test_auth_roles_revision_and_atomic_rejection(self):
        self.assertEqual(401, self.request('/v1/plan')[0])
        _, plan = self.request('/v1/plan', 'phone')
        plan['revision'] = 2
        self.assertEqual(403, self.request('/v1/plan', 'phone', plan)[0])
        self.assertEqual(200, self.request('/v1/plan', 'planner', plan)[0])
        stale = dict(plan, revision=1)
        self.assertEqual(409, self.request('/v1/plan', 'planner', stale)[0])
        invalid = dict(plan, revision=3, tasks=[{'task_id':'bad'}])
        self.assertEqual(400, self.request('/v1/plan', 'planner', invalid)[0])
        for changed in ({'generated_at':123}, {'schema_version':True}, {'valid_until':'2000-01-01T00:00:00Z'}, {'revision':2.5}):
            with self.subTest(changed=changed):
                self.assertEqual(400, self.request('/v1/plan', 'planner', dict(plan, **changed))[0])
        self.assertEqual(2, self.request('/v1/plan', 'phone')[1]['revision'])
        self.assertEqual(200, self.request('/v1/plan', 'planner', dict(plan, revision=3, tasks=[]))[0])

    def test_usage_upload_is_idempotent_and_private(self):
        data = {'schema_version': 1, 'date': dt.datetime.now(dt.timezone.utc).date().isoformat(), 'timezone': 'Asia/Manila', 'device_alias': 'synthetic', 'screen_interactive_ms': 1000}
        self.assertEqual(200, self.request('/v1/usage', 'phone', data)[0])
        self.assertEqual(200, self.request('/v1/usage', 'phone', data)[0])
        self.assertEqual(404, self.request('/v1/usage', 'phone')[0])
        self.assertEqual(1, len(self.request('/v1/usage', 'planner')[1]['days']))

    def test_detail_batches_replace_atomically_and_delete_is_authenticated(self):
        day = dt.datetime.now(dt.timezone.utc).date().isoformat()
        identity = {'schema_version':1, 'date':day, 'timezone':'Asia/Manila', 'device_alias':'synthetic'}
        self.request('/v1/usage', 'phone', dict(identity, has_details=True))
        def span(key, start, end):
            return {'event_id':key, 'app_package':'example.synthetic', 'start_at':f'{day}T00:00:{start:02d}+00:00', 'end_at':f'{day}T00:00:{end:02d}+00:00'}
        batch = dict(identity, batch_id='a'*64, total_chunks=2, chunk_index=0, intervals=[span('one',0,10)])
        self.assertEqual(200, self.request('/v1/intervals', 'phone', batch)[0])
        self.assertEqual([], self.request('/v1/intervals?date='+day, 'planner')[1]['intervals'])
        self.request('/v1/intervals', 'phone', dict(batch, chunk_index=1, intervals=[span('two',10,20)]))
        self.assertEqual(2, len(self.request('/v1/intervals?date='+day, 'planner')[1]['intervals']))
        self.request('/v1/intervals', 'phone', dict(batch, batch_id='b'*64, total_chunks=1, intervals=[span('one',0,15)]))
        self.assertEqual(1, len(self.request('/v1/intervals?date='+day, 'planner')[1]['intervals']))
        self.assertEqual(401, self.request('/v1/usage', method='DELETE')[0])
        self.assertEqual(200, self.request('/v1/usage', 'phone', method='DELETE')[0])
        self.assertEqual([], self.request('/v1/usage', 'planner')[1]['days'])


if __name__ == '__main__':
    unittest.main()
