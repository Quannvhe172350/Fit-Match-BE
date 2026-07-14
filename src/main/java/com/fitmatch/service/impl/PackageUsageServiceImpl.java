package com.fitmatch.service.impl;

import com.fitmatch.common.enums.CustomerPackageStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.dto.booking.CustomerPackageResponse;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.CustomerPackage;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.CustomerPackageRepository;
import com.fitmatch.service.PackageUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PackageUsageServiceImpl implements PackageUsageService {

    private final CustomerPackageRepository customerPackageRepository;

    @Override
    @Transactional
    public void onBookingFulfilled(Booking booking) {
        if (booking.getCustomerPackage() != null) {
            consume(booking.getCustomerPackage());
            return;
        }
        if (booking.getTrainingPackage() != null
                && !customerPackageRepository.existsByPurchaseBooking_Id(booking.getId())) {
            activate(booking);
        }
    }

    /** Kích hoạt gói khi booking mua gói hoàn tất buổi đầu — buổi đầu tính là 1 buổi đã dùng. */
    private void activate(Booking purchase) {
        var pkg = purchase.getTrainingPackage();
        int total = pkg.getSessionCount() != null ? pkg.getSessionCount() : 1;
        CustomerPackage cp = CustomerPackage.builder()
                .customer(purchase.getCustomer())
                .trainingPackage(pkg)
                .purchaseBooking(purchase)
                .sessionsTotal(total)
                .sessionsUsed(1)
                .expiresAt(pkg.getValidityDays() != null
                        ? LocalDateTime.now().plusDays(pkg.getValidityDays()) : null)
                .status(total <= 1 ? CustomerPackageStatus.EXHAUSTED : CustomerPackageStatus.ACTIVE)
                .build();
        customerPackageRepository.save(cp);
        log.info("Customer package {} activated from booking {} ({} sessions, first consumed)",
                cp.getId(), purchase.getId(), total);
    }

    private void consume(CustomerPackage cp) {
        cp.setSessionsUsed(cp.getSessionsUsed() + 1);
        if (cp.getSessionsUsed() >= cp.getSessionsTotal()) {
            cp.setStatus(CustomerPackageStatus.EXHAUSTED);
        }
        customerPackageRepository.save(cp);
        log.info("Customer package {} consumed a session ({}/{})",
                cp.getId(), cp.getSessionsUsed(), cp.getSessionsTotal());
    }

    @Override
    @Transactional
    public CustomerPackage requireUsable(Long customerPackageId, String customerUsername) {
        CustomerPackage cp = customerPackageRepository
                .findByIdAndCustomer_Username(customerPackageId, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Customer package", customerPackageId));
        refreshExpiry(cp);
        if (cp.getStatus() != CustomerPackageStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "Customer package is " + cp.getStatus() + " and cannot be used for booking");
        }
        return cp;
    }

    @Override
    @Transactional
    public List<CustomerPackageResponse> myPackages(String customerUsername) {
        return customerPackageRepository.findByCustomer_UsernameOrderByIdDesc(customerUsername).stream()
                .peek(this::refreshExpiry)
                .map(CustomerPackageResponse::of)
                .toList();
    }

    /** Đánh giá hạn dùng lazy khi đọc — không cần scheduler riêng cho EXPIRED. */
    private void refreshExpiry(CustomerPackage cp) {
        if (cp.getStatus() == CustomerPackageStatus.ACTIVE
                && cp.getExpiresAt() != null && cp.getExpiresAt().isBefore(LocalDateTime.now())) {
            cp.setStatus(CustomerPackageStatus.EXPIRED);
            customerPackageRepository.save(cp);
        }
    }
}
