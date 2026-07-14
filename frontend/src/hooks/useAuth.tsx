import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { getAdminToken, login as apiLogin, logout as apiLogout } from '../api/client';

interface AuthContextValue {
  isAdmin: boolean;
  /** Opens the login modal — call from anywhere (header, a gated page, etc.). */
  openLogin: () => void;
  closeLogin: () => void;
  isLoginOpen: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Wraps the whole app (see App.tsx). "Admin" here just means the browser currently holds a token
 * the server still accepts — there's no client-side user record, no expiry timer. A 401 from any
 * API call clears the stored token (see client.ts's requestJson), so isAdmin can go back to false
 * without an explicit logout if the server ever stops honoring it.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAdmin, setIsAdmin] = useState(() => getAdminToken() !== null);
  const [isLoginOpen, setIsLoginOpen] = useState(false);

  const login = useCallback(async (username: string, password: string) => {
    await apiLogin(username, password);
    setIsAdmin(true);
    setIsLoginOpen(false);
  }, []);

  const logout = useCallback(() => {
    apiLogout();
    setIsAdmin(false);
  }, []);

  const openLogin = useCallback(() => setIsLoginOpen(true), []);
  const closeLogin = useCallback(() => setIsLoginOpen(false), []);

  const value = useMemo(
    () => ({ isAdmin, openLogin, closeLogin, isLoginOpen, login, logout }),
    [isAdmin, openLogin, closeLogin, isLoginOpen, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}
