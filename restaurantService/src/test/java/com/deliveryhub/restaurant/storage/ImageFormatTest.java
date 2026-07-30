package com.deliveryhub.restaurant.storage;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageFormatTest {

    @Test
    void detectsJpeg() {
        assertThat(ImageFormat.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0}))
                .contains(ImageFormat.JPEG);
    }

    @Test
    void detectsPng() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        assertThat(ImageFormat.detect(png)).contains(ImageFormat.PNG);
    }

    @Test
    void detectsWebp() {
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(ImageFormat.detect(webp)).contains(ImageFormat.WEBP);
    }

    @Test
    void rejectsRiffContainerThatIsNotWebp() {
        byte[] wav = "RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(ImageFormat.detect(wav)).isEmpty();
    }

    @Test
    void rejectsHtmlAndTooShortContent() {
        assertThat(ImageFormat.detect("<html></html>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormat.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(ImageFormat.detect(new byte[0])).isEmpty();
    }

    @Test
    void exposesContentTypes() {
        assertThat(ImageFormat.JPEG.contentType()).isEqualTo("image/jpeg");
        assertThat(ImageFormat.PNG.contentType()).isEqualTo("image/png");
        assertThat(ImageFormat.WEBP.contentType()).isEqualTo("image/webp");
    }
}
