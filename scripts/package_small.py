"""Build a small Windows offline package or a Java-required package.
Small: --windows-jdk ZIP --checksum-file FILE --jlink /path/to/jlink
Java-required: --java-required
Uses only Python standard library; build the application JAR first.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import struct
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MODULES = ['java.base', 'java.xml', 'jdk.httpserver']
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--java-required', action='store_true')
p.add_argument('--windows-jdk', type=Path)
p.add_argument('--checksum-file', type=Path)
p.add_argument('--jlink')
args = p.parse_args()
jar = ROOT / 'dist/ppt-format-converter.jar'
if not jar.is_file(): raise SystemExit('Build the JAR first.')
variant = 'Java-required' if args.java_required else 'Windows-x64-Small'
destination = ROOT / f'dist/PPTFormatConverter-1.0-{variant}.zip'
if destination.exists(): raise SystemExit('Output exists; preserve or move it before rebuilding.')
if not args.java_required and not all([args.windows_jdk, args.checksum_file, args.jlink]):
    p.error('Small offline package requires --windows-jdk, --checksum-file and --jlink')
manifest = {'application': 'PPT Format Converter V1.0', 'variant': variant,
            'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest()}
with tempfile.TemporaryDirectory(prefix='small-', dir=ROOT / 'dist') as stage:
    app = Path(stage) / 'PPTFormatConverter'
    app.mkdir()
    if not args.java_required:
        with args.windows_jdk.open('rb') as stream:
            h = hashlib.sha256()
            while chunk := stream.read(1024 * 1024): h.update(chunk)
        expected = args.checksum_file.read_text().strip().split()[0].lower()
        if h.hexdigest() != expected: raise SystemExit('Official Windows JDK SHA-256 mismatch.')
        jmods = Path(stage) / 'jmods'
        jmods.mkdir()
        notice = None
        with zipfile.ZipFile(args.windows_jdk) as z:
            for entry in z.infolist():
                path = PurePosixPath(entry.filename)
                if path.is_absolute() or '..' in path.parts or '\\' in entry.filename:
                    raise SystemExit('Unsafe Windows JDK ZIP path.')
                if len(path.parts) >= 2 and path.parts[-2] == 'jmods' and path.name in {m+'.jmod' for m in MODULES}:
                    with z.open(entry) as source, (jmods / path.name).open('wb') as target: shutil.copyfileobj(source,target)
                if len(path.parts) == 2 and path.name == 'NOTICE': notice = z.read(entry)
        for module in MODULES:
            if not (jmods / (module+'.jmod')).is_file(): raise SystemExit('Required Windows module missing: '+module)
        # jlink reads the target platform from java.base.jmod: this can cross-link Windows on Linux.
        subprocess.run([args.jlink, '--module-path', str(jmods), '--add-modules', ','.join(MODULES),
                        '--strip-debug', '--no-header-files', '--no-man-pages', '--compress=1',
                        '--exclude-files=**/*.lib',
                        '--output', str(app / 'runtime')], check=True)
        exe = (app / 'runtime/bin/java.exe').read_bytes()
        if exe[:2] != b'MZ': raise SystemExit('Runtime launcher is not Windows PE.')
        pe = struct.unpack_from('<I', exe, 0x3c)[0]
        if exe[pe:pe+4] != b'PE\0\0' or struct.unpack_from('<H', exe, pe+4)[0] != 0x8664:
            raise SystemExit('Runtime launcher is not Windows x64.')
        if not (app / 'runtime/legal/java.base').is_dir(): raise SystemExit('Runtime legal notices missing.')
        if notice: (app / 'runtime/NOTICE').write_bytes(notice)
        manifest.update(runtime_source='https://github.com/adoptium/temurin21-binaries',
                        windows_jdk_sha256=expected, runtime_modules=MODULES)
    shutil.copy2(jar, app / jar.name)
    (app / 'Start.cmd').write_bytes((ROOT / 'Start.cmd').read_text().replace('\r\n','\n').replace('\n','\r\n').encode('ascii'))
    if args.java_required:
        text = '此精简包不包含 Java。工作电脑必须已经安装 Java 17+，否则无法启动。\n双击 Start.cmd。详细功能和限制见 README.md。\n'
    else:
        text = (ROOT / 'docs/Windows使用说明.txt').read_text() + '\n此小型离线包只包含应用需要的 Java 模块，转换功能不减少。\n'
    (app / '使用说明.txt').write_text(text, encoding='utf-8-sig')
    shutil.copy2(ROOT / 'README.md', app / 'README.md')
    (app / 'build-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for file in sorted(app.rglob('*')):
            if file.is_file(): z.write(file,file.relative_to(Path(stage)).as_posix())
with zipfile.ZipFile(destination) as z:
    if z.testzip() is not None: raise SystemExit('Output ZIP integrity check failed.')
print(f'{destination.name}: {destination.stat().st_size:,} bytes ({destination.stat().st_size/1_000_000:.2f} MB)')
