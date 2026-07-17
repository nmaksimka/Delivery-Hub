package com.deliveryhub.restaurant.storage;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageIntakeTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    private final InMemoryImageStorage storage = new InMemoryImageStorage();
    private final ImageIntake intake = new ImageIntake(storage, properties(DataSize.ofBytes(16)));

    @Test
    void storesValidImageWithDetectedContentType() {
        intake.save("a/b", PNG);

        assertThat(storage.contentOf("a/b")).containsExactly(PNG);
        assertThat(storage.contentTypeOf("a/b")).isEqualTo("image/png");
    }

    @Test
    void rejectsImageOverTheLimit() {
        byte[] big = new byte[17];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        assertThatThrownBy(() -> intake.save("big", big)).isInstanceOf(ImageTooLargeException.class);
        assertThat(storage.exists("big")).isFalse();
    }

    @Test
    void acceptsImageExactlyAtTheLimit() {
        byte[] exact = new byte[16];
        System.arraycopy(PNG, 0, exact, 0, PNG.length);

        intake.save("exact", exact);

        assertThat(storage.exists("exact")).isTrue();
    }

    @Test
    void rejectsContentThatIsNotAnImage() {
        assertThatThrownBy(() -> intake.save("text", "hello world".getBytes()))
                .isInstanceOf(UnsupportedImageException.class);
        assertThat(storage.exists("text")).isFalse();
    }

    static StorageProperties properties(DataSize maxImageSize) {
        return new StorageProperties("http://localhost:1", "http://localhost:1", "garage", "images", "key", "secret",
                Duration.ofMinutes(10), maxImageSize, Duration.ofSeconds(1), Duration.ofSeconds(2));
    }
}
