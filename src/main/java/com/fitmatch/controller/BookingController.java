package com.fitmatch.controller;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.common.response.ApiResponse;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.booking.BookingResponse;
import com.fitmatch.dto.booking.BookingStatusHistoryResponse;
import com.fitmatch.dto.booking.CancelBookingRequest;
import com.fitmatch.dto.booking.CreateBookingRequest;
import com.fitmatch.dto.booking.CustomerPackageResponse;
import com.fitmatch.dto.booking.SessionNoteResponse;
import com.fitmatch.dto.booking.RescheduleBookingRequest;
import com.fitmatch.dto.booking.WaitlistRequest;
import com.fitmatch.dto.booking.WaitlistResponse;
import com.fitmatch.dto.payment.PaymentOrderResponse;
import com.fitmatch.dto.payment.RefundReasonRequest;
import com.fitmatch.dto.payment.RefundResponse;
import com.fitmatch.service.BookingQueryService;
import com.fitmatch.service.BookingService;
import com.fitmatch.service.PaymentService;
import com.fitmatch.service.RefundService;
import com.fitmatch.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "E. Bookings", description = "Đặt lịch dịch vụ/gói tập (UC-031 → UC-045). Yêu cầu ROLE_CUSTOMER.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('CUSTOMER')")
public class BookingController {

    private final BookingService bookingService;
    private final WaitlistService waitlistService;
    private final BookingQueryService bookingQueryService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final com.fitmatch.service.PackageUsageService packageUsageService;
    private final com.fitmatch.service.SessionNoteService sessionNoteService;
    private final com.fitmatch.service.VoucherService voucherService;
    private final com.fitmatch.service.LoyaltyService loyaltyService;

