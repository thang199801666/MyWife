"""One-off extraction of formatted UI strings. Do not rerun after manual changes."""
from pathlib import Path
import html
root=Path(__file__).resolve().parents[1]; java=root/'app/src/main/java/com/example/videoshield'
strings={
 'gesture_sensitivity':('Gesture sensitivity %1$s','Độ nhạy thao tác %1$s'),
 'double_tap_seek':('Double-tap seek %1$d seconds','Chạm hai lần để tua %1$d giây'),
 'wifi_quality':('Wi-Fi quality %1$s','Chất lượng Wi-Fi %1$s'),
 'mobile_quality':('Mobile data quality %1$s','Chất lượng dữ liệu di động %1$s'),
 'allowed_channels':('Allowed channels: %1$d','Kênh ngoại lệ: %1$d'),
 'rules_rolled_back':('Rolled back to rules v%1$d','Đã khôi phục quy tắc v%1$d'),
 'rules_updated':('Rules updated to v%1$d','Đã cập nhật quy tắc v%1$d'),
 'downloads_summary':('%1$d items • Offline on this device','%1$d mục • Xem offline trên thiết bị'),
 'downloads_active':('%1$d items • %2$d downloading','%1$d mục • %2$d đang tải'),
 'download_progress':('Downloading • %1$d%%','Đang tải • %1$d%%'),
 'options_for':('Options for %1$s','Tùy chọn cho %1$s'),
 'queue_count':('Queue • %1$d','Hàng đợi • %1$d'),
 'video_count':('%1$d videos • On this device','%1$d video • Trên thiết bị'),
 'channel_count':('%1$d channels • On this device','%1$d kênh • Trên thiết bị'),
 'clear_section':('Clear %1$s?','Xóa %1$s?'),
 'hide_channel':('Hide channel: %1$s','Ẩn kênh: %1$s'),
 'up_next':('Up next: %1$s','Tiếp theo: %1$s'),
 'playback_speed':('Playback speed %1$s','Tốc độ phát %1$s'),
 'resume_at':('Resuming at %1$s','Tiếp tục từ %1$s'),
 'quality_profile':('%1$s quality','Chất lượng %1$s'),
 'quality_selected':('%1$s quality %2$s','Chất lượng %1$s %2$s'),
 'sleep_minutes':('%1$d minutes','%1$d phút'),
 'sleep_button':('Sleep %1$d min','Hẹn giờ %1$d phút'),
 'pause_after':('Playback will pause in %1$d minutes','Video sẽ tạm dừng sau %1$d phút'),
 'ads_allowed':('Ads allowed for %1$s','Đã cho phép quảng cáo của %1$s'),
 'ads_protected':('Ad filtering restored for %1$s','Đã khôi phục chặn quảng cáo của %1$s'),
 'search_query':('Search • %1$s','Tìm kiếm • %1$s'),
 'download_highest':('Highest source (%1$sp)','Nguồn cao nhất (%1$sp)'),
 'download_best_mp3':('Best source → MP3 (VBR)','Nguồn tốt nhất → MP3 (VBR)'),
 'recommend_followed':('From a channel you follow','Từ kênh bạn đăng ký'),
 'recommend_watched':('Because you watch %1$s','Vì bạn xem %1$s'),
 'recommend_interest':('Matches your interest: %1$s','Hợp sở thích của bạn: %1$s'),
 'home_hint':('Watch or search for videos to discover more content you may like.','Xem hoặc tìm kiếm video để khám phá thêm nội dung phù hợp với bạn.'),
 'history_suggestions':('Suggestions from your watch history','Gợi ý theo lịch sử xem'),
 'next_videos':('Videos to play next','Video phát tiếp theo'),
 'favorite_videos':('Your favorite videos','Các video yêu thích'),
 'offline_videos':('Video and MP3 for offline playback','Video và MP3 để xem offline'),
 'offline_eq':('Adjust offline playback audio','Điều chỉnh âm thanh phát offline'),
 'customize_experience':('Customize your experience','Tùy chỉnh trải nghiệm'),
 'your_library':('Your library','Thư viện của bạn'),
 'tune_recommendations':('Tune recommendations','Điều chỉnh gợi ý'),
 'clear_list':('Clear this list','Xóa danh sách này'),
 'cleared':('Cleared','Đã xóa'),
 'hide_video':('Hide this video','Ẩn video này'),
 'hidden_done':('Hidden. Use Tune to restore suggestions.','Đã ẩn. Dùng Điều chỉnh gợi ý để khôi phục.'),
 'restore_videos':('Restore videos','Khôi phục video'),
 'restored_videos':('Hidden videos restored','Đã khôi phục video đã ẩn'),
 'saved_queue':('Saved in Queue','Đã lưu vào hàng đợi'),
 'add_queue':('Add to queue','Thêm vào hàng đợi'),
 'play_next':('Play next','Phát tiếp theo'),
 'save_favorites':('Save to favorites','Lưu vào yêu thích'),
 'remove_favorites':('Remove from favorites','Xóa khỏi yêu thích'),
 'move_up':('Move up','Chuyển lên'),
 'move_down':('Move down','Chuyển xuống'),
 'remove':('Remove','Xóa'),
 'saved_favorites':('Saved to Favorites','Đã lưu vào yêu thích'),
 'removed_favorites':('Removed from Favorites','Đã xóa khỏi yêu thích'),
 'protect':('Protect','Bảo vệ'),
 'queued':('Queued','Đã thêm hàng đợi'),
 'repeat_on':('Repeat on','Đã bật lặp lại'),
 'playing_video':('Playing video','Đang phát video'),
 'youtube_video':('YouTube video','Video YouTube'),
 'search_youtube':('Search YouTube','Tìm kiếm YouTube'),
 'safe_mode':('Compatibility safe mode','Chế độ an toàn tương thích'),
 'enhanced_playback':('Enhanced playback','Phát video nâng cao'),
 'timer_off':('Off','Tắt'),
 'back_seconds':('Back 10 seconds','Lùi 10 giây'),
 'forward_seconds':('Forward 10 seconds','Tiến 10 giây'),
 'eq_off':('EQ is off','EQ đang tắt'),
 'eq_no_control':('EQ has no control of this playback session','EQ chưa được cấp quyền điều khiển phiên phát này'),
 'eq_unsupported':('EQ is not supported for this playback session','Thiết bị chưa hỗ trợ EQ cho phiên phát này'),
 'eq_open':('Open video/MP3 in Downloads to hear EQ','Mở video/MP3 trong Nội dung tải xuống để nghe EQ'),
 'eq_active':('EQ applied to offline playback • %1$d device bands','EQ đang áp dụng cho file offline • %1$d dải của thiết bị'),
 'custom_eq':('Custom','Tùy chỉnh'),
 'preset_flat':('Flat','Trung tính'),
 'preset_bass_boost':('Bass boost','Tăng âm trầm'),
 'preset_vocal':('Vocal','Giọng hát'),
 'preset_treble':('Treble boost','Tăng âm cao'),
 'preset_soft':('Soft','Êm dịu'),
}
def xml(value):return html.escape(value,quote=False).replace("'","\\'").replace('"','\\"')
for directory,column in [('values',1),('values-en',0)]:
    (root/'app/src/main/res'/directory/'text_formats.xml').write_text('<resources>\n'+''.join(f'    <string name="{key}">{xml(pair[column])}</string>\n' for key,pair in strings.items())+'</resources>\n',encoding='utf-8')
