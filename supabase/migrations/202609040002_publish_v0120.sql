insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 15,
 '0.12.0',
 'https://github.com/outbox04s/taichinh/releases/download/v0.12.0/taichinh-v0.12.0-debug.apk',
 'Cập nhật khoản vay hiện có: tách tổng số tháng và số kỳ còn lại; thêm ngày trả đầu tiên; hỗ trợ trả gốc + lãi, trả gốc và trả lãi kèm khoản gốc tùy chọn; đồng bộ giao diện theo trang Tổng quan.',
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
