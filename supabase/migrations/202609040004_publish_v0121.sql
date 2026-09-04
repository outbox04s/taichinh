insert into public.app_releases(
 version_code,version_name,apk_url,release_notes,is_mandatory,is_published
) values (
 16,
 '0.12.1',
 'https://github.com/outbox04s/taichinh/releases/download/v0.12.1/taichinh-v0.12.1-debug.apk',
 'Sửa hiển thị tổng số tháng và hình thức thanh toán từ database; ngăn chế độ trả gốc + lãi sinh thêm kỳ lãi sau kỳ cuối; tự hoàn tất khoản vay khi nợ gốc về 0.',
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
