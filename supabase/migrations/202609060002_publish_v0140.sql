insert into public.app_releases(version_code,version_name,apk_url,release_notes,is_mandatory,is_published)
values(24,'0.14.0',
 'https://github.com/outbox04s/taichinh/releases/download/v0.14.0/taichinh-v0.14.0-debug.apk',
 'Quản lý nguồn thu nhập: thêm, sửa, xóa và hiển thị trạng thái theo tháng hiện tại. Khoản nợ mặc định Tất cả, Gần hạn lọc các kỳ còn phải trả trong tháng. Sửa Kỳ hiện tại đã trả theo tháng thực tế. Báo cáo cộng dồn từ tài sản khả dụng, chuyển số dư sang tháng sau và không tính lại khoản đã nhận hoặc đã trả.',false,true)
on conflict(version_code) do update set version_name=excluded.version_name,apk_url=excluded.apk_url,
release_notes=excluded.release_notes,is_mandatory=excluded.is_mandatory,is_published=excluded.is_published,published_at=now();
