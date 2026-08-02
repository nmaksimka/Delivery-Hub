package com.deliveryhub.order.cart;

import com.deliveryhub.order.client.MenuQuote;
import com.deliveryhub.order.client.RestaurantCatalog;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.dto.CreateOrderRequest;
import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.dto.OrderItemRequest;
import com.deliveryhub.order.exception.ErrorCode;
import com.deliveryhub.order.exception.ItemNotFoundException;
import com.deliveryhub.order.exception.OrderRejectedException;
import com.deliveryhub.order.promo.PromoService;
import com.deliveryhub.order.promo.PromoUse;
import com.deliveryhub.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartStore store;
    private final RestaurantCatalog restaurantCatalog;
    private final OrderService orderService;
    private final PromoService promoService;

    public CartService(CartStore store, RestaurantCatalog restaurantCatalog, OrderService orderService,
                       PromoService promoService) {
        this.store = store;
        this.restaurantCatalog = restaurantCatalog;
        this.orderService = orderService;
        this.promoService = promoService;
    }

    public CartDto get(Long userId) {
        return view(store.load(userId), userId);
    }

    public CartDto addItem(Long userId, AddCartItemRequest request, boolean replace) {
        MenuQuote quote = restaurantCatalog.quote(request.restaurantId(), List.of(request.menuItemId()));
        if (quote.item(request.menuItemId()).isEmpty()) {
            throw new ItemNotFoundException(ErrorCode.ITEM_NOT_FOUND, "Menu item with ID: " + request.menuItemId()
                    + " was not found in restaurant " + request.restaurantId());
        }
        Cart cart = store.update(userId, current ->
                current.add(request.restaurantId(), request.menuItemId(), request.quantity(), replace));
        log.info("User {} added menu item {} of restaurant {} to the cart", userId, request.menuItemId(),
                request.restaurantId());
        return view(cart, userId);
    }

    public CartDto changeQuantity(Long userId, Long menuItemId, int quantity) {
        return view(store.update(userId, current -> current.changeQuantity(menuItemId, quantity)), userId);
    }

    public CartDto removeItem(Long userId, Long menuItemId) {
        return view(store.update(userId, current -> current.remove(menuItemId)), userId);
    }

    public CartDto applyPromo(Long userId, String code) {
        Cart cart = store.load(userId);
        if (!cart.hasLines()) {
            throw new OrderRejectedException(ErrorCode.CART_EMPTY, "Cart is empty");
        }
        CartDto priced = view(cart.withoutPromo(), userId);
        PromoUse use = promoService.preview(code, userId, cart.restaurantId(), priced.subtotal());
        store.update(userId, current -> current.withPromo(use.code()));
        log.info("User {} applied promo code {} to the cart", userId, use.code());
        return discounted(priced, use);
    }

    public CartDto removePromo(Long userId) {
        return view(store.update(userId, Cart::withoutPromo), userId);
    }

    public void clear(Long userId) {
        store.clear(userId);
    }

    public OrderDto checkout(Long userId) {
        Cart cart = store.load(userId);
        if (!cart.hasLines()) {
            throw new OrderRejectedException(ErrorCode.CART_EMPTY, "Cart is empty");
        }
        List<OrderItemRequest> items = cart.lines().stream()
                .map(line -> new OrderItemRequest(line.menuItemId(), line.quantity()))
                .toList();
        OrderDto order = orderService.createOrder(new CreateOrderRequest(cart.restaurantId(), items), userId,
                cart.promoCode());
        store.clear(userId);
        log.info("Cart of user {} became order {}", userId, order.id());
        return order;
    }

    private CartDto view(Cart cart, Long userId) {
        CartDto priced = price(cart);
        if (cart.promoCode() == null) {
            return priced;
        }
        try {
            PromoUse use = promoService.preview(cart.promoCode(), userId, cart.restaurantId(), priced.subtotal());
            return discounted(priced, use);
        } catch (OrderRejectedException | ItemNotFoundException e) {
            return new CartDto(priced.restaurant(), priced.items(), priced.subtotal(), BigDecimal.ZERO,
                    priced.total(), cart.promoCode(), e.code(), priced.orderable());
        }
    }

    private static CartDto discounted(CartDto priced, PromoUse use) {
        return new CartDto(priced.restaurant(), priced.items(), priced.subtotal(), use.discount(),
                priced.subtotal().subtract(use.discount()), use.code(), null, priced.orderable());
    }

    private CartDto price(Cart cart) {
        if (!cart.hasLines()) {
            return CartDto.empty();
        }
        List<Long> menuItemIds = cart.lines().stream().map(CartLine::menuItemId).toList();
        MenuQuote quote = restaurantCatalog.quote(cart.restaurantId(), menuItemIds);
        List<CartLineDto> lines = cart.lines().stream().map(line -> lineOf(line, quote)).toList();
        BigDecimal subtotal = lines.stream()
                .filter(CartLineDto::available)
                .map(CartLineDto::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        RestaurantDto restaurant = quote.restaurant();
        boolean orderable = restaurant.active() && !restaurant.closed()
                && lines.stream().allMatch(CartLineDto::available);
        CartRestaurantDto restaurantView = new CartRestaurantDto(restaurant.id(), restaurant.name(),
                restaurant.active(), restaurant.closed(), restaurant.workingHours());
        return new CartDto(restaurantView, lines, subtotal, BigDecimal.ZERO, subtotal, null, null, orderable);
    }

    private static CartLineDto lineOf(CartLine line, MenuQuote quote) {
        return quote.item(line.menuItemId())
                .map(item -> availableLine(line, item))
                .orElseGet(() -> new CartLineDto(line.menuItemId(), null, null, line.quantity(), null, false));
    }

    private static CartLineDto availableLine(CartLine line, MenuItemDto item) {
        return new CartLineDto(item.id(), item.name(), item.price(), line.quantity(),
                item.price().multiply(BigDecimal.valueOf(line.quantity())), item.available());
    }
}
