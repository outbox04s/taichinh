insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 17,
 '0.12.2',
 'https://github.com/outbox04s/taichinh/releases/download/v0.12.2/taichinh-v0.12.2-debug.apk',
 'Thêm thao tác nhấn giữ để xóa tài khoản hoặc khoản nợ; có xác nhận Hủy/Xóa; dữ liệu lịch sử được giữ an toàn bằng cơ chế xóa mềm.',
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
