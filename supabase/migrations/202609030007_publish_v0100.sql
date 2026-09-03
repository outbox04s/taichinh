insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 10,
 '0.10.0',
 'https://github.com/outbox04s/taichinh/releases/download/v0.10.0/taichinh-v0.10.0-debug.apk',
 'Giao diện Liquid Glass xanh–trắng mới; nâng cấp quản lý khoản vay mới và hiện có, thanh toán trước hạn, tùy chỉnh từng kỳ, tất toán có phí; thêm tài khoản ngân hàng, số dư ban đầu và tìm kiếm 65 ngân hàng qua VietQR.',
 false,
 true
)
on conflict (version_code) do update set
 version_name=excluded.version_name,
 apk_url=excluded.apk_url,
 release_notes=excluded.release_notes,
 is_mandatory=excluded.is_mandatory,
 is_published=excluded.is_published,
 published_at=now();
