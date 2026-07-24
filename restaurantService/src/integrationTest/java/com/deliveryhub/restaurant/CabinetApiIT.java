package com.deliveryhub.restaurant;

import com.deliveryhub.contracts.pricing.ItemStatus;
import com.deliveryhub.contracts.pricing.QuoteRequest;
import com.deliveryhub.contracts.pricing.QuoteResponse;
import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.grpc.MenuPricingGrpcService;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import com.jayway.jsonpath.JsonPath;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.cache.type=redis")
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, GarageContainerConfig.class})
class CabinetApiIT {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9, 8, 7, 6, 5};
    private static final long OWNER = 501L;
    private static final long OTHER_OWNER = 502L;
    private static final long MISSING_RESTAURANT = 987654321L;
    private static final String CABINET = "/api/cabinet/restaurants";
    private static final String MENU_ITEM_JSON = "{\"name\": \"Hacked\", \"price\": 1.00}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RestaurantRepository restaurantRepository;
    @Autowired
    private MenuItemRepository menuItemRepository;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private MenuPricingGrpcService menuPricing;

    @BeforeEach
    void emptyCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).invalidate());
    }

    @Test
    void adminAssignsOwnerAndOwnerSeesOnlyOwnRestaurants() throws Exception {
        Restaurant mine = restaurantRepository.save(new Restaurant("Mine " + System.nanoTime(), null, null));
        Restaurant foreign = restaurantOf(OTHER_OWNER, "Foreign " + System.nanoTime());
        String ownerUri = "/api/admin/restaurants/" + mine.getId() + "/owner";

        mvc.perform(put(ownerUri).header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ownerId\": " + OWNER + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(OWNER));
        mvc.perform(get(ownerUri).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(OWNER));

        String listed = body(asOwner(get(CABINET), OWNER));
        assertThat(listed).contains(mine.getName()).doesNotContain(foreign.getName()).doesNotContain("ownerId");
        mvc.perform(asOwner(get(CABINET + "/" + mine.getId()), OWNER)).andExpect(status().isOk());

        mvc.perform(delete(ownerUri).header("X-User-Role", "ADMIN")).andExpect(status().isNoContent());

        mvc.perform(asOwner(get(CABINET + "/" + mine.getId()), OWNER)).andExpect(status().isNotFound());
        assertThat(body(asOwner(get(CABINET), OWNER))).doesNotContain(mine.getName());
    }

    @Test
    void ownerAssignmentIsValidated() throws Exception {
        Restaurant restaurant = restaurantRepository.save(new Restaurant("Validated", null, null));

        mvc.perform(put("/api/admin/restaurants/" + restaurant.getId() + "/owner").header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ownerId\": 0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/restaurants/" + MISSING_RESTAURANT + "/owner").header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ownerId\": 5}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void foreignRestaurantAnswersExactlyLikeMissingOne() throws Exception {
        Restaurant foreign = restaurantOf(OTHER_OWNER, "Foreign");
        Long foreignItem = idOf(foreign, "Soup");

        for (Long restaurantId : List.of(foreign.getId(), MISSING_RESTAURANT)) {
            for (AbstractMockHttpServletRequestBuilder<?> request : everyCabinetRequest(restaurantId, foreignItem)) {
                mvc.perform(asOwner(request, OWNER))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.detail")
                                .value("Restaurant with ID: " + restaurantId + " was not found"));
            }
        }

        Restaurant untouched = restaurantRepository.findById(foreign.getId()).orElseThrow();
        MenuItem untouchedItem = menuItemRepository.findById(foreignItem).orElseThrow();
        assertThat(untouched.getOpenTime()).isNull();
        assertThat(untouched.getImageKey()).isNull();
        assertThat(untouchedItem.isAvailable()).isTrue();
        assertThat(untouchedItem.getName()).isEqualTo("Soup");
        assertThat(menuItemRepository.findByRestaurantId(foreign.getId())).hasSize(2);
    }

    @Test
    void cabinetIsForbiddenWithoutOwnerRole() throws Exception {
        Restaurant restaurant = restaurantOf(OWNER, "Guarded");
        String uri = CABINET + "/" + restaurant.getId();

        mvc.perform(get(uri)).andExpect(status().isForbidden());
        mvc.perform(get(uri).header("X-User-Id", OWNER).header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
        mvc.perform(get(uri).header("X-User-Id", OWNER).header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden());
        mvc.perform(get(uri).header("X-User-Role", "RESTAURANT_OWNER")).andExpect(status().isForbidden());
        mvc.perform(put(uri + "/stop-list/" + idOf(restaurant, "Soup"))
                        .header("X-User-Id", OWNER).header("X-User-Role", "COURIER"))
                .andExpect(status().isForbidden());

        assertThat(menuItemRepository.findById(idOf(restaurant, "Soup")).orElseThrow().isAvailable()).isTrue();
    }

    @Test
    void stopListHidesItemFromCachedMenuAndFromQuote() throws Exception {
        Restaurant restaurant = restaurantOf(OWNER, "Stop list place");
        Long soupId = idOf(restaurant, "Soup");
        String publicMenu = "/api/restaurants/" + restaurant.getId() + "/menu";
        String stopList = CABINET + "/" + restaurant.getId() + "/stop-list";
        assertThat(body(get(publicMenu))).contains("Soup", "Tea");
        assertThat(quote(restaurant.getId(), soupId).getItems(0).getStatus())
                .isEqualTo(ItemStatus.ITEM_STATUS_AVAILABLE);

        mvc.perform(asOwner(put(stopList + "/" + soupId), OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        assertThat(body(get(publicMenu))).contains("Tea").doesNotContain("Soup");
        assertThat(quote(restaurant.getId(), soupId).getItems(0).getStatus())
                .isEqualTo(ItemStatus.ITEM_STATUS_UNAVAILABLE);
        assertThat(body(asOwner(get(stopList), OWNER))).contains("Soup").doesNotContain("Tea");
        assertThat(body(asOwner(get(CABINET + "/" + restaurant.getId() + "/menu"), OWNER))).contains("Soup", "Tea");

        mvc.perform(asOwner(delete(stopList + "/" + soupId), OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        assertThat(body(get(publicMenu))).contains("Soup", "Tea");
        assertThat(quote(restaurant.getId(), soupId).getItems(0).getStatus())
                .isEqualTo(ItemStatus.ITEM_STATUS_AVAILABLE);
        assertThat(body(asOwner(get(stopList), OWNER))).isEqualTo("[]");
    }

    @Test
    void stopListIgnoresItemOfAnotherRestaurant() throws Exception {
        Restaurant mine = restaurantOf(OWNER, "Mine");
        Restaurant foreign = restaurantOf(OTHER_OWNER, "Foreign");
        Long foreignItem = idOf(foreign, "Soup");

        mvc.perform(asOwner(put(CABINET + "/" + mine.getId() + "/stop-list/" + foreignItem), OWNER))
                .andExpect(status().isNotFound());

        assertThat(menuItemRepository.findById(foreignItem).orElseThrow().isAvailable()).isTrue();
    }

    @Test
    void changedHoursReachCachedRestaurantAndCloseItInQuote() throws Exception {
        Restaurant restaurant = restaurantOf(OWNER, "Hours place");
        Long soupId = idOf(restaurant, "Soup");
        String publicUri = "/api/restaurants/" + restaurant.getId();
        String hoursUri = CABINET + "/" + restaurant.getId() + "/hours";
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Tomsk")).truncatedTo(ChronoUnit.MINUTES);
        assertThat(JsonPath.<Object>read(body(get(publicUri)), "$.openTime")).isNull();
        assertThat(quote(restaurant.getId(), soupId).getRestaurantClosed()).isFalse();

        mvc.perform(asOwner(put(hoursUri), OWNER).contentType(MediaType.APPLICATION_JSON)
                        .content(hoursJson(now.plusHours(2), now.plusHours(3))))
                .andExpect(status().isOk());

        assertThat(JsonPath.<String>read(body(get(publicUri)), "$.openTime")).startsWith(now.plusHours(2).toString());
        QuoteResponse closed = quote(restaurant.getId(), soupId);
        assertThat(closed.getRestaurantClosed()).isTrue();
        assertThat(closed.getOpensAt()).isEqualTo(now.plusHours(2).toString());
        assertThat(closed.getClosesAt()).isEqualTo(now.plusHours(3).toString());

        mvc.perform(asOwner(put(hoursUri), OWNER).contentType(MediaType.APPLICATION_JSON)
                        .content(hoursJson(now.minusHours(1), now.plusHours(1))))
                .andExpect(status().isOk());

        assertThat(quote(restaurant.getId(), soupId).getRestaurantClosed()).isFalse();
    }

    @Test
    void hoursRequireBothTimes() throws Exception {
        Restaurant restaurant = restaurantOf(OWNER, "Strict hours");

        mvc.perform(asOwner(put(CABINET + "/" + restaurant.getId() + "/hours"), OWNER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"openTime\": \"10:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.closeTime").exists());
    }

    @Test
    void ownerEditsMenuAndUploadsPhotos() throws Exception {
        Restaurant restaurant = restaurantOf(OWNER, "Edited place");
        String cabinetUri = CABINET + "/" + restaurant.getId();
        String publicUri = "/api/restaurants/" + restaurant.getId();
        assertThat(body(get(publicUri + "/menu"))).doesNotContain("Pelmeni");
        assertThat(body(get(publicUri))).doesNotContain("X-Amz-Signature");

        String created = body(asOwner(post(cabinetUri + "/menu"), OWNER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Pelmeni\", \"price\": 390.00, \"category\": \"hot\"}"), 201);
        Integer itemId = JsonPath.read(created, "$.id");
        assertThat(body(get(publicUri + "/menu"))).contains("Pelmeni");

        mvc.perform(asOwner(put(cabinetUri + "/menu/" + itemId), OWNER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Pelmeni XL\", \"price\": 450.00}"))
                .andExpect(status().isOk());
        assertThat(body(get(publicUri + "/menu"))).contains("Pelmeni XL", "450.0");
        assertThat(new BigDecimal(quote(restaurant.getId(), itemId.longValue()).getItems(0).getPrice()))
                .isEqualByComparingTo("450.00");

        mvc.perform(asOwner(multipart(HttpMethod.PUT, cabinetUri + "/menu/" + itemId + "/image")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG)), OWNER))
                .andExpect(status().isOk());
        mvc.perform(asOwner(multipart(HttpMethod.PUT, cabinetUri + "/image")
                        .file(new MockMultipartFile("file", "b.png", "image/png", PNG)), OWNER))
                .andExpect(status().isOk());
        assertThat(body(get(publicUri + "/menu"))).contains("X-Amz-Signature=");
        assertThat(body(get(publicUri))).contains("X-Amz-Signature=");

        mvc.perform(asOwner(delete(cabinetUri + "/menu/" + itemId), OWNER)).andExpect(status().isNoContent());
        assertThat(body(get(publicUri + "/menu"))).doesNotContain("Pelmeni");
    }

    private List<AbstractMockHttpServletRequestBuilder<?>> everyCabinetRequest(Long restaurantId, Long menuItemId) {
        String uri = CABINET + "/" + restaurantId;
        String item = uri + "/menu/" + menuItemId;
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG);
        return List.of(
                get(uri),
                put(uri + "/hours").contentType(MediaType.APPLICATION_JSON)
                        .content(hoursJson(LocalTime.of(1, 0), LocalTime.of(2, 0))),
                multipart(HttpMethod.PUT, uri + "/image").file(file),
                get(uri + "/menu"),
                post(uri + "/menu").contentType(MediaType.APPLICATION_JSON).content(MENU_ITEM_JSON),
                put(item).contentType(MediaType.APPLICATION_JSON).content(MENU_ITEM_JSON),
                multipart(HttpMethod.PUT, item + "/image").file(file),
                delete(item),
                get(uri + "/stop-list"),
                put(uri + "/stop-list/" + menuItemId),
                delete(uri + "/stop-list/" + menuItemId));
    }

    private Restaurant restaurantOf(long ownerId, String name) {
        Restaurant draft = new Restaurant(name, "Lenina 1", null);
        draft.assignOwner(ownerId);
        draft.addMenuItem("Soup", null, new BigDecimal("250.00"), null);
        draft.addMenuItem("Tea", null, new BigDecimal("90.00"), null);
        return restaurantRepository.saveAndFlush(draft);
    }

    private static Long idOf(Restaurant restaurant, String name) {
        return restaurant.getMenuItems().stream()
                .filter(item -> item.getName().equals(name)).findFirst().orElseThrow().getId();
    }

    private QuoteResponse quote(Long restaurantId, Long menuItemId) {
        AtomicReference<QuoteResponse> answer = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        menuPricing.quote(QuoteRequest.newBuilder().setRestaurantId(restaurantId).addMenuItemIds(menuItemId).build(),
                new StreamObserver<>() {
                    @Override
                    public void onNext(QuoteResponse value) {
                        answer.set(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        failure.set(t);
                    }

                    @Override
                    public void onCompleted() {
                    }
                });
        assertThat(failure.get()).isNull();
        return answer.get();
    }

    private static String hoursJson(LocalTime openTime, LocalTime closeTime) {
        return "{\"openTime\": \"" + openTime + "\", \"closeTime\": \"" + closeTime + "\"}";
    }

    private static AbstractMockHttpServletRequestBuilder<?> asOwner(AbstractMockHttpServletRequestBuilder<?> request,
                                                                    long ownerId) {
        return request.header("X-User-Id", ownerId).header("X-User-Role", "RESTAURANT_OWNER");
    }

    private String body(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return body(request, 200);
    }

    private String body(AbstractMockHttpServletRequestBuilder<?> request, int expectedStatus) throws Exception {
        return mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse()
                .getContentAsString();
    }
}
