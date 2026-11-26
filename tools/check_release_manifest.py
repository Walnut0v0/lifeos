from pathlib import Path
import sys
import xml.etree.ElementTree as ET

flavor = sys.argv[1]
assert flavor in ('lab', 'online')
root = Path(__file__).resolve().parents[1] / 'android/app/build/intermediates'
manifest = next(root.glob(f'merged_manifest*/{flavor}Release/**/AndroidManifest.xml'))
tree = ET.parse(manifest).getroot()
assert not any('DebugReceiver' in str(element.attrib) for element in tree.iter())
print(f'{flavor} release manifest: no debug receiver')
