"""Package the supplied artwork as Android launcher assets (no redraw)."""
from pathlib import Path
import sys
from PIL import Image, ImageOps

root = Path(__file__).resolve().parent
res = root / 'app/src/main/res'
source = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'branding/votuibe-original.png'
art = Image.open(source).convert('RGBA')
assets = root / 'branding'
assets.mkdir(exist_ok=True)
art.save(assets / 'votuibe-original.png')
def square(size):
    resized = ImageOps.contain(art, (size, size), Image.Resampling.LANCZOS)
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    canvas.paste(resized, ((size - resized.width) // 2, (size - resized.height) // 2))
    return canvas
for density, size in [('mdpi',48), ('hdpi',72), ('xhdpi',96), ('xxhdpi',144), ('xxxhdpi',192)]:
    folder = res / f'mipmap-{density}'
    folder.mkdir(exist_ok=True)
    icon = square(size)
    for name in ['ic_launcher', 'ic_launcher_round']:
        icon.save(folder / f'{name}.png')
# Adaptive foreground: 72dp of artwork in the 108dp layer. Android applies
# launcher masks; the supplied square stays centered inside their safe area.
foreground = Image.new('RGBA', (432,432), (0,0,0,0))
foreground.paste(square(288), (72,72))
folder = res / 'drawable-nodpi'
folder.mkdir(exist_ok=True)
foreground.save(folder / 'ic_launcher_foreground.png')
folder = res / 'mipmap-anydpi-v26'
folder.mkdir(exist_ok=True)
xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''
for name in ['ic_launcher', 'ic_launcher_round']:
    (folder / f'{name}.xml').write_text(xml, encoding='utf-8')
(res / 'values/ic_launcher_colors.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources><color name="ic_launcher_background">#140F1C</color></resources>
''', encoding='utf-8')
for path in (res / 'layout').glob('*.xml'):
    text = path.read_text(encoding='utf-8')
    if 'YouTooBee' in text:
        path.write_text(text.replace('YouTooBee', 'VoTuibe'), encoding='utf-8')
java = root / 'app/src/main/java/com/example/videoshield'
replacements = {
    'MainActivity.kt': [('else -> "YouTooBee"', 'else -> "VoTuibe"'), ('YouTooBee paused automatic recovery.', 'VoTuibe paused automatic recovery.')],
    'PlaybackService.kt': [('private var title = "YouTooBee"', 'private var title = "VoTuibe"'), ('ifBlank { "YouTooBee" }', 'ifBlank { "VoTuibe" }')],
    'LibraryActivity.kt': [('YouTooBee queue item', 'VoTuibe queue item')],
}
for filename, changes in replacements.items():
    path = java / filename
    text = path.read_text(encoding='utf-8')
    for before, after in changes:
        text = text.replace(before, after)
    path.write_text(text, encoding='utf-8')
print('VoTuibe launcher resources and visible branding updated.')