simple={pair[0]:key for key,pair in strings.items() if '%' not in pair[0]}
simple.update({pair[1]:key for key,pair in strings.items() if '%' not in pair[1]})
files=['MainActivity','SettingsActivity','LibraryActivity','DownloadsActivity','SaveVideoController','DownloadListAdapter']
for name in files:
    path=java/(name+'.kt'); text=path.read_text(encoding='utf-8');prefix='activity.' if name in ['SaveVideoController','DownloadListAdapter'] else ''
    for source,key in sorted(simple.items(),key=lambda item:-len(item[0])):
        text=text.replace('"'+source+'"',prefix+'getString(R.string.'+key+')')
    path.write_text(text,encoding='utf-8')
changes={
 'SettingsActivity':{
  '"Gesture sensitivity ${formatSensitivity(p.gestureSensitivity)}"':'getString(R.string.gesture_sensitivity, formatSensitivity(p.gestureSensitivity))',
  '"Double-tap seek ${p.doubleTapSeekSeconds}s"':'getString(R.string.double_tap_seek, p.doubleTapSeekSeconds)',
  '"Wi-Fi / unmetered quality ${qualityLabel(p.preferredQuality)}"':'getString(R.string.wifi_quality, qualityLabel(p.preferredQuality))',
  '"Mobile / metered quality ${qualityLabel(p.preferredQualityMobile)}"':'getString(R.string.mobile_quality, qualityLabel(p.preferredQualityMobile))',
  '"Allowed channels: ${p.whitelistedChannels.size}"':'getString(R.string.allowed_channels, p.whitelistedChannels.size)',
  '"Rolled back to rules v${rules.active().ruleVersion}"':'getString(R.string.rules_rolled_back, rules.active().ruleVersion)'},
 'DownloadsActivity':{
  '"${rows.size} mục${if(active>0) " • $active đang tải" else " • Xem offline trên thiết bị"}"':'if(active>0) getString(R.string.downloads_active,rows.size,active) else getString(R.string.downloads_summary,rows.size)'},
 'DownloadListAdapter':{
  '"Đang tải • ${job.progress}%"':'activity.getString(R.string.download_progress,job.progress)',
  '"Options for ${job.title}"':'activity.getString(R.string.options_for,job.title)'},
 'LibraryActivity':{
  '"Queue • ${loadedRows.size}"':'getString(R.string.queue_count,loadedRows.size)',
  '"${rows.size} ${if (mode == MODE_SUBSCRIPTIONS) "channels" else "videos"} • On this device"':'getString(if (mode == MODE_SUBSCRIPTIONS) R.string.channel_count else R.string.video_count,rows.size)',
  '"Clear ${title.text}?"':'getString(R.string.clear_section,title.text)',
  '"Hide channel: ${video.channel}"':'getString(R.string.hide_channel,video.channel)',
  '"${row.item.name}\\nLocal subscription"':'"${row.item.name}\\n${getString(R.string.ui_local_subscription)}"'},
 'MainActivity':{
  '"Rules updated to v${result.version}"':'getString(R.string.rules_updated,result.version)',
  '"Up next: ${next.title}"':'getString(R.string.up_next,next.title)',
  '"Playback speed ${formatSpeed(next)}"':'getString(R.string.playback_speed,formatSpeed(next))',
  '"Resuming at ${formatPosition(target)}"':'getString(R.string.resume_at,formatPosition(target))',
  '"$profileName quality"':'getString(R.string.quality_profile,profileName)',
  '"$profileName quality ${labels[which]}"':'getString(R.string.quality_selected,profileName,labels[which])',
  '"$profile quality ${qualityLabel(effectivePreferredQuality())}"':'getString(R.string.quality_selected,profile,qualityLabel(effectivePreferredQuality()))',
  '"Playback will pause in $minutes minutes"':'getString(R.string.pause_after,minutes)',
  '"Ads allowed for $channel"':'getString(R.string.ads_allowed,channel)',
  '"Ad filtering restored for $channel"':'getString(R.string.ads_protected,channel)',
  '"Search • ${browseRoute.query}"':'getString(R.string.search_query,browseRoute.query)',
  '"Sleep ${sleepMinutes}m"':'getString(R.string.sleep_button,sleepMinutes)',
  '"Enhanced playback$network$health"':'getString(R.string.enhanced_playback)+network+health'}
}
for name,replacements in changes.items():
    path=java/(name+'.kt');text=path.read_text(encoding='utf-8')
    for source,target in replacements.items():
        if source not in text:print('Missing',name,source)
        text=text.replace(source,target)
    path.write_text(text,encoding='utf-8')
