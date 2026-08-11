package com.fitmatch.service.support;

import com.fitmatch.config.MediaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Đọc kích thước ảnh và sinh bản thu nhỏ bằng ImageIO của JDK — cố ý KHÔNG thêm
 * thư viện xử lý ảnh nào: thumbnail ở đây chỉ cần đủ nhẹ cho lưới ảnh, không phải
 * pipeline chỉnh sửa.
 *
 * <p>ImageIO không đọc được WebP; với ảnh WebP ta bỏ qua thumbnail và để width/height
 * null thay vì từ chối upload — ảnh gốc vẫn dùng bình thường (FE tự co giãn).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageProcessor {

    private final MediaProperties properties;

    public record Dimensions(Integer width, Integer height) {}

    /** Thumbnail đã encode; {@code bytes} null nghĩa là không sinh được. */
    public record Thumbnail(byte[] bytes, String mimeType) {}

    public Dimensions readDimensions(byte[] content) {
        BufferedImage image = decode(content);
        return image == null
                ? new Dimensions(null, null)
                : new Dimensions(image.getWidth(), image.getHeight());
    }

    /**
     * Bản thu nhỏ giữ tỉ lệ, cạnh dài tối đa {@code app.media.thumbnail-max-edge}.
     * Ảnh vốn đã nhỏ hơn ngưỡng thì không sinh thumbnail (thừa file, thừa tiền lưu trữ).
     */
    public Thumbnail createThumbnail(byte[] content) {
        int maxEdge = properties.getThumbnailMaxEdge();
        if (maxEdge <= 0) return new Thumbnail(null, null);

        BufferedImage source = decode(content);
        if (source == null) return new Thumbnail(null, null);

        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= maxEdge && height <= maxEdge) return new Thumbnail(null, null);

        double scale = (double) maxEdge / Math.max(width, height);
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        // TYPE_INT_RGB: thumbnail luôn ghi ra JPEG nên kênh alpha bị bỏ; nền đen
        // của ảnh trong suốt được tránh bằng cách vẽ lên nền trắng trước.
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(target, "jpg", out)) return new Thumbnail(null, null);
            return new Thumbnail(out.toByteArray(), "image/jpeg");
        } catch (Exception e) {
            log.warn("Could not encode thumbnail: {}", e.getMessage());
            return new Thumbnail(null, null);
        }
    }

    private BufferedImage decode(byte[] content) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
            return ImageIO.read(in);
        } catch (Exception e) {
            log.debug("ImageIO could not decode the image: {}", e.getMessage());
            return null;
        }
    }
}
