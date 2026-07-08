import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useSyncExternalStore, type ReactNode } from 'react';
import { authApi, type Credentials, type Registration } from '../api/auth';
import { queryKeys } from '../api/queries';
import { AuthContext, type AuthContextValue } from './AuthContext';
import { sessionStore } from './sessionStore';

async function login(credentials: Credentials) {
  const response = await authApi.login(credentials);
  sessionStore.set({
    token: response.token,
    user: {
      id: response.id,
      email: response.email,
      name: response.name,
      role: response.role,
    },
  });
}

async function register(registration: Registration) {
  await authApi.register(registration);
  await login({ email: registration.email, password: registration.password });
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const session = useSyncExternalStore(sessionStore.subscribe, sessionStore.get);
  const user = session?.user ?? null;

  useEffect(() => {
    if (!user) {
      queryClient.removeQueries({ queryKey: queryKeys.orders });
    }
  }, [user, queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({ user, login, register, logout: sessionStore.clear }),
    [user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
