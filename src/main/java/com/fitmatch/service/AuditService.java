package com.fitmatch.service;

/**
 * Ghi nhật ký audit cho các hành động nhạy cảm. Cross-cutting, dùng lại bởi nhiều module.
 * Actor & timestamp do JPA auditing (BaseEntity) tự điền.
 */
public interface AuditService {

    void record(String action, String targetType, Object targetId, String description);
}
