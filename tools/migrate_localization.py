"""One-off migration to Android string resources; curated translations in localization.tsv."""
from pathlib import Path
import re, html, hashlib
root=Path(__file__).resolve().parents[1]
resources=[]; lookup={}
for line in (root/'tools/localization.tsv').read_text(encoding='utf-8').splitlines():
    if not line: continue
    en,vi=line.split('\t',1)
    if en in lookup: continue
    key='ui_'+re.sub(r'[^a-z0-9]+','_',en.lower()).strip('_')[:65]
    if any(row[0]==key for row in resources): key+='_'+hashlib.sha1(en.encode()).hexdigest()[:6]
    resources.append((key,vi,en)); lookup[en]=key; lookup.setdefault(vi,key)
extras=[('app_name','Vợ Tui','Vợ Tui'),('language_title','Ngôn ngữ','Language'),
 ('language_vi','Tiếng Việt','Tiếng Việt'),('language_en','English','English'),
 ('settings_title','Cài đặt Vợ Tui','Vợ Tui settings'),('dashboard_title','Thống kê Vợ Tui','Vợ Tui dashboard'),
 ('library_subtitle','Thư viện của bạn trên thiết bị','Your library on this device'),
 ('offline_save_device','Lưu vào Downloads/VoTuibe để giữ lâu dài','Save to Downloads/VoTuibe for permanent storage'),
 ('offline_keep_file','Lưu tại Downloads/VoTuibe • Giữ file khi xóa khỏi danh sách','Saved in Downloads/VoTuibe • Removing the entry keeps the file'),
 ('community_privacy','Mặc định tắt. Khi bật, Vợ Tui dùng cơ chế k-anonymity tương thích SponsorBlock: chỉ gửi tiền tố SHA-256 ngắn và lọc dữ liệu video phù hợp trên thiết bị. Dữ liệu cộng đồng: SponsorBlock của Ajay Ramachandran, CC BY-NC-SA 4.0.','Disabled by default. When enabled, Vợ Tui uses the SponsorBlock-compatible k-anonymity endpoint: only a short SHA-256 prefix is sent and matching video data is filtered locally. Community segment data: SponsorBlock by Ajay Ramachandran, CC BY-NC-SA 4.0.'),
 ('rules_explanation','Bộ quy tắc được kiểm tra trước khi dùng. Vợ Tui giữ bản trước đó để khôi phục. App có thể tự bật chế độ an toàn nếu trình phát lỗi nhiều lần.','Rule packs are validated before activation. Vợ Tui keeps the previous pack for rollback. Compatibility Guard can automatically enter Safe mode if repeated player failures are detected.'),
 ('offline_recovery','Vợ Tui đã tạm dừng tự khôi phục. Kết nối lại mạng rồi nhấn Thử lại.','Vợ Tui paused automatic recovery. Reconnect to the internet, then Retry.'),
 ('about_body','Vợ Tui giúp bạn xem video, thu nhỏ trình phát, nhận gợi ý theo lịch sử xem và hạn chế quảng cáo. Bạn có thể lưu video hoặc MP3 vào thiết bị, hoặc lưu tạm để xem offline trong 30 ngày.\\n\\nÂm thanh mặc định được bật và điều chỉnh bằng âm lượng media của điện thoại. Lịch sử xem và sở thích được lưu trên thiết bị.\\n\\nThis App I wrote for my wife and my unborn child','Vợ Tui lets you watch videos, minimize the player, receive suggestions from your viewing history and limit ads. Save videos or MP3 to your device, or save temporarily for offline playback for 30 days.\\n\\nSound is enabled by default and uses the phone’s media volume. Viewing history and preferences are stored on your device.\\n\\nThis App I wrote for my wife and my unborn child')]
aliases={'VoTuibe':'app_name','VoTuibe settings':'settings_title','VoTuibe dashboard':'dashboard_title',
 'Thư viện của bạn trên thiết bị':'library_subtitle','Lưu vào Downloads/VoTuibe để giữ lâu dài':'offline_save_device',
 'Lưu tại Downloads/VoTuibe • Giữ file khi xóa khỏi danh sách':'offline_keep_file',
 'VoTuibe paused automatic recovery. Reconnect to the internet, then Retry.':'offline_recovery',
 'Chưa có nội dung tải xuống\\n\\nMở video → Download để lưu video hoặc MP3.':lookup['No downloads yet\\n\\nOpen a video → Download to save video or MP3.'],
 'Chưa có nội dung trong mục này\\n\\nChọn All để xem tất cả.':lookup['Nothing in this category yet\\n\\nSelect All to see everything.']}
lookup.update(aliases);resources+=extras
def xmltext(value): return html.escape(value,quote=False).replace("'", "\\'").replace('"','\\"')
for directory,column in [('values',1),('values-en',2)]:
    destination=root/'app/src/main/res'/directory/'strings.xml';destination.parent.mkdir(parents=True,exist_ok=True)
    destination.write_text('<resources>\n'+''.join(f'    <string name="{r[0]}" formatted="false">{xmltext(r[column])}</string>\n' for r in resources)+'</resources>\n',encoding='utf-8')
java=root/'app/src/main/java/com/example/videoshield'
for name in ['MainActivity','SettingsActivity','LibraryActivity','DownloadsActivity','OfflinePlayerActivity','ShieldDashboardActivity','SaveVideoController','DownloadListAdapter','EqDialog','ActionSheet']:
    file=java/(name+'.kt');text=file.read_text(encoding='utf-8')
    prefix='activity.' if name in ['SaveVideoController','DownloadListAdapter','EqDialog','ActionSheet'] else ''
    parts=re.split(r'("""[\s\S]*?""")',text)
    for index in range(0,len(parts),2):
        for source,key in sorted(lookup.items(),key=lambda pair:-len(pair[0])):
            escaped=source.replace('"','\\"')
            parts[index]=parts[index].replace('"'+escaped+'"',prefix+'getString(R.string.'+key+')')
    text=''.join(parts)
    text=text.replace(': Activity() {',': LocalizedActivity() {')
    file.write_text(text,encoding='utf-8')
for file in (root/'app/src/main/res/layout').glob('*.xml'):
    text=file.read_text(encoding='utf-8')
    def replace(match):
        attribute,value=match.groups(); source=html.unescape(value)
        key=lookup.get(source)
        if source.startswith('Disabled by default. When enabled, VoTuibe'):key='community_privacy'
        if source.startswith('Rule packs are validated before activation. VoTuibe'):key='rules_explanation'
        return f'android:{attribute}="@string/{key}"' if key else match.group(0)
    text=re.sub(r'android:(text|hint|contentDescription)="([^"]*)"',replace,text)
    file.write_text(text,encoding='utf-8')
print(f'Migrated {len(resources)} paired Android strings')
