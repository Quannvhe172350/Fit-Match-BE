package com.fitmatch.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Kiểm tra {@link VietnamPhone}.
 *
 * <p>Chấp nhận cách gõ của người dùng thật ("(028) 3822-1234", "0901.234.567",
 * "+84 901 234 567") chứ không bắt gõ liền một mạch: bắt đúng định dạng chỉ làm
 * người nhập bỏ cuộc, trong khi thứ cần chặn là số SAI — thiếu chữ số, đầu số
 * không tồn tại, hoặc số nước ngoài.
 *
 * <p>KHÔNG tự sửa giá trị: validator chỉ được phép nói đúng/sai. Muốn chuẩn hoá
 * về một dạng lưu duy nhất thì phải làm ở tầng service, nơi nhìn thấy cả bản ghi.
 */
public class VietnamPhoneValidator implements ConstraintValidator<VietnamPhone, String> {

    /** Ký tự trình bày, bỏ trước khi so khớp. */
    private static final Pattern SEPARATORS = Pattern.compile("[\\s.()\\-]");

    /**
     * Số nội địa đã đưa về dạng bắt đầu bằng 0.
     *
     * <ul>
     *   <li>Di động: {@code 0} + đầu số 3/5/7/8/9 + 8 chữ số = 10 chữ số. Đầu số
     *       4 và 6 không còn được cấp sau đợt chuyển 11 -&gt; 10 số năm 2018.</li>
     *   <li>Cố định: {@code 02} + 9 chữ số = 11 chữ số (mã vùng 02x/02xx rồi tới
     *       số thuê bao). Chi nhánh phòng gym hay khai số bàn nên phải nhận.</li>
     * </ul>
     */
    private static final Pattern NATIONAL = Pattern.compile("^0(?:[35789]\\d{8}|2\\d{9})$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String compact = SEPARATORS.matcher(value).replaceAll("");
        // Chuỗi rỗng/toàn khoảng trắng là chuyện của @NotBlank; báo ở đây nữa thì
        // một ô để trống nhận hai thông báo lỗi.
        if (compact.isEmpty()) {
            return true;
        }
        return NATIONAL.matcher(toNational(compact)).matches();
    }

    /**
     * Đưa dạng quốc tế về dạng nội địa bắt đầu bằng 0.
     *
     * <p>Số nội địa LUÔN bắt đầu bằng 0, nên "84..." không thể là số nội địa —
     * không có chuyện nhầm lẫn với đầu số 08x ("0084..." / "084..." đều bắt đầu
     * bằng 0 và không rơi vào nhánh này).
     */
    private static String toNational(String compact) {
        String rest;
        if (compact.startsWith("+84")) {
            rest = compact.substring(3);
        } else if (compact.startsWith("0084")) {
            rest = compact.substring(4);
        } else if (compact.startsWith("84")) {
            rest = compact.substring(2);
        } else {
            return compact;
        }
        // "+84901234567" bỏ số 0 đầu, "+840901234567" thì giữ nguyên — cả hai cách
        // viết đều gặp ngoài đời.
        return rest.startsWith("0") ? rest : "0" + rest;
    }
}
