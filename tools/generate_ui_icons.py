"""Generate the consistent 24dp vector set and wire icon-only native buttons."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1]
res = root / 'app/src/main/res'
stroke = {
 'back': 'M20,12H4 M4,12L11,5 M4,12L11,19',
 'close': 'M6,6L18,18 M18,6L6,18',
 'create': 'M12,3A9,9 0,1 1,12,21A9,9 0,1 1,12,3 M12,7V17 M7,12H17',
 'settings': 'M9,3H15L16,6L19,7L21,10L19,12L21,14L19,17L16,18L15,21H9L8,18L5,17L3,14L5,12L3,10L5,7L8,6Z M8,12A4,4 0,1 1,16,12A4,4 0,1 1,8,12',
 'expand': 'M4,9V4H9 M15,4H20V9 M20,15V20H15 M9,20H4V15',
 'minimize': 'M6,9L12,15L18,9',
 'queue': 'M3,5H16 M3,10H16 M3,15H10 M18,13V21 M14,17H22',
 'bookmark': 'M6,3H18V21L12,17L6,21Z',
 'repeat': 'M4,9V7H19 M16,4L19,7L16,10 M20,15V17H5 M8,14L5,17L8,20',
 'shuffle': 'M3,6H6L18,18H21 M18,15L21,18L18,21 M3,18H6L10,14 M14,10L18,6H21 M18,3L21,6L18,9',
 'timer': 'M12,3A9,9 0,1 1,12,21A9,9 0,1 1,12,3 M12,7V12L16,14',
 'pip': 'M3,4H21V20H3Z M12,11H19V18H12Z',
 'seek_back': 'M5,7L5,3 M5,7H9 M5,7A8,8 0,1 1,4,14 M8,11L10,10V17 M14,10H17V17H14Z',
 'seek_forward': 'M19,7L19,3 M19,7H15 M19,7A8,8 0,1 0,20,14 M8,11L10,10V17 M14,10H17V17H14Z',
}
fill = {
 'play': 'M8,5L19,12L8,19Z',
 'pause': 'M6,5H10V19H6Z M14,5H18V19H14Z',
 'bookmark_filled': stroke['bookmark'],
 'more': 'M12,3A1.5,1.5 0,1 1,12,6A1.5,1.5 0,1 1,12,3Z M12,10.5A1.5,1.5 0,1 1,12,13.5A1.5,1.5 0,1 1,12,10.5Z M12,18A1.5,1.5 0,1 1,12,21A1.5,1.5 0,1 1,12,18Z',
}
for name, path in {**stroke, **fill}.items():
    size = 32 if name == 'create' else 24
    paint = 'android:fillColor="#FFFFFFFF"' if name in fill else 'android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.7" android:strokeLineCap="round" android:strokeLineJoin="round"'
    (res / f'drawable/ic_ui_{name}.xml').write_text(f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{size}dp" android:height="{size}dp" android:viewportWidth="24" android:viewportHeight="24"><path {paint} android:pathData="{path}"/></vector>\n', encoding='utf-8')
mapping = {'settingsButton':'settings', 'goButton':'search', 'miniPlayPauseButton':'pause', 'miniExpandButton':'expand', 'miniCloseButton':'close', 'closeLibraryButton':'back', 'librarySearchToggle':'search', 'recommendationControlsButton':'more', 'navCreateButton':'create', 'seekBackButton':'seek_back', 'seekForwardButton':'seek_forward', 'playPauseButton':'play', 'stopPlaybackButton':'close'}
for file in ['activity_main.xml', 'activity_library.xml']:
    target = res / 'layout' / file
    text = target.read_text(encoding='utf-8')
    def replace(match):
        attributes = match.group(1)
        name = re.search(r'android:id="@\+id/([^"]+)"', attributes).group(1)
        if name not in mapping: return match.group(0)
        attributes = re.sub(r'\s+android:(text|drawableTop|textSize)="[^"]*"', '', attributes)
        drawable = 'ic_search' if mapping[name] == 'search' else 'ic_ui_' + mapping[name]
        return f'<com.example.videoshield.IconButton{attributes} android:drawableTop="@drawable/{drawable}"/>'
    text = re.sub(r'<Button\b([^>]*android:id="@\+id/[^>]+)/>', replace, text)
    target.write_text(text, encoding='utf-8')
