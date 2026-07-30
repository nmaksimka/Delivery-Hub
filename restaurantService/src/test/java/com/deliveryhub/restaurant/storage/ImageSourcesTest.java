package com.deliveryhub.restaurant.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Set;

import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

class ImageSourcesTest {

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "172.16.0.5", "192.168.1.1", "169.254.169.254", "0.0.0.0",
            "100.64.0.1", "::1", "fd00::1", "fe80::1"})
    void treatsPrivateAndLoopbackAddressesAsInternal(String address) throws UnknownHostException {
        assertThat(ImageSources.isInternal(InetAddress.getByName(address))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"93.184.216.34", "8.8.8.8", "2606:4700::1111"})
    void treatsPublicAddressesAsExternal(String address) throws UnknownHostException {
        assertThat(ImageSources.isInternal(InetAddress.getByName(address))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://www.themealdb.com/a.jpg", "https://evil.example/a.jpg", "javascript:alert(1)",
            "data:image/png;base64,AAAA", "https://user@www.themealdb.com/a.jpg", "https://www.themealdb.com/a b.jpg"})
    void distrustsUrlsOutsideAllowedSources(String url) {
        assertThat(publicSources().trusted(url)).isEmpty();
    }

    @Test
    void trustsAllowedHttpsUrlAndRejectsTooLongOne() {
        String url = "https://www.themealdb.com/images/media/meals/1.jpg";

        assertThat(publicSources().trusted(url)).contains(url);
        assertThat(publicSources().trusted(url + "?" + "a".repeat(512))).isEmpty();
        assertThat(publicSources().trusted(null)).isEmpty();
    }

    private static ImageSources publicSources() {
        StorageProperties properties = new StorageProperties("http://localhost:1", "http://localhost:1", "garage",
                "images", "key", "secret", Duration.ofMinutes(10), DataSize.ofMegabytes(1), Duration.ofSeconds(1),
                Duration.ofSeconds(2), Duration.ofSeconds(30), new StorageProperties.Mirror(Set.of("https"), Set.of("www.themealdb.com")));
        return new ImageSources(properties, host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")});
    }
}
