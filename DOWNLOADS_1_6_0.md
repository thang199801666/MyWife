# Lưu video / MP3 — VoTuibe 1.6.0

## Cách dùng

Khi đang mở clip, chạm Save → Save To Device hoặc Temporary Save → Video/MP3
→ chọn chất lượng. Giữ Save để thêm/bỏ yêu thích như trước. Nút + → Downloads
mở danh sách tải; chạm hàng để phát offline, hủy, Retry hoặc xóa.

- Save To Device: Android 29+ xuất qua MediaStore vào `Download/VoTuibe`, không
  xin quyền đọc/ghi toàn bộ bộ nhớ. Android 26–28 dùng bộ chọn file của hệ thống.
  Xóa hàng trong app không xóa file đã xuất vào thiết bị.
- Temporary Save: file riêng của app, giữ 30 ngày kể từ hoàn tất. JobScheduler
  kiểm tra hàng ngày; app cũng kiểm tra khi khởi động, mở Downloads và trước phát.
  File hết hạn không được mở lại và sẽ được dọn. Lịch chạy nền có thể bị Android
  trì hoãn; không hứa xóa vật lý chính xác từng giây ở mốc 30 ngày.
- Video: đọc chất lượng thực từ nguồn. Highest dùng `bestvideo+bestaudio/best`,
  không ép về 1080p/2160p. Ghép/remux MKV để giữ codec nguồn và tránh giảm chất lượng.
- MP3: lấy nguồn âm thanh tốt nhất rồi chuyển sang MP3 VBR hoặc 320/192/128 kbps.
  Chuyển đổi MP3 là lossy; bitrate cao không tạo thêm chất lượng so với nguồn.

## Tải nền và dữ liệu

yt-dlp + FFmpeg khởi tạo trên worker; lần đầu có giải nén native và kiểm tra bản
yt-dlp stable. Foreground dataSync service tải tuần tự, hiển thị tiến độ trong app
và thông báo nếu được phép. Không hỏi lại quyền thông báo. Có hủy và Retry; trạng
thái bị gián đoạn có thể thử lại. Không tự phục hồi sau force-stop.

File xuất MediaStore được đánh dấu pending trong lúc copy; lưu URI để Retry có
thể khôi phục sau gián đoạn. Bản tạm được chia thư mục theo UUID, metadata ghi
AtomicFile, FileProvider chỉ chia sẻ thư mục offline. Dữ liệu lịch sử/yêu thích
và application ID được giữ nguyên. Đóng màn hình trong lúc xử lý Retry sẽ giữ
tác vụ queued, tránh khởi động FGS từ Activity đã rời nền trước.

Chỉ nhận URL clip YouTube; không tải playlist/livestream đang phát, không lấy
định dạng DRM. Nguồn bị hạn chế tài khoản/vùng hoặc lỗi mạng có thể không tải
được; app hiển thị lỗi thay vì báo đã lưu. Khả năng phát codec nguồn phụ thuộc
thiết bị. APK universal lớn hơn do chứa Python/FFmpeg cho bốn ABI.

## Kiểm chứng

- 34 JVM tests đạt, lint 0 lỗi: `build-votuibe-1.6.0-final.log`.
- Bộ test mới kiểm tra nguồn tối đa 4320p, loại DRM, không cap resolution,
  tên file an toàn, từ chối live và ranh giới 30 ngày từ completedAt.
- Đóng gói cuối: `build-votuibe-1.6.0-package.log`; giữ nguyên policy đã kiểm thử.
- Giả lập API 37: nguồn Rick Astley trả 2160p/1440p/1080p/720p/480p/360p/240p/144p.
- Temporary video 144p tải hoàn tất; FFmpeg kiểm tra MKV có AV1 256×144, 25fps
  và Opus stereo 48kHz, thời lượng 3:33. Mở được trong player offline.
- MP3 Best source VBR: tác vụ bị hủy ở lần đầu; Retry đã hoàn tất. File thực
  trong Downloads/VoTuibe có 7,045,580 bytes, URI `content://media/external/downloads/125`.
  Mở được bằng player offline của app.
- Bằng chứng: `branding/download-source-1.6.0.xml`, `branding/mp3-quality-1.6.0.xml`,
  `branding/downloads-final-1.6.0.png`. File thử vẫn giữ trên giả lập.
- Chưa tải nguyên file 2160p/4320p, chưa kiểm tra trên điện thoại hoặc Android 26–28;
  chưa chạy thử đủ 30 ngày thực. Expiry được kiểm tra bằng policy test.

## APK và nguồn thư viện

- `dist/VoTuibe-v1.6.0-debug.apk`
- `dist/VoTuibe-v1.6.0-release-unsigned.apk`
- Debug universal khoảng 205 MB (Python/FFmpeg), versionCode 27.
- [youtubedl-android 0.18.1](https://github.com/yausername/youtubedl-android)
- [yt-dlp](https://github.com/yt-dlp/yt-dlp)
- [FFmpeg](https://ffmpeg.org/)

SHA-256 debug APK cuối: 0824C2BB2D0C7C45728129A27C06ABB14E6FE7AEE174C3AB181AAD677489B219. Đã cài cập nhật thành công trên emulator-5554, giữ dữ liệu offline.
