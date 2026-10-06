# VoTuibe 1.6.3 — APK theo kiến trúc CPU

APK 1.6.2 universal đã ký: 199,831,548 bytes. Trong ZIP, native libraries nén
chiếm khoảng 186.5 MiB: arm64-v8a 47.8, armeabi-v7a 41.5, x86_64 50.7, x86 46.5.
Từng bộ có Python, FFmpeg và QuickJS để tải video, ghép audio/video nguồn cao nhất
và chuyển MP3. Nén ZIP thêm không giúp đáng kể vì Python/FFmpeg đã là bundle ZIP.
Code Kotlin đã bật R8 và resource shrinking; classes.dex chỉ khoảng 0.5 MiB nén.

## Thay đổi

Build với `gradlew.bat assembleRelease -PsplitApks=true` tạo APK riêng cho mỗi
kiến trúc cùng một universal dự phòng. Mỗi APK riêng chỉ mang native libraries
cho CPU tương ứng; không bỏ chức năng tải video/MP3, không đổi cách phát/chất lượng.
Build debug thông thường vẫn universal để dùng với giả lập như trước.

- arm64-v8a: thiết bị ARM 64-bit tương thích.
- armeabi-v7a: thiết bị ARM 32-bit tương thích.
- x86_64/x86: giả lập hoặc thiết bị Intel tương ứng.
- universal: hỗ trợ cả bốn kiến trúc, vẫn dung lượng lớn.

Ký bằng khóa thử nghiệm hiện có như bản 1.6.2 để cập nhật giữ dữ liệu. Đây chưa
phải khóa production. Không giảm bitrate/chất lượng để làm APK nhỏ; chất lượng
video tải về không phụ thuộc dung lượng APK. Tách APK giảm file cài/không gian
lưu APK; không phải bằng chứng cải thiện FPS hay RAM lúc xem video.

Log build: build-votuibe-1.6.3.log.

arm64-v8a: 54459140 bytes (54.46 MB), signed release: dist/VoTuibe-v1.6.3-release-arm64-v8a.apk.

armeabi-v7a: 47807248 bytes (47.81 MB), signed release: dist/VoTuibe-v1.6.3-release-armeabi-v7a.apk.

x86_64: 57449202 bytes (57.45 MB), signed release: dist/VoTuibe-v1.6.3-release-x86_64.apk.

x86: 53045984 bytes (53.05 MB), signed release: dist/VoTuibe-v1.6.3-release-x86.apk.

universal: 199831548 bytes (199.83 MB), signed release: dist/VoTuibe-v1.6.3-release-universal.apk.

Build release và lintVital đạt; zipalign 16KB và chữ ký đã verify cho cả 5 APK. Từng bản riêng được kiểm tra có đúng 1 ABI; ARM64 giữ đầy đủ bundle Python/FFmpeg/QuickJS. ARM64 nhỏ hơn universal khoảng 72.75%. Bằng chứng: dist/apk-sizes-1.6.3.json và các file *-verification.txt. Chưa cài kiểm tra trên điện thoại ARM thực tế trong lượt này.
