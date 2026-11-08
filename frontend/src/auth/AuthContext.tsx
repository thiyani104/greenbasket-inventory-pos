import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, currentRefreshToken, hasTokens, saveTokens, setAuthLostHandler } from '../api/client';
import type { Settings, TokenPair, UserInfo } from '../api/types';
import { setCurrency } from '../lib/format';

interface AuthState {
  user: UserInfo | null;
  settings: Settings;
  ready: boolean;
  isManager: boolean;
  login: (username: string, password: string) => Promise<UserInfo>;
  logout: () => Promise<void>;
  changePassword: (current: string, next: string) => Promise<void>;
  reloadSettings: () => Promise<void>;
}

const Ctx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [settings, setSettings] = useState<Settings>({});
  const [ready, setReady] = useState(false);

  const reloadSettings = useCallback(async () => {
    const s = await api<Settings>('/api/settings');
    setSettings(s);
    setCurrency(s.currency);
  }, []);

  useEffect(() => {
    setAuthLostHandler(() => setUser(null));
    if (!hasTokens()) { setReady(true); return; }
    api<UserInfo>('/api/auth/me')
      .then(async (u) => {
        setUser(u);
        if (!u.mustChangePassword) await reloadSettings();
      })
      .catch(() => saveTokens(null))
      .finally(() => setReady(true));
  }, [reloadSettings]);

  const login = useCallback(async (username: string, password: string) => {
    const t = await api<TokenPair>('/api/auth/login', { method: 'POST', body: { username, password }, auth: false });
    saveTokens(t);
    setUser(t.user);
    if (!t.user.mustChangePassword) await reloadSettings();
    return t.user;
  }, [reloadSettings]);

  const logout = useCallback(async () => {
    const rt = currentRefreshToken();
    try { await api('/api/auth/logout', { method: 'POST', body: { refreshToken: rt } }); } catch { /* already signed out */ }
    saveTokens(null);
    setUser(null);
    setSettings({});
  }, []);

  const changePassword = useCallback(async (current: string, next: string) => {
    const t = await api<TokenPair>('/api/auth/change-password', { method: 'POST', body: { currentPassword: current, newPassword: next } });
    saveTokens(t);
    setUser(t.user);
    await reloadSettings();
  }, [reloadSettings]);

  const value = useMemo<AuthState>(() => ({
    user, settings, ready, isManager: user?.role === 'MANAGER', login, logout, changePassword, reloadSettings,
  }), [user, settings, ready, login, logout, changePassword, reloadSettings]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth must be used inside AuthProvider');
  return v;
}
