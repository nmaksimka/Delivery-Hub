package com.deliveryhub.order.cart;

import com.deliveryhub.order.client.MenuQuote;
import com.deliveryhub.order.client.RestaurantCatalog;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.dto.CreateOrderRequest;
import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.dto.OrderItemRequest;
import com.deliveryhub.order.exception.Coded;
import com.deliveryhub.order.exception.ErrorCode;
import com.deliveryhub.order.exception.OrderRejectedException;
import com.deliveryhub.order.promo.PromoService;
import com.deliveryhub.order.promo.PromoUse;
import com.deliveryhub.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final long USER = 42L;
    private static final long RESTAURANT = 1L;

    @Mock
    private CartStore store;
    @Mock
    private RestaurantCatalog restaurantCatalog;
    @Mock
    private OrderService orderService;
    @Mock
    private PromoService promoService;

    private CartService service;
    private Cart stored = Cart.empty();

    @BeforeEach
    void setUp() {
        service = new CartService(store, restaurantCatalog, orderService, promoService);
    }

    private void storeBacksCart() {
        lenient().when(store.load(USER)).thenAnswer(invocation -> stored);
        lenient().when(store.update(eq(USER), any())).thenAnswer(invocation -> {
            UnaryOperator<Cart> change = invocation.getArgument(1);
            stored = change.apply(stored);
            return stored;
        });
        lenient().when(store.take(USER)).thenAnswer(invocation -> {
            Cart taken = stored;
            stored = Cart.empty();
            return taken;
        });
        lenient().when(store.restore(eq(USER), any())).thenAnswer(invocation -> {
            if (stored.hasLines()) {
                return false;
            }
            stored = invocation.getArgument(1);
            return true;
        });
    }

    private void menuIs(RestaurantDto restaurant, MenuItemDto... items) {
        Map<Long, MenuItemDto> byId = new HashMap<>();
        for (MenuItemDto item : items) {
            byId.put(item.id(), item);
        }
        when(restaurantCatalog.quote(eq(RESTAURANT), anyCollection())).thenReturn(new MenuQuote(restaurant, byId));
    }

    private static RestaurantDto open() {
        return new RestaurantDto(RESTAURANT, "Pizzeria", true);
    }

    private static MenuItemDto pizza(boolean available) {
        return new MenuItemDto(10L, "Pizza", new BigDecimal("12.50"), available);
    }

    @Test
    void emptyCartIsShownWithoutAskingRestaurantService() {
        storeBacksCart();

        CartDto cart = service.get(USER);

        assertThat(cart.items()).isEmpty();
        assertThat(cart.orderable()).isFalse();
        assertThat(cart.total()).isEqualByComparingTo("0");
        verify(restaurantCatalog, never()).quote(any(), anyCollection());
    }

    @Test
    void pricesAreTakenFromQuoteNotFromStoredCart() {
        storeBacksCart();
        menuIs(open(), pizza(true));
        service.addItem(USER, new AddCartItemRequest(RESTAURANT, 10L, 2), false);

        menuIs(open(), new MenuItemDto(10L, "Pizza", new BigDecimal("15.00"), true));
        CartDto cart = service.get(USER);

        assertThat(cart.items()).singleElement().satisfies(line -> {
            assertThat(line.price()).isEqualByComparingTo("15.00");
            assertThat(line.lineTotal()).isEqualByComparingTo("30.00");
        });
        assertThat(cart.total()).isEqualByComparingTo("30.00");
        assertThat(cart.orderable()).isTrue();
    }

    @Test
    void unavailableAndDeletedItemsMakeCartNotOrderableAndStayOutOfTotal() {
        storeBacksCart();
        menuIs(open(), pizza(true), new MenuItemDto(11L, "Cola", new BigDecimal("3.00"), true));
        service.addItem(USER, new AddCartItemRequest(RESTAURANT, 10L, 1), false);
        service.addItem(USER, new AddCartItemRequest(RESTAURANT, 11L, 1), false);

        menuIs(open(), pizza(false));
        CartDto cart = service.get(USER);

        assertThat(cart.items()).extracting(CartLineDto::available).containsExactly(false, false);
        assertThat(cart.items().get(1).name()).isNull();
        assertThat(cart.total()).isEqualByComparingTo("0");
        assertThat(cart.orderable()).isFalse();
    }

    @Test
    void closedRestaurantMakesCartNotOrderable() {
        storeBacksCart();
        menuIs(new RestaurantDto(RESTAURANT, "Pizzeria", true, true, "10:00-22:00 Asia/Tomsk", null), pizza(true));
        service.addItem(USER, new AddCartItemRequest(RESTAURANT, 10L, 1), false);

        CartDto cart = service.get(USER);

        assertThat(cart.restaurant().closed()).isTrue();
        assertThat(cart.orderable()).isFalse();
    }

    @Test
    void addingItemUnknownToRestaurantIsRejectedAndNothingIsStored() {
        storeBacksCart();
        menuIs(open());

        assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest(RESTAURANT, 99L, 1), false))
                .isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ITEM_NOT_FOUND));
        verify(store, never()).update(any(), any());
    }

    @Test
    void addingFromOtherRestaurantConflictsUnlessReplaced() {
        storeBacksCart();
        menuIs(open(), pizza(true));
        service.addItem(USER, new AddCartItemRequest(RESTAURANT, 10L, 1), false);
        when(restaurantCatalog.quote(eq(2L), anyCollection())).thenReturn(new MenuQuote(
                new RestaurantDto(2L, "Sushi", true),
                Map.of(20L, new MenuItemDto(20L, "Roll", new BigDecimal("8.00"), true))));
        AddCartItemRequest fromOther = new AddCartItemRequest(2L, 20L, 1);

        assertThatThrownBy(() -> service.addItem(USER, fromOther, false))
                .isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code())
                        .isEqualTo(ErrorCode.CART_OTHER_RESTAURANT));

        CartDto replaced = service.addItem(USER, fromOther, true);
        assertThat(replaced.restaurant().id()).isEqualTo(2L);
        assertThat(replaced.items()).extracting(CartLineDto::menuItemId).containsExactly(20L);
    }

    @Test
    void checkoutTakesCartAndCreatesOrderFromItsLines() {
        storeBacksCart();
        menuIs(open(), pizza(true), new MenuItemDto(11L, "Cola", new BigDecimal("3.00"), true));
        service.addItem(USER, new AddCartItemRequest(RESTAURANT, 10L, 2), false);
        service.addItem(USER, new AddCartItemRequest(RESTAURANT, 11L, 1), false);
        OrderDto created = new OrderDto(5L, USER, RESTAURANT, null, null, null, new BigDecimal("28.00"), BigDecimal.ZERO, null,
                null, null, List.of());
        when(orderService.createOrder(any(), eq(USER), any())).thenReturn(created);

        OrderDto order = service.checkout(USER);

        assertThat(order.id()).isEqualTo(5L);
        ArgumentCaptor<CreateOrderRequest> request = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orderService).createOrder(request.capture(), eq(USER), any());
        assertThat(request.getValue().restaurantId()).isEqualTo(RESTAURANT);
        assertThat(request.getValue().orderItemsRequest())
                .containsExactly(new OrderItemRequest(10L, 2), new OrderItemRequest(11L, 1));
        assertThat(stored.hasLines()).isFalse();
        verify(store, never()).restore(any(), any());
    }

    @Test
    void failedCheckoutGivesCartBack() {
        storeBacksCart();
        menuIs(open(), pizza(true));
        service.addItem(USER, new AddCartItemRequest(RESTAURANT, 10L, 1), false);
        when(orderService.createOrder(any(), eq(USER), any()))
                .thenThrow(new OrderRejectedException(
                        ErrorCode.RESTAURANT_CLOSED, "Restaurant 1 is closed now"));

        assertThatThrownBy(() -> service.checkout(USER)).isInstanceOf(Coded.class);

        assertThat(stored.lines()).extracting(CartLine::menuItemId).containsExactly(10L);
    }

    @Test
    void redisFailureWhileGivingCartBackDoesNotHideCheckoutError() {
        storeBacksCart();
        menuIs(open(), pizza(true));
        service.addItem(USER, new AddCartItemRequest(RESTAURANT, 10L, 1), false);
        when(orderService.createOrder(any(), eq(USER), any()))
                .thenThrow(new OrderRejectedException(ErrorCode.RESTAURANT_CLOSED, "Restaurant 1 is closed now"));
        when(store.restore(eq(USER), any())).thenThrow(new RedisConnectionFailureException("redis is down"));

        assertThatThrownBy(() -> service.checkout(USER))
                .isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.RESTAURANT_CLOSED));
    }

    @Test
    void secondCheckoutOfSameCartFindsItEmpty() {
        cartWithTwoPizzas();
        when(orderService.createOrder(any(), eq(USER), any())).thenReturn(new OrderDto(5L, USER, RESTAURANT, null,
                null, null, new BigDecimal("25.00"), BigDecimal.ZERO, null, null, null, List.of()));

        service.checkout(USER);

        assertThatThrownBy(() -> service.checkout(USER))
                .isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CART_EMPTY));
        verify(orderService).createOrder(any(), eq(USER), any());
    }

    @Test
    void checkoutOfEmptyCartIsRejected() {
        storeBacksCart();

        assertThatThrownBy(() -> service.checkout(USER))
                .isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CART_EMPTY));
        verify(orderService, never()).createOrder(any(), any(), any());
    }

    private void cartWithTwoPizzas() {
        storeBacksCart();
        menuIs(open(), pizza(true));
        service.addItem(USER, new AddCartItemRequest(RESTAURANT, 10L, 2), false);
    }

    @Test
    void appliedPromoShowsDiscountAndIsKeptInCart() {
        cartWithTwoPizzas();
        when(promoService.preview("spring10", USER, RESTAURANT, new BigDecimal("25.00")))
                .thenReturn(new PromoUse(7L, "SPRING10", new BigDecimal("2.50")));

        CartDto cart = service.applyPromo(USER, "spring10");

        assertThat(cart.subtotal()).isEqualByComparingTo("25.00");
        assertThat(cart.discount()).isEqualByComparingTo("2.50");
        assertThat(cart.total()).isEqualByComparingTo("22.50");
        assertThat(cart.promoCode()).isEqualTo("SPRING10");
        assertThat(stored.promoCode()).isEqualTo("SPRING10");
    }

    @Test
    void rejectedPromoLeavesCartWithoutPromo() {
        cartWithTwoPizzas();
        when(promoService.preview(any(), any(), any(), any()))
                .thenThrow(new OrderRejectedException(ErrorCode.PROMO_MIN_AMOUNT, "too small"));

        assertThatThrownBy(() -> service.applyPromo(USER, "SPRING10"))
                .isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PROMO_MIN_AMOUNT));

        assertThat(stored.promoCode()).isNull();
    }

    @Test
    void promoCannotBeAppliedToEmptyCart() {
        storeBacksCart();

        assertThatThrownBy(() -> service.applyPromo(USER, "SPRING10"))
                .isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CART_EMPTY));
        verify(promoService, never()).preview(any(), any(), any(), any());
    }

    @Test
    void removingPromoRestoresFullTotal() {
        cartWithTwoPizzas();
        stored = stored.withPromo("SPRING10");

        CartDto cart = service.removePromo(USER);

        assertThat(cart.promoCode()).isNull();
        assertThat(cart.discount()).isEqualByComparingTo("0");
        assertThat(cart.total()).isEqualByComparingTo("25.00");
        verify(promoService, never()).preview(any(), any(), any(), any());
    }

    @Test
    void promoThatStoppedWorkingIsReportedWithoutDiscount() {
        cartWithTwoPizzas();
        stored = stored.withPromo("SPRING10");
        when(promoService.preview(any(), any(), any(), any()))
                .thenThrow(new OrderRejectedException(ErrorCode.PROMO_EXPIRED, "expired"));

        CartDto cart = service.get(USER);

        assertThat(cart.promoCode()).isEqualTo("SPRING10");
        assertThat(cart.promoProblem()).isEqualTo(ErrorCode.PROMO_EXPIRED);
        assertThat(cart.discount()).isEqualByComparingTo("0");
        assertThat(cart.total()).isEqualByComparingTo("25.00");
    }

    @Test
    void checkoutPassesPromoCodeToOrderService() {
        cartWithTwoPizzas();
        stored = stored.withPromo("SPRING10");
        OrderDto created = new OrderDto(5L, USER, RESTAURANT, null, null, null, new BigDecimal("22.50"), new BigDecimal("2.50"),
                "SPRING10", null, null, List.of());
        when(orderService.createOrder(any(), eq(USER), eq("SPRING10"))).thenReturn(created);

        assertThat(service.checkout(USER).promoCode()).isEqualTo("SPRING10");
    }
}
