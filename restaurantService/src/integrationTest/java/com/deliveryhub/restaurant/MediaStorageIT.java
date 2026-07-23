package com.deliveryhub.restaurant;

import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import com.deliveryhub.restaurant.storage.ImageStorage;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GarageContainerConfig.class})
class MediaStorageIT {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9, 8, 7, 6, 5};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 4, 3, 2, 1};

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RestaurantRepository restaurantRepository;
    @Autowired
    private MenuItemRepository menuItemRepository;
    @Autowired
    private ImageStorage imageStorage;

    @DynamicPropertySource
    static void smallImageLimit(DynamicPropertyRegistry registry) {
        registry.add("app.storage.max-image-size", () -> "1KB");
    }

    @Test
    void uploadedRestaurantImageIsServedThroughStableSignedUrl() throws Exception {
        Restaurant restaurant = restaurantRepository.save(new Restaurant("Photo", null, null));

        MvcResult uploaded = upload("/api/restaurants/" + restaurant.getId() + "/image", "image/png", PNG);

        String imageUrl = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.imageUrl");
        assertThat(imageUrl).contains("/images/restaurants/" + restaurant.getId() + "/")
                .contains("X-Amz-Expires=43200").contains("X-Amz-Signature=");
        HttpResponse<byte[]> fetched = fetch(imageUrl);
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).containsExactly(PNG);
        assertThat(fetched.headers().firstValue("Content-Type")).contains("image/png");
        String servedUrl = JsonPath.read(mvc.perform(get("/api/restaurants/" + restaurant.getId())).andReturn()
                .getResponse().getContentAsString(), "$.imageUrl");
        assertThat(servedUrl).isEqualTo(imageUrl);
    }

    @Test
    void replacingImageRemovesThePreviousObject() throws Exception {
        Restaurant restaurant = restaurantRepository.save(new Restaurant("Twice", null, null));
        String uri = "/api/restaurants/" + restaurant.getId() + "/image";

        upload(uri, "image/png", PNG);
        String firstKey = restaurantRepository.findById(restaurant.getId()).orElseThrow().getImageKey();
        upload(uri, "image/jpeg", JPEG);
        String secondKey = restaurantRepository.findById(restaurant.getId()).orElseThrow().getImageKey();

        assertThat(secondKey).isNotEqualTo(firstKey);
        assertThat(imageStorage.exists(secondKey)).isTrue();
        assertThat(imageStorage.exists(firstKey)).isFalse();
    }

    @Test
    void uploadedMenuItemImageIsServedThroughSignedUrl() throws Exception {
        Restaurant restaurant = new Restaurant("With menu", null, null);
        MenuItem item = restaurant.addMenuItem("Soup", null, new BigDecimal("250.00"), "soup");
        restaurantRepository.save(restaurant);
        Long itemId = menuItemRepository.findByRestaurantId(restaurant.getId()).get(0).getId();

        MvcResult uploaded = upload("/api/restaurants/" + restaurant.getId() + "/menu/" + itemId + "/image",
                "image/jpeg", JPEG);

        String imageUrl = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.imageUrl");
        assertThat(imageUrl).contains("/images/menu/" + restaurant.getId() + "/" + itemId + "/");
        assertThat(fetch(imageUrl).body()).containsExactly(JPEG);
        assertThat(item.getName()).isEqualTo("Soup");
    }

    @Test
    void rejectsFileThatIsNotAnImageEvenWithImageContentType() throws Exception {
        Restaurant restaurant = restaurantRepository.save(new Restaurant("Fake", null, null));

        mvc.perform(multipart(HttpMethod.PUT, "/api/restaurants/" + restaurant.getId() + "/image")
                        .file(new MockMultipartFile("file", "a.png", "image/png", "<html>".getBytes())))
                .andExpect(status().isUnsupportedMediaType());

        assertThat(restaurantRepository.findById(restaurant.getId()).orElseThrow().getImageKey()).isNull();
    }

    @Test
    void rejectsImageOverTheSizeLimit() throws Exception {
        Restaurant restaurant = restaurantRepository.save(new Restaurant("Big", null, null));
        byte[] big = new byte[2048];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        mvc.perform(multipart(HttpMethod.PUT, "/api/restaurants/" + restaurant.getId() + "/image")
                        .file(new MockMultipartFile("file", "a.png", "image/png", big)))
                .andExpect(status().isContentTooLarge());
    }

    @Test
    void answersNotFoundForUnknownRestaurantAndMissingFile() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/restaurants/999999/image")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG)))
                .andExpect(status().isNotFound());
        Restaurant restaurant = restaurantRepository.save(new Restaurant("No file", null, null));

        mvc.perform(multipart(HttpMethod.PUT, "/api/restaurants/" + restaurant.getId() + "/image"))
                .andExpect(status().isBadRequest());
    }

    private MvcResult upload(String uri, String contentType, byte[] content) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, uri).file(new MockMultipartFile("file", "photo", contentType,
                content))).andExpect(status().isOk()).andReturn();
    }

    private static HttpResponse<byte[]> fetch(String url) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }
}