    @Operation(
            summary = "UC-031 — Tạo booking nháp",
            description = "Actor: **Customer**. Tạo booking DRAFT với tối thiểu một đích (serviceId/packageId/branchId/ptId) để xác định Gym chịu trách nhiệm; mọi đích phải thuộc cùng một Gym. Lỗi: 400 thiếu/lệch Gym; 404 đích không tồn tại.")
    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Booking draft created",
                bookingService.createDraft(userDetails.getUsername(), request)));
    }

    @Operation(
            summary = "UC-035 — Checkout booking",
            description = "Actor: **Customer**. Validate đủ điều kiện (UC-033: gym/catalog/lịch/PT/capacity), chốt giá (UC-034) và chuyển DRAFT -> PENDING_PAYMENT (miễn phí -> PENDING_GYM). Lỗi: 409 không đủ điều kiện kèm danh sách lý do; 404 không thuộc về bạn.")
    @PostMapping("/{id}/checkout")
    public ResponseEntity<ApiResponse<BookingResponse>> checkout(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Booking checked out",
                bookingService.checkout(userDetails.getUsername(), id)));
    }

    @Operation(
            summary = "UC-041 — Dời lịch booking",
            description = "Actor: **Customer**. Dời PENDING_GYM/CONFIRMED sang khung giờ mới hợp lệ (PT rảnh, branch mở, còn chỗ). Lỗi: 409 sai trạng thái hoặc khung giờ vi phạm; 404 không thuộc về bạn.")
    @PostMapping("/{id}/reschedule")
    public ResponseEntity<ApiResponse<BookingResponse>> reschedule(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody RescheduleBookingRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Booking rescheduled",
                bookingService.reschedule(userDetails.getUsername(), id, request.getStartAt(), request.getEndAt())));
    }

    @Operation(
            summary = "UC-042 — Hủy booking",
            description = "Actor: **Customer**. Hủy DRAFT/PENDING_PAYMENT/PENDING_GYM/CONFIRMED; hủy CONFIRMED trong cửa sổ mất phí bị đánh dấu lateCancellation (ảnh hưởng hoàn tiền — UC-043). Lỗi: 409 trạng thái cuối; 404 không thuộc về bạn.")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<BookingResponse>> cancel(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CancelBookingRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled",
                bookingService.cancel(userDetails.getUsername(), id, reason)));
    }

    @Operation(
            summary = "UC-052 — Xem đơn thanh toán VietQR của booking",
            description = "Actor: **Customer**. Trả về refCode, số tiền, nội dung VietQR (FE render QR), trạng thái. Khách chuyển khoản với nội dung = refCode; Casso sẽ đối soát. Lỗi: 404 chưa có đơn/không thuộc về bạn.")
    @GetMapping("/{id}/payment")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> payment(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getForCustomer(id, userDetails.getUsername())));
    }

    // ==================== UC-045: History & details ====================

    @Operation(
            summary = "UC-045 — Booking của tôi",
            description = "Actor: **Customer**. Danh sách booking của mình, lọc theo trạng thái, phân trang.")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> myBookings(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingQueryService.myBookings(userDetails.getUsername(), status, pageable)));
    }

    @Operation(
            summary = "UC-045 — Chi tiết booking",
            description = "Actor: **Customer** (chủ booking). Lỗi: 404 không thuộc về bạn.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> detail(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingQueryService.detail(userDetails.getUsername(), id)));
    }

    @Operation(
            summary = "UC-040 — Timeline trạng thái booking",
            description = "Actor: **Customer** (chủ booking). Toàn bộ lịch sử chuyển trạng thái kèm lý do/actor. Lỗi: 404 không thuộc về bạn.")
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<BookingStatusHistoryResponse>>> history(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingQueryService.history(userDetails.getUsername(), id)));
    }

    // ==================== UC-044: Waitlist ====================

    @Operation(
            summary = "UC-044 — Đăng ký danh sách chờ",
            description = "Actor: **Customer**. Khi slot mong muốn không còn: đăng ký chờ cho đúng MỘT dịch vụ hoặc gói kèm khung giờ mong muốn. Lỗi: 400 sai số đích; 404 đích không tồn tại.")
    @PostMapping("/waitlist")
    public ResponseEntity<ApiResponse<WaitlistResponse>> joinWaitlist(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody WaitlistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Joined waitlist",
                waitlistService.join(userDetails.getUsername(), request)));
    }

    @Operation(
            summary = "UC-044 — Rời danh sách chờ",
            description = "Actor: **Customer**. Lỗi: 404 không thuộc về bạn.")
    @DeleteMapping("/waitlist/{id}")
    public ResponseEntity<ApiResponse<Void>> leaveWaitlist(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        waitlistService.leave(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Left waitlist", null));
    }

    @Operation(
            summary = "UC-044 — Danh sách chờ của tôi",
            description = "Actor: **Customer**.")
    @GetMapping("/waitlist")
    public ResponseEntity<ApiResponse<List<WaitlistResponse>>> myWaitlist(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(waitlistService.myEntries(userDetails.getUsername())));
    }

    @Operation(
            summary = "UC-032 — Cập nhật lựa chọn của booking nháp",
            description = "Actor: **Customer**. Đổi service/package/PT/branch/khung giờ khi booking còn DRAFT; field null giữ nguyên. Lỗi: 409 không ở DRAFT; 400 lựa chọn khác Gym; 404 không thuộc về bạn.")
    @PutMapping("/{id}/selection")
    public ResponseEntity<ApiResponse<BookingResponse>> updateSelection(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Booking selection updated",
                bookingService.updateSelection(userDetails.getUsername(), id, request)));
    }

    @Operation(
            summary = "UC-073 — Áp voucher vào booking nháp",
            description = "Actor: **Customer**. Booking DRAFT, không phải buổi từ gói. Trả booking với số tiền đã giảm. Lỗi: 404 mã không tồn tại; 409 voucher không hợp lệ/không đủ điều kiện.")
    @PostMapping("/{id}/voucher")
    public ResponseEntity<ApiResponse<BookingResponse>> applyVoucher(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody com.fitmatch.dto.voucher.ApplyVoucherRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Voucher applied",
                voucherService.applyToBooking(userDetails.getUsername(), id, request.getCode())));
    }

    @Operation(
            summary = "UC-073 — Gỡ voucher khỏi booking nháp",
            description = "Actor: **Customer**.")
    @DeleteMapping("/{id}/voucher")
    public ResponseEntity<ApiResponse<BookingResponse>> removeVoucher(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Voucher removed",
                voucherService.removeFromBooking(userDetails.getUsername(), id)));
    }

    @Operation(
            summary = "UC-073 — Dùng điểm thưởng cho booking nháp",
            description = "Actor: **Customer**. Booking DRAFT, không phải buổi từ gói; loại trừ lẫn nhau với voucher. Lỗi: 409 không đủ điểm/không hợp lệ.")
    @PostMapping("/{id}/loyalty")
    public ResponseEntity<ApiResponse<BookingResponse>> applyLoyalty(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody com.fitmatch.dto.loyalty.ApplyLoyaltyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Points applied",
                loyaltyService.applyToBooking(userDetails.getUsername(), id, request.getPoints())));
    }

    @Operation(
            summary = "UC-073 — Gỡ điểm thưởng khỏi booking nháp",
            description = "Actor: **Customer**.")
    @DeleteMapping("/{id}/loyalty")
    public ResponseEntity<ApiResponse<BookingResponse>> removeLoyalty(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Points removed",
                loyaltyService.removeFromBooking(userDetails.getUsername(), id)));
    }

    @Operation(
            summary = "UC-046 — Customer check-in buổi tập",
            description = "Actor: **Customer**. Booking CONFIRMED, mở từ 30' trước giờ bắt đầu đến hết giờ; mỗi booking check-in một lần. Lỗi: 409 sai trạng thái/ngoài cửa sổ/đã check-in; 404 không thuộc về bạn.")
    @PostMapping("/{id}/check-in")
    public ResponseEntity<ApiResponse<BookingResponse>> checkIn(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Checked in",
                bookingService.checkIn(userDetails.getUsername(), id)));
    }

    @Operation(
            summary = "UC-051 — Gói tập đã mua của tôi",
            description = "Actor: **Customer**. Danh sách gói đã kích hoạt kèm số buổi còn lại; dùng customerPackageId để đặt buổi tiếp theo miễn phí (UC-049).")
    @GetMapping("/my-packages")
    public ResponseEntity<ApiResponse<java.util.List<CustomerPackageResponse>>> myPackages(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                packageUsageService.myPackages(userDetails.getUsername())));
    }

    @Operation(
            summary = "UC-048/051 — Ghi chú buổi tập của booking",
            description = "Actor: **Customer**. Đọc ghi chú/bằng chứng do Gym/PT ghi nhận — theo dõi tiến độ luyện tập.")
    @GetMapping("/{id}/notes")
    public ResponseEntity<ApiResponse<java.util.List<SessionNoteResponse>>> notes(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                sessionNoteService.listForCustomer(userDetails.getUsername(), id)));
    }

    @Operation(
            summary = "UC-055 — Mở yêu cầu hoàn tiền cho booking",
            description = "Actor: **Customer**. Cho booking REJECTED/CANCELLED/NO_SHOW còn giữ tiền; Finance duyệt và thực thi (UC-056). Lỗi: 409 sai trạng thái/không còn tiền giữ/đã có yêu cầu mở; 404 không thuộc về bạn.")
    @PostMapping("/{id}/refund-request")
    public ResponseEntity<ApiResponse<RefundResponse>> requestRefund(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody RefundReasonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Refund request created",
                refundService.createForCustomer(userDetails.getUsername(), id, request.getReason())));
    }

    @Operation(
            summary = "UC-055 — Danh sách yêu cầu hoàn tiền của tôi",
            description = "Actor: **Customer**.")
    @GetMapping("/refunds")
    public ResponseEntity<ApiResponse<PageResponse<RefundResponse>>> myRefunds(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                refundService.listForCustomer(userDetails.getUsername(), pageable)));
    }
}
