# Tối ưu VoTuibe 1.5.4

## Thay đổi

- Gộp refresh UI theo frame; tránh cập nhật text/icon không đổi và đăng ký lại
  document-start script khi chính sách không đổi. Tìm kiếm thư viện debounce 150ms.
- Chuyển repair/integrity check lúc khởi động, đọc trạng thái thư viện của video
  và ghi yêu thích/đăng ký sang worker SQLite hiện có.
- Kết quả đọc/ghi chỉ cập nhật màn hình nếu video, navigation generation và
  request vẫn phù hợp. Giữ history writes trong lúc chờ resume; kiểm tra lại
  vị trí phát, tùy chọn resume và thời điểm xóa lịch sử trước seek trì hoãn.
- Dừng refresh UI khi Activity ở nền (ngoại trừ PiP). Dừng vòng đo tọa độ video
  khi WebView mất focus; đo ngay khi focus trở lại để vuốt nhanh vẫn hoạt động.
- Giữ application ID, dữ liệu người dùng, icon và tên VoTuibe. Không đổi schema DB.

## Kiểm chứng

- Build cuối: `build-votuibe-1.5.4-focus.log`, debug/release và lint thành công.
- 27 kiểm thử JVM đạt, gồm 4 kiểm thử mới về khóa navigation và resume; lint 0 lỗi.
- Giả lập API 37: tìm kiếm `Rick` trả đúng một video; Save → Saved → Save hoạt động.
- Trở lại từ thư viện, phát rồi vuốt xuống trong 300ms: mini-player xuất hiện,
  giữ cùng đối tượng video và tiếp tục phát (137.30s → 140.38s).
- Bằng chứng: `branding/ui-search-1.5.4.xml`,
  `branding/ui-mini-final-1.5.4.xml`, `branding/ui-performance-1.5.4.png`.
- Đã cài debug versionCode 23; kết thúc kiểm tra với video tạm dừng và đóng
  cổng debug ADB đã mở cho phiên kiểm tra.

## Giới hạn và bước tiếp theo

Queue toggle/pop vẫn đồng bộ để giữ thứ tự playback. Cần thiết kế giao dịch và
kiểm thử hủy/khôi phục trước khi chuyển các thao tác đó sang worker. Toggle đăng
ký chưa được kiểm chứng trực tiếp vì metadata kênh đang trống, khiến nút bị vô hiệu.

Các snapshot `dist/performance-1.5.4-gfx.txt` và `dist/performance-1.5.4-memory.txt`
thu thập cả phiên, chưa cùng workload với baseline. Giả lập từng báo System UI
không phản hồi sau QuickBoot/build. Không dùng các mẫu này để kết luận mức tăng
FPS hay hiệu năng điện thoại. Cần đo cold/warm start và trace trên cùng thiết bị,
sau đó profile JavaScript, đề xuất và bộ nhớ theo PERFORMANCE_PLAN.md.

Đợt này không sửa bộ lọc quảng cáo; kết quả hiệu năng không chứng minh chặn hết quảng cáo.

## APK

- [Debug APK](dist/VoTuibe-v1.5.4-debug.apk)
- [Release APK chưa ký](dist/VoTuibe-v1.5.4-release-unsigned.apk)
- SHA256 debug: `CAE48BB971938D8791AA09F630D5C1F4E4A814B9150085E3D3DDDB013425F29B`
