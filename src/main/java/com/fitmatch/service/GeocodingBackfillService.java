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
