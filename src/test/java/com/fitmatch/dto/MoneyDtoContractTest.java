package com.fitmatch.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitmatch.common.enums.LoyaltyTxnType;
import com.fitmatch.common.enums.PaymentStatus;
import com.fitmatch.common.enums.RefundStatus;
import com.fitmatch.common.enums.WalletTxnType;
import com.fitmatch.dto.loyalty.LoyaltyTransactionResponse;
import com.fitmatch.dto.payment.PaymentOrderResponse;
import com.fitmatch.dto.payment.RefundResponse;
import com.fitmatch.dto.payment.WalletTransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hợp đồng JSON của các DTO dòng tiền sau khi mô hình booking chết (P4).
 *
 * <p>Chuyển neo từ {@code bookingId} sang {@code ticketId} là thay đổi PHÁ VỠ
 * hợp đồng với FE, mà không có gì trong build canh nó: đổi tên một trường DTO
 * biên dịch sạch, mọi test service vẫn xanh, và lỗi chỉ hiện ra dưới dạng
 * "trường luôn null" hoặc 400 lúc chạy thật — đúng như ca
 * {@code ReconciliationApplyRequest} đã gặp.
 */
class MoneyDtoContractTest {

    /** Tên trường trỏ vào các bảng đã bị migration V77/V78/V81 xoá. */
    private static final Set<String> DEAD_ANCHORS =
            Set.of("bookingId", "customerPackageId", "gymServiceId", "trainingPackageId");

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void moneyResponses_exposeTicketAnchorAndNeverBookingId() throws Exception {
        List<Object> samples = List.of(
                WalletTransactionResponse.builder()
                        .id(1L).type(WalletTxnType.HOLD).amount(new BigDecimal("100.00"))
                        .ticketId(42L).heldAfter(new BigDecimal("100.00"))
                        .pendingAfter(BigDecimal.ZERO).availableAfter(BigDecimal.ZERO)
                        .frozenAfter(BigDecimal.ZERO).createdAt(LocalDateTime.now()).build(),
                LoyaltyTransactionResponse.builder()
                        .id(2L).type(LoyaltyTxnType.EARN).points(10).balanceAfter(10)
                        .ticketId(42L).createdAt(LocalDateTime.now()).build(),
                PaymentOrderResponse.builder()
                        .id(3L).ticketId(42L).refCode("FM42AB12CD")
                        .amount(new BigDecimal("100.00")).status(PaymentStatus.PENDING).build(),
                RefundResponse.builder()
                        .id(4L).ticketId(42L).amount(new BigDecimal("100.00"))
                        .status(RefundStatus.PENDING).build());

        for (Object dto : samples) {
            String body = json.writeValueAsString(dto);
            assertThat(json.readTree(body).has("ticketId"))
                    .as(dto.getClass().getSimpleName() + " phải trả về neo vé").isTrue();
            assertThat(json.readTree(body).get("ticketId").asLong())
                    .as(dto.getClass().getSimpleName() + ".ticketId").isEqualTo(42L);
            for (String dead : DEAD_ANCHORS) {
                assertThat(json.readTree(body).has(dead))
                        .as(dto.getClass().getSimpleName() + " còn sót khoá " + dead).isFalse();
            }
        }
    }

    /**
     * Quét TOÀN BỘ package dto + entity thay vì liệt kê tay vài lớp: chỗ tôi bỏ
     * sót lần trước ({@code LoyaltyTransaction.bookingId}) không nằm trong danh
     * sách nào cả — nó chỉ lộ ra khi Hibernate validate lúc khởi động.
     *
     * <p>Bắt cả getter không có field, vì Jackson serialize theo getter.
     */
    @Test
    void noDtoOrEntityStillCarriesADeadAnchor() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return true;
            }
        };
        scanner.addIncludeFilter((reader, factory) -> true);

        List<String> offenders = new ArrayList<>();
        for (String pkg : List.of("com.fitmatch.dto", "com.fitmatch.entity")) {
            for (var candidate : scanner.findCandidateComponents(pkg)) {
                Class<?> type = Class.forName(candidate.getBeanClassName());

                for (Field f : type.getDeclaredFields()) {
                    if (DEAD_ANCHORS.contains(f.getName())) {
                        offenders.add(type.getSimpleName() + "." + f.getName() + " (field)");
                    }
                }
                for (Method m : type.getDeclaredMethods()) {
                    String name = m.getName();
                    if (name.startsWith("get") && m.getParameterCount() == 0
                            && DEAD_ANCHORS.contains(decapitalise(name.substring(3)))) {
                        offenders.add(type.getSimpleName() + "." + name + "() (getter)");
                    }
                }
            }
        }

        assertThat(offenders)
                .as("neo trỏ vào bảng đã bị xoá — sẽ làm ddl-auto=validate chặn boot "
                        + "hoặc trả về trường luôn null cho FE")
                .isEmpty();
    }

    private static String decapitalise(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
