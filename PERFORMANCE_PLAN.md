# Kế hoạch tối ưu VoTuibe

Mục tiêu: giảm công việc trên luồng UI và WebView, giữ ổn định playback,
vuốt thu nhỏ, bộ lọc quảng cáo và dữ liệu cá nhân. Không lấy độ mượt giả lập
làm bằng chứng về hiệu năng điện thoại nếu chưa đo trên cùng thiết bị.

## Đợt 1 — giảm cập nhật và cấp phát lặp (đã triển khai)

1. Gộp các yêu cầu refresh UI trong cùng frame, luôn vẽ trạng thái mới nhất.
2. Chỉ cập nhật text và icon navigation khi giá trị thay đổi.
3. Giữ đăng ký document-start script nếu chính sách không đổi; vẫn cập nhật
   khi bật/tắt bảo vệ, đổi safe mode hoặc whitelist.
4. Debounce tìm kiếm thư viện 150ms; hủy callback khi màn hình bị đóng.
5. Dừng đo bounds phục vụ vuốt khi WebView mất focus. Khi trở lại, tiếp tục
   làm nóng tọa độ để thao tác nhanh vẫn hoạt động.

Đã build debug/release, chạy lint và JVM; kiểm tra tìm kiếm thư viện và
phát → vuốt thu nhỏ trên giả lập. JavaScript regression đã chạy ở đợt trước;
đợt này không sửa JavaScript. Chưa có benchmark đối chứng để lượng hóa FPS.

## Đợt 2 — SQLite và khởi động (đã triển khai một phần)

- Chuyển repair/integrity check và truy vấn trạng thái thư viện khỏi luồng UI.
- Đọc trạng thái video theo một tác vụ thay vì nhiều truy vấn rời; dùng video
  ID và navigation generation để bỏ kết quả cũ.
- Giữ thứ tự resume seek, history writes và queue mutations. Kiểm tra đổi
  video nhanh, đóng Activity giữa truy vấn, clear history và auto-advance.
- Đo cold/warm start; chỉ cache kết quả khi có quy tắc invalidation rõ ràng.

Đã chuyển kiểm tra khởi động, đọc trạng thái playback và ghi yêu thích/đăng ký
sang worker. Có kiểm thử khóa video/navigation và điều kiện resume; đã kiểm tra
toggle yêu thích trên giả lập. Chưa kiểm tra được toggle đăng ký trực tiếp vì
trang đang phát không trả metadata kênh. Queue toggle/pop vẫn chạy đồng bộ;
cần xử lý thứ tự giao dịch và kiểm thử hủy/khôi phục trước khi chuyển sang nền.
Đo khởi động đối chứng và benchmark trên điện thoại thật còn chờ thực hiện.

## Đợt 3 — chi phí JavaScript, đề xuất và bộ nhớ (đã bắt đầu)
 
Đã bắt đầu phần thumbnail trong 1.5.5: gộp request theo video ID, dùng tham chiếu
yếu cho view, giữ ảnh khi bind lại cùng video. Search có fallback ảnh gần viewport,
batch mutation 250ms và không quét khi trang ẩn. Profiling toàn bộ JS/heap,
đề xuất và benchmark đối chứng vẫn còn chờ thực hiện.

- Profile sweep, observer, discovery và bộ lọc quảng cáo trên trang thật.
- Tách quét DOM khỏi nhịp cập nhật playback nếu đo cho thấy có lợi; vẫn phản
  ứng nhanh với preroll/midroll, Skip mới xuất hiện và chuyển trang SPA.
- Hủy công việc đề xuất đã lỗi thời; kiểm tra giới hạn hàng đợi và dữ liệu
  lớn. Tránh giữ Activity/view trong tác vụ nền sau onDestroy.
- Theo dõi cache thumbnail, số WebView và heap khi xoay màn hình, mở/đóng
  thư viện, PiP và phát nền nhiều lần.

## Đo và tiêu chí nghiệm thu

- Ghi phiên bản, thiết bị, cấu hình, trạng thái media và workload trước/sau.
- Dùng gfxinfo/trace cho UI, timing JavaScript cho sweep và log cold/warm
  start. Ghi rõ cỡ mẫu và giới hạn so sánh; không tuyên bố tăng FPS theo cảm giác.
- Không đổi codec/chất lượng mặc định hoặc giảm tần suất xử lý quảng cáo chỉ
  để làm đẹp benchmark.
- Hoàn tất từng đợt bằng artifact APK và báo cáo thay đổi/kiểm tra. Các đợt
  sau phụ thuộc kết quả đo, không coi là đã hoàn tất khi mới ghi kế hoạch.
