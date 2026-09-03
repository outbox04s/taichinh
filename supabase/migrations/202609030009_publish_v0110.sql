insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 11,
 '0.11.0',
 'https://github.com/outbox04s/taichinh/releases/download/v0.11.0/taichinh-v0.11.0-debug.apk',
 'Nâng cấp thanh điều hướng Liquid Glass; đồng bộ giao diện xanh trắng; sửa tiêu đề một dòng; bổ sung số tài khoản, tên hiển thị và mục đích sử dụng tài khoản.',
 false,
 true
)
on conflict (version_code) do update set
 version_name=excluded.version_name,
 apk_url=excluded.apk_url,
 release_notes=excluded.release_notes,
 is_mandatory=excluded.is_mandatory,
 is_published=excluded.is_published;
