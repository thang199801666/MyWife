# Search và tải thumbnail — VoTuibe 1.5.5

Search native yêu cầu kết quả video (`sp=EgIQAQ%3D%3D`) để hiển thị thẻ clip có
tiêu đề và ảnh xem trước trước các khối kênh/album. Link nhập trực tiếp vẫn giữ
hành vi cũ. Tên clip và metadata do trang YouTube trả về; chạm thẻ mở player native.
Preview trong bản này là ảnh thumbnail, chưa có tự phát đoạn video trong thẻ.

SearchPreviewScript sửa ảnh trống hoặc tải lỗi bằng thumbnail đúng video ID,
không thay ảnh đang tải thành công và không thay tiêu đề. Chỉ xử lý kết quả gần
viewport; gom mutation trong 250ms, dừng quét khi ẩn hoặc ngoài trang Search.
Gỡ các observer target đã bị tháo khỏi DOM khi đổi từ khóa trong SPA.
Không tạo thêm WebView hay truy xuất URL media.

VideoThumbnailLoader dùng một request cho mỗi video ID với nhiều subscriber yếu,
không giữ ImageView sống trong tác vụ download. Giữ ảnh khi bind lại cùng video,
kiểm tra tag trước giao ảnh cho row tái sử dụng, dọn subscriber/cache khi đóng.
Cache 4MiB, hai worker và giới hạn hàng đợi hiện có được giữ nguyên.

## Kiểm tra

- Debug/release build và lint thành công: `build-votuibe-1.5.5-final.log`; lint 0 lỗi.
- 29 JVM tests đạt; test keyword có dấu/ký tự `&`, video filter, URL và input trống.
- Fixture Search: ảnh trống/lỗi, giữ ảnh gốc, video ID tái sử dụng, ngoại domain,
  ngoài viewport, batching, dọn target rời DOM, trang ẩn và ngoài Search đều đạt.
- 28 player regression fixtures và fixture SPA navigation đạt.
- API 37 emulator: nhập Search native `rick stley` (YouTube tự sửa thành Rick Astley),
  thấy tiêu đề và ảnh 686px của các kết quả đầu tiên. Làm trống thumbnail đầu tiên
  bằng probe: fallback `mqdefault.jpg` tải thành công, rộng 320px, tên clip giữ nguyên.
- Chạm kết quả thứ hai mở player `yPYZpwSpKmA`.
- Ảnh đối chiếu: `branding/search-before-1.5.5.png`, `branding/search-after-1.5.5.png`.

Ảnh trước/sau dùng truy vấn và cấu hình filter khác nhau để kiểm tra bố cục,
không phải benchmark hiệu năng. Chưa lượng hóa FPS, thời gian search hoặc đo
trên điện thoại thật; việc tự phát video preview vẫn cần triển khai riêng.

## APK

- `dist/VoTuibe-v1.5.5-debug.apk` — đã cài lên giả lập, giữ dữ liệu app.
- `dist/VoTuibe-v1.5.5-release-unsigned.apk`.
- SHA256 debug: `A948FA127C21867EFCA54D02DDFBC2996141CE16F2F7D386E12B6E5E96BD8E35`.
