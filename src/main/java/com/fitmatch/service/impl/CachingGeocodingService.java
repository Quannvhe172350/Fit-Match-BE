package com.fitmatch.service.impl;

import com.fitmatch.config.GeocodingProperties;
import com.fitmatch.config.GeocodingProviderConfig;
import com.fitmatch.entity.GeocodeCache;
import com.fitmatch.repository.GeocodeCacheRepository;
import com.fitmatch.service.GeocodingService;
import com.fitmatch.service.support.GeoPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Bọc {@link GoogleGeocodingService} bằng bộ nhớ đệm bền trong DB (V59, UC-18).
 *
 * <p>Cùng một chuỗi địa chỉ bị geocode lại rất nhiều lần — vài chi nhánh trong
 * cùng toà nhà, backfill chạy lặp cho tới khi {@code remaining} = 0, operator
 * bấm Lưu vài lần. Mỗi lần là một lượt gọi tính tiền cho một câu trả lời không
 * đổi.
 *
 * <p>Là {@code @Primary} nên mọi chỗ inject {@code GeocodingService} đều đi qua
 * đây; provider thật được lấy qua qualifier {@code geocodingDelegate} để lớp đệm
 * không tự trỏ vào chính mình.
 *
 * <p>CHỈ đệm hai chiều tra cứu có khoá rời rạc (địa chỉ, place_id). Reverse
 * geocode nhận toạ độ GPS liên tục — hai lần bấm "Vị trí của tôi" gần như không
 * bao giờ trùng nhau tới 7 chữ số thập phân, đệm chỉ tổ làm phình bảng.
 */
@Slf4j
@Primary
@Service
public class CachingGeocodingService implements GeocodingService {

    private final GeocodingService delegate;
    private final GeocodeCacheRepository cacheRepository;
    private final GeocodingProperties properties;

    public CachingGeocodingService(@Qualifier(GeocodingProviderConfig.DELEGATE) GeocodingService delegate,
                                   GeocodeCacheRepository cacheRepository,
                                   GeocodingProperties properties) {
        this.delegate = delegate;
        this.cacheRepository = cacheRepository;
        this.properties = properties;
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }

    @Override
    public Optional<GeoPoint> geocode(String address) {
        return cached(address, "addr:" + normalize(address), () -> delegate.geocode(address));
    }

    @Override
    public Optional<GeoPoint> geocodeByPlaceId(String placeId) {
        // place_id đã là định danh chuẩn hoá sẵn, không cần hạ chữ thường.
        return cached(placeId, "place:" + StringUtils.trimWhitespace(placeId),
                () -> delegate.geocodeByPlaceId(placeId));
    }

    /**
     * Gắn tên provider vào khoá đệm (V65).
     *
     * <p>Không có tiền tố này thì sau khi đổi provider, mọi bản ghi đệm cũ vẫn
     * khớp: hệ thống trả về toạ độ VÀ place_id của dịch vụ cũ dưới danh nghĩa
     * dịch vụ mới, rồi job làm mới đem place_id đó đi tra nhầm chỗ. Bản ghi cũ
     * đơn giản là không bao giờ khớp nữa và tự hết hạn theo TTL.
     */
    private String providerScoped(String cacheKey) {
        return properties.getProvider().name() + "|" + cacheKey;
    }

    @Override
    public Optional<GeoPoint> reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        return delegate.reverseGeocode(latitude, longitude);
    }

    /**
     * Chuyển tiếp thẳng, KHÔNG đệm (V65). Cùng lý do với reverse geocode: khoá tra
     * cứu ở đây là chuỗi gõ dở, nên "ngu", "nguy", "nguyen" là ba khoá khác nhau
     * cho cùng một ý định. Đệm chúng chỉ làm phình bảng bằng những dòng gần như
     * không bao giờ trúng lại.
     */
    @Override
    public java.util.List<com.fitmatch.service.support.GeoSuggestion> autocomplete(String query, int limit) {
        return delegate.autocomplete(query, limit);
    }

    /**
     * Tra đệm trước, gọi Google khi trượt, rồi ghi lại kết quả.
     *
     * <p>Kết quả RỖNG không được đệm: địa chỉ hôm nay Google chưa biết thì mai có
     * thể biết (dữ liệu bản đồ được cập nhật liên tục), đệm lại là khoá cứng một
     * hồ sơ ở trạng thái "không tìm thấy" suốt cả kỳ TTL.
     */
    private Optional<GeoPoint> cached(String rawQuery, String cacheKey,
                                      java.util.function.Supplier<Optional<GeoPoint>> loader) {
        if (!isEnabled() || !StringUtils.hasText(rawQuery)) {
            return Optional.empty();
        }
        String hash = sha256(providerScoped(cacheKey));
        if (hash == null) {
            return loader.get();
        }
        LocalDateTime freshAfter = LocalDateTime.now().minusDays(properties.getCacheTtlDays());
        Optional<GeocodeCache> hit = cacheRepository.findById(hash)
                .filter(entry -> entry.getCachedAt().isAfter(freshAfter));
        if (hit.isPresent()) {
            GeocodeCache entry = hit.get();
            log.debug("Geocode cache hit cho \"{}\"", rawQuery);
            // Khoá đệm đã gắn tên provider nên một lần trúng chắc chắn là kết quả
            // của CHÍNH provider đang chạy — không cần lưu thêm cột nào trong bảng.
            return Optional.of(new GeoPoint(entry.getLatitude(), entry.getLongitude(),
                    entry.getFormattedAddress(), entry.getPlaceId(), entry.getLocationType(),
                    properties.getProvider()));
        }
        Optional<GeoPoint> fresh = loader.get();
        fresh.ifPresent(point -> store(hash, rawQuery, point));
        return fresh;
    }

    /**
     * Lỗi ghi đệm không được làm hỏng thao tác đang chạy — đệm là tối ưu chi phí,
     * không phải dữ liệu nghiệp vụ. Trùng khoá (hai request song song cùng
     * geocode một địa chỉ) cũng rơi vào đây và bị bỏ qua một cách vô hại.
     */
    private void store(String hash, String rawQuery, GeoPoint point) {
        try {
            cacheRepository.save(GeocodeCache.builder()
                    .queryHash(hash)
                    .query(truncate(rawQuery))
                    .latitude(point.latitude())
                    .longitude(point.longitude())
                    .placeId(point.placeId())
                    .formattedAddress(point.formattedAddress())
                    .locationType(point.locationType())
                    .cachedAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("Không ghi được geocode cache cho \"{}\": {}", rawQuery, e.getMessage());
        }
    }

    /** Gộp khoảng trắng + hạ chữ thường để "12  Nguyễn Trãi" và "12 nguyễn trãi" dùng chung một ô đệm. */
    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private static String truncate(String value) {
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 là thuật toán bắt buộc của mọi JRE; tới đây nghĩa là môi
            // trường hỏng nặng — bỏ qua đệm chứ không chặn luồng geocode.
            log.warn("Không băm được khoá cache, bỏ qua đệm: {}", e.getMessage());
            return null;
        }
    }
}
