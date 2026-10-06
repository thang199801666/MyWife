# Home và quyền thông báo — VoTuibe 1.5.6

## Quyền thông báo

Lưu `notification_permission_asked` trước khi gọi requestPermissions. Sau lần hỏi
đầu, app không gọi lại khi khởi động, dù từ chối hoặc đóng hộp thoại. Khi nâng cấp,
quyền đã cấp hoặc rationale cho lần từ chối trước đó cũng đánh dấu đã quyết định.
Người dùng vẫn có thể đổi quyền trong cài đặt Android. Xóa dữ liệu/cài mới làm
mất cờ này như các tùy chọn khác.

Giả lập: đã lưu cờ true; thu hồi POST_NOTIFICATIONS, force-stop rồi mở lại.
Permission granted=false nhưng UI dump chỉ có package app, không có dialog quyền.
Khôi phục quyền granted=true ban đầu sau kiểm tra. Bằng chứng:
`branding/notification-denied-1.5.6.xml`. Chưa kiểm tra lần cài sạch trên điện thoại.

## Đề xuất Home

Nguyên nhân: bộ đề xuất có sẵn nhưng chỉ được nối vào For you trong thư viện.
Home trước đây chỉ hiển thị nội dung website YouTube.

Thêm khối “Dành cho bạn” trên Home với tối đa 12 kết quả từ bộ xếp hạng hiện có:
lịch sử/thời gian xem, yêu thích, kênh theo dõi và video thu thập từ các trang đã
browse. Loại video đã xem, video/kênh đã ẩn theo quy tắc hiện có. Tất cả truy vấn
ở library worker; kết quả cũ bị bỏ khi request, route hoặc tùy chọn thay đổi.
Refresh khi vào Home, page ready, resume và sau thu thập candidate. Không dựng
lại DOM nếu ranking không đổi; ảnh lazy/async. Không tạo WebView mới.

Tắt cá nhân hóa hoặc ghi lịch sử sẽ gỡ khối đề xuất. Nếu chưa có candidate chưa
xem thì hiển thị hướng dẫn tìm/xem video; bộ xếp hạng không tự tìm video ngoài
danh sách đã thu thập. Dữ liệu và schema thư viện được giữ nguyên.

Kiểm tra giả lập API 37: Home hiển thị các clip Rick Astley phù hợp lịch sử,
tiêu đề và thumbnail 320px tải thành công. Xem
`branding/home-1.5.6-final.png`. Fixture JS kiểm tra thẻ/link, title an toàn,
ảnh lazy, cache render, empty state, opt-out và route isolation đều đạt.

## Build

`build-votuibe-1.5.6-final.log`: debug/release và lint đạt; 29 JVM tests không lỗi,
lint 0 lỗi. Đã cài debug versionCode 25 lên giả lập. Video kiểm tra đã tạm dừng,
cổng debug được đóng. Chưa có benchmark đối chứng hiệu năng trên điện thoại.

- `dist/VoTuibe-v1.5.6-debug.apk`
- `dist/VoTuibe-v1.5.6-release-unsigned.apk`
- SHA256 debug: `AB8B5DE374194218DADDA144691FA38F5A38477ED85A6B16D4798954EAD08AB2`
