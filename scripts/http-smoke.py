"""Validate a running local app using Python's standard library only."""
import argparse
import io
import json
from pathlib import Path
import re
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--url', default='http://127.0.0.1:18123')
args = p.parse_args()
url = args.url.rstrip('/')
page = urllib.request.urlopen(url + '/', timeout=10).read().decode('utf-8')
token = re.search(r'name="app-token" content="([^"]+)"', page).group(1)
def request(path, method='GET', data=None):
    req = urllib.request.Request(url + path, method=method, data=data, headers={'X-App-Token':token, 'Content-Type':'application/octet-stream'})
    with urllib.request.urlopen(req, timeout=15) as response: return response.read()
batch = json.loads(request('/api/batches', 'POST', b''))['id']
route = '/api/batches/' + batch
try:
    sample = Path(__file__).resolve().parents[1] / 'examples/sample-4x3.pptx'
    item = json.loads(request(route + '/files?name=sample-4x3.pptx', 'POST', sample.read_bytes()))['id']
    deadline = time.monotonic() + 30
    while True:
        status = json.loads(request(route))
        if status['complete']: break
        if time.monotonic() > deadline: raise RuntimeError('Conversion timed out')
        time.sleep(.1)
    converted = status['files'][0]
    if converted['status'] != 'done': raise RuntimeError(converted.get('error', 'Conversion failed'))
    data = request(route + '/files/' + item + '/download')
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        if z.testzip() is not None: raise RuntimeError('Downloaded ZIP is damaged')
        presentation = ET.fromstring(z.read('ppt/presentation.xml'))
        size = presentation.find('{http://schemas.openxmlformats.org/presentationml/2006/main}sldSz')
        if (int(size.get('cx')), int(size.get('cy'))) != (12192000, 6858000): raise RuntimeError('Wrong output dimensions')
        if 'ppt/charts/chart1.xml' not in z.namelist(): raise RuntimeError('Chart missing')
    print('PASS running app: upload → conversion → download; 16:9, original height, chart retained')
finally:
    request(route, 'DELETE')
