package com.fitmatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmatch.dto.payment.PaymentTransactionResponse;
import com.fitmatch.exception.GlobalExceptionHandler;
import com.fitmatch.service.PaymentReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đối soát thủ công (UC-053/056) — kiểm ở tầng BINDING, không phải tầng service.
 *
 * <p>Vì sao cần: {@code PaymentReconciliationServiceImplTest} gọi thẳng
 * {@code applyToTicket(7L, 1L, ...)} nên nó đúng dù tên trường JSON là gì. Và
 * đó chính là chỗ đã hỏng thật: FE gửi {@code {"ticketId": …}} trong khi
 * {@code ReconciliationApplyRequest} còn khai {@code bookingId} kèm
 * {@code @NotNull} ⇒ Jackson bind vào null ⇒ endpoint trả 400 cho MỌI lần gọi.
 * Toàn bộ test service vẫn xanh suốt thời gian đó.
 *
 * <p>Dùng standalone MockMvc thay vì {@code @WebMvcTest}: không cần Spring
 * context nên chạy trong {@code mvn test} thường, không phụ thuộc DB. Đánh đổi
 * là không kiểm được {@code @PreAuthorize} — phân quyền không phải thứ test này
 * canh, hợp đồng JSON mới là.
 */
@ExtendWith(MockitoExtension.class)
class AdminPaymentReconciliationControllerTest {

    private static final String URL = "/api/admin/payments/reconciliation/7/apply";

    @Mock private PaymentReconciliationService reconciliationService;

    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new AdminPaymentReconciliationController(reconciliationService))
                // Không gắn advice thì lỗi validation ra 400 rỗng của Spring,
                // không phải body ErrorResponse thật mà FE đang đọc.
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        UserDetails actor = User.withUsername("finance1").password("x").roles("FINANCE_ADMIN").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, actor.getAuthorities()));
    }

    @Test
    void apply_bindsTicketIdAndPassesItThrough() throws Exception {
        when(reconciliationService.applyToTicket(eq(7L), eq(42L), anyBoolean(), any(), eq("finance1")))
                .thenReturn(PaymentTransactionResponse.builder().id(7L).ticketId(42L).build());

        mvc.perform(post(URL)
                        .contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of(
                                "ticketId", 42,
                                "allowAmountMismatch", false,
                                "note", "khách CK sai nội dung"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketId").value(42));

        // Kiểm đúng giá trị chứ không chỉ "có gọi": nhầm vé nghĩa là gắn tiền
        // của khách này vào vé của khách khác.
        verify(reconciliationService).applyToTicket(
                7L, 42L, false, "khách CK sai nội dung", "finance1");
    }

    @Test
    void apply_rejectsLegacyBookingIdPayload() throws Exception {
        // Client cũ (hoặc ai đó chép lại DTO đã xoá) gửi bookingId. Trường này
        // không còn tồn tại nên ticketId là null ⇒ PHẢI 400. Điều tuyệt đối
        // không được xảy ra: âm thầm đi tiếp với ticketId null.
        mvc.perform(post(URL)
                        .contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of(
                                "bookingId", 42,
                                "allowAmountMismatch", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ticketId")));

        verify(reconciliationService, never())
                .applyToTicket(any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void apply_rejectsMissingTicketId() throws Exception {
        for (String body : List.of("{}", "{\"allowAmountMismatch\":true,\"note\":\"x\"}")) {
            mvc.perform(post(URL).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(reconciliationService, never())
                .applyToTicket(any(), any(), anyBoolean(), any(), any());
    }
}
