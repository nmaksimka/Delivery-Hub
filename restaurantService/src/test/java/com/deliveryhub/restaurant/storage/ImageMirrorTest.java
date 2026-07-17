package com.deliveryhub.restaurant.storage;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.util.unit.DataSize;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class ImageMirrorTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};

    private final WireMockServer images = new WireMockServer(options().dynamicPort());
    private final InMemoryImageStorage storage = new InMemoryImageStorage();

    private ImageMirror mirror;

    @BeforeEach
    void setUp() {
        images.start();
        StorageProperties properties = ImageIntakeTest.properties(DataSize.ofBytes(32));
        mirror = new ImageMirror(storage, new ImageIntake(storage, properties), properties, RestClient.builder());
    }

    @AfterEach
    void tearDown() {
        images.stop();
    }

    @Test
    void copiesImageIntoStorageUnderGivenKey() {
        images.stubFor(get(urlEqualTo("/meal.jpg")).willReturn(aResponse().withStatus(200).withBody(JPEG)));

        assertThat(mirror.mirror("mealdb/meals/1", images.baseUrl() + "/meal.jpg")).contains("mealdb/meals/1");

        assertThat(storage.contentOf("mealdb/meals/1")).containsExactly(JPEG);
        assertThat(storage.contentTypeOf("mealdb/meals/1")).isEqualTo("image/jpeg");
    }

    @Test
    void skipsDownloadWhenObjectIsAlreadyStored() {
        storage.put("mealdb/meals/1", JPEG, "image/jpeg");

        assertThat(mirror.mirror("mealdb/meals/1", images.baseUrl() + "/meal.jpg")).contains("mealdb/meals/1");

        images.verify(0, getRequestedFor(urlEqualTo("/meal.jpg")));
    }

    @Test
    void givesUpOnNotFound() {
        images.stubFor(get(urlEqualTo("/missing.jpg")).willReturn(aResponse().withStatus(404)));

        assertThat(mirror.mirror("k", images.baseUrl() + "/missing.jpg")).isEmpty();
        assertThat(storage.size()).isZero();
    }

    @Test
    void givesUpOnOversizedImage() {
        images.stubFor(get(urlEqualTo("/big.jpg")).willReturn(aResponse().withStatus(200).withBody(new byte[1000])));

        assertThat(mirror.mirror("k", images.baseUrl() + "/big.jpg")).isEmpty();
        assertThat(storage.size()).isZero();
    }

    @Test
    void givesUpOnContentThatIsNotAnImage() {
        images.stubFor(get(urlEqualTo("/page")).willReturn(aResponse().withStatus(200).withBody("<html>")));

        assertThat(mirror.mirror("k", images.baseUrl() + "/page")).isEmpty();
    }

    @Test
    void ignoresMissingAndNonHttpUrls() {
        assertThat(mirror.mirror("k", null)).isEmpty();
        assertThat(mirror.mirror("k", "file:///etc/passwd")).isEmpty();
        assertThat(mirror.mirror("k", "ftp://host/a.jpg")).isEmpty();
    }

    @Test
    void survivesUnreachableHost() {
        String url = images.baseUrl() + "/gone.jpg";
        images.stop();

        assertThat(mirror.mirror("k", url)).isEmpty();
    }
}
