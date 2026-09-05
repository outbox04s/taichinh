# Chi phí cố định và báo cáo

- Mở **Chi phí cố định hàng tháng** từ Tổng quan hoặc **Quản lý chi phí cố định** trong Báo cáo để thêm, sửa, xóa hoặc tạm dừng khoản chi. Chọn tài khoản, danh mục chi tiêu, số tiền, ngày đầu tiên và ngày kết thúc nếu có. Ngày 29–31 được chuyển về ngày cuối tháng khi tháng ngắn hơn.
- Dữ liệu dùng bảng `recurring_entries` hiện có và chính sách RLS theo người dùng. Không cần migration mới. Các khoản chi lặp hàng tháng đã tạo từ giao dịch cũng xuất hiện trong danh sách này. Thiết lập kế hoạch không tự tạo giao dịch chi tiền.
- Báo cáo hiển thị 12 tháng từ tháng hiện tại, tính trên toàn bộ tài khoản. Thu nhập lấy từ nguồn đang hoạt động, theo tần suất tháng/tuần hoặc ngày cụ thể của nguồn không định kỳ. Kỳ thu có dữ liệu nhận thực tế dùng số thực nhận thay dự kiến, không cộng hai lần.
- Số còn cho chi tiêu khác = thu nhập − chi phí cố định đang áp dụng − tổng gốc/lãi/phí theo lịch vay. Đây là kế hoạch trọn tháng, gồm kỳ nợ đã thanh toán, không phải số dư tài khoản hay dự báo số dư kể từ hôm nay. Chi tiêu biến đổi chưa được trừ. Phần nợ chưa trả từ trước tháng hiện tại được cộng vào nghĩa vụ tháng đầu tiên một lần.
- Dự toán phụ thuộc lịch trả đã được thiết lập; khoản vay không có lịch sẽ được cảnh báo. Không tự suy diễn khoản trả sau kỳ cuối cùng của lịch.
- **Xem tất cả** trong Cơ cấu chi tiêu mở tổng hợp tất cả danh mục: số tiền, số giao dịch, tỷ trọng và tổng chi. Giữ bộ lọc thời gian/tài khoản từ Tổng quan và cho phép đổi bộ lọc tại màn hình này. Chỉ tính giao dịch chi đã xác nhận, chưa xóa, theo múi giờ Việt Nam; gồm nhóm chưa phân loại. Nút của Giao dịch gần nhất vẫn mở Giao dịch.

Kiểm tra: `gradlew.bat testDebugUnitTest lintDebug compileDebugAndroidTestKotlin assembleDebug`.
