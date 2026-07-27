package com.deliveryhub.restaurant.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageIntakeTest {

    private static final byte[] PNG = TestImages.png(1, 1);
    private static final DataSize LIMIT = DataSize.ofKilobytes(4);

    private final InMemoryImageStorage storage = new InMemoryImageStorage();
    private final ImageIntake intake = new ImageIntake(storage, properties(LIMIT));

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void storesValidImageWithDetectedContentType() {
        intake.save("a/b", PNG);

        assertThat(TestImages.decode(storage.contentOf("a/b")).getWidth()).isEqualTo(1);
        assertThat(storage.contentTypeOf("a/b")).isEqualTo("image/png");
    }

    @Test
    void rejectsImageOverTheLimit() {
        byte[] big = new byte[(int) LIMIT.toBytes() + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        assertThatThrownBy(() -> intake.save("big", big)).isInstanceOf(ImageTooLargeException.class);
        assertThat(storage.exists("big")).isFalse();
    }

    @Test
    void acceptsImageExactlyAtTheLimit() {
        ImageIntake tight = new ImageIntake(storage, properties(DataSize.ofBytes(PNG.length)));

        tight.save("exact", PNG);

        assertThat(storage.exists("exact")).isTrue();
    }

    @Test
    void dropsExifFromJpeg() {
        byte[] withExif = TestImages.jpegWithExif("GPS 56.48N 84.95E");

        intake.save("photo", withExif);

        String stored = new String(storage.contentOf("photo"), StandardCharsets.ISO_8859_1);
        assertThat(stored).doesNotContain("GPS 56.48N").doesNotContain("Exif");
        assertThat(TestImages.decode(storage.contentOf("photo")).getWidth()).isEqualTo(2);
        assertThat(storage.contentTypeOf("photo")).isEqualTo("image/jpeg");
    }

    @Test
    void rejectsImageWithTooLargeSides() {
        byte[] wide = TestImages.png(ImageReencoder.MAX_SIDE + 1, 1);

        assertThatThrownBy(() -> intake.save("wide", wide)).isInstanceOf(ImageTooLargeException.class);
        assertThat(storage.exists("wide")).isFalse();
    }

    @Test
    void rejectsBrokenImageWithValidSignature() {
        byte[] broken = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

        assertThatThrownBy(() -> intake.save("broken", broken)).isInstanceOf(UnsupportedImageException.class);
        assertThat(storage.exists("broken")).isFalse();
    }

    @Test
    void keepsWebpAsIs() {
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 data".getBytes(StandardCharsets.ISO_8859_1);

        intake.save("w", webp);

        assertThat(storage.contentOf("w")).containsExactly(webp);
    }

    @Test
    void rejectsContentThatIsNotAnImage() {
        assertThatThrownBy(() -> intake.save("text", "hello world".getBytes()))
                .isInstanceOf(UnsupportedImageException.class);
        assertThat(storage.exists("text")).isFalse();
    }

    @Test
    void removesUploadedImageWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        intake.save("fresh", PNG);

        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(storage.exists("fresh")).isFalse();
    }

    @Test
    void keepsUploadedImageWhenTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();
        intake.save("fresh", PNG);

        TransactionSynchronizationUtils.invokeAfterCommit(TransactionSynchronizationManager.getSynchronizations());
        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(storage.exists("fresh")).isTrue();
    }

    @Test
    void deletesReplacedImageOnlyAfterCommit() {
        storage.put("menu/1/2/old", PNG, "image/png");
        TransactionSynchronizationManager.initSynchronization();
        intake.discardAfterCommit("menu/1/2/old");

        assertThat(storage.exists("menu/1/2/old")).isTrue();
        TransactionSynchronizationUtils.invokeAfterCommit(TransactionSynchronizationManager.getSynchronizations());

        assertThat(storage.exists("menu/1/2/old")).isFalse();
    }

    @Test
    void keepsSharedMirroredImage() {
        storage.put("mealdb/meals/52772", PNG, "image/png");
        TransactionSynchronizationManager.initSynchronization();
        intake.discardAfterCommit("mealdb/meals/52772");

        TransactionSynchronizationUtils.invokeAfterCommit(TransactionSynchronizationManager.getSynchronizations());

        assertThat(storage.exists("mealdb/meals/52772")).isTrue();
    }

    @Test
    void failedDeleteAfterCommitDoesNotBreakFollowingHooks() {
        ImageIntake failing = new ImageIntake(new InMemoryImageStorage() {
            @Override
            public void delete(String key) {
                throw new IllegalStateException("storage is down");
            }
        }, properties(LIMIT));
        boolean[] nextHookRan = {false};
        TransactionSynchronizationManager.initSynchronization();
        failing.discardAfterCommit("restaurants/1/old");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                nextHookRan[0] = true;
            }
        });

        TransactionSynchronizationUtils.invokeAfterCommit(TransactionSynchronizationManager.getSynchronizations());

        assertThat(nextHookRan[0]).isTrue();
    }

    private static void complete(int status) {
        TransactionSynchronizationUtils.invokeAfterCompletion(
                TransactionSynchronizationManager.getSynchronizations(), status);
    }

    static StorageProperties properties(DataSize maxImageSize) {
        return new StorageProperties("http://localhost:1", "http://localhost:1", "garage", "images", "key", "secret",
                Duration.ofMinutes(10), maxImageSize, Duration.ofSeconds(1), Duration.ofSeconds(2),
                new StorageProperties.Mirror(Set.of("http"), Set.of("localhost")));
    }
}
