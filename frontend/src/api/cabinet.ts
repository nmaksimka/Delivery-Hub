import { z } from 'zod';
import { request } from './http';
import { orderSchema } from './orders';
import { pageOf } from './page';
import { menuItemSchema, restaurantSchema } from './restaurants';

export type MenuItemInput = {
  name: string;
  description: string | null;
  price: number;
  category: string | null;
  available: boolean;
};

const base = '/api/cabinet/restaurants';

export const cabinetApi = {
  restaurants(signal?: AbortSignal) {
    return request(base, { schema: z.array(restaurantSchema), signal });
  },
  restaurant(restaurantId: number, signal?: AbortSignal) {
    return request(`${base}/${restaurantId}`, { schema: restaurantSchema, signal });
  },
  changeHours(restaurantId: number, openTime: string, closeTime: string) {
    return request(`${base}/${restaurantId}/hours`, {
      method: 'PUT',
      body: { openTime, closeTime },
      schema: restaurantSchema,
    });
  },
  menu(restaurantId: number, signal?: AbortSignal) {
    return request(`${base}/${restaurantId}/menu`, { schema: z.array(menuItemSchema), signal });
  },
  addMenuItem(restaurantId: number, input: MenuItemInput) {
    return request(`${base}/${restaurantId}/menu`, {
      method: 'POST',
      body: input,
      schema: menuItemSchema,
    });
  },
  updateMenuItem(restaurantId: number, menuItemId: number, input: MenuItemInput) {
    return request(`${base}/${restaurantId}/menu/${menuItemId}`, {
      method: 'PUT',
      body: input,
      schema: menuItemSchema,
    });
  },
  removeMenuItem(restaurantId: number, menuItemId: number) {
    return request(`${base}/${restaurantId}/menu/${menuItemId}`, {
      method: 'DELETE',
      schema: z.undefined(),
    });
  },
  stopSelling(restaurantId: number, menuItemId: number) {
    return request(`${base}/${restaurantId}/stop-list/${menuItemId}`, {
      method: 'PUT',
      schema: menuItemSchema,
    });
  },
  resumeSelling(restaurantId: number, menuItemId: number) {
    return request(`${base}/${restaurantId}/stop-list/${menuItemId}`, {
      method: 'DELETE',
      schema: menuItemSchema,
    });
  },
  changeOrderStatus(orderId: number, status: 'IN_DELIVERY' | 'CANCELLED') {
    return request(`/api/cabinet/orders/${orderId}/status`, {
      method: 'PATCH',
      body: { status },
      schema: orderSchema,
    });
  },
  uploadMenuItemImage(restaurantId: number, menuItemId: number, file: File) {
    const body = new FormData();
    body.append('file', file);
    return request(`${base}/${restaurantId}/menu/${menuItemId}/image`, {
      method: 'PUT',
      body,
      schema: menuItemSchema,
    });
  },
  orders(options: { restaurantId: number; page: number; size: number; signal?: AbortSignal }) {
    return request('/api/cabinet/orders', {
      query: { restaurantId: options.restaurantId, page: options.page, size: options.size },
      schema: pageOf(orderSchema),
      signal: options.signal,
    });
  },
};
