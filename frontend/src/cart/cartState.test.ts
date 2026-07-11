import { describe, expect, it } from 'vitest';
import {
  cartCount,
  cartReducer,
  cartTotal,
  emptyCart,
  maxQuantity,
  type CartState,
} from './cartState';

const pizzeria = { id: 1, name: 'Пицца на Ленина' };
const sushiBar = { id: 2, name: 'Суши Томь' };
const margherita = { menuItemId: 10, name: 'Маргарита', price: 549.9 };
const pepperoni = { menuItemId: 11, name: 'Пепперони', price: 620 };
const roll = { menuItemId: 20, name: 'Ролл с лососем', price: 410 };

function cartWith(...actions: Parameters<typeof cartReducer>[1][]): CartState {
  return actions.reduce(cartReducer, emptyCart);
}

describe('cartReducer', () => {
  it('increasesQuantityWhenSameItemAddedAgain', () => {
    const state = cartWith(
      { type: 'added', restaurant: pizzeria, item: margherita },
      { type: 'added', restaurant: pizzeria, item: margherita },
      { type: 'added', restaurant: pizzeria, item: pepperoni },
    );

    expect(state.lines).toEqual([
      { ...margherita, quantity: 2 },
      { ...pepperoni, quantity: 1 },
    ]);
    expect(cartCount(state)).toBe(3);
  });

  it('replacesCartWhenItemComesFromAnotherRestaurant', () => {
    const state = cartWith(
      { type: 'added', restaurant: pizzeria, item: margherita },
      { type: 'added', restaurant: sushiBar, item: roll },
    );

    expect(state.restaurant).toEqual(sushiBar);
    expect(state.lines).toEqual([{ ...roll, quantity: 1 }]);
  });

  it('forgetsRestaurantWhenLastItemDecrementedToZero', () => {
    const state = cartWith(
      { type: 'added', restaurant: pizzeria, item: margherita },
      { type: 'decremented', menuItemId: margherita.menuItemId },
    );

    expect(state).toEqual(emptyCart);
  });

  it('neverExceedsMaxQuantityPerLine', () => {
    const full: CartState = {
      restaurant: pizzeria,
      lines: [{ ...margherita, quantity: maxQuantity }],
    };

    const state = cartReducer(full, { type: 'added', restaurant: pizzeria, item: margherita });

    expect(state.lines[0]?.quantity).toBe(maxQuantity);
  });

  it('sumsTotalWithoutFloatingPointDrift', () => {
    const state = cartWith(
      { type: 'added', restaurant: pizzeria, item: margherita },
      { type: 'added', restaurant: pizzeria, item: margherita },
      { type: 'added', restaurant: pizzeria, item: margherita },
      { type: 'added', restaurant: pizzeria, item: pepperoni },
    );

    expect(cartTotal(state)).toBe(2269.7);
  });
});
