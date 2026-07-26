package com.deliveryhub.restaurant;

import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.MenuSection;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.cache.type=redis")
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class, RedisContainerConfig.class, GarageContainerConfig.class})
class CatalogCacheIT {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9, 8, 7, 6, 5};

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RestaurantRepository restaurantRepository;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void emptyCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).invalidate());
    }

    @Test
    void secondReadIsServedFromRedisWithoutTouchingTheDatabase() throws Exception {
        Restaurant saved = restaurantRepository.save(new Restaurant("Before", "Lenina 1", null));
        String uri = "/api/restaurants/" + saved.getId();
        assertThat(read(uri)).contains("Before");
        assertThat(read("/api/restaurants")).contains("Before");

        Restaurant changedBehindTheCache = restaurantRepository.findById(saved.getId()).orElseThrow();
        changedBehindTheCache.changeContacts("After", "Lenina 1", null);
        restaurantRepository.save(changedBehindTheCache);

        assertThat(read(uri)).contains("Before");
        assertThat(read("/api/restaurants")).contains("Before");
    }

    @Test
    void cachedValueIsTypedJsonWithImageKeyAndNoSignedLink() throws Exception {
        Restaurant draft = new Restaurant("Typed", "Lenina 1", null);
        draft.storeImage("restaurants/7/some-key");
        Restaurant saved = restaurantRepository.save(draft);

        String served = read("/api/restaurants/" + saved.getId());

        assertThat(served).contains("X-Amz-Signature=");
        Set<String> keys = redis.keys("restaurant:catalog-restaurant::" + saved.getId());
        assertThat(keys).hasSize(1);
        String stored = redis.opsForValue().get(keys.iterator().next());
        assertThat(stored).contains("\"imageKey\":\"restaurants/7/some-key\"")
                .doesNotContain("X-Amz").doesNotContain("@class").doesNotContain("imageUrl");
        assertThat(JsonPath.<Integer>read(stored, "$.id")).isEqualTo(saved.getId().intValue());
    }

    @Test
    void cachedRestaurantListIsStoredAsPlainJsonArray() throws Exception {
        restaurantRepository.save(new Restaurant("Listed", null, null));

        read("/api/restaurants");

        String stored = redis.opsForValue().get("restaurant:catalog-restaurants::true");
        assertThat(stored).startsWith("[").contains("\"name\":\"Listed\"");
    }

    @Test
    void updatingRestaurantThroughApiInvalidatesListAndItem() throws Exception {
        Integer id = createRestaurant("Old name");
        String uri = "/api/restaurants/" + id;
        assertThat(read(uri)).contains("Old name");
        assertThat(read("/api/restaurants")).contains("Old name");

        mvc.perform(put(uri).header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"New name\"}"))
                .andExpect(status().isOk());

        assertThat(read(uri)).contains("New name").doesNotContain("Old name");
        assertThat(read("/api/restaurants")).contains("New name").doesNotContain("Old name");
    }

    @Test
    void creatingRestaurantThroughApiShowsUpInCachedList() throws Exception {
        assertThat(read("/api/restaurants")).doesNotContain("Fresh place");

        createRestaurant("Fresh place");

        assertThat(read("/api/restaurants")).contains("Fresh place");
    }

    @Test
    void deletingRestaurantDropsItAndItsMenuFromCache() throws Exception {
        Integer id = createRestaurant("Doomed");
        String menuUri = "/api/restaurants/" + id + "/menu";
        createMenuItem(menuUri, "Soup");
        assertThat(read(menuUri)).contains("Soup");
        assertThat(read("/api/restaurants/" + id)).contains("Doomed");

        mvc.perform(delete("/api/restaurants/" + id).header("X-User-Role", "ADMIN")).andExpect(status().isNoContent());

        mvc.perform(get("/api/restaurants/" + id)).andExpect(status().isNotFound());
        assertThat(read(menuUri)).isEqualTo("[]");
    }

    @Test
    void menuIsCachedPerSectionAndInvalidatedOnChanges() throws Exception {
        Restaurant draft = new Restaurant("Menu place", null, null);
        draft.addMenuItem("Borsch", null, new BigDecimal("100.00"), "soup");
        MenuItem tea = draft.addMenuItem("Tea", null, new BigDecimal("50.00"), "tea");
        tea.moveToSection(MenuSection.DRINK);
        Long id = restaurantRepository.save(draft).getId();
        String menuUri = "/api/restaurants/" + id + "/menu";

        assertThat(read(menuUri)).contains("Borsch", "Tea");
        assertThat(read(menuUri + "?section=DRINK")).contains("Tea").doesNotContain("Borsch");
        assertThat(redis.keys("restaurant:catalog-menu::" + id + ":*")).hasSize(2);

        Integer soupId = createMenuItem(menuUri, "Soup");

        assertThat(read(menuUri)).contains("Borsch", "Tea", "Soup");

        mvc.perform(delete(menuUri + "/" + soupId).header("X-User-Role", "ADMIN")).andExpect(status().isNoContent());

        assertThat(read(menuUri)).doesNotContain("Soup");
        assertThat(read(menuUri + "?section=DRINK")).contains("Tea");
    }

    @Test
    void uploadedImageInvalidatesCacheAndRereadStillGetsSignedLink() throws Exception {
        Integer id = createRestaurant("Photo");
        String uri = "/api/restaurants/" + id;
        assertThat(read(uri)).doesNotContain("X-Amz-Signature");

        mvc.perform(multipart(HttpMethod.PUT, uri + "/image").header("X-User-Role", "ADMIN")
                .file(new MockMultipartFile("file", "a.png", "image/png", PNG))).andExpect(status().isOk());

        assertThat(read(uri)).contains("X-Amz-Expires=43200").contains("X-Amz-Signature=");
        assertThat(read(uri)).contains("X-Amz-Signature=");
        assertThat(redis.opsForValue().get("restaurant:catalog-restaurant::" + id))
                .contains("\"imageKey\":\"restaurants/" + id + "/").doesNotContain("X-Amz");
    }

    @Test
    void cacheHitsAndMissesAreVisibleInMetrics() throws Exception {
        Restaurant saved = restaurantRepository.save(new Restaurant("Measured", null, null));
        String uri = "/api/restaurants/" + saved.getId();
        double hitsBefore = counter("hit");
        double missesBefore = counter("miss");

        read(uri);
        read(uri);
        read(uri);

        assertThat(counter("hit") - hitsBefore).isEqualTo(2.0);
        assertThat(counter("miss") - missesBefore).isEqualTo(1.0);
    }

    private double counter(String result) {
        var counter = meterRegistry.find("cache.gets").tag("name", "catalog-restaurant").tag("result", result)
                .functionCounter();
        return counter == null ? 0.0 : counter.count();
    }

    private Integer createRestaurant(String name) throws Exception {
        String body = mvc.perform(post("/api/restaurants")
                .header("X-User-Role", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"address\": \"Lenina 1\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private Integer createMenuItem(String menuUri, String name) throws Exception {
        String body = mvc.perform(post(menuUri).header("X-User-Role", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"price\": 100.00}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String read(String uri) throws Exception {
        return mvc.perform(get(uri)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}
