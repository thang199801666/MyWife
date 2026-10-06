# Nguồn cập nhật Vợ Tui

Repo: https://github.com/thang199801666/MyWife

Bản base hiện tại là **0.1.5**, `versionCode = 46`. Các số 1.x trước đó là nhãn
trong quá trình phát triển. App so sánh `versionCode`, không so sánh thứ tự
`versionName`, nên bản đã cài 1.7.0 (code 40) vẫn cập nhật được lên base 0.1.0.
Các bản base tiếp theo dùng 0.1.1, 0.1.2… và luôn tăng versionCode.

App đọc release ổn định mới nhất qua:
`https://api.github.com/repos/thang199801666/MyWife/releases/latest`.
Draft và prerelease không được dùng làm bản cập nhật. Không nhúng token GitHub trong APK.

Mỗi release cần có:

- `VoTui-vX.Y.Z-release-arm64-v8a.apk`
- `update.json` (schemaVersion, packageName, versionCode, versionName, minSdk, abi,
  assetName, size, sha256)
- `SHA256SUMS.txt`

App đối chiếu manifest với tag và asset của chính repo này, chỉ tải qua HTTPS trên
GitHub/CDN GitHub. Trước khi mở trình cài đặt, app kiểm tra SHA-256, dung lượng,
package name, versionCode/versionName và chứng chỉ trùng với bản đang cài.
Chọn **Cài đặt → Kiểm tra cập nhật**, hoặc **Bạn → About → Kiểm tra cập nhật**.
Chỉ tải khi người dùng chọn, không hỏi quyền cài đặt lúc khởi động, không cài ngầm.
Rời màn hình cập nhật sẽ hủy tải đang chạy; có thể thử lại. File đã xác minh giữ
trong cache riêng để tránh tải lại; Android vẫn xác nhận việc cài và có thể yêu cầu
bật “Cho phép từ nguồn này”. Không dùng APK ARM64 để cập nhật giả lập x86.

## Phát hành bản tiếp theo trên Windows

1. Tăng cả `versionCode` và `versionName` trong `app/build.gradle.kts`.
2. Tạo `releases/vX.Y.Z.md` ghi thay đổi của bản đó.
3. Kiểm tra, commit và push code lên `main`.
4. Với Android SDK 37, build-tools 36.0.0 và JDK 25 đã cài, chạy:

```powershell
gh auth login
./tools/release.ps1 -Publish
```

Script build release ARM64, chạy unit test/lint, ký, xác minh và tạo manifest,
upload cả 3 asset vào draft rồi mới công bố thành latest release. Dùng `-SkipBuild`
chỉ khi APK unsigned của đúng phiên bản đã build và kiểm tra xong.
Nếu upload lỗi, draft sẽ không xuất hiện trong nguồn cập nhật; hoàn tất hoặc xóa
draft thủ công trước khi chạy lại cùng phiên bản.

Mặc định script dùng chứng chỉ phát triển Android hiện có tại
`%USERPROFILE%/.android/debug.keystore`. Không tạo khóa thay thế; không commit hoặc
upload khóa ký lên repo. Nếu sau này chuyển sang khóa riêng, phải lên kế hoạch di
chuyển chữ ký Android trước để không làm mất khả năng cập nhật giữ dữ liệu.
Với khóa riêng, truyền `-KeystorePath`/`-KeyAlias`, đặt mật khẩu qua biến môi trường
`VOTUI_STORE_PASSWORD` và `VOTUI_KEY_PASSWORD`; không lưu chúng trong code.

GitHub Actions chạy build/test/lint khi push hoặc mở PR; ký/phát hành hiện chạy
trên máy giữ khóa hiện có. Không cần đưa khóa đó vào GitHub Actions secrets.

Tài liệu API: [GitHub Releases](https://docs.github.com/en/rest/releases/releases),
[Android package verification](https://developer.android.com/reference/android/content/pm/PackageManager),
[quyền cài từ nguồn này](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_UNKNOWN_APP_SOURCES).
