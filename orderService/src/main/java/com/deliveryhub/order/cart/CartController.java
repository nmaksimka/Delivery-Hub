package com.deliveryhub.order.cart;

import com.deliveryhub.order.client.dto.DeliveryQuoteDto;
import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.idempotency.IdempotencyFilter;
import com.deliveryhub.order.idempotency.MissingIdempotencyKeyException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartDto getCart(@RequestHeader("X-User-Id") Long userId) {
        return cartService.get(userId);
    }

    @PostMapping("/items")
    public CartDto addItem(@Valid @RequestBody AddCartItemRequest request,
                           @RequestParam(defaultValue = "false") boolean replace,
                           @RequestHeader("X-User-Id") Long userId) {
        return cartService.addItem(userId, request, replace);
    }

    @PutMapping("/items/{menuItemId}")
    public CartDto changeQuantity(@PathVariable Long menuItemId,
                                  @Valid @RequestBody ChangeQuantityRequest request,
                                  @RequestHeader("X-User-Id") Long userId) {
        return cartService.changeQuantity(userId, menuItemId, request.quantity());
    }

    @DeleteMapping("/items/{menuItemId}")
    public CartDto removeItem(@PathVariable Long menuItemId, @RequestHeader("X-User-Id") Long userId) {
        return cartService.removeItem(userId, menuItemId);
    }

    @PostMapping("/promo")
    public CartDto applyPromo(@Valid @RequestBody ApplyPromoRequest request,
                              @RequestHeader("X-User-Id") Long userId) {
        return cartService.applyPromo(userId, request.code());
    }

    @DeleteMapping("/promo")
    public CartDto removePromo(@RequestHeader("X-User-Id") Long userId) {
        return cartService.removePromo(userId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@RequestHeader("X-User-Id") Long userId) {
        cartService.clear(userId);
    }

    @GetMapping("/delivery-quote")
    public DeliveryQuoteDto deliveryQuote(@RequestParam Long addressId, @RequestHeader("X-User-Id") Long userId) {
        return cartService.deliveryQuote(userId, addressId);
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto checkout(@RequestHeader("X-User-Id") Long userId,
                             @RequestHeader(name = IdempotencyFilter.HEADER_IDEMPOTENCY_KEY, required = false)
                             String idempotencyKey,
                             @Valid @RequestBody CheckoutRequest request) {
        if (idempotencyKey == null) {
            throw new MissingIdempotencyKeyException("Checkout requires the Idempotency-Key header");
        }
        return cartService.checkout(userId, request.addressId());
    }
}
