import { z } from 'zod';
import { request } from './http';

export const notificationSchema = z.object({
  id: z.string(),
  type: z.string(),
  orderId: z
    .number()
    .nullish()
    .transform((value) => value ?? null),
  title: z.string(),
  message: z.string(),
  read: z.boolean(),
  createdAt: z.string(),
});

export type Notification = z.infer<typeof notificationSchema>;

const notificationPageSchema = z.object({
  items: z.array(notificationSchema),
  unread: z.number(),
});

export type NotificationPage = z.infer<typeof notificationPageSchema>;

export const notificationsApi = {
  recent(signal?: AbortSignal) {
    return request('/api/notifications', {
      query: { size: 20 },
      schema: notificationPageSchema,
      signal,
    });
  },
  markRead(id: string) {
    return request(`/api/notifications/${id}/read`, {
      method: 'POST',
      schema: notificationSchema,
    });
  },
};
