import { createContext, useContext } from 'react';
import type { Credentials, Registration } from '../api/auth';
import type { SessionUser } from './sessionStore';

export type AuthContextValue = {
  user: SessionUser | null;
  login: (credentials: Credentials) => Promise<void>;
  register: (registration: Registration) => Promise<void>;
  logout: () => void;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth вызван вне AuthProvider');
  }
  return value;
}
