"""Package the Java 8 compatible Windows app, with no bundled runtime.
Run build.sh/build.cmd first. Python standard library only.
"""
import hashlib
import json
from pathlib import Path
import struct
import zipfile

root = Path(__file__).resolve().parents[1]
jar = root / 'dist/ppt-format-converter.jar'
if not jar.is_file(): raise SystemExit('Build the application first.')
with zipfile.ZipFile(jar) as z:
    classes = [i for i in z.namelist() if i.endswith('.class')]
    if not classes: raise SystemExit('JAR contains no classes.')
    for name in classes:
        data = z.read(name)
        if data[:4] != b'\xca\xfe\xba\xbe' or struct.unpack('>H', data[6:8])[0] != 52:
            raise SystemExit('Not Java 8 bytecode: ' + name)
destination = root / 'dist/PPTFormatConverter-1.0-Windows-Java8.zip'
if destination.exists(): raise SystemExit('Output exists; preserve or move it before rebuilding.')
prefix = 'PPTFormatConverter-Java8/'
manifest = {'application': 'PPT Format Converter V1.0', 'java_minimum': '8', 'class_major_version': 52,
            'platform': 'Windows 10 x64 with existing Java 8 JRE', 'bundled_java': False,
            'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
            'validation': 'Linux Java 8u202 tests and browser workflow; Windows Oracle 8u201 needs user acceptance'}
guide = (root / 'docs/Java8使用说明.txt').read_text()
with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    z.write(jar, prefix + jar.name)
    z.writestr(prefix + 'Start.cmd', (root / 'Start.cmd').read_text().replace('\r\n','\n').replace('\n','\r\n').encode('ascii'))
    z.writestr(prefix + '使用说明.txt', guide.encode('utf-8-sig'))
    z.writestr(prefix + 'build-manifest.json', json.dumps(manifest,indent=2))
    z.write(root / 'examples/sample-4x3.pptx', prefix + 'examples/sample-4x3.pptx')
with zipfile.ZipFile(destination) as z:
    if z.testzip() is not None: raise SystemExit('ZIP integrity check failed.')
print(f'{destination.name}: {destination.stat().st_size:,} bytes ({destination.stat().st_size/1024:.1f} KiB)')
