export type CartRestaurant = {
  id: number;
  name: string;
};

export type CartItem = {
  menuItemId: number;
  name: string;
  price: number;
};

export type CartLine = CartItem & {
  quantity: number;
};

export type CartState = {
  restaurant: CartRestaurant | null;
  lines: CartLine[];
};

export type CartAction =
  | { type: 'added'; restaurant: CartRestaurant; item: CartItem }
  | { type: 'decremented'; menuItemId: number }
  | { type: 'removed'; menuItemId: number }
  | { type: 'cleared' };

export const maxQuantity = 99;

export const emptyCart: CartState = { restaurant: null, lines: [] };

function withLines(state: CartState, lines: CartLine[]): CartState {
  return lines.length === 0 ? emptyCart : { restaurant: state.restaurant, lines };
}

function add(state: CartState, restaurant: CartRestaurant, item: CartItem): CartState {
  const sameRestaurant = state.restaurant?.id === restaurant.id;
  const lines = sameRestaurant ? state.lines : [];
  const existing = lines.find((line) => line.menuItemId === item.menuItemId);

  if (!existing) {
    return { restaurant, lines: [...lines, { ...item, quantity: 1 }] };
  }
  return {
    restaurant,
    lines: lines.map((line) =>
      line.menuItemId === item.menuItemId
        ? { ...line, quantity: Math.min(line.quantity + 1, maxQuantity) }
        : line,
    ),
  };
}

export function cartReducer(state: CartState, action: CartAction): CartState {
  switch (action.type) {
    case 'added':
      return add(state, action.restaurant, action.item);
    case 'decremented':
      return withLines(
        state,
        state.lines
          .map((line) =>
            line.menuItemId === action.menuItemId ? { ...line, quantity: line.quantity - 1 } : line,
          )
          .filter((line) => line.quantity > 0),
      );
    case 'removed':
      return withLines(
        state,
        state.lines.filter((line) => line.menuItemId !== action.menuItemId),
      );
    case 'cleared':
      return emptyCart;
  }
}

export function cartCount(state: CartState) {
  return state.lines.reduce((sum, line) => sum + line.quantity, 0);
}

export function cartTotal(state: CartState) {
  const kopecks = state.lines.reduce(
    (sum, line) => sum + Math.round(line.price * 100) * line.quantity,
    0,
  );
  return kopecks / 100;
}
