package com.fitmatch.service;

/**
 * Bổ sung toạ độ cho dữ liệu gym/chi nhánh đã tồn tại trước V55 (UC-18).
 *
 * <p>Các hồ sơ tạo trước khi có tính năng geocode đều để trống lat/lng nên sẽ
 * không bao giờ xuất hiện trong kết quả "gym quanh đây". Admin chạy backfill một
 * lần sau khi cấu hình API key.
 */
public interface GeocodingBackfillService {

    /**
     * @param limit trần số bản ghi xử lý trong một lần gọi — mỗi bản ghi là một
     *              lượt gọi Google, chạy hết vài nghìn hồ sơ trong một HTTP request
     *              sẽ timeout và có thể chạm rate limit của Google.
     */
    BackfillResult backfill(int limit, String actorUsername);

    /**
     * V59 — tra lại toạ độ của những bản ghi geocode đã lâu, THEO place_id.
     *
     * <p>Địa điểm ngoài đời không đứng yên: toà nhà đổi số, Google chỉnh lại dữ
     * liệu bản đồ. Tra theo place_id chứ không geocode lại chuỗi địa chỉ, vì
     * chuỗi chữ có thể ra một địa điểm khác hẳn và dời ghim của phòng gym đi nơi
     * khác. Bản ghi không có place_id (operator tự ghim) không bao giờ bị đụng.
     *
     * @return số bản ghi được quét và số bản ghi thực sự đổi toạ độ
     */
    RefreshResult refreshStale(int limit);

    /**
     * @param scanned số bản ghi đã tra lại
     * @param updated số bản ghi có toạ độ mới khác toạ độ cũ
     */
    record RefreshResult(int scanned, int updated) {
    }

    /** Tình trạng phủ toạ độ toàn hệ thống — Admin xem để biết có cần chạy backfill không. */
    GeocodingCoverage coverage();

    /**
     * @param gymsTotal        tổng số hồ sơ gym
     * @param gymsGeocoded     số hồ sơ đã có toạ độ (mới xuất hiện được trong tìm quanh đây)
     * @param gymsImprecise    số hồ sơ Google chỉ khớp tới mức tương đối
     * @param gymsPinned       số hồ sơ chủ gym tự kéo ghim — job làm mới không đụng vào
     * @param branchesTotal    tổng số chi nhánh đang hoạt động
     * @param branchesGeocoded số chi nhánh đã có toạ độ
     * @param cachedQueries    số câu trả lời của Google đang được đệm
     */
    record GeocodingCoverage(long gymsTotal, long gymsGeocoded, long gymsImprecise, long gymsPinned,
                             long branchesTotal, long branchesGeocoded,
                             long cachedQueries, boolean enabled) {
    }

    /**
     * @param gymsScanned   số hồ sơ gym được thử geocode
     * @param gymsUpdated   số hồ sơ gym có toạ độ mới
     * @param branchesScanned số chi nhánh được thử geocode
     * @param branchesUpdated số chi nhánh có toạ độ mới
     * @param remaining     số bản ghi vẫn còn thiếu toạ độ sau lần chạy này
     */
    record BackfillResult(int gymsScanned, int gymsUpdated,
                          int branchesScanned, int branchesUpdated,
                          int remaining) {
    }
}
