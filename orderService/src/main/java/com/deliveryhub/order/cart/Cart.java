package com.deliveryhub.order.cart;

import com.deliveryhub.order.dto.OrderItemRequest;
import com.deliveryhub.order.exception.ErrorCode;
import com.deliveryhub.order.exception.ItemNotFoundException;
import com.deliveryhub.order.exception.OrderRejectedException;

import java.util.ArrayList;
import java.util.List;

public record Cart(Long restaurantId, List<CartLine> lines) {

    public static final int MAX_LINES = 50;

    public Cart {
        lines = List.copyOf(lines);
    }

    public static Cart empty() {
        return new Cart(null, List.of());
    }

    public boolean hasLines() {
        return !lines.isEmpty();
    }

    public Cart add(Long targetRestaurantId, Long menuItemId, int quantity, boolean replace) {
        Cart base = this;
        if (hasLines() && !restaurantId.equals(targetRestaurantId)) {
            if (!replace) {
                throw new OrderRejectedException(ErrorCode.CART_OTHER_RESTAURANT,
                        "Cart already holds items of restaurant " + restaurantId);
            }
            base = empty();
        }
        List<CartLine> updated = new ArrayList<>(base.lines);
        int index = base.indexOf(menuItemId);
        if (index >= 0) {
            int total = updated.get(index).quantity() + quantity;
            requireWithinLimit(menuItemId, total);
            updated.set(index, new CartLine(menuItemId, total));
        } else {
            requireWithinLimit(menuItemId, quantity);
            if (updated.size() >= MAX_LINES) {
                throw new OrderRejectedException(ErrorCode.CART_TOO_MANY_LINES,
                        "Cart must not contain more than " + MAX_LINES + " lines");
            }
            updated.add(new CartLine(menuItemId, quantity));
        }
        return new Cart(targetRestaurantId, updated);
    }

    public Cart changeQuantity(Long menuItemId, int quantity) {
        int index = indexOf(menuItemId);
        if (index < 0) {
            throw notInCart(menuItemId);
        }
        requireWithinLimit(menuItemId, quantity);
        List<CartLine> updated = new ArrayList<>(lines);
        updated.set(index, new CartLine(menuItemId, quantity));
        return new Cart(restaurantId, updated);
    }

    public Cart remove(Long menuItemId) {
        List<CartLine> updated = lines.stream()
                .filter(line -> !line.menuItemId().equals(menuItemId))
                .toList();
        return updated.isEmpty() ? empty() : new Cart(restaurantId, updated);
    }

    private int indexOf(Long menuItemId) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).menuItemId().equals(menuItemId)) {
                return i;
            }
        }
        return -1;
    }

    private static void requireWithinLimit(Long menuItemId, int quantity) {
        if (quantity > OrderItemRequest.MAX_QUANTITY) {
            throw new OrderRejectedException(ErrorCode.QUANTITY_LIMIT, "Quantity of menu item " + menuItemId
                    + " exceeds " + OrderItemRequest.MAX_QUANTITY);
        }
    }

    private static ItemNotFoundException notInCart(Long menuItemId) {
        return new ItemNotFoundException(ErrorCode.CART_ITEM_NOT_FOUND,
                "Menu item " + menuItemId + " is not in the cart");
    }
}
