# VoTuibe 1.6.1 — âm thanh và About

- Player WebView bật âm thanh nội dung mặc định (`muted=false`, `volume=1`).
- Đồng bộ trạng thái mute qua `player.unMute()` khi API của player có sẵn.
- Khôi phục âm thanh khi metadata/play/volume thay đổi hoặc phần tử video được thay thế.
- Không đặt lại âm lượng hệ thống: người dùng chỉnh bằng nút âm lượng của điện thoại hoặc thao tác volume hiện có.
- Main, You và player offline chọn STREAM_MUSIC cho phím âm lượng.
- Ẩn Tap to unmute, mute và thanh âm lượng riêng của website trong player.
- Browse preview giữ nguyên chế độ im lặng; xử lý mute quảng cáo hiện có vẫn hoạt động riêng.
- You có nút About, mở mô tả app, phiên bản và nguyên văn câu:
  "This App I wrote for my wife and my unborn child".

## Kiểm chứng

- 29 fixture player JavaScript đạt (`tests/adblock-script.cjs`), gồm khôi phục
  âm thanh nội dung, không mở tiếng browse preview và chuyển quảng cáo → nội dung.
- Build/lint/JVM: `build-votuibe-1.6.1.log`.
- APK: `dist/VoTuibe-v1.6.1-debug.apk`, `dist/VoTuibe-v1.6.1-release-unsigned.apk`.

Kiểm tra thực tế emulator API 37: video đang phát có muted=false, volume=1, playerMuted=false; nút ytp-unmute display=none; diagnostics errors=0. Bằng chứng: branding/audio-default-1.6.1.json. About đã mở từ You, hiển thị phiên bản 1.6.1 và nguyên văn lời đề tặng (branding/about-dialog-1.6.1.png). 34 JVM tests đạt, lint 0 lỗi, build debug/release thành công. Bản debug đã cài cập nhật lên giả lập, giữ dữ liệu cũ.
