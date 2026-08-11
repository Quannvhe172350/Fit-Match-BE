package com.fitmatch.service;

import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.config.MediaProperties;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.service.support.ImageFileValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageFileValidatorTest {

    private ImageFileValidator validator;
    private MediaProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MediaProperties();
        properties.setMaxImageSizeMb(1);
        validator = new ImageFileValidator(properties);
    }

    private static byte[] withHeader(byte[] header, int totalSize) {
        byte[] bytes = new byte[Math.max(totalSize, header.length)];
        System.arraycopy(header, 0, bytes, 0, header.length);
        return bytes;
    }

    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PNG_HEADER =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    void validate_realJpeg_returnsDetectedMimeType() {
        var file = new MockMultipartFile("files", "photo.jpg", "image/jpeg", withHeader(JPEG_HEADER, 64));

        assertThat(validator.validate(file)).isEqualTo("image/jpeg");
    }

    @Test
    void validate_realPng_returnsDetectedMimeType() {
        var file = new MockMultipartFile("files", "photo.png", "image/png", withHeader(PNG_HEADER, 64));

        assertThat(validator.validate(file)).isEqualTo("image/png");
    }

    @Test
    void validate_htmlDisguisedAsJpeg_isRejected() {
        // Đây là lý do phải đọc magic bytes: client tự đặt Content-Type và tên file.
        // Nếu lọt, bucket public sẽ phục vụ file HTML như một trang web trên domain
        // storage — stored XSS.
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("files", "photo.jpg", "image/jpeg", html);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void validate_emptyFile_isRejected() {
        var file = new MockMultipartFile("files", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validate_oversizedFile_isRejected() {
        var file = new MockMultipartFile("files", "big.jpg", "image/jpeg",
                withHeader(JPEG_HEADER, 2 * 1024 * 1024));

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1MB");
    }

    @Test
    void validate_gifWhenNotWhitelisted_isRejected() {
        // GIF nhận dạng được nhưng không nằm trong allowed-mime-types mặc định.
        byte[] gif = withHeader(new byte[]{'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0}, 32);
        var file = new MockMultipartFile("files", "anim.gif", "image/gif", gif);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateBatch_tooManyFiles_isRejected() {
        properties.setMaxFilesPerRequest(2);
        var files = new org.springframework.web.multipart.MultipartFile[]{
                new MockMultipartFile("files", "a.jpg", "image/jpeg", withHeader(JPEG_HEADER, 16)),
                new MockMultipartFile("files", "b.jpg", "image/jpeg", withHeader(JPEG_HEADER, 16)),
                new MockMultipartFile("files", "c.jpg", "image/jpeg", withHeader(JPEG_HEADER, 16)),
        };

        assertThatThrownBy(() -> validator.validateBatch(files))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 files");
    }

    @Test
    void validateBatch_noFiles_isRejected() {
        assertThatThrownBy(() -> validator.validateBatch(new org.springframework.web.multipart.MultipartFile[0]))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void extensionFor_derivesFromDetectedMimeNotClientFilename() {
        assertThat(validator.extensionFor("image/png")).isEqualTo("png");
        assertThat(validator.extensionFor("image/webp")).isEqualTo("webp");
        assertThat(validator.extensionFor("image/jpeg")).isEqualTo("jpg");
    }
}
