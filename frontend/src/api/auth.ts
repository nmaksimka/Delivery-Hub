import { z } from 'zod';
import { request } from './http';

const loginResponseSchema = z.object({
  token: z.string().min(1),
  id: z.number(),
  email: z.string(),
  name: z.string(),
  role: z.string(),
});

const registeredUserSchema = z.object({
  id: z.number(),
  email: z.string(),
  name: z.string(),
  role: z.string(),
});

export type LoginResponse = z.infer<typeof loginResponseSchema>;
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
      schema: loginResponseSchema,
    });
  },
  register(registration: Registration) {
    return request('/api/auth/register', {
      method: 'POST',
      body: registration,
      schema: registeredUserSchema,
    });
  },
};
