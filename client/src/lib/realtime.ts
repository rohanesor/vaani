/** WebSocket client with exponential back-off reconnect and an observable connection state. */
import { API_BASE } from './api';

export type ConnectionState = 'connecting' | 'connected' | 'reconnecting' | 'offline';
type Handler = (event: string, data: any) => void;

export class RealtimeClient {
  private ws: WebSocket | null = null;
  private attempt = 0;
  private timer: number | undefined;
  private stopped = false;
  state: ConnectionState = 'connecting';
  private stateListeners = new Set<(s: ConnectionState) => void>();
  private token: string;
  private onEvent: Handler;

  constructor(token: string, onEvent: Handler) {
    this.token = token;
    this.onEvent = onEvent;
    window.addEventListener('online', this.onOnline);
    window.addEventListener('offline', this.onOffline);
    if (!navigator.onLine) this.setState('offline'); else this.connect();
  }

  private url() {
    const base = API_BASE || window.location.origin;
    const u = new URL('/ws', base);
    u.protocol = u.protocol === 'https:' ? 'wss:' : 'ws:';
    u.searchParams.set('token', this.token);
    return u.toString();
  }

  private connect() {
    if (this.stopped) return;
    this.setState(this.attempt === 0 ? 'connecting' : 'reconnecting');
    const ws = new WebSocket(this.url());
    this.ws = ws;
    ws.onopen = () => { this.attempt = 0; this.setState('connected'); };
    ws.onmessage = (e) => { try { const m = JSON.parse(e.data); this.onEvent(m.event, m.data); } catch { /* ignore malformed */ } };
    ws.onclose = () => {
      if (this.ws !== ws) return;
      this.ws = null;
      if (this.stopped) return;
      if (!navigator.onLine) { this.setState('offline'); return; }
      this.attempt++;
      this.setState('reconnecting');
      const delay = Math.min(15_000, 500 * 2 ** Math.min(this.attempt, 5)) * (0.75 + Math.random() * 0.5);
      this.timer = window.setTimeout(() => this.connect(), delay);
    };
  }

  private onOnline = () => { window.clearTimeout(this.timer); this.attempt = 0; if (!this.ws) this.connect(); };
  private onOffline = () => { this.setState('offline'); this.ws?.close(); };

  private setState(s: ConnectionState) {
    if (this.state === s) return;
    this.state = s;
    this.stateListeners.forEach((l) => l(s));
  }
  subscribe(l: (s: ConnectionState) => void) { this.stateListeners.add(l); return () => { this.stateListeners.delete(l); }; }

  send(event: string, data: unknown) { if (this.ws?.readyState === WebSocket.OPEN) this.ws.send(JSON.stringify({ event, data })); }

  close() {
    this.stopped = true;
    window.clearTimeout(this.timer);
    window.removeEventListener('online', this.onOnline);
    window.removeEventListener('offline', this.onOffline);
    this.ws?.close();
  }
}
