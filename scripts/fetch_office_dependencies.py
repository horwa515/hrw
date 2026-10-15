"""Fetch pinned Java 8 compatible Office-encryption dependencies from Maven Central.
Every JAR is checked against the strongest published Maven Central checksum over verified HTTPS.
"""
from pathlib import Path
import hashlib
import json
import urllib.request
import urllib.error

root=Path(__file__).resolve().parents[1]
target=root/'build/office-libs'
target.mkdir(parents=True,exist_ok=True)
artifacts=[('org.apache.poi','poi','5.4.1'),('commons-codec','commons-codec','1.18.0'),
           ('org.apache.commons','commons-collections4','4.4'),('org.apache.commons','commons-math3','3.6.1'),
           ('commons-io','commons-io','2.18.0'),('com.zaxxer','SparseBitSet','1.3'),
           ('org.apache.logging.log4j','log4j-api','2.24.3')]
manifest=[]
lock=root/'scripts/office-dependencies.lock.json'
pinned={(e['artifact'],e['version']):e['sha512'] for e in json.loads(lock.read_text())} if lock.exists() else {}
for group,name,version in artifacts:
    filename=f'{name}-{version}.jar'
    url=f'https://repo.maven.apache.org/maven2/{group.replace(".","/")}/{name}/{version}/{filename}'
    for algorithm in ['sha512','sha256','sha1']:
        try:
            expected=urllib.request.urlopen(url+'.'+algorithm,timeout=30).read().decode().strip().split()[0].lower()
            break
        except urllib.error.HTTPError as error:
            if error.code!=404:raise
    else:raise SystemExit('No authoritative checksum published for '+filename)
    dest=target/filename
    if not dest.exists():
        data=urllib.request.urlopen(url,timeout=60).read()
        if hashlib.new(algorithm,data).hexdigest()!=expected:raise SystemExit('Official checksum mismatch: '+filename)
        dest.write_bytes(data)
    if hashlib.new(algorithm,dest.read_bytes()).hexdigest()!=expected:raise SystemExit('Cached JAR failed verification: '+filename)
    if pinned and hashlib.sha512(dest.read_bytes()).hexdigest()!=pinned.get((name,version)):
        raise SystemExit('Pinned SHA-512 mismatch: '+filename)
    manifest.append(dict(group=group,artifact=name,version=version,verification_algorithm=algorithm,official_checksum=expected,
                         sha512=hashlib.sha512(dest.read_bytes()).hexdigest(),url=url))
    print('Verified:',filename)
(target/'dependencies.json').write_text(json.dumps(manifest,indent=2))
