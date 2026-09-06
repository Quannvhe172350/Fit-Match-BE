package com.fitmatch.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * V94: số VIỆC TỒN của từng khu vực trong khu quản trị — nguồn của chấm đỏ cạnh
 * mỗi mục menu.
 *
 * <p>Vì sao một endpoint gộp thay vì để mỗi trang tự đếm: chấm đỏ phải hiện
 * NGAY khi admin đăng nhập, trước khi họ mở bất cứ trang nào — mà mục đích của
 * nó chính là chỉ đường tới trang cần mở. Đếm ở từng trang thì chấm chỉ sáng
 * sau khi người ta đã vào đúng chỗ, tức là muộn hơn lúc cần đúng một bước.
 *
 * <p>Chỉ đếm những khu vực có khái niệm "việc chờ xử lý". Các mục như Voucher,
 * CMS hay Nhật ký hệ thống không có hàng đợi nào nên cố ý không có mặt ở đây —
 * gắn cho chúng một con số 0 vĩnh viễn chỉ làm người đọc tưởng đang bị lỗi.
 *
 * <p>Tên trường khớp {@code navKey} của sidebar FE để hai bên không phải giữ một
 * bảng ánh xạ riêng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPendingCountsResponse {

    /** Hồ sơ phòng gym đang chờ duyệt. PT không còn qua platform verification (UC-019). */
    private long verification;

    /** Tranh chấp còn mở hoặc đang xem xét. */
    private long disputes;

    /** Tố cáo đánh giá còn chờ kiểm duyệt. */
    private long reviews;

    /** Báo cáo vấn đề dịch vụ/hành vi còn mở (UC-070/071). */
    private long issueReports;

    /** Lệnh rút tiền đang chờ duyệt. */
    private long withdrawals;

    /** Giao dịch chuyển khoản chưa khớp được vào vé (UC-053/056). */
    private long reconciliation;
}
