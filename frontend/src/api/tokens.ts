import { z } from 'zod';
import type { Session } from '../auth/sessionStore';

export const tokenResponseSchema = z.object({
  accessToken: z.string().min(1),
  refreshToken: z.string().min(1),
  expiresIn: z.number(),
  id: z.number(),
  email: z.string(),
  name: z.string(),
  role: z.string(),
});

export type TokenResponse = z.infer<typeof tokenResponseSchema>;

export function sessionFrom(response: TokenResponse): Session {
  return {
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    user: {
      id: response.id,
      email: response.email,
      name: response.name,
      role: response.role,
    },
  };
}
