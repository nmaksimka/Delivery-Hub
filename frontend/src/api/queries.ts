import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { repeatOrder } from '../cart/repeatOrder';
import { isFinalStatus } from '../lib/orderStatus';
import { adminApi, type CourierInput, type ImportKind } from './admin';
import { cabinetApi, type MenuItemInput } from './cabinet';
import { analyticsApi, type AnalyticsPeriod } from './analytics';
import { addressesApi, type AddressInput } from './addresses';
import { cartApi, emptyCart, type Cart, type CartItemToAdd } from './cart';
import { favoritesApi } from './favorites';
import { ApiError } from './http';
import { createAttemptKeys } from './idempotency';
import { notificationsApi, type NotificationPage } from './notifications';
import { ordersApi, type Order } from './orders';
import { restaurantsApi } from './restaurants';
import { reviewsApi, type ReviewInput } from './reviews';
import { walletApi } from './wallet';

const ordersPageSize = 10;
const orderPollInterval = 2_000;
const notificationPollInterval = 30_000;

export const queryKeys = {
  restaurants: (activeOnly: boolean) => ['restaurants', { activeOnly }] as const,
  restaurant: (id: number) => ['restaurants', id] as const,
  menu: (restaurantId: number) => ['restaurants', restaurantId, 'menu'] as const,
  orders: ['orders'] as const,
  ordersPage: (page: number) => ['orders', { page }] as const,
  order: (id: number) => ['orders', id] as const,
  cart: ['cart'] as const,
  userCart: (userId: number) => ['cart', userId] as const,
  deliveryQuote: (userId: number, restaurantId: number, addressId: number) =>
    ['cart', userId, 'delivery-quote', restaurantId, addressId] as const,
  wallet: ['wallet'] as const,
  userWallet: (userId: number) => ['wallet', userId] as const,
  addresses: ['addresses'] as const,
  userAddresses: (userId: number) => ['addresses', userId] as const,
  reviews: ['reviews'] as const,
  userReviews: (userId: number) => ['reviews', userId] as const,
  notifications: ['notifications'] as const,
  userNotifications: (userId: number) => ['notifications', userId] as const,
  favorites: ['favorites'] as const,
  userFavorites: (userId: number) => ['favorites', userId] as const,
  adminAnalytics: (period: AnalyticsPeriod, restaurantId?: number) =>
    ['analytics', 'admin', period, restaurantId ?? null] as const,
  cabinetAnalytics: (restaurantId: number, period: AnalyticsPeriod) =>
    ['analytics', 'cabinet', restaurantId, period] as const,
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

export function useDeliveryQuote(restaurantId: number | null, addressId: number | null) {
  const userId = useUserId();
  return useQuery({
    queryKey: queryKeys.deliveryQuote(userId ?? 0, restaurantId ?? 0, addressId ?? 0),
    queryFn: ({ signal }) => cartApi.deliveryQuote(addressId ?? 0, signal),
    enabled: userId !== null && restaurantId !== null && addressId !== null,
    retry: false,
    staleTime: 60_000,
  });
}

export function useCheckout() {
  const queryClient = useQueryClient();
  const userId = useUserId();
  const [attempts] = useState(createAttemptKeys);
  return useMutation({
    mutationFn: ({
      cart,
      addressId,
      expectedDeliveryFee,
    }: {
      cart: Cart;
      addressId: number;
      expectedDeliveryFee?: number;
    }) =>
      cartApi.checkout(
        attempts.keyFor({
          restaurantId: cart.restaurant?.id,
          items: cart.items.map((line) => [line.menuItemId, line.quantity]),
          promoCode: cart.promoCode,
          addressId,
          expectedDeliveryFee,
        }),
        addressId,
        expectedDeliveryFee,
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

export function useMyReviews() {
  const userId = useUserId();
  return useQuery({
    queryKey: queryKeys.userReviews(userId ?? 0),
    queryFn: ({ signal }) => reviewsApi.mine(signal),
    enabled: userId !== null,
  });
}

export function useSubmitReview() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orderId, input }: { orderId: number; input: ReviewInput }) =>
      reviewsApi.submit(orderId, input),
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.reviews }),
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

export function useAddresses() {
  const userId = useUserId();
  return useQuery({
    queryKey: queryKeys.userAddresses(userId ?? 0),
    queryFn: ({ signal }) => addressesApi.list(signal),
    enabled: userId !== null,
  });
}

function useAddressWrite<TVariables, TResult>(write: (variables: TVariables) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: write,
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.addresses }),
  });
}

export function useSaveAddress() {
  return useAddressWrite(({ id, input }: { id: number | null; input: AddressInput }) =>
    id === null ? addressesApi.create(input) : addressesApi.update(id, input),
  );
}

export function useDeleteAddress() {
  return useAddressWrite((id: number) => addressesApi.remove(id));
}

export function useMakePrimaryAddress() {
  return useAddressWrite((id: number) => addressesApi.makePrimary(id));
}

export function useFavorites() {
  const userId = useUserId();
  return useQuery({
    queryKey: queryKeys.userFavorites(userId ?? 0),
    queryFn: ({ signal }) => favoritesApi.list(signal),
    enabled: userId !== null,
  });
}

