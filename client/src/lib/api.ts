/** HTTP client. The browser only ever holds a VAANI session token — never provider API keys. */
const TOKEN_KEY = 'vaani.session';

export const API_BASE = import.meta.env.VITE_API_BASE ?? '';

export class ApiError extends Error {
  status: number; code: string; details?: { field?: string; retryable?: boolean };
  constructor(status: number, code: string, message: string, details?: ApiError['details']) {
    super(message); this.status = status; this.code = code; this.details = details;
  }
  get offline() { return this.code === 'network'; }
}

export const tokenStore = {
  get(): string | null { try { return localStorage.getItem(TOKEN_KEY); } catch { return null; } },
  set(t: string | null) { try { if (t) localStorage.setItem(TOKEN_KEY, t); else localStorage.removeItem(TOKEN_KEY); } catch { /* private mode */ } },
};

let onUnauthorized: () => void = () => {};
export const setUnauthorizedHandler = (fn: () => void) => { onUnauthorized = fn; };

export async function api<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = tokenStore.get();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  let body = init.body;
  if (init.json !== undefined) { headers.set('Content-Type', 'application/json'); body = JSON.stringify(init.json); }
  let res: Response;
  try {
    res = await fetch(`${API_BASE}/api${path}`, { ...init, headers, body });
  } catch (e) {
    if ((e as Error).name === 'AbortError') throw e;
    throw new ApiError(0, 'network', 'You’re offline or the server can’t be reached.');
  }
  if (res.status === 204) return undefined as T;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    if (res.status === 401 && token) onUnauthorized();
    throw new ApiError(res.status, data?.error?.code ?? 'http_error', data?.error?.message ?? `Request failed (${res.status}).`, data?.error?.details);
  }
  return data as T;
}

/** Fetch binary (voice notes, TTS) with auth; returns an object URL the caller must revoke. */
export async function fetchBlobUrl(path: string, init: RequestInit & { json?: unknown } = {}): Promise<string> {
  const headers = new Headers(init.headers);
  const token = tokenStore.get();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  let body = init.body;
  if (init.json !== undefined) { headers.set('Content-Type', 'application/json'); body = JSON.stringify(init.json); }
  let res: Response;
  try { res = await fetch(`${API_BASE}/api${path}`, { ...init, headers, body }); }
  catch { throw new ApiError(0, 'network', 'You’re offline or the server can’t be reached.'); }
  if (!res.ok) {
    const data = await res.json().catch(() => null);
    throw new ApiError(res.status, data?.error?.code ?? 'http_error', data?.error?.message ?? `Request failed (${res.status}).`);
  }
  return URL.createObjectURL(await res.blob());
}

/**
 * Stream a server-sent-event response from a POST. Calls onEvent per event; resolves when the stream ends.
 * Abort via `signal` to cancel (the server stops the model call when the connection closes).
 */
export async function streamSSE(path: string, json: unknown, onEvent: (event: string, data: any) => void, signal?: AbortSignal) {
  const headers = new Headers({ 'Content-Type': 'application/json', Accept: 'text/event-stream' });
  const token = tokenStore.get();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  let res: Response;
  try { res = await fetch(`${API_BASE}/api${path}`, { method: 'POST', headers, body: JSON.stringify(json), signal }); }
  catch (e) {
    if ((e as Error).name === 'AbortError') throw e;
    throw new ApiError(0, 'network', 'You’re offline or the server can’t be reached.');
  }
  if (!res.ok || !res.body) {
    const data = await res.json().catch(() => null);
    throw new ApiError(res.status, data?.error?.code ?? 'http_error', data?.error?.message ?? 'Request failed.', data?.error?.details);
  }
  const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
  let buf = '';
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buf += value;
    let idx;
    while ((idx = buf.indexOf('\n\n')) >= 0) {
      const block = buf.slice(0, idx); buf = buf.slice(idx + 2);
      const ev = /^event: (.*)$/m.exec(block)?.[1] ?? 'message';
      const data = /^data: (.*)$/m.exec(block)?.[1];
      if (data) onEvent(ev, JSON.parse(data));
    }
  }
}
