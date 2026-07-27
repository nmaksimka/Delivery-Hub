package com.deliveryhub.restaurant.storage;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class S3ImageStorageTest {

    private static final StorageProperties PROPERTIES = new StorageProperties("http://garage:3900",
            "http://localhost:3900", "garage", "images", "GKtest", "secret", Duration.ofHours(12),
            DataSize.ofMegabytes(5), Duration.ofSeconds(1), Duration.ofSeconds(2),
            Duration.ofSeconds(30), new StorageProperties.Mirror(Set.of("https"), Set.of("www.themealdb.com")));

    @Test
    void signsUrlForPublicEndpointWithConfiguredTtl() {
        String url = storageAt("2026-07-10T08:15:00Z").readUrl("menu/1/2/photo");

        assertThat(url).startsWith("http://localhost:3900/images/menu/1/2/photo?")
                .contains("X-Amz-Expires=43200")
                .contains("X-Amz-Date=20260710T060000Z")
                .contains("X-Amz-Signature=");
    }

    @Test
    void givesSameUrlWithinOneWindow() {
        assertThat(storageAt("2026-07-10T06:00:01Z").readUrl("menu/1/2/photo"))
                .isEqualTo(storageAt("2026-07-10T11:59:59Z").readUrl("menu/1/2/photo"));
    }

    @Test
    void givesNewUrlInNextWindow() {
        assertThat(storageAt("2026-07-10T11:59:59Z").readUrl("menu/1/2/photo"))
                .isNotEqualTo(storageAt("2026-07-10T12:00:00Z").readUrl("menu/1/2/photo"));
    }

    private static S3ImageStorage storageAt(String instant) {
        return new S3ImageStorage(null, AwsBasicCredentials.create("GKtest", "secret"), PROPERTIES,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
