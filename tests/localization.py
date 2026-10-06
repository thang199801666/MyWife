"""Check resource parity and formatting contracts before packaging either language."""
from pathlib import Path
import re,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
def catalog(folder):
    rows={}
    for file in (root/'app/src/main/res'/folder).glob('*.xml'):
        for string in ET.parse(file).getroot().findall('string'):
            name=string.attrib['name']
            assert name not in rows, f'Duplicate {folder}/{name}'
            rows[name]=''.join(string.itertext())
    return rows
vi,en=catalog('values'),catalog('values-en')
assert vi.keys()==en.keys(),f'Missing translations: {vi.keys() ^ en.keys()}'
pattern=re.compile(r'%(?:\d+\$)?[dsf]')
for key in vi:
    assert vi[key].strip() and en[key].strip(),f'Empty {key}'
    assert pattern.findall(vi[key])==pattern.findall(en[key]),f'Placeholder mismatch {key}'
assert vi['app_name']==en['app_name']=='Vợ Tui'
assert vi['language_vi']=='Tiếng Việt' and en['language_en']=='English'
assert 'This App I wrote for my wife and my unborn child' in vi['about_body']
assert 'This App I wrote for my wife and my unborn child' in en['about_body']
config=ET.parse(root/'app/src/main/res/xml/locales_config.xml').getroot()
assert [node.attrib['{http://schemas.android.com/apk/res/android}name'] for node in config]==['vi','en']
print(f'PASS {len(vi)} paired strings, placeholder contracts, branding, dedication and supported locales')
