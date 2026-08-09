package com.fitmatch.service.support;

import com.fitmatch.entity.WithdrawalRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Sinh link ảnh QR VietQR trỏ tới tài khoản NGƯỜI THỤ HƯỞNG của một lệnh rút
 * (V61) — admin mở app ngân hàng quét là có sẵn ngân hàng, số tài khoản, số tiền
 * và nội dung chuyển khoản, không phải gõ tay.
 * <p>
 * Khác chiều với QR ở {@code PaymentServiceImpl}: chiều thu tiền dựng QR trỏ về
 * tài khoản nền tảng (cấu hình {@code app.vietqr.*}), còn ở đây tài khoản đích
 * lấy từ chính lệnh rút nên mã BIN phải đến từ master data {@code banks}.
 * <p>
 * Nội dung chuyển khoản LUÔN là {@link WithdrawalRequest#getRefCode()} — đây là
 * mấu chốt để webhook Casso khớp giao dịch CHI trên sao kê về đúng lệnh rút.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutQrService {

    private static final String BASE = "https://img.vietqr.io/image/";
    private static final String TEMPLATE = "compact2";

    /**
     * @return link ảnh QR, hoặc null nếu lệnh rút thiếu mã BIN (tài khoản nhập
     *         tay từ dữ liệu cũ) — khi đó admin chuyển khoản thủ công như trước.
     */
    public String buildQrUrl(WithdrawalRequest request) {
        if (request.getBankBin() == null || request.getBankBin().isBlank()) {
            log.warn("Withdrawal {} has no bank BIN - cannot render payout QR", request.getId());
            return null;
        }
        // VietQR nhận số tiền dạng số nguyên đồng, không thập phân.
        String amount = request.getAmount().setScale(0, RoundingMode.DOWN).toPlainString();
        return BASE + request.getBankBin() + "-" + request.getBankAccount() + "-" + TEMPLATE + ".png"
                + "?amount=" + amount
                + "&addInfo=" + encode(request.getRefCode())
                + "&accountName=" + encode(request.getAccountHolder());
    }

    /** Số tiền admin phải chuyển, làm tròn xuống đồng cho khớp với QR. */
    public BigDecimal payableAmount(WithdrawalRequest request) {
        return request.getAmount().setScale(0, RoundingMode.DOWN);
    }

    private String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
