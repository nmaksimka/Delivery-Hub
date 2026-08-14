package com.deliveryhub.order;

import com.deliveryhub.order.client.MenuQuote;
import com.deliveryhub.order.client.RestaurantCatalog;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.idempotency.IdempotencyFilter;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class, KafkaContainerConfig.class,
        RedisContainerConfig.class})
class CartApiIT {

    @Autowired
    private MockMvcTester mvc;
    @MockitoBean
    private RestaurantCatalog restaurantCatalog;

    private RestaurantDto pizzeria = new RestaurantDto(1L, "Pizzeria", true);
    private final Map<Long, MenuItemDto> pizzeriaMenu = new HashMap<>();

    @BeforeEach
    void stubRestaurants() {
        pizzeria = new RestaurantDto(1L, "Pizzeria", true);
        pizzeriaMenu.clear();
        pizzeriaMenu.put(10L, new MenuItemDto(10L, "Pizza", new BigDecimal("12.50"), true));
        pizzeriaMenu.put(11L, new MenuItemDto(11L, "Cola", new BigDecimal("3.00"), true));
        when(restaurantCatalog.quote(eq(1L), any())).thenAnswer(invocation -> new MenuQuote(pizzeria, pizzeriaMenu));
        when(restaurantCatalog.quote(eq(2L), any())).thenReturn(new MenuQuote(new RestaurantDto(2L, "Sushi", true),
                Map.of(20L, new MenuItemDto(20L, "Roll", new BigDecimal("8.00"), true))));
    }

    @Test
    void newUserHasEmptyCart() {
        assertThat(get(1001L)).hasStatusOk().bodyJson().extractingPath("$.items").asArray().isEmpty();
        assertThat(get(1001L)).bodyJson().extractingPath("$.orderable").isEqualTo(false);
    }

    @Test
    void addsItemsAndMergesQuantityOfSameItem() {
        add(1002L, 1, 10, 2, false);
        add(1002L, 1, 11, 1, false);
        MvcTestResult merged = add(1002L, 1, 10, 1, false);

        assertThat(merged).hasStatusOk();
        assertThat(merged).bodyJson().extractingPath("$.items[0].quantity").isEqualTo(3);
        assertThat(merged).bodyJson().extractingPath("$.items[0].lineTotal").isEqualTo(37.5);
        assertThat(merged).bodyJson().extractingPath("$.total").isEqualTo(40.5);
        assertThat(merged).bodyJson().extractingPath("$.restaurant.name").isEqualTo("Pizzeria");
        assertThat(merged).bodyJson().extractingPath("$.orderable").isEqualTo(true);
    }

    @Test
    void cartsOfDifferentUsersAreIndependent() {
        add(1003L, 1, 10, 1, false);

        assertThat(get(1004L)).bodyJson().extractingPath("$.items").asArray().isEmpty();
    }

    @Test
    void itemFromOtherRestaurantIsConflictUnlessReplaceRequested() {
        add(1005L, 1, 10, 1, false);

        MvcTestResult conflict = add(1005L, 2, 20, 1, false);
        assertThat(conflict).hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("CART_OTHER_RESTAURANT");
        assertThat(get(1005L)).bodyJson().extractingPath("$.restaurant.id").isEqualTo(1);

        MvcTestResult replaced = add(1005L, 2, 20, 1, true);
        assertThat(replaced).hasStatusOk().bodyJson().extractingPath("$.restaurant.id").isEqualTo(2);
        assertThat(replaced).bodyJson().extractingPath("$.items").asArray().hasSize(1);
    }

    @Test
    void changesQuantityAndRemovesItems() {
        add(1006L, 1, 10, 1, false);
        add(1006L, 1, 11, 1, false);

        MvcTestResult changed = put(1006L, 10, 5);
        assertThat(changed).hasStatusOk().bodyJson().extractingPath("$.items[0].quantity").isEqualTo(5);

        MvcTestResult removed = delete(1006L, "/api/cart/items/10");
        assertThat(removed).hasStatusOk().bodyJson().extractingPath("$.items").asArray().hasSize(1);

        MvcTestResult emptied = delete(1006L, "/api/cart/items/11");
        assertThat(emptied).hasStatusOk().bodyJson().extractingPath("$.items").asArray().isEmpty();
        assertThat(emptied).bodyJson().extractingPath("$.restaurant").isNull();
    }

    @Test
    void changingQuantityOfItemNotInCartIsNotFoundWithCode() {
        add(1007L, 1, 10, 1, false);

        assertThat(put(1007L, 11, 2)).hasStatus(404).bodyJson().extractingPath("$.code")
                .isEqualTo("CART_ITEM_NOT_FOUND");
    }

