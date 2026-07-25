package com.fitmatch.service.support;

import com.fitmatch.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * UC-075: áp template admin cấu hình (nếu có & enabled) lên thông báo; ngược lại
 * dùng văn bản mặc định trong code. Placeholder {key} thay bằng vars — key không
 * có giá trị giữ nguyên (admin nhìn thấy để sửa). Lỗi tra DB được nuốt: thông
 * báo không bao giờ fail vì template.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationTemplateResolver {

    private final NotificationTemplateRepository templateRepository;

    public record Rendered(String title, String body) {}

    public Rendered render(String code, String defaultTitle, String defaultBody, Map<String, String> vars) {
        try {
            return templateRepository.findByCode(code)
                    .filter(t -> t.isEnabled())
                    .map(t -> new Rendered(substitute(t.getTitle(), vars), substitute(t.getBody(), vars)))
                    .orElseGet(() -> new Rendered(defaultTitle, defaultBody));
        } catch (Exception e) {
            log.warn("Notification template {} lookup failed - using defaults: {}", code, e.getMessage());
            return new Rendered(defaultTitle, defaultBody);
        }
    }

    private String substitute(String text, Map<String, String> vars) {
        if (text == null || vars == null || vars.isEmpty()) return text;
        String out = text;
        for (var e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }
}
