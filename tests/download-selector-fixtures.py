"""Exercise selectors with the actual packaged yt-dlp, without network access."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'app/src/main/res/raw/ytdlp'))
from yt_dlp import YoutubeDL

def video(name, height, audio='none'):
    return dict(format_id=name, height=height, vcodec='h264', acodec=audio,
                ext='mp4', url='https://fixture.invalid/' + name)

audio = dict(format_id='audio', vcodec='none', acodec='aac', ext='m4a',
             url='https://fixture.invalid/audio')

def selected(selector, formats):
    with YoutubeDL({'quiet': True}) as engine:
        return list(engine._select_formats(formats, engine.build_format_selector(selector)))

highest = 'bestvideo*+bestaudio/best[vcodec!=none]/bestvideo'
# Already-muxed video may have a higher resolution than separate video streams.
result = selected(highest, [audio, video('separate', 360), video('muxed', 1080, 'aac')])
assert result[0]['height'] == 1080, result
# A silent video is a valid downloadable video too.
assert selected(highest, [video('silent', 2160)])[0]['format_id'] == 'silent'
# A selected ceiling must not be silently exceeded by the fallback.
capped = 'bestvideo*[height<=720]+bestaudio/best[height<=720][vcodec!=none]/bestvideo[height<=720]'
assert selected(capped, [video('silent', 720), video('bigger', 2160)])[0]['height'] == 720
assert not selected(capped, [video('bigger', 2160)])
# Audio-only and storyboards must not become downloadable video selections.
assert not selected(highest, [audio])
print('PASS packaged yt-dlp: muxed maximum, silent video, capped fallback, audio rejection')
