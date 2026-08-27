import { z } from 'zod';
import { request } from './http';

const ticketSchema = z.object({ ticket: z.string(), expiresIn: z.number() });

export const trackingApi = {
  ticket() {
    return request('/api/tracking/ticket', { method: 'POST', schema: ticketSchema });
  },
};
