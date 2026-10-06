from pathlib import Path
import html,re
root=Path(__file__).resolve().parents[1];java=root/'app/src/main/java/com/example/videoshield'
def edit(name,replacements):
    path=java/(name+'.kt');text=path.read_text(encoding='utf-8-sig')
    for source,target in replacements.items():
        assert source in text,(name,source)
        text=text.replace(source,target)
    path.write_text(text,encoding='utf-8')
edit('AppLanguage',{'private var language = "vi"':'private var language = "vi"\n    protected val uiLanguageTag: String get() = language'})
edit('MainActivity',{
 'outState.putString("app_language_tag",AppLanguage.tag(this))':'outState.putString("app_language_tag",uiLanguageTag)',
 'arrayOf(getString(R.string.timer_off), "15 minutes", "30 minutes", "60 minutes", "90 minutes")':'arrayOf(getString(R.string.timer_off), getString(R.string.sleep_minutes,15), getString(R.string.sleep_minutes,30), getString(R.string.sleep_minutes,60), getString(R.string.sleep_minutes,90))'})
edit('OfflineEqualizer',{
 'class OfflineEqualizer(context: Context':'class OfflineEqualizer(private val context: Context',
 '"EQ đang tắt"':'context.getString(R.string.eq_off)',
 '"EQ chưa được cấp quyền điều khiển phiên phát này"':'context.getString(R.string.eq_no_control)',
 '"EQ đang áp dụng cho file offline • $count dải của thiết bị"':'context.getString(R.string.eq_active,count)',
 '"Thiết bị chưa hỗ trợ EQ cho phiên phát này"':'context.getString(R.string.eq_unsupported)',
 'fun currentStatus(): String':'fun currentStatus(context: Context): String',
 '"Mở video/MP3 trong Downloads để nghe EQ"':'context.getString(R.string.eq_open)'})
edit('EqDialog',{
 'OfflineEqualizer.currentStatus()':'OfflineEqualizer.currentStatus(activity)',
 'android.R.layout.simple_spinner_dropdown_item, choices)':'android.R.layout.simple_spinner_dropdown_item, choices.map { key -> when(key) {\n                "Flat" -> activity.getString(R.string.preset_flat); "Bass" -> activity.getString(R.string.preset_bass_boost)\n                "Treble" -> activity.getString(R.string.preset_treble); "Vocal" -> activity.getString(R.string.preset_vocal)\n                "Custom" -> activity.getString(R.string.custom_eq); else -> key\n            } })'})
extra={
 'library':('Library','Thư viện'), 'channels':('Channels','Kênh'),
 'search_titles':('Search titles or channels','Tìm tiêu đề hoặc kênh'),
 'nothing_playing':('Nothing playing','Chưa phát nội dung'),
 'seek_back':('Seek back 10 seconds','Tua lùi 10 giây'),
 'seek_forward':('Seek forward 10 seconds','Tua tiến 10 giây'),
 'play_pause':('Play or pause','Phát hoặc tạm dừng'),
 'close_player':('Close player','Đóng trình phát'),
 'expand_player':('Expand player','Mở rộng trình phát'),
 'playback_unavailable':('Playback unavailable','Không thể phát video'),
 'playback_notification':('Video playback','Phát video'),
 'shorts':('Shorts','Shorts'), 'pip':('PiP','PiP'), 'eq':('EQ','EQ'),
 'segment_sponsor':('sponsor','tài trợ'), 'segment_promo':('self-promo','tự quảng bá'),
 'segment_interaction':('interaction','tương tác'), 'segment_intro':('intro','mở đầu'),
 'segment_outro':('outro','kết thúc'), 'segment_preview':('preview','xem trước'),
 'segment_music':('off-topic music','nhạc ngoài nội dung'), 'segment_generic':('segment','đoạn'),
 'segment_skipped':('Skipped %1$s • %2$d seconds','Đã bỏ qua %1$s • %2$d giây'),
 'page_error':('Page error • %1$s','Lỗi trang • %1$s'), 'browse_error':('Browse error • %1$s','Lỗi duyệt nội dung • %1$s'),
 'recovery_restore':('%1$s • restoring playback','%1$s • Đang khôi phục phát'),
 'download_interrupted':('Download interrupted. Select Retry to download again.','Tải bị gián đoạn. Chọn Thử lại để tải lại.'),
}
for directory,column in [('values',1),('values-en',0)]:
    path=root/'app/src/main/res'/directory/'text_formats.xml';text=path.read_text(encoding='utf-8')
    additions=''.join(f'    <string name="{key}">{html.escape(pair[column],quote=False)}</string>\n' for key,pair in extra.items())
    text=text.replace('</resources>',additions+'</resources>');path.write_text(text,encoding='utf-8')
