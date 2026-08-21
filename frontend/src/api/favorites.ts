import { z } from 'zod';
import { request } from './http';

export const favoritesApi = {
  list(signal?: AbortSignal) {
    return request('/api/users/me/favorites', { schema: z.array(z.number()), signal });
  },
  add(restaurantId: number) {
    return request(`/api/users/me/favorites/${restaurantId}`, {
      method: 'PUT',
      schema: z.undefined(),
    });
  },
  remove(restaurantId: number) {
    return request(`/api/users/me/favorites/${restaurantId}`, {
      method: 'DELETE',
      schema: z.undefined(),
    });
  },
};
