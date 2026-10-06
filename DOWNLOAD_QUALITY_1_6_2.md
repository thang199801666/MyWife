# VoTuibe 1.6.2

- Trang video có nút Download với biểu tượng tải xuống và nhãn rõ ràng, ngay sau Share.
- Nút này gọi bridge native đã kiểm tra origin, mở Save To Device / Temporary Save.
- Dùng URL video đang mở, không dùng metadata cũ của video trước đó.
- Cài nút idempotent, khôi phục sau thay đổi DOM/navigation từ sweep đã có; không thêm timer/observer riêng.
- Mỗi video mới bắt đầu với Highest. Khi có danh sách nguồn, chọn độ phân giải tối đa
  thực tế (không cap 1080p/4K); chờ nếu danh sách chưa sẵn sàng.
- Nếu player không cung cấp danh sách, thử API quality với highres.
- Cho phép đổi chất lượng thủ công cho video hiện tại. Video mới thử Highest lại.
- Đây là yêu cầu chất lượng gửi cho player: chất lượng thực tế vẫn phụ thuộc nguồn,
  codec/thiết bị và khả năng của player/mạng.

## Kiểm chứng

- 30 fixture JavaScript player đạt, gồm source list chưa sẵn sàng, max 4320p,
  không ép lại liên tục và đổi thủ công.
- Build/lint: build-votuibe-1.6.2.log.
- APK: dist/VoTuibe-v1.6.2-debug.apk và dist/VoTuibe-v1.6.2-release-unsigned.apk.

Nút được tạo bằng createElement/createElementNS và textContent để tương thích Trusted Types của YouTube, không chèn innerHTML. Đã kiểm tra live: đúng một Download sau Share, click mở hai lựa chọn native; nguồn video mẫu có max 2160p và player đã đạt hd2160, videoWidth=3840/videoHeight=2160. Screenshot: branding/download-action-1.6.2.png; menu: branding/download-options-1.6.2.xml. Đóng gói sửa DOM cuối: build-votuibe-1.6.2-final.log. Không kiểm tra phát 4320p thực tế, chỉ fixture source list.

APK cuối đã cài thành công trên emulator-5554. Không cần script chèn tay: nút Download có đúng 1 bản, previousLabel=Share, click mở hai lựa chọn native. Bằng chứng cuối: branding/download-quality-final-1.6.2.json và branding/download-final-options-1.6.2.xml. SHA-256 debug: A2321784859064B49BBD5858CDA4177B9AFE0F3D655ED8BDBF2275C4199FC152.

Release cài được: dist/VoTuibe-v1.6.2-release.apk, 199831548 bytes. APK release tối ưu không debuggable, ký bằng androiddebugkey hiện có (khóa thử nghiệm, không phải khóa production); chứng chỉ giống debug 1.6.2 để cập nhật giữ dữ liệu. Zipalign 16 KB và chữ ký v2/v3 đã xác minh. SHA-256: B270FE80E489071B2BCB32E840768D3386DF0638AF6F3AEFD50091119E4EBB1F.
