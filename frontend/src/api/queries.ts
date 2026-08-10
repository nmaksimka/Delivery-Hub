import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { repeatOrder } from '../cart/repeatOrder';
import { isFinalStatus } from '../lib/orderStatus';
import { cartApi, emptyCart, type Cart, type CartItemToAdd } from './cart';
import { ApiError } from './http';
import { createAttemptKeys } from './idempotency';
import { ordersApi, type Order } from './orders';
import { restaurantsApi } from './restaurants';
import { walletApi } from './wallet';

const ordersPageSize = 10;
const orderPollInterval = 2_000;

export const queryKeys = {
  restaurants: (activeOnly: boolean) => ['restaurants', { activeOnly }] as const,
  restaurant: (id: number) => ['restaurants', id] as const,
  menu: (restaurantId: number) => ['restaurants', restaurantId, 'menu'] as const,
  orders: ['orders'] as const,
  ordersPage: (page: number) => ['orders', { page }] as const,
  order: (id: number) => ['orders', id] as const,
  cart: ['cart'] as const,
  userCart: (userId: number) => ['cart', userId] as const,
  wallet: ['wallet'] as const,
  userWallet: (userId: number) => ['wallet', userId] as const,
};

export function useRestaurants(activeOnly = true) {
  return useQuery({
    queryKey: queryKeys.restaurants(activeOnly),
    queryFn: ({ signal }) => restaurantsApi.list({ activeOnly, signal }),
  });
}

export function useRestaurant(id: number) {
  return useQuery({
    queryKey: queryKeys.restaurant(id),
    queryFn: ({ signal }) => restaurantsApi.byId(id, signal),
    enabled: Number.isFinite(id),
  });
}

export function useMenu(restaurantId: number) {
  return useQuery({
    queryKey: queryKeys.menu(restaurantId),
    queryFn: ({ signal }) => restaurantsApi.menu(restaurantId, signal),
    enabled: Number.isFinite(restaurantId),
  });
}

export function useMyOrders(page: number) {
  return useQuery({
    queryKey: queryKeys.ordersPage(page),
    queryFn: ({ signal }) => ordersApi.mine({ page, size: ordersPageSize, signal }),
    placeholderData: keepPreviousData,
  });
}

function isClientError(error: unknown) {
  return error instanceof ApiError && error.status >= 400 && error.status < 500;
}

export function useOrder(id: number) {
  return useQuery({
    queryKey: queryKeys.order(id),
    queryFn: ({ signal }) => ordersApi.byId(id, signal),
    enabled: Number.isInteger(id),
    staleTime: 0,
    refetchInterval: (query) => {
      const { data, error } = query.state;
      if (isClientError(error) || (data && isFinalStatus(data.status))) {
        return false;
      }
      return orderPollInterval;
    },
  });
}

function useUserId() {
  return useAuth().user?.id ?? null;
}

export function useServerCart() {
  const userId = useUserId();
  return useQuery({
    queryKey: queryKeys.userCart(userId ?? 0),
    queryFn: ({ signal }) => cartApi.get(signal),
    enabled: userId !== null,
  });
}

function useCartWrite<TVariables>(write: (variables: TVariables) => Promise<Cart>) {
  const queryClient = useQueryClient();
  const userId = useUserId();
  return useMutation({
    mutationFn: write,
    onSuccess: (cart) => {
      if (userId !== null) {
        queryClient.setQueryData(queryKeys.userCart(userId), cart);
      }
    },
    onError: () => queryClient.invalidateQueries({ queryKey: queryKeys.cart }),
  });
}

export function useAddToCart() {
  return useCartWrite(({ item, replace }: { item: CartItemToAdd; replace: boolean }) =>
    cartApi.add(item, replace),
  );
}

export function useChangeQuantity() {
  return useCartWrite(({ menuItemId, quantity }: { menuItemId: number; quantity: number }) =>
    quantity > 0 ? cartApi.changeQuantity(menuItemId, quantity) : cartApi.remove(menuItemId),
  );
}

export function useRemoveFromCart() {
  return useCartWrite((menuItemId: number) => cartApi.remove(menuItemId));
}

export function useClearCart() {
  return useCartWrite<undefined>(async () => {
    await cartApi.clear();
    return emptyCart;
  });
}

export function useApplyPromo() {
  return useCartWrite((code: string) => cartApi.applyPromo(code));
}

export function useRemovePromo() {
  return useCartWrite<undefined>(() => cartApi.removePromo());
}

export function useRepeatOrder() {
  const queryClient = useQueryClient();
  const userId = useUserId();
  return useMutation({
    mutationFn: (order: Order) => repeatOrder(order, cartApi),
    onSuccess: ({ cart }) => {
      if (userId !== null) {
        queryClient.setQueryData(queryKeys.userCart(userId), cart);
      }
    },
    onError: () => queryClient.invalidateQueries({ queryKey: queryKeys.cart }),
  });
}

export function useCheckout() {
  const queryClient = useQueryClient();
  const userId = useUserId();
  const [attempts] = useState(createAttemptKeys);
  return useMutation({
    mutationFn: (cart: Cart) =>
      cartApi.checkout(
        attempts.keyFor({
          restaurantId: cart.restaurant?.id,
          items: cart.items.map((line) => [line.menuItemId, line.quantity]),
          promoCode: cart.promoCode,
        }),
      ),
    onSettled: (_order, error) => attempts.settle(error),
    onSuccess: (order) => {
      if (userId !== null) {
        queryClient.setQueryData(queryKeys.userCart(userId), emptyCart);
      }
      queryClient.setQueryData(queryKeys.order(order.id), order);
      void queryClient.invalidateQueries({ queryKey: queryKeys.orders });
      void queryClient.invalidateQueries({ queryKey: queryKeys.wallet });
    },
    onError: () => queryClient.invalidateQueries({ queryKey: queryKeys.cart }),
  });
}

export function useCancelOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (orderId: number) => ordersApi.cancel(orderId),
    onSuccess: (order) => {
      queryClient.setQueryData(queryKeys.order(order.id), order);
      void queryClient.invalidateQueries({ queryKey: queryKeys.orders });
      void queryClient.invalidateQueries({ queryKey: queryKeys.wallet });
    },
  });
}

export function useWallet() {
  const userId = useUserId();
  return useQuery({
    queryKey: queryKeys.userWallet(userId ?? 0),
    queryFn: ({ signal }) => walletApi.get(signal),
    enabled: userId !== null,
  });
}

export function useTopUp() {
  const queryClient = useQueryClient();
  const userId = useUserId();
  const [attempts] = useState(createAttemptKeys);
  return useMutation({
    mutationFn: (amount: number) => walletApi.topUp(amount, attempts.keyFor({ amount })),
    onSettled: (_wallet, error) => attempts.settle(error),
    onSuccess: (wallet) => {
      if (userId !== null) {
        queryClient.setQueryData(queryKeys.userWallet(userId), wallet);
      }
    },
  });
}
