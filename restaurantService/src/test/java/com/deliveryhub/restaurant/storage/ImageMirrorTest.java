package com.deliveryhub.restaurant.storage;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.util.unit.DataSize;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class ImageMirrorTest {

    private static final byte[] JPEG = TestImages.jpeg(1, 1);

    private final WireMockServer images = new WireMockServer(options().dynamicPort());
    private final InMemoryImageStorage storage = new InMemoryImageStorage();

    private ImageMirror mirror;

    @BeforeEach
    void setUp() {
        images.start();
        StorageProperties properties = ImageIntakeTest.properties(DataSize.ofKilobytes(4));
        mirror = mirrorWith(properties, host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")});
    }

    private ImageMirror mirrorWith(StorageProperties properties, ImageSources.HostResolver resolver) {
        return new ImageMirror(storage, new ImageIntake(storage, properties), new ImageSources(properties, resolver),
                properties, RestClient.builder());
    }

    @AfterEach
    void tearDown() {
        images.stop();
    }

    @Test
    void copiesImageIntoStorageUnderGivenKey() {
        images.stubFor(get(urlEqualTo("/meal.jpg")).willReturn(aResponse().withStatus(200).withBody(JPEG)));

        assertThat(mirror.mirror("mealdb/meals/1", images.baseUrl() + "/meal.jpg")).contains("mealdb/meals/1");

        assertThat(TestImages.decode(storage.contentOf("mealdb/meals/1")).getWidth()).isEqualTo(1);
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
        images.stubFor(get(urlEqualTo("/big.jpg")).willReturn(aResponse().withStatus(200).withBody(new byte[5000])));

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

    @Test
    void doesNotFollowRedirects() {
        images.stubFor(get(urlEqualTo("/moved.jpg"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", images.baseUrl() + "/meal.jpg")));
        images.stubFor(get(urlEqualTo("/meal.jpg")).willReturn(aResponse().withStatus(200).withBody(JPEG)));

        assertThat(mirror.mirror("k", images.baseUrl() + "/moved.jpg")).isEmpty();

        images.verify(0, getRequestedFor(urlEqualTo("/meal.jpg")));
        assertThat(storage.size()).isZero();
    }

    @Test
    void refusesHostOutsideAllowlist() {
        images.stubFor(get(urlEqualTo("/meal.jpg")).willReturn(aResponse().withStatus(200).withBody(JPEG)));

        assertThat(mirror.mirror("k", "http://127.0.0.1:" + images.port() + "/meal.jpg")).isEmpty();

        images.verify(0, getRequestedFor(urlEqualTo("/meal.jpg")));
    }

    @Test
    void refusesAllowedHostThatResolvesToInternalAddress() {
        images.stubFor(get(urlEqualTo("/meal.jpg")).willReturn(aResponse().withStatus(200).withBody(JPEG)));
        ImageMirror strict = mirrorWith(ImageIntakeTest.properties(DataSize.ofKilobytes(4)), InetAddress::getAllByName);

        assertThat(strict.mirror("k", images.baseUrl() + "/meal.jpg")).isEmpty();

        images.verify(0, getRequestedFor(urlEqualTo("/meal.jpg")));
    }

    @Test
    void refusesPlainHttpWhenOnlyHttpsIsAllowed() {
        images.stubFor(get(urlEqualTo("/meal.jpg")).willReturn(aResponse().withStatus(200).withBody(JPEG)));
        StorageProperties httpsOnly = new StorageProperties("http://localhost:1", "http://localhost:1", "garage",
                "images", "key", "secret", Duration.ofMinutes(10), DataSize.ofKilobytes(4), Duration.ofSeconds(1),
                Duration.ofSeconds(2), new StorageProperties.Mirror(Set.of("https"), Set.of("localhost")));
        ImageMirror strict = mirrorWith(httpsOnly, host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")});

        assertThat(strict.mirror("k", images.baseUrl() + "/meal.jpg")).isEmpty();

        images.verify(0, getRequestedFor(urlEqualTo("/meal.jpg")));
    }
}
