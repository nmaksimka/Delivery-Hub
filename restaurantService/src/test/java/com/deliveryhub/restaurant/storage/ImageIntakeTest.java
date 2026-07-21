package com.deliveryhub.restaurant.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageIntakeTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    private final InMemoryImageStorage storage = new InMemoryImageStorage();
    private final ImageIntake intake = new ImageIntake(storage, properties(DataSize.ofBytes(16)));

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

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
        storage.put("old", PNG, "image/png");
        TransactionSynchronizationManager.initSynchronization();
        intake.discardAfterCommit("old");

        assertThat(storage.exists("old")).isTrue();
        TransactionSynchronizationUtils.invokeAfterCommit(TransactionSynchronizationManager.getSynchronizations());

        assertThat(storage.exists("old")).isFalse();
    }

    @Test
    void failedDeleteAfterCommitDoesNotBreakFollowingHooks() {
        ImageIntake failing = new ImageIntake(new InMemoryImageStorage() {
            @Override
            public void delete(String key) {
                throw new IllegalStateException("storage is down");
            }
        }, properties(DataSize.ofBytes(16)));
        boolean[] nextHookRan = {false};
        TransactionSynchronizationManager.initSynchronization();
        failing.discardAfterCommit("old");
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
