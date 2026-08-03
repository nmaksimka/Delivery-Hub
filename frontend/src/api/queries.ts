import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { createAttemptKeys } from './idempotency';
import { ordersApi, type NewOrder } from './orders';
import { restaurantsApi } from './restaurants';

const ordersPageSize = 10;

export const queryKeys = {
  restaurants: (activeOnly: boolean) => ['restaurants', { activeOnly }] as const,
  restaurant: (id: number) => ['restaurants', id] as const,
  menu: (restaurantId: number) => ['restaurants', restaurantId, 'menu'] as const,
  orders: ['orders'] as const,
  ordersPage: (page: number) => ['orders', { page }] as const,
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

export function useCreateOrder() {
  const queryClient = useQueryClient();
  const [attempts] = useState(createAttemptKeys);
  return useMutation({
    mutationFn: (order: NewOrder) => ordersApi.create(order, attempts.keyFor(order)),
    onSettled: (_order, error) => attempts.settle(error),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.orders }),
    onError: (_error, order) =>
      queryClient.invalidateQueries({ queryKey: queryKeys.restaurant(order.restaurantId) }),
  });
}
