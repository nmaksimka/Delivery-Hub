import { renewAccessToken } from '../api/http';
import { sessionStore } from '../auth/sessionStore';

const expirySkewSeconds = 15;

export function isExpiring(token: string, nowSeconds: number) {
  const payload = token.split('.')[1];
  if (!payload) {
    return false;
  }
  try {
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const claims: unknown = JSON.parse(atob(normalized));
    if (claims && typeof claims === 'object' && 'exp' in claims && typeof claims.exp === 'number') {
      return claims.exp - expirySkewSeconds <= nowSeconds;
    }
  } catch {
    return false;
  }
  return false;
}

export async function currentAccessToken(forceRenew: boolean): Promise<string | null> {
  const token = sessionStore.get()?.accessToken ?? null;
  if (token && !forceRenew && !isExpiring(token, Date.now() / 1000)) {
    return token;
  }
  return token ? renewAccessToken() : null;
}
