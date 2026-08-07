package com.fitmatch.dto.gym;

import com.fitmatch.entity.GymBranch;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchResponse {

    private Long id;
    private String name;
    private String address;
    private String city;
    private String district;
    private String phone;
    private String amenities;
    private Integer capacity;
    private boolean active;

    /** UC-18 (V55): toạ độ chi nhánh — marker bản đồ + tìm kiếm theo bán kính. */
    private java.math.BigDecimal latitude;
    private java.math.BigDecimal longitude;

    /** Địa chỉ Google đã chuẩn hoá; null khi chưa geocode được. */
    private String formattedAddress;

    /**
     * V60: toạ độ do chủ gym kéo ghim tay. Form sửa PHẢI đọc và gửi lại cờ này —
     * thiếu nó thì mỗi lần sửa số điện thoại là ghim tay bị hạ cấp về "Google
     * đoán" và job làm mới sẽ kéo đi chỗ khác.
     */
    private boolean coordinatesPinned;

    /** Giờ mở cửa theo ngày — chỉ đổ ở luồng public marketplace (UC-009). */
    private java.util.List<OperatingHourDto> operatingHours;

    public static BranchResponse of(GymBranch b) {
        return BranchResponse.builder()
                .id(b.getId())
                .name(b.getName())
                .address(b.getAddress())
                .city(b.getCity())
                .district(b.getDistrict())
                .phone(b.getPhone())
                .amenities(b.getAmenities())
                .capacity(b.getCapacity())
                .active(b.isActive())
                .latitude(b.getLatitude())
                .longitude(b.getLongitude())
                .formattedAddress(b.getFormattedAddress())
                .coordinatesPinned(b.isCoordinatesPinned())
                .build();
    }

    public static BranchResponse of(GymBranch b, java.util.List<OperatingHourDto> hours) {
        BranchResponse r = of(b);
        r.setOperatingHours(hours);
        return r;
    }
}
