"""Optional independent Office-format verification (pip install msoffcrypto-tool==5.4.2).
Run build-enhanced.sh first. Python is not needed by either delivered app.
"""
from io import BytesIO
from pathlib import Path
import zipfile
import msoffcrypto
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
with (root/'build/encrypted-validation.pptx').open('rb') as source:
    file=msoffcrypto.OfficeFile(source)
    file.load_key(password='3609',verify_password=True)
    data=BytesIO();file.decrypt(data,verify_integrity=True)
with zipfile.ZipFile(BytesIO(data.getvalue())) as z:
    assert z.testzip() is None
    size=ET.fromstring(z.read('ppt/presentation.xml')).find('{http://schemas.openxmlformats.org/presentationml/2006/main}sldSz')
    assert (size.get('cx'),size.get('cy'))==('12192000','6858000')
    assert b'SimHei' in z.read('ppt/slides/slide1.xml')
print('PASS independent msoffcrypto: password, Agile AES integrity, output dimensions and Chinese fonts')
