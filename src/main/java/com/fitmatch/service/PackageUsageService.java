package com.fitmatch.service;

import com.fitmatch.entity.Booking;
import com.fitmatch.entity.CustomerPackage;
import com.fitmatch.dto.booking.CustomerPackageResponse;

import java.util.List;

/**
 * Sử dụng gói tập (UC-049/051). Mô hình: booking mua gói (trả đủ tiền gói) khi
 * hoàn tất buổi đầu sẽ kích hoạt CustomerPackage; các buổi sau đặt qua
 * customerPackageId, miễn phí, trừ dần sessionsUsed khi hoàn tất/no-show.
 */
public interface PackageUsageService {

    /**
     * Gọi khi booking chuyển COMPLETED/NO_SHOW: kích hoạt gói (booking mua gói)
     * hoặc trừ một buổi (booking thuộc gói đã mua). Idempotent theo purchase booking.
     */
    void onBookingFulfilled(Booking booking);

    /** Gói của khách còn dùng được — 409 nếu hết buổi/quá hạn/không thuộc về khách. */
    CustomerPackage requireUsable(Long customerPackageId, String customerUsername);

    /** UC-051: danh sách gói đã mua của khách (đánh giá EXPIRED lazy khi đọc). */
    List<CustomerPackageResponse> myPackages(String customerUsername);
}
