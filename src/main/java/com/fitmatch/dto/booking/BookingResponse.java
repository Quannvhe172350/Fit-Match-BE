package com.fitmatch.dto.booking;

import com.fitmatch.common.enums.BookingStatus;
import com.fitmatch.entity.Booking;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private Long id;
    private String customerUsername;
    private Long gymId;
    private String gymName;
    private Long branchId;
    private String branchName;
    private Long serviceId;
    private String serviceName;
    private Long packageId;
    private String packageName;
    private Long ptId;
    private Long customerPackageId;
    private LocalDateTime checkedInAt;
    private String ptDisplayName;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private BookingStatus status;
    private String statusReason;
    private String customerNote;
    private BigDecimal totalAmount;
    private BigDecimal payableAmount;
    private BigDecimal discountAmount;
    private String voucherCode;
    private boolean lateCancellation;
    private LocalDateTime createdAt;

    public static BookingResponse of(Booking b) {
        return BookingResponse.builder()
                .id(b.getId())
                .customerUsername(b.getCustomer() != null ? b.getCustomer().getUsername() : null)
                .gymId(b.getGymProfile() != null ? b.getGymProfile().getId() : null)
                .gymName(b.getGymProfile() != null ? b.getGymProfile().getGymName() : null)
                .branchId(b.getGymBranch() != null ? b.getGymBranch().getId() : null)
                .branchName(b.getGymBranch() != null ? b.getGymBranch().getName() : null)
                .serviceId(b.getGymService() != null ? b.getGymService().getId() : null)
                .serviceName(b.getGymService() != null ? b.getGymService().getName() : null)
                .packageId(b.getTrainingPackage() != null ? b.getTrainingPackage().getId() : null)
                .packageName(b.getTrainingPackage() != null ? b.getTrainingPackage().getName() : null)
                .ptId(b.getPtProfile() != null ? b.getPtProfile().getId() : null)
                .customerPackageId(b.getCustomerPackage() != null ? b.getCustomerPackage().getId() : null)
                .checkedInAt(b.getCheckedInAt())
                .ptDisplayName(b.getPtProfile() != null ? b.getPtProfile().getDisplayName() : null)
                .startAt(b.getStartAt())
                .endAt(b.getEndAt())
                .status(b.getStatus())
                .statusReason(b.getStatusReason())
                .customerNote(b.getCustomerNote())
                .totalAmount(b.getTotalAmount())
                .payableAmount(b.getPayableAmount())
                .discountAmount(b.getDiscountAmount())
                .voucherCode(b.getVoucher() != null ? b.getVoucher().getCode() : null)
                .lateCancellation(b.isLateCancellation())
                .createdAt(b.getCreatedAt())
                .build();
    }
}
