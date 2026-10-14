"""Build an offline Windows x64 ZIP using a verified official Temurin JRE ZIP.
Usage: python scripts/package_portable.py --runtime-zip PATH --checksum-file PATH
Run build.sh/build.cmd first. No third-party Python dependencies required.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--runtime-zip', type=Path, required=True)
p.add_argument('--checksum-file', type=Path, required=True)
args = p.parse_args()
expected = args.checksum_file.read_text().strip().split()[0].lower()
with args.runtime_zip.open('rb') as stream:
    h = hashlib.sha256()
    while chunk := stream.read(1024 * 1024): h.update(chunk)
actual = h.hexdigest()
if actual != expected: raise SystemExit('Runtime SHA-256 verification failed; nothing packaged.')
jar = ROOT / 'dist/ppt-format-converter.jar'
if not jar.is_file(): raise SystemExit('Run build.sh/build.cmd first.')
destination = ROOT / 'dist/PPTFormatConverter-1.0-Windows-x64.zip'
if destination.exists(): raise SystemExit('Output ZIP exists. Move it aside before packaging again.')
with tempfile.TemporaryDirectory(prefix='portable-', dir=ROOT / 'dist') as stage:
    app = Path(stage) / 'PPTFormatConverter'
    app.mkdir()
    runtime = app / 'runtime'
    runtime.mkdir()
    with zipfile.ZipFile(args.runtime_zip) as z:
        roots = {PurePosixPath(i.filename).parts[0] for i in z.infolist()}
        if len(roots) != 1: raise SystemExit('Expected one runtime root in official ZIP.')
        for entry in z.infolist():
            path = PurePosixPath(entry.filename)
            if path.is_absolute() or '..' in path.parts or '\\' in entry.filename:
                raise SystemExit('Unsafe runtime ZIP path.')
            if len(path.parts) < 2: continue
            target = runtime.joinpath(*path.parts[1:])
            if entry.is_dir(): target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with z.open(entry) as source, target.open('wb') as out: shutil.copyfileobj(source, out)
    if not (runtime / 'bin/java.exe').is_file(): raise SystemExit('Not a Windows JRE ZIP.')
    shutil.copy2(jar, app / jar.name)
    shutil.copy2(ROOT / 'Start.cmd', app / 'Start.cmd')
    # ASCII command file: avoid Windows legacy codepage ambiguity.
    (app / 'Start.cmd').write_bytes((ROOT / 'Start.cmd').read_text().replace('\r\n', '\n').replace('\n', '\r\n').encode('ascii'))
    shutil.copy2(ROOT / 'README.md', app / 'README.md')
    shutil.copytree(ROOT / 'examples', app / 'examples')
    shutil.copy2(ROOT / 'docs/Windows使用说明.txt', app / '使用说明.txt')
    manifest = {'application': 'PPT Format Converter V1.0', 'platform': 'Windows 10/11 x64',
                'runtime_archive': args.runtime_zip.name, 'runtime_sha256': actual,
                'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                'runtime_source': 'https://github.com/adoptium/temurin21-binaries',
                'license_notice': 'Temurin licenses and third-party notices are retained under runtime/legal and runtime/NOTICE.'}
    (app / 'build-manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for file in sorted(app.rglob('*')):
            if file.is_file(): z.write(file, file.relative_to(Path(stage)).as_posix())
print(f'Windows offline portable ZIP: {destination}')
