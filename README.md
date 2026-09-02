# Tài chính cá nhân Android

Ứng dụng một người dùng bằng Kotlin, Jetpack Compose/Material 3 và Supabase. Tiền dùng `Long`/PostgreSQL `BIGINT` (VND), thời gian lưu UTC và hiển thị theo `Asia/Ho_Chi_Minh`. Cảnh báo rủi ro là công cụ theo dõi dựa trên quy tắc, không phải tư vấn tài chính chuyên nghiệp.

## Kiến trúc

```text
Android Compose → ViewModel/StateFlow → Repository → Supabase Auth/PostgREST/Edge Functions
SePay → HTTPS sepay-webhook → sepay_raw_events → transactions → Android
Android/WorkManager → sepay-sync → SePay API v2 → đối soát idempotent
```

Android không gọi SePay trực tiếp. Edge Functions giữ token/secret; PostgreSQL RPC xử lý raw event và transaction trong cùng transaction. Mọi bảng người dùng bật RLS. `sepay_raw_events` không cấp quyền trực tiếp cho Android. Xem [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) và [docs/DATABASE.md](docs/DATABASE.md).

## Cấu hình Android

Yêu cầu JDK 17, Android SDK/API 37. Tạo `local.properties` và không commit tệp này:

```properties
sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
SUPABASE_URL=http://10.0.2.2:54321
SUPABASE_ANON_KEY=<anon-key-from-supabase-status>
```

Chỉ anon/publishable key được phép vào APK; không dùng service-role key, SePay token hay webhook secret.

## Supabase local và migration

Yêu cầu Docker Desktop và Supabase CLI:

```bash
cp .env.example .env
supabase start
supabase db reset
supabase secrets set --env-file .env
supabase functions serve --env-file .env
```

Kiểm tra database:

```bash
supabase db lint --local --level error
supabase test db
```

Migration là forward-only. Rollback an toàn nhất ở production là khôi phục backup vào project mới hoặc viết migration bù; không xóa bảng tài chính trực tiếp. Với local có thể dùng `supabase db reset` để dựng lại từ đầu.

## Cấu hình SePay sandbox

Các biến trong `.env.example` phải được điền qua Supabase Secrets, không commit giá trị thật:

- `SEPAY_WEBHOOK_SECRET`: khóa HMAC của webhook.
- `SEPAY_WEBHOOK_API_KEY`: chỉ dùng phương thức API Key khi thử sandbox.
- `SEPAY_API_TOKEN`: Bearer token API v2.
- `SEPAY_API_BASE_URL`: sandbox là `https://userapi-sandbox.sepay.vn/v2`.
- `SEPAY_ENVIRONMENT`: `sandbox` hoặc `production`.
- `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY` dành cho Edge Functions.
- `APP_TIME_ZONE=Asia/Ho_Chi_Minh`.

Trong SePay Test mode, liên kết ngân hàng và tạo webhook:

```text
https://<project-ref>.supabase.co/functions/v1/sepay-webhook
```

Chọn POST, `application/json`, HMAC-SHA256. SePay gửi `X-SePay-Signature: sha256=<hex>` và `X-SePay-Timestamp`; chữ ký được tính trên `{timestamp}.{raw_body}`. Endpoint trả HTTP 200 với `{"success":true}`. Production bắt buộc HTTPS và ưu tiên HMAC. Không in secret hoặc Authorization header khi chẩn đoán.

Tài khoản Android cần được ánh xạ `sepay_account_id` với định danh tài khoản mà webhook/API trả về. Sự kiện chưa ánh xạ được giữ ở `pending_review`; không bị bỏ mất. Giao dịch SePay mới ở trạng thái `pending` để người dùng chọn danh mục, không tự suy đoán nội dung.

## Test và build APK

```powershell
.\gradlew.bat testDebugUnitTest lintDebug compileDebugAndroidTestKotlin assembleDebug
```

Edge Functions:

```bash
deno test --allow-net supabase/functions/_shared/sepay_test.ts
```

Instrumentation test cần emulator/thiết bị:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

APK debug nằm tại `app/build/outputs/apk/debug/app-debug.apk`. Không tạo release signing key giả.

## Backup và khôi phục

Trước migration production, tạo backup trên Supabase Dashboard hoặc dùng `pg_dump` với chuỗi kết nối lấy từ project secrets. Kiểm tra file dump và lưu mã hóa ngoài project. Khôi phục thử vào database tách biệt bằng `pg_restore`, chạy migration còn thiếu, `supabase db lint`, test RLS rồi mới chuyển traffic. Không đưa connection string/password vào script hay README.

## Giới hạn hiện tại

- Đồng bộ tiền ra phụ thuộc ngân hàng và loại tài khoản SePay hỗ trợ; tài liệu hiện nêu chỉ một số ngân hàng hỗ trợ webhook tiền ra.
- Gửi webhook thử của SePay có thể dùng ID mock `0`; không dùng dữ liệu đó làm dữ liệu production.
- Chưa thể xác minh webhook sandbox thật nếu chưa có tài khoản/token/secret sandbox đang hoạt động và endpoint public.
- Khóa PIN/sinh trắc học và đăng nhập cần được xác minh end-to-end trước phát hành production.
