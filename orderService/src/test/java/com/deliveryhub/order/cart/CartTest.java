package com.deliveryhub.order.cart;

import com.deliveryhub.order.exception.Coded;
import com.deliveryhub.order.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartTest {

    @Test
    void addsNewLinesInOrderAndSumsQuantityOfRepeatedItem() {
        Cart cart = Cart.empty()
                .add(1L, 10L, 2, false)
                .add(1L, 11L, 1, false)
                .add(1L, 10L, 3, false);

        assertThat(cart.restaurantId()).isEqualTo(1L);
        assertThat(cart.lines()).containsExactly(new CartLine(10L, 5), new CartLine(11L, 1));
    }

    @Test
    void refusesItemFromOtherRestaurantUnlessReplaceRequested() {
        Cart cart = Cart.empty().add(1L, 10L, 2, false);

        assertThatThrownBy(() -> cart.add(2L, 20L, 1, false))
                .isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code())
                        .isEqualTo(ErrorCode.CART_OTHER_RESTAURANT));

        Cart replaced = cart.add(2L, 20L, 1, true);
        assertThat(replaced.restaurantId()).isEqualTo(2L);
        assertThat(replaced.lines()).containsExactly(new CartLine(20L, 1));
    }

    @Test
    void replaceFlagDoesNothingSpecialForSameRestaurant() {
        Cart cart = Cart.empty().add(1L, 10L, 2, false).add(1L, 11L, 1, true);

        assertThat(cart.lines()).hasSize(2);
    }

    @Test
    void rejectsQuantityAboveLimitAfterSumming() {
        Cart cart = Cart.empty().add(1L, 10L, 60, false);

        assertThatThrownBy(() -> cart.add(1L, 10L, 41, false))
                .isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.QUANTITY_LIMIT));
        assertThat(cart.add(1L, 10L, 40, false).lines()).containsExactly(new CartLine(10L, 100));
    }

    @Test
    void rejectsMoreLinesThanOrderCanHold() {
        Cart full = Cart.empty();
        for (long id : LongStream.rangeClosed(1, Cart.MAX_LINES).toArray()) {
            full = full.add(1L, id, 1, false);
        }
        Cart cart = full;

        assertThatThrownBy(() -> cart.add(1L, 999L, 1, false))
                .isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code())
                        .isEqualTo(ErrorCode.CART_TOO_MANY_LINES));
        assertThat(cart.add(1L, 1L, 1, false).lines()).hasSize(Cart.MAX_LINES);
    }

    @Test
    void changesQuantityOfExistingLineOnly() {
        Cart cart = Cart.empty().add(1L, 10L, 2, false);

        assertThat(cart.changeQuantity(10L, 7).lines()).containsExactly(new CartLine(10L, 7));
        assertThatThrownBy(() -> cart.changeQuantity(99L, 1))
                .isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code())
                        .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND));
        assertThatThrownBy(() -> cart.changeQuantity(10L, 101))
                .isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.QUANTITY_LIMIT));
    }

    @Test
    void removingLastLineEmptiesCartAndForgetsRestaurant() {
        Cart cart = Cart.empty().add(1L, 10L, 2, false).add(1L, 11L, 1, false);

        assertThat(cart.remove(10L).lines()).containsExactly(new CartLine(11L, 1));
        Cart emptied = cart.remove(10L).remove(11L);
        assertThat(emptied.hasLines()).isFalse();
        assertThat(emptied.restaurantId()).isNull();
        assertThat(cart.remove(99L)).isEqualTo(cart);
    }

    @Test
    void promoCodeSurvivesLineChangesButNotRestaurantReplacementOrEmptying() {
        Cart cart = Cart.empty().add(1L, 10L, 2, false).withPromo("SPRING10");

        assertThat(cart.add(1L, 11L, 1, false).promoCode()).isEqualTo("SPRING10");
        assertThat(cart.changeQuantity(10L, 3).promoCode()).isEqualTo("SPRING10");
        assertThat(cart.add(2L, 20L, 1, true).promoCode()).isNull();
        assertThat(cart.remove(10L).promoCode()).isNull();
        assertThat(cart.withoutPromo().promoCode()).isNull();
    }

    @Test
    void emptyCartCannotHoldPromoCode() {
        assertThat(Cart.empty().withPromo("SPRING10").promoCode()).isNull();
    }
}
