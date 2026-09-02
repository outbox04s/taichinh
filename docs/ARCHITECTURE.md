# Kiến trúc và lộ trình

## Cây thư mục

```text
app/src/main/java/vn/personalfinance/
├── data/              # Supabase repository, DataStore, WorkManager
├── di/                # Hilt modules
├── domain/            # Model số tiền Long và repository contracts
└── presentation/      # Compose theme, navigation, screens, ViewModels
supabase/
├── migrations/        # PostgreSQL schema + RLS
└── functions/         # Cổng tích hợp SePay phía server
```

Kiến trúc MVVM phân lớp nhẹ được chọn: UI/ViewModel → domain contract → data implementation. Không tách module Gradle khi quy mô hiện tại chưa cần.

## Giai đoạn

1. **Nền tảng (hiện tại):** scaffold, theme, navigation, Hilt, Supabase, schema/RLS, WorkManager/DataStore, placeholder.
2. **Đăng nhập & khóa ứng dụng:** email/password, khôi phục phiên, BiometricPrompt/PIN được mã hóa bằng Android Keystore.
3. **Sổ cái:** CRUD tài khoản, danh mục, giao dịch thủ công, lương định kỳ; kiểm thử ownership/RLS.
4. **Ngân sách & khoản nợ:** hạn mức, lịch trả, đã trả/còn lại, notification worker.
5. **SePay:** xác nhận contract webhook thực tế, signature verification theo tài liệu, ánh xạ tài khoản, xử lý event idempotent và backfill qua Edge Function.
6. **Phân tích:** dòng tiền, dự báo thanh khoản và cảnh báo có giải thích; luôn ghi rõ không phải tư vấn tài chính chuyên nghiệp.
7. **Hardening:** offline/error UX, rate limiting, audit log, backup/restore, test UI/integration và release signing.