    @Test
    void rejectsUnknownMenuItemQuantityOutOfRangeAndOverLimit() {
        assertThat(add(1008L, 1, 99, 1, false)).hasStatus(404).bodyJson().extractingPath("$.code")
                .isEqualTo("ITEM_NOT_FOUND");
        assertThat(add(1008L, 1, 10, 0, false)).hasStatus(400).bodyJson().extractingPath("$.code")
                .isEqualTo("VALIDATION_FAILED");
        add(1008L, 1, 10, 100, false);
        assertThat(add(1008L, 1, 10, 1, false)).hasStatus(409).bodyJson().extractingPath("$.code")
                .isEqualTo("QUANTITY_LIMIT");
    }

    @Test
    void cartReflectsCurrentMenuPricesAndAvailability() {
        add(1009L, 1, 10, 2, false);

        pizzeriaMenu.put(10L, new MenuItemDto(10L, "Pizza", new BigDecimal("20.00"), false));
        MvcTestResult stale = get(1009L);

        assertThat(stale).bodyJson().extractingPath("$.items[0].price").isEqualTo(20.0);
        assertThat(stale).bodyJson().extractingPath("$.items[0].available").isEqualTo(false);
        assertThat(stale).bodyJson().extractingPath("$.orderable").isEqualTo(false);
    }

    @Test
    void clearEmptiesCart() {
        add(1010L, 1, 10, 1, false);

        assertThat(mvc.delete().uri("/api/cart").header("X-User-Id", "1010")).hasStatus(204);
        assertThat(get(1010L)).bodyJson().extractingPath("$.items").asArray().isEmpty();
    }

    @Test
    void checkoutCreatesOrderAndClearsCart() {
        add(1011L, 1, 10, 2, false);
        add(1011L, 1, 11, 1, false);

        MvcTestResult order = checkout(1011L);

        assertThat(order).hasStatus(201).bodyJson().extractingPath("$.totalAmount").isEqualTo(28.0);
        assertThat(order).bodyJson().extractingPath("$.userId").isEqualTo(1011);
        assertThat(order).bodyJson().extractingPath("$.orderItems").asArray().hasSize(2);
        assertThat(get(1011L)).bodyJson().extractingPath("$.items").asArray().isEmpty();
        Integer orderId = JsonPath.read(bodyOf(order), "$.id");
        assertThat(mvc.get().uri("/api/orders/" + orderId).header("X-User-Id", "1011")).hasStatusOk();
    }

    @Test
    void checkoutOfClosedRestaurantKeepsCartAndReturnsCode() {
        add(1012L, 1, 10, 1, false);
        pizzeria = new RestaurantDto(1L, "Pizzeria", true, true, "10:00-22:00 Asia/Tomsk", null);

        assertThat(checkout(1012L)).hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("RESTAURANT_CLOSED");
        assertThat(get(1012L)).bodyJson().extractingPath("$.items").asArray().hasSize(1);
    }

    @Test
    void checkoutOfUnavailableItemAndEmptyCartReturnCodes() {
        assertThat(checkout(1013L)).hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("CART_EMPTY");

        add(1014L, 1, 10, 1, false);
        pizzeriaMenu.put(10L, new MenuItemDto(10L, "Pizza", new BigDecimal("12.50"), false));
        assertThat(checkout(1014L)).hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("ITEM_UNAVAILABLE");
    }

    @Test
    void checkoutWithoutIdempotencyKeyIsRejectedAndKeepsCart() {
        add(1015L, 1, 10, 1, false);

        assertThat(mvc.post().uri("/api/cart/checkout").header("X-User-Id", "1015")).hasStatus(400)
                .bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(get(1015L)).bodyJson().extractingPath("$.items").asArray().hasSize(1);
    }

    private MvcTestResult get(long userId) {
        return mvc.get().uri("/api/cart").header("X-User-Id", Long.toString(userId)).exchange();
    }

    private MvcTestResult add(long userId, int restaurantId, int menuItemId, int quantity, boolean replace) {
        return mvc.post().uri("/api/cart/items?replace=" + replace)
                .header("X-User-Id", Long.toString(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"restaurantId\": %d, \"menuItemId\": %d, \"quantity\": %d}"
                        .formatted(restaurantId, menuItemId, quantity))
                .exchange();
    }

    private MvcTestResult put(long userId, int menuItemId, int quantity) {
        return mvc.put().uri("/api/cart/items/" + menuItemId)
                .header("X-User-Id", Long.toString(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": " + quantity + "}")
                .exchange();
    }

    private MvcTestResult delete(long userId, String uri) {
        return mvc.delete().uri(uri).header("X-User-Id", Long.toString(userId)).exchange();
    }

    private MvcTestResult checkout(long userId) {
        return mvc.post().uri("/api/cart/checkout")
                .header("X-User-Id", Long.toString(userId))
                .header(IdempotencyFilter.HEADER_IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                .exchange();
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
