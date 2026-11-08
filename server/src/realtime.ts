/**
 * WebSocket hub. Clients connect to /ws?token=<session token>.
 * Server → client events: message.new, message.updated, conversation.updated, typing, presence, read, hello.
 * Client → server events: typing {conversationId}, ping.
 * Fan-out is always computed from conversation membership in the database.
 */
import crypto from 'node:crypto';
import type { Server } from 'node:http';
import { WebSocketServer, WebSocket } from 'ws';
import { verifyToken } from './auth.js';
import type { Store } from './store.js';

export interface Hub {
  toConversation(conversationId: number, event: string, data: unknown, exceptUserId?: number): void;
  toUser(userId: number, event: string, data: unknown): void;
  isOnline(userId: number): boolean;
  close(): void;
}

export function attachRealtime(server: Server, store: Store, secret: string): Hub {
  const wss = new WebSocketServer({ noServer: true, maxPayload: 16 * 1024 });
  const sockets = new Map<number, Set<WebSocket>>();
  const send = (ws: WebSocket, event: string, data: unknown) => {
    if (ws.readyState !== WebSocket.OPEN) return;
    const conversationId = typeof data === 'object' && data !== null && 'conversationId' in data ? (data as { conversationId?: number }).conversationId : undefined;
    ws.send(JSON.stringify({ event, data, eventId: crypto.randomUUID(), eventType: event, eventVersion: 1, ...(conversationId ? { conversationId } : {}), timestamp: new Date().toISOString() }));
  };

  const hub: Hub = {
    toUser(userId, event, data) { for (const ws of sockets.get(userId) ?? []) send(ws, event, data); },
    toConversation(conversationId, event, data, exceptUserId) {
      for (const uid of store.memberIds(conversationId)) if (uid !== exceptUserId) hub.toUser(uid, event, data);
    },
    isOnline: (userId) => (sockets.get(userId)?.size ?? 0) > 0,
    close: () => { for (const set of sockets.values()) for (const ws of set) ws.terminate(); wss.close(); },
  };

  const peersOf = (userId: number) => {
    const ids = new Set<number>();
    for (const c of store.listConversations(userId)) for (const m of c.members) if (m.id !== userId) ids.add(m.id);
    return ids;
  };
  const announce = (userId: number, online: boolean) => { for (const p of peersOf(userId)) hub.toUser(p, 'presence', { userId, online }); };

  server.on('upgrade', (req, socket, head) => {
    const url = new URL(req.url ?? '/', 'http://localhost');
    if (url.pathname !== '/ws') return;
    const claims = verifyToken(url.searchParams.get('token') ?? '', secret);
    if (!claims?.sid || !store.isSessionActive(claims.sid, claims.sub)) {
      socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n'); socket.destroy(); return;
    }
    wss.handleUpgrade(req, socket, head, (ws) => {
      const uid = claims.sub;
      const set = sockets.get(uid) ?? new Set();
      const wasOffline = set.size === 0;
      set.add(ws); sockets.set(uid, set);
      (ws as WebSocket & { alive?: boolean }).alive = true;
      const online = [...peersOf(uid)].filter((p) => hub.isOnline(p));
      send(ws, 'hello', { userId: uid, online, serverTime: new Date().toISOString() });
      if (wasOffline) announce(uid, true);

      ws.on('pong', () => { (ws as WebSocket & { alive?: boolean }).alive = true; });
      ws.on('message', (raw) => {
        let msg: { event?: string; data?: { conversationId?: number } };
        try { msg = JSON.parse(String(raw)); } catch { return; }
        if (msg.event === 'typing' && Number.isInteger(msg.data?.conversationId)) {
          try { store.assertMember(msg.data!.conversationId!, uid); } catch { return; }
          hub.toConversation(msg.data!.conversationId!, 'typing', { conversationId: msg.data!.conversationId, userId: uid }, uid);
        } else if (msg.event === 'ping') send(ws, 'pong', { t: Date.now() });
      });
      ws.on('close', () => {
        set.delete(ws);
        if (set.size === 0) { sockets.delete(uid); announce(uid, false); }
      });
    });
  });

  const heartbeat = setInterval(() => {
    for (const set of sockets.values()) for (const ws of set) {
      const w = ws as WebSocket & { alive?: boolean };
      if (!w.alive) { ws.terminate(); continue; }
      w.alive = false; ws.ping();
    }
  }, 30_000);
  heartbeat.unref();
  wss.on('close', () => clearInterval(heartbeat));
  return hub;
}

/** No-op hub for tests and scripts. */
export const nullHub: Hub = { toConversation() {}, toUser() {}, isOnline: () => false, close() {} };
