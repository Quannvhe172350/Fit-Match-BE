package com.fitmatch.repository.projection;

/**
 * Một dòng kết quả của truy vấn tìm gym theo bán kính (UC-18): id gym và khoảng
 * cách (km) từ điểm người dùng tới điểm gần nhất của gym (trụ sở hoặc chi nhánh).
 */
public interface GymDistanceView {

    Long getGymId();

    Double getDistanceKm();
}
