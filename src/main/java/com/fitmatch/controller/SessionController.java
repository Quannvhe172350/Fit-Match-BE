package com.fitmatch.controller;

import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.dto.ticket.SessionCancellationQuote;
import com.fitmatch.dto.ticket.SessionPtCancellationDto;
import com.fitmatch.dto.ticket.TrainingSessionResponse;
import com.fitmatch.dto.ticket.UpdateSessionDateRequest;
import com.fitmatch.dto.ticket.UpdateSessionPtRequest;
import com.fitmatch.service.SessionPtCancellationService;
import com.fitmatch.service.TicketSchedulingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Tag(name = "C. Training Sessions", description = "Ngày tập của khách")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('CUSTOMER')")
public class SessionController {

    private final TicketSchedulingService schedulingService;
    private final SessionPtCancellationService cancellationService;
    private final com.fitmatch.service.TicketReviewService reviewService;

    @Operation(summary = "Lịch tập của tôi trong một khoảng ngày",
            description = "Actor: **Customer**. Lọc khoảng ngày ở server. FE dùng cho lịch cá nhân "
                    + "và cho cảnh báo 'ngày này bạn đã có buổi tập ở phòng gym khác'.")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<TrainingSessionResponse>>> mySessions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(
                schedulingService.mySessions(userDetails.getUsername(), from, to)));
    }

    @Operation(summary = "Dời ngày tập",
            description = "Actor: **Customer** (chủ vé). Áp dụng cho CẢ vé DAY lẫn vé gói (V94 — "
                    + "trước đây vé gói nhận 409). Hạn đổi là 00:00 của ngày tập; ngày mới phải "
                    + "trong hạn dùng vé và không trùng một ngày khác của chính vé đó. Buổi có PT "
                    + "thì ngày mới phải còn đúng khung giờ đó, nếu không nhận 409.")
    @PutMapping("/{id}/date")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> updateDate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSessionDateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Session date updated",
                schedulingService.updateDate(userDetails.getUsername(), id, request.getDate())));
    }

    @Operation(summary = "Chọn / đổi PT cho một ngày",
            description = "Actor: **Customer** (chủ vé). Dùng cho cả đổi khung giờ trong cùng ngày "
                    + "lẫn bổ sung PT cho ngày đang trống của vé gói. Yêu cầu vé có kèm PT.")
    @PutMapping("/{id}/pt")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> setPt(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSessionPtRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PT updated",
                schedulingService.setPt(userDetails.getUsername(), id, request)));
    }

    @Operation(summary = "Bỏ PT khỏi một ngày",
            description = "Actor: **Customer** (chủ vé). Không hoàn tiền — phụ phí PT tính theo vé.")
    @DeleteMapping("/{id}/pt")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> removePt(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("PT removed",
                schedulingService.removePt(userDetails.getUsername(), id)));
    }

    @Operation(summary = "Đánh giá PT của một buổi tập",
            description = "Actor: **Customer** (chủ vé). Mở ngay khi buổi đó xong (DONE) và buổi "
                    + "có PT — câu 36. Mỗi buổi đúng một đánh giá. Đánh giá PHÒNG GYM nằm ở "
                    + "POST /api/tickets/{id}/review và chỉ mở khi dùng hết vé.")
    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<com.fitmatch.dto.review.ReviewResponse>> reviewPt(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody com.fitmatch.dto.review.TicketReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Review created",
                reviewService.reviewPt(userDetails.getUsername(), id, request)));
    }

    @Operation(summary = "Buổi tập bị PT xin nghỉ, đang chờ tôi quyết định",
            description = "Actor: **Customer**. Buổi mà PT đã được duyệt nghỉ: khách chọn PT thay "
                    + "thế (PUT /api/sessions/{id}/pt) hoặc nhận hoàn phụ phí HLV của ngày đó. "
                    + "estimatedRefund = 0 nghĩa là không còn tiền hoàn được (voucher/điểm đã phủ "
                    + "hết vé) — chỉ còn đường chọn HLV thay thế.")
    @GetMapping("/pt-cancellations")
    public ResponseEntity<ApiResponse<List<SessionPtCancellationDto>>> myPtCancellations(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                cancellationService.myPending(userDetails.getUsername())));
    }

    @Operation(summary = "Nhận hoàn phụ phí HLV thay vì chọn HLV khác",
            description = "Actor: **Customer** (chủ vé). Hoàn ĐÚNG phụ phí HLV của MỘT ngày, đã "
                    + "chiết theo tỉ lệ voucher/điểm đã dùng — vé vẫn dùng được cả ngày nên tiền vé "
                    + "không bị hoàn. Tiền vào ví khách. "
                    + "Lỗi: 409 buổi không có yêu cầu nào đang chờ quyết định, hoặc tiền của vé "
                    + "không còn ở trạng thái giữ; 404 buổi không thuộc bạn.")
    @PostMapping("/{id}/pt-cancellation/refund")
    public ResponseEntity<ApiResponse<SessionPtCancellationDto>> refundPtCancellation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("PT surcharge refunded",
                cancellationService.refund(userDetails.getUsername(), id)));
    }

    @Operation(summary = "Huỷ buổi này thì được hoàn bao nhiêu",
            description = "Actor: **Customer** (chủ vé). Hỏi TRƯỚC khi huỷ — số tiền phụ thuộc vào "
                    + "lúc hỏi, nên bắt khách bấm huỷ rồi mới biết mất bao nhiêu là đặt câu hỏi sau "
                    + "khi đã trả lời. Buổi không huỷ được thì trả `cancellable=false` kèm lý do, "
                    + "KHÔNG phải lỗi. 404 buổi không thuộc bạn.")
    @GetMapping("/{id}/cancel-quote")
    public ResponseEntity<ApiResponse<SessionCancellationQuote>> cancelQuote(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                schedulingService.cancelQuote(userDetails.getUsername(), id)));
    }

    @Operation(summary = "Huỷ một ngày tập",
            description = "Actor: **Customer** (chủ vé). Hoàn theo mốc báo trước do phòng gym cấu "
                    + "hình (mặc định: sớm hơn 24h hoàn 100%, sớm hơn 12h hoàn 50%, muộn hơn không "
                    + "hoàn). Tiền vào ví khách. Ngày đã huỷ KHÔNG trả lại cho vé — khách đã nhận "
                    + "tiền của đúng ngày đó. Lỗi: 409 buổi đã qua ngày, không còn SCHEDULED, vé "
                    + "không dùng được, hoặc tiền của vé không còn ở trạng thái giữ; 404 buổi không "
                    + "thuộc bạn.")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<SessionCancellationQuote>> cancel(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CancelSessionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Session cancelled",
                schedulingService.cancel(userDetails.getUsername(), id,
                        request == null ? null : request.getReason())));
    }

    /**
     * Lý do huỷ là TUỲ CHỌN: bắt buộc chỉ tạo ra một ô ai cũng gõ cho xong.
     *
     * <p>Trần 300 chứ không 500: lý do được ghép vào ghi chú lịch sử cùng với số
     * giờ báo trước và số tiền hoàn, mà cột {@code session_status_history.reason}
     * chỉ có 500 ký tự — nhận đủ 500 ở đây là để dành sẵn một lỗi ghi CSDL.
     */
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CancelSessionRequest {
        @jakarta.validation.constraints.Size(max = 300, message = "reason must be at most 300 characters")
        private String reason;
    }

    @Operation(summary = "Check-in buổi tập có PT",
            description = "Actor: **Customer** (chủ vé). Chỉ buổi CÓ PT và chỉ trong đúng ngày tập. "
                    + "Không đổi trạng thái buổi và không ảnh hưởng dòng tiền — buổi vẫn tiêu theo ngày.")
    @PostMapping("/{id}/check-in")
    public ResponseEntity<ApiResponse<TrainingSessionResponse>> checkIn(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Checked in",
                schedulingService.checkIn(userDetails.getUsername(), id)));
    }
}
