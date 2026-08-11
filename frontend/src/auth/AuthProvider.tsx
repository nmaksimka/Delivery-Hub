import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useSyncExternalStore, type ReactNode } from 'react';
import { authApi, type Credentials, type Registration } from '../api/auth';
import { sessionFrom } from '../api/tokens';
import { queryKeys } from '../api/queries';
import { AuthContext, type AuthContextValue } from './AuthContext';
import { sessionStore } from './sessionStore';

async function login(credentials: Credentials) {
  const response = await authApi.login(credentials);
  sessionStore.set(sessionFrom(response));
}

async function register(registration: Registration) {
  await authApi.register(registration);
  await login({ email: registration.email, password: registration.password });
}

function logout() {
  const session = sessionStore.get();
  sessionStore.clear();
  if (session) {
    authApi.logout(session.refreshToken).catch(() => undefined);
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const session = useSyncExternalStore(sessionStore.subscribe, sessionStore.get);
  const user = session?.user ?? null;

  useEffect(() => {
    if (!user) {
      queryClient.removeQueries({ queryKey: queryKeys.orders });
      queryClient.removeQueries({ queryKey: queryKeys.cart });
      queryClient.removeQueries({ queryKey: queryKeys.wallet });
    }
  }, [user, queryClient]);

  const value = useMemo<AuthContextValue>(() => ({ user, login, register, logout }), [user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
