package com.fitmatch.service;

import com.fitmatch.dto.admin.SystemConfigResponse;

import java.util.List;

/**
 * UC-078: tham số hệ thống chỉnh runtime. Quy tắc: key phải được seed bằng
 * migration và có code đọc thật; API chỉ cho update giá trị, không tạo key mới.
 */
public interface SystemConfigService {

    List<SystemConfigResponse> list();

    SystemConfigResponse update(String key, String value);

    /** Giá trị Long của key, hoặc null nếu chưa có/không parse được — caller tự fallback. */
    Long findLong(String key);
}