export function useToggleFavorite() {
  const queryClient = useQueryClient();
  const userId = useUserId();
  const key = queryKeys.userFavorites(userId ?? 0);
  return useMutation({
    mutationFn: ({ restaurantId, favorite }: { restaurantId: number; favorite: boolean }) =>
      favorite ? favoritesApi.add(restaurantId) : favoritesApi.remove(restaurantId),
    onMutate: async ({ restaurantId, favorite }) => {
      await queryClient.cancelQueries({ queryKey: key });
      const previous = queryClient.getQueryData<number[]>(key);
      const rest = (previous ?? []).filter((id) => id !== restaurantId);
      queryClient.setQueryData(key, favorite ? [restaurantId, ...rest] : rest);
      return { previous };
    },
    onError: (_error, _variables, context) => {
      queryClient.setQueryData(key, context?.previous);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.favorites }),
  });
}

export function useAdminAnalytics(period: AnalyticsPeriod, restaurantId?: number) {
  return useQuery({
    queryKey: queryKeys.adminAnalytics(period, restaurantId),
    queryFn: ({ signal }) => analyticsApi.admin(period, restaurantId, signal),
    placeholderData: keepPreviousData,
  });
}

export function useCabinetAnalytics(restaurantId: number, period: AnalyticsPeriod) {
  return useQuery({
    queryKey: queryKeys.cabinetAnalytics(restaurantId, period),
    queryFn: ({ signal }) => analyticsApi.cabinet(restaurantId, period, signal),
    placeholderData: keepPreviousData,
  });
}

const cabinetKey = ['cabinet'] as const;
const cabinetOrdersPageSize = 15;
const couriersPageSize = 15;
const importPollInterval = 2_000;

export function useCabinetRestaurants() {
  return useQuery({
    queryKey: [...cabinetKey, 'restaurants'],
    queryFn: ({ signal }) => cabinetApi.restaurants(signal),
  });
}

export function useCabinetRestaurant(restaurantId: number) {
  return useQuery({
    queryKey: [...cabinetKey, 'restaurants', restaurantId],
    queryFn: ({ signal }) => cabinetApi.restaurant(restaurantId, signal),
  });
}

export function useCabinetMenu(restaurantId: number) {
  return useQuery({
    queryKey: [...cabinetKey, 'menu', restaurantId],
    queryFn: ({ signal }) => cabinetApi.menu(restaurantId, signal),
  });
}

type MenuChange =
  | { kind: 'add'; input: MenuItemInput }
  | { kind: 'update'; menuItemId: number; input: MenuItemInput }
  | { kind: 'remove'; menuItemId: number }
  | { kind: 'stop'; menuItemId: number }
  | { kind: 'resume'; menuItemId: number };

export function useChangeMenu(restaurantId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (change: MenuChange) => {
      switch (change.kind) {
        case 'add':
          return cabinetApi.addMenuItem(restaurantId, change.input);
        case 'update':
          return cabinetApi.updateMenuItem(restaurantId, change.menuItemId, change.input);
        case 'remove':
          return cabinetApi.removeMenuItem(restaurantId, change.menuItemId);
        case 'stop':
          return cabinetApi.stopSelling(restaurantId, change.menuItemId);
        case 'resume':
          return cabinetApi.resumeSelling(restaurantId, change.menuItemId);
      }
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: [...cabinetKey, 'menu', restaurantId] }),
  });
}

export function useCabinetOrders(restaurantId: number, page: number) {
  return useQuery({
    queryKey: [...cabinetKey, 'orders', restaurantId, page],
    queryFn: ({ signal }) =>
      cabinetApi.orders({ restaurantId, page, size: cabinetOrdersPageSize, signal }),
    placeholderData: keepPreviousData,
  });
}

export function useChangeCabinetOrderStatus(restaurantId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orderId, status }: { orderId: number; status: 'IN_DELIVERY' | 'CANCELLED' }) =>
      cabinetApi.changeOrderStatus(orderId, status),
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: [...cabinetKey, 'orders', restaurantId] }),
  });
}

export function useUploadMenuItemImage(restaurantId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ menuItemId, file }: { menuItemId: number; file: File }) =>
      cabinetApi.uploadMenuItemImage(restaurantId, menuItemId, file),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: [...cabinetKey, 'menu', restaurantId] }),
  });
}

export function useCouriers(page: number) {
  return useQuery({
    queryKey: ['admin', 'couriers', page],
    queryFn: ({ signal }) => adminApi.couriers({ page, size: couriersPageSize, signal }),
    placeholderData: keepPreviousData,
  });
}

export function useRegisterCourier() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CourierInput) => adminApi.registerCourier(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'couriers'] }),
  });
}

export function useImportJobs() {
  return useQuery({
    queryKey: ['admin', 'imports'],
    queryFn: ({ signal }) => adminApi.importJobs(signal),
    refetchInterval: (query) =>
      query.state.data?.some((job) => job.status === 'RUNNING' || job.status === 'PENDING')
        ? importPollInterval
        : false,
  });
}

export function useStartImport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (kind: ImportKind) => adminApi.startImport(kind),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'imports'] }),
  });
}

export function useNotifications() {
  const userId = useUserId();
  return useQuery({
    queryKey: queryKeys.userNotifications(userId ?? 0),
    queryFn: ({ signal }) => notificationsApi.recent(signal),
    enabled: userId !== null,
    refetchInterval: notificationPollInterval,
  });
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  const userId = useUserId();
  const key = queryKeys.userNotifications(userId ?? 0);
  return useMutation({
    mutationFn: (id: string) => notificationsApi.markRead(id),
    onSuccess: (updated) => {
      queryClient.setQueryData<NotificationPage>(key, (page) => {
        if (!page) {
          return page;
        }
        const wasUnread = page.items.some((item) => item.id === updated.id && !item.read);
        return {
          items: page.items.map((item) => (item.id === updated.id ? updated : item)),
          unread: wasUnread ? Math.max(0, page.unread - 1) : page.unread,
        };
      });
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications }),
  });
}
