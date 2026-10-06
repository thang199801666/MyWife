# Gợi ý khi gõ Search — VoTuibe 1.5.7

Ô nhập native trước đây chỉ nhận từ khóa để submit, không có autocomplete.
Bản này hiển thị danh sách gợi ý ngay dưới ô Search khi gõ, biểu tượng tìm kiếm
mỗi hàng, chạm để tìm ngay. Back, submit, mất focus hoặc Activity pause đóng
danh sách và vô hiệu kết quả cũ. Không thay đổi dữ liệu đề xuất Home/thư viện.

Dùng endpoint HTTPS `suggestqueries.google.com/complete/search` với
`client=firefox`, `ds=yt`, `hl=vi`. Chỉ gửi từ khóa nhập, không gửi lịch sử xem.
Parser kiểm tra từ khóa trả về, loại trùng/giá trị không phải chuỗi, giới hạn
10 gợi ý, mỗi chuỗi 160 ký tự và response 32KiB. Redirect bị tắt.

Debounce 250ms; một worker và tối đa một tác vụ chờ. Hủy/purge yêu cầu cũ,
generation guard bỏ kết quả sau đổi từ khóa hoặc đóng màn hình. Timeout kết nối
và đọc 2.5s; cache tối đa 20 truy vấn trong phiên. Khi chưa có gợi ý hoặc mạng lỗi,
người dùng vẫn chạm hàng từ khóa hiện tại hoặc submit bằng bàn phím.

Gợi ý là từ khóa văn bản; chưa có thẻ thực thể với avatar/mô tả kênh như một số
phiên bản giao diện YouTube desktop.

## Kiểm chứng

- 31 JVM tests đạt, gồm parser completions, mismatch keyword, Unicode,
  loại giá trị lỗi/trùng và giới hạn số hàng. Build debug/release, lint thành công.
- API 37 emulator: gõ `new` hiển thị 10 gợi ý thực như newjeans, newcastle,
  new heart, new trick, newjeans playlist; dữ liệu không hardcode.
- Chạm new trick mở `/results?search_query=new+trick`, kết quả có tiêu đề và ảnh.
- Bằng chứng: `branding/autocomplete-1.5.7.xml`, `branding/autocomplete-1.5.7.png`.
- Build cuối: `build-votuibe-1.5.7-final.log`. APK debug versionCode 26.

## APK

- `dist/VoTuibe-v1.5.7-debug.apk`
- `dist/VoTuibe-v1.5.7-release-unsigned.apk`
