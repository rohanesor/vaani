import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, ApiError, setUnauthorizedHandler, tokenStore } from '../lib/api';
import type { Settings, User } from '../lib/types';

type Status = 'loading' | 'anon' | 'authed' | 'error';
interface SessionValue {
  status: Status; error: string | null; user: User | null; settings: Settings | null; token: string | null;
  login(email: string, password: string): Promise<void>;
  register(input: { email: string; password: string; displayName: string; preferredLanguage: string }): Promise<void>;
  logout(): void;
  retry(): void;
  updateProfile(patch: Partial<Pick<User, 'displayName' | 'preferredLanguage' | 'avatarHue'>>): Promise<void>;
  updateSettings(patch: Partial<Settings>): Promise<void>;
}

const Ctx = createContext<SessionValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');
  const [error, setError] = useState<string | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [settings, setSettings] = useState<Settings | null>(null);
  const [token, setToken] = useState<string | null>(tokenStore.get());
  const [attempt, setAttempt] = useState(0);

  const logout = useCallback(() => {
    // Revoke the current server session when reachable; always clear this device's token.
    // A failed network logout cannot keep a browser session authenticated locally.
    if (tokenStore.get()) void api('/auth/logout', { method: 'POST' }).catch(() => {});
    tokenStore.set(null); setToken(null); setUser(null); setSettings(null); setStatus('anon');
  }, []);
  useEffect(() => setUnauthorizedHandler(logout), [logout]);

  useEffect(() => {
    if (!token) { setStatus('anon'); return; }
    let live = true;
    setStatus('loading');
    const slow = setTimeout(() => live && setError('Still connecting…'), 4000);
    api<{ user: User; settings: Settings }>('/me')
      .then((r) => { if (!live) return; setUser(r.user); setSettings(r.settings); setStatus('authed'); setError(null); })
      .catch((e: ApiError) => { if (!live) return; if (e.status === 401) logout(); else { setError(e.message); setStatus('error'); } })
      .finally(() => clearTimeout(slow));
    return () => { live = false; clearTimeout(slow); };
  }, [token, attempt, logout]);

  // Resolve the theme preference to data-theme="light|dark" and follow the OS when set to System.
  useEffect(() => {
    const pref = settings?.theme ?? (() => { try { return (localStorage.getItem('vaani.theme') as Settings['theme']) ?? 'system'; } catch { return 'system'; } })();
    try { localStorage.setItem('vaani.theme', pref); } catch { /* private mode */ }
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const apply = () => document.documentElement.setAttribute('data-theme', pref === 'dark' || (pref === 'system' && mq.matches) ? 'dark' : 'light');
    apply();
    if (pref !== 'system') return;
    mq.addEventListener('change', apply);
    return () => mq.removeEventListener('change', apply);
  }, [settings?.theme]);

  const accept = (r: { token: string; user: User; settings: Settings }) => {
    tokenStore.set(r.token); setUser(r.user); setSettings(r.settings); setToken(r.token); setStatus('authed');
  };

  const value = useMemo<SessionValue>(() => ({
    status, error, user, settings, token, logout,
    retry: () => setAttempt((n) => n + 1),
    async login(email, password) { accept(await api('/auth/login', { method: 'POST', json: { email, password } })); },
    async register(input) { accept(await api('/auth/register', { method: 'POST', json: input })); },
    async updateProfile(patch) { const r = await api<{ user: User }>('/me', { method: 'PATCH', json: patch }); setUser(r.user); },
    async updateSettings(patch) {
      setSettings((s) => (s ? { ...s, ...patch } : s)); // optimistic
      try { setSettings(await api<Settings>('/me/settings', { method: 'PATCH', json: patch })); }
      catch (e) { setSettings(await api<Settings>('/me/settings')); throw e; }
    },
  }), [status, error, user, settings, token, logout]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useSession() {
  const v = useContext(Ctx);
  if (!v) throw new Error('useSession outside SessionProvider');
  return v;
}