lookup={pair[0]:key for key,pair in extra.items() if '%' not in pair[0]}
lookup.update({'Search YouTube':'search_youtube','Playing video':'playing_video'})
for file in (root/'app/src/main/res/layout').glob('*.xml'):
    text=file.read_text(encoding='utf-8')
    for source,key in lookup.items():
        for attr in ['text','hint','contentDescription']:text=text.replace(f'android:{attr}="{source}"',f'android:{attr}="@string/{key}"')
    if file.name=='activity_settings.xml':
        text=text.replace('android:layout_height="46dp"','android:layout_height="wrap_content" android:minHeight="48dp"').replace('android:layout_height="48dp"','android:layout_height="wrap_content" android:minHeight="48dp"')
    file.write_text(text,encoding='utf-8')
edit('MainActivity',{
 '"Skipped ${segmentCategoryLabel(category)} • ${seconds}s"':'getString(R.string.segment_skipped,segmentCategoryLabel(category),seconds)',
 '"$reason • restoring playback"':'getString(R.string.recovery_restore,reason)',
 '"Page error • $message"':'getString(R.string.page_error,message)',
 '"Browse error • $message"':'getString(R.string.browse_error,message)',
 '"sponsor"':'getString(R.string.segment_sponsor)', '"self-promo"':'getString(R.string.segment_promo)',
 '"interaction"':'getString(R.string.segment_interaction)', '"intro"':'getString(R.string.segment_intro)',
 '"outro"':'getString(R.string.segment_outro)', '"preview"':'getString(R.string.segment_preview)',
 '"off-topic music"':'getString(R.string.segment_music)', '"segment"':'getString(R.string.segment_generic)',
 '"Mobile"':'getString(R.string.ui_mobile_metered)',
 ').searchHint':').searchHint.let { if(it == "Search YouTube") getString(R.string.search_youtube) else it }'})
edit('DownloadsActivity',{'"Tác vụ bị gián đoạn. Chọn Retry để tải lại."':'getString(R.string.download_interrupted)'})
edit('PlaybackService',{'private var title = "VoTuibe"':'private var title = "Vợ Tui"',
 'ifBlank { "VoTuibe" }':'ifBlank { getString(R.string.app_name) }',
 '"Pause"':'AppLanguage.wrap(this).getString(R.string.ui_pause)',
 '"Play"':'AppLanguage.wrap(this).getString(R.string.ui_play)',
 '"Next"':'AppLanguage.wrap(this).getString(R.string.ui_next)',
 '"Stop"':'AppLanguage.wrap(this).getString(R.string.ui_stop)'})
edit('DownloadService',{'NotificationChannel("downloads","Downloads",':'NotificationChannel("downloads",AppLanguage.wrap(this).getString(R.string.ui_downloads),',
 '"Tải xuống • $progress%"':'AppLanguage.wrap(this).getString(R.string.download_progress,progress)',
 'Notification.Action.Builder(null,"Hủy",':'Notification.Action.Builder(null,AppLanguage.wrap(this).getString(R.string.ui_cancel),'})
