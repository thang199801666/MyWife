import io
import zipfile
import sys
import hashlib
from pathlib import Path

root = Path.home() / '.gradle/caches/modules-2/files-2.1/io.github.junkfood02.youtubedl-android/library/0.18.1'
library = Path(sys.argv[1]) if len(sys.argv) > 1 else next(root.rglob('library-0.18.1.aar'))
with zipfile.ZipFile(library) as aar:
    if 'res/raw/ytdlp' in aar.namelist():
        raw = aar.read('res/raw/ytdlp')
    else:
        # Release resource optimization renames raw resources.
        raw = next(data for name in aar.namelist() if name.startswith('res/')
                   for data in [aar.read(name)] if data.startswith(b'#!/usr/bin/env python'))
print('SHA256:', hashlib.sha256(raw).hexdigest())
with zipfile.ZipFile(io.BytesIO(raw)) as binary:
    print(binary.read('yt_dlp/version.py').decode())
