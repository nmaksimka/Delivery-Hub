import { z } from 'zod';
import { request } from './http';
import { tokenResponseSchema } from './tokens';

const registeredUserSchema = z.object({
  id: z.number(),
  email: z.string(),
  name: z.string(),
  role: z.string(),
});

export type RegisteredUser = z.infer<typeof registeredUserSchema>;

export type Credentials = {
  email: string;
  password: string;
};

export type Registration = Credentials & {
  name: string;
  phone?: string;
};

export const authApi = {
  login(credentials: Credentials) {
    return request('/api/auth/login', {
      method: 'POST',
      body: credentials,
      schema: tokenResponseSchema,
    });
  },
  register(registration: Registration) {
    return request('/api/auth/register', {
      method: 'POST',
      body: registration,
      schema: registeredUserSchema,
    });
  },
  logout(refreshToken: string) {
    return request('/api/auth/logout', {
      method: 'POST',
      body: { refreshToken },
      schema: z.undefined(),
    });
  },
};
