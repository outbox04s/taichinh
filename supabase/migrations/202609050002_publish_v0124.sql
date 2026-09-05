insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 19,
 '0.12.4',
 'https://github.com/outbox04s/taichinh/releases/download/v0.12.4/taichinh-v0.12.4-debug.apk',
 'Cải tiến khoản vay hiện có và hình thức trả lãi; đồng bộ giao diện Liquid Glass cho Giao dịch, Tài khoản và Khoản nợ; sửa nút Tạo và nút Thêm giao dịch che nội dung.',
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
