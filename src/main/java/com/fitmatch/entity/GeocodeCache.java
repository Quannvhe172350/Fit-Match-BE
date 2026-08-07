package com.fitmatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một câu trả lời của Google đã lưu lại (V59, UC-18).
 *
 * <p>KHÔNG kế thừa {@code BaseEntity}: đây là bộ nhớ đệm, không phải dữ liệu
 * nghiệp vụ — không cần audit created_by/updated_at, và khoá chính là hash của
 * truy vấn chứ không phải id tự tăng.
 */
@Entity
@Table(name = "geocode_cache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeocodeCache {

    /** SHA-256 (hex) của chuỗi truy vấn đã chuẩn hoá. */
    @Id
    @Column(name = "query_hash", length = 64, nullable = false)
    private String queryHash;

    /** Chuỗi gốc — chỉ để con người đọc khi soi cache, không dùng để tra cứu. */
    @Column(name = "query", length = 500, nullable = false)
    private String query;

    @Column(name = "latitude", precision = 10, scale = 7)
    private java.math.BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private java.math.BigDecimal longitude;

    @Column(name = "place_id", length = 255)
    private String placeId;

    @Column(name = "formatted_address", length = 500)
    private String formattedAddress;

    @Column(name = "location_type", length = 30)
    private String locationType;

    @Column(name = "cached_at", nullable = false)
    private java.time.LocalDateTime cachedAt;
}
