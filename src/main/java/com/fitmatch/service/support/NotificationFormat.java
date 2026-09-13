package com.fitmatch.service.support;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Ngày giờ và tiền trong THÔNG BÁO, viết theo cách người Việt đọc.
 *
 * <p>Trước đây mọi thông báo nối thẳng đối tượng vào chuỗi, nên khách nhận được
 * "Buổi ngày 2026-09-20" và "hoàn lại 50000.00 đ". Ngày ISO là định dạng để máy
 * trao đổi với nhau, không phải để đọc; còn số tiền không có dấu phân nhóm thì
 * người ta phải tự đếm chữ số mới biết là năm chục nghìn hay năm trăm nghìn —
 * đúng loại thông tin không được phép bắt ai đoán.
 *
 * <p>Định dạng tiền khớp với {@code formatCurrency} của FE
 * ({@code Intl.NumberFormat("vi-VN", VND)}) để cùng một khoản tiền đọc giống hệt
 * nhau dù hiện trong app hay trong hộp thư.
 *
 * <p>Tất cả đều nhận null và trả chuỗi rỗng: thông báo là đường phụ, một trường
 * trống không được phép làm hỏng cả giao dịch nghiệp vụ đứng trước nó.
 */
public final class NotificationFormat {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private NotificationFormat() {
    }

    /** "20/09/2026" */
    public static String date(LocalDate value) {
        return value == null ? "" : value.format(DATE);
    }

    /** "19:00" — bỏ phần giây, không ai hẹn giờ tập theo giây. */
    public static String time(LocalTime value) {
        return value == null ? "" : value.format(TIME);
    }

    /** "20/09/2026 19:00" */
    public static String dateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME);
    }

    /** Ngày của một mốc có cả giờ — dùng khi chỉ NGÀY mới có ý nghĩa (hạn dùng vé). */
    public static String dateOf(LocalDateTime value) {
        return value == null ? "" : date(value.toLocalDate());
    }

    /**
     * "50" chứ không "50.00". Tỉ lệ nguyên là trường hợp thường gặp nhất, và cái
     * đuôi {@code .00} chỉ là scale của {@code BigDecimal} lộ ra ngoài. Vẫn giữ
     * phần lẻ khi có thật ("30.5").
     */
    public static String percent(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * "1.000.000 ₫". Làm tròn về đồng vì VND không có đơn vị nhỏ hơn — hiển thị
     * "50000.00" là để lộ kiểu dữ liệu chứ không phải nói cho ai nghe.
     *
     * <p>Tạo {@link DecimalFormat} MỚI mỗi lần thay vì giữ một hằng số tĩnh:
     * DecimalFormat không an toàn đa luồng, mà lớp gọi nó là singleton dùng
     * chung. Thông báo không phải đường nóng nên cái giá này không đáng kể, còn
     * một con số tiền sai vì tranh chấp luồng thì rất đáng.
     */
    public static String money(BigDecimal value) {
        if (value == null) {
            return "";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", symbols).format(value) + " ₫";
    }
}
