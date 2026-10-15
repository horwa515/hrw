"""Build V1.1 Basic and Enhanced ZIPs, both protected with the requested password.
Requires Info-ZIP `zip` on the build machine; produced ZIPs open on Windows.
Run build.sh, fetch_office_dependencies.py and build-enhanced.sh first.
"""
from pathlib import Path
import hashlib
import json
import shutil
import struct
import subprocess
import tempfile
import zipfile

root=Path(__file__).resolve().parents[1]
jar=root/'dist/ppt-format-converter.jar'
adapter=root/'dist/ppt-office-encryption.jar'
for binary in [jar,adapter]:
    with zipfile.ZipFile(binary) as z:
        for name in z.namelist():
            if name.endswith('.class') and struct.unpack('>H',z.read(name)[6:8])[0]!=52:
                raise SystemExit('Non-Java8 class: '+name)
if not shutil.which('zip'):raise SystemExit('Info-ZIP zip is required on the build machine.')
for edition in ['Basic','Enhanced']:
    output=root/f'downloads/PPTFormatConverter-1.1-Java8-{edition}-Password.zip'
    if output.exists():raise SystemExit('Output exists; preserve or move before rebuilding: '+output.name)
    with tempfile.TemporaryDirectory(prefix='ppt-v11-',dir=root/'build') as stage:
        app=Path(stage)/('PPTFormatConverter-1.1-'+edition);app.mkdir()
        shutil.copy2(jar,app/jar.name)
        launcher=(root/'Start.cmd').read_text().replace('title PPT Format Converter V1.1','title PPT Format Converter V1.1 '+edition)
        if edition=='Enhanced':
            launcher=launcher.replace('"%JAVA%" -Dfile.encoding=UTF-8 -jar "%APP%" %*',
                '"%JAVA%" -Dfile.encoding=UTF-8 -cp "%APP%;ppt-office-encryption.jar;lib/*" com.pptconverter.App --office-password %*')
            shutil.copy2(adapter,app/adapter.name)
            libs=app/'lib';libs.mkdir()
            lock=json.loads((root/'scripts/office-dependencies.lock.json').read_text())
            for dependency in lock:
                name=dependency['artifact']+'-'+dependency['version']+'.jar'
                source=root/'build/office-libs'/name
                if hashlib.sha512(source.read_bytes()).hexdigest()!=dependency['sha512']:raise SystemExit('Pinned dependency failed: '+name)
                shutil.copy2(source,libs/name)
            (app/'dependencies.json').write_text(json.dumps(lock,indent=2))
            (app/'THIRD-PARTY-NOTICES.txt').write_text('Office encryption uses Apache POI and its declared dependencies.\nOriginal licenses and notices are retained in each lib/*.jar under META-INF.\nArtifact versions and SHA-512 checksums are listed in dependencies.json.\n')
        (app/'Start.cmd').write_bytes(launcher.replace('\r\n','\n').replace('\n','\r\n').encode('ascii'))
        guide=(root/'docs/V1.1使用说明.txt').read_text()
        guide=('当前版本：'+('增强版：自动处理 PPT 打开密码。' if edition=='Enhanced' else '基础版：PPT 打开密码仍需手动处理。')+'\n\n')+guide
        (app/'使用说明.txt').write_text(guide,encoding='utf-8-sig')
        examples=app/'examples';examples.mkdir()
        shutil.copy2(root/'examples/sample-company-1.4444.pptx',examples/'sample-company-1.4444.pptx')
        if edition=='Enhanced':shutil.copy2(root/'examples/sample-company-3609.pptx',examples/'sample-company-3609.pptx')
        (app/'build-manifest.json').write_text(json.dumps(dict(version='1.1',edition=edition,java_minimum=8,bundled_java=False,
            jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),office_output_encrypted=edition=='Enhanced'),indent=2))
        expected={p.relative_to(Path(stage)).as_posix():p.read_bytes() for p in app.rglob('*') if p.is_file()}
        subprocess.run(['zip','-q','-r','-9','-P','3609',str(output),app.name],cwd=stage,check=True)
        with zipfile.ZipFile(output) as z:
            files=[i for i in z.infolist() if not i.is_dir()]
            if {i.filename for i in files}!=set(expected):raise SystemExit('Package files changed during ZIP creation.')
            for item in files:
                if not item.flag_bits&1 or z.read(item,pwd=b'3609')!=expected[item.filename]:raise SystemExit('Encryption/content verification failed.')
            for pwd in [None,b'incorrect']:
                try:z.read(files[0],pwd=pwd)
                except (RuntimeError,zipfile.BadZipFile):pass
                else:raise SystemExit('ZIP password verification failed.')
    print(output.name+f': {output.stat().st_size:,} bytes ({output.stat().st_size/1_000_000:.2f} MB)')
