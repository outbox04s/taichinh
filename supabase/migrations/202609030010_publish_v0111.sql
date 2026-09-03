insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 12,
 '0.11.1',
 'https://github.com/outbox04s/taichinh/releases/download/v0.11.1/taichinh-v0.11.1-debug.apk',
 'Sửa lỗi phiên Supabase khi thêm tài khoản và tạo khoản vay; tự khôi phục hoặc tạo phiên thiết bị; hiển thị nguyên nhân lỗi dữ liệu rõ ràng hơn.',
 false,
 true
)
on conflict (version_code) do update set
 version_name=excluded.version_name,
 apk_url=excluded.apk_url,
 release_notes=excluded.release_notes,
 is_mandatory=excluded.is_mandatory,
 is_published=excluded.is_published;
