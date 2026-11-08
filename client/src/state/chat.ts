/**
 * Chat store: conversations, messages, outbox, presence, typing and connection state.
 * A small external store consumed with useSyncExternalStore — keeps networking out of components.
 */
import { useSyncExternalStore } from 'react';
import { api, ApiError, tokenStore, API_BASE } from '../lib/api';
import { RealtimeClient, type ConnectionState } from '../lib/realtime';
import type { Conversation, Message, UIMessage } from '../lib/types';
import { uid } from '../lib/format';

interface Outgoing { clientId: string; conversationId: number; kind: 'text' | 'voice'; body?: string; replyTo?: number | null; aiAssisted?: boolean; wav?: Blob; durationMs?: number; levels?: number[] }
type PersistedOutgoing = Pick<Outgoing, 'clientId' | 'conversationId' | 'kind' | 'body' | 'replyTo' | 'aiAssisted'>;
const OUTBOX_KEY = (userId: number) => `vaani.outbox.v1.${userId}`;

export interface ChatState {
  conversations: Conversation[];
  conversationsLoaded: boolean;
  conversationsError: string | null;
  messages: Record<number, UIMessage[]>;
  hasMore: Record<number, boolean>;
  loading: Record<number, boolean>;
  loadError: Record<number, string | null>;
  readState: Record<number, Record<number, number>>;
  typing: Record<number, Record<number, number>>; // convId → userId → expiry ms
  online: Set<number>;
  connection: ConnectionState;
}

const initial = (): ChatState => ({
  conversations: [], conversationsLoaded: false, conversationsError: null, messages: {}, hasMore: {}, loading: {}, loadError: {},
  readState: {}, typing: {}, online: new Set(), connection: 'connecting',
});

class ChatStore {
  state: ChatState = initial();
  private listeners = new Set<() => void>();
  private rt: RealtimeClient | null = null;
  private outbox = new Map<string, Outgoing>();
  private meId = 0;
  private lastTypingSent = 0;
  private typingSweep: number | undefined;

  subscribe = (l: () => void) => { this.listeners.add(l); return () => { this.listeners.delete(l); }; };
  private incoming = new Set<(m: Message) => void>();
  /** Fires for realtime messages from other people (used for background notifications). */
  onIncoming(l: (m: Message) => void) { this.incoming.add(l); return () => { this.incoming.delete(l); }; }
  getSnapshot = () => this.state;
  private set(patch: Partial<ChatState>) { this.state = { ...this.state, ...patch }; this.listeners.forEach((l) => l()); }

  start(meId: number) {
    this.stop();
    this.meId = meId;
    this.state = initial();
    this.outbox = this.loadPersistedOutbox(meId);
    const token = tokenStore.get();
    if (!token) return;
    this.rt = new RealtimeClient(token, this.onEvent);
    this.rt.subscribe((s) => {
      this.set({ connection: s });
      if (s === 'connected') { void this.loadConversations(); this.flushOutbox(); this.refreshOpen(); }
    });
    void this.loadConversations();
    this.typingSweep = window.setInterval(() => this.sweepTyping(), 1500);
  }
  stop() { this.rt?.close(); this.rt = null; window.clearInterval(this.typingSweep); this.outbox.clear(); }

  /** Pending text only: audio Blobs are intentionally never placed in browser storage. */
  private loadPersistedOutbox(userId: number) {
    try {
      const raw = JSON.parse(localStorage.getItem(OUTBOX_KEY(userId)) ?? '[]') as unknown;
      if (!Array.isArray(raw)) return new Map<string, Outgoing>();
      const valid = raw.filter((item): item is PersistedOutgoing => {
        if (!item || typeof item !== 'object') return false;
        const v = item as PersistedOutgoing;
        return v.kind === 'text' && typeof v.clientId === 'string' && typeof v.conversationId === 'number' && typeof v.body === 'string';
      }).slice(-100);
      return new Map(valid.map((item) => [item.clientId, item]));
    } catch { return new Map<string, Outgoing>(); }
  }
  private persistOutbox() {
    try {
      const text = [...this.outbox.values()].filter((o): o is Outgoing & { kind: 'text'; body: string } => o.kind === 'text' && typeof o.body === 'string').slice(-100)
        .map(({ clientId, conversationId, kind, body, replyTo, aiAssisted }) => ({ clientId, conversationId, kind, body, replyTo, aiAssisted }));
      if (text.length) localStorage.setItem(OUTBOX_KEY(this.meId), JSON.stringify(text));
      else localStorage.removeItem(OUTBOX_KEY(this.meId));
    } catch { /* private mode/storage quota: retain in-memory behavior */ }
  }

  /* ---------------- conversations ---------------- */
  async loadConversations() {
    try {
      const list = await api<Conversation[]>('/conversations');
      this.set({ conversations: list, conversationsLoaded: true, conversationsError: null });
    } catch (e) {
      this.set({ conversationsLoaded: true, conversationsError: (e as Error).message });
    }
  }
  private async refreshConversation(id: number) {
    try {
      const c = await api<Conversation>(`/conversations/${id}`);
      const others = this.state.conversations.filter((x) => x.id !== id);
      this.set({ conversations: [c, ...others].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)) });
    } catch (e) {
      if ((e as ApiError).status === 404) this.set({ conversations: this.state.conversations.filter((x) => x.id !== id) });
    }
  }
  upsertConversation(c: Conversation) {
    const others = this.state.conversations.filter((x) => x.id !== c.id);
    this.set({ conversations: [c, ...others] });
  }

  /* ---------------- messages ---------------- */
  async loadMessages(conversationId: number, older = false) {
    if (this.state.loading[conversationId]) return;
    const current = this.state.messages[conversationId] ?? [];
    const before = older ? current.find((m) => m.id > 0)?.id : undefined;
    this.set({ loading: { ...this.state.loading, [conversationId]: true }, loadError: { ...this.state.loadError, [conversationId]: null } });
    try {
      const after = !older && current.filter((m) => m.id > 0).at(-1)?.id;
      const r = await api<{ messages: Message[]; readState: Record<number, number> }>(`/conversations/${conversationId}/messages?limit=50${before ? `&before=${before}` : after ? `&after=${after}` : ''}`);
      const pending = (this.state.messages[conversationId] ?? []).filter((m) => m.local);
      const merged = older ? [...r.messages, ...current] : after ? [...current, ...r.messages] : [...r.messages, ...pending];
      this.set({
        messages: { ...this.state.messages, [conversationId]: dedupe(merged) },
        hasMore: { ...this.state.hasMore, [conversationId]: r.messages.length === 50 },
        readState: { ...this.state.readState, [conversationId]: r.readState },
      });
    } catch (e) {
      this.set({ loadError: { ...this.state.loadError, [conversationId]: (e as Error).message } });
    } finally {
      this.set({ loading: { ...this.state.loading, [conversationId]: false } });
    }
  }
  private refreshOpen() { for (const id of Object.keys(this.state.messages)) void this.loadMessages(Number(id)); }

  private putMessage(m: UIMessage) {
    const list = this.state.messages[m.conversationId];
    if (!list) return;
    const idx = list.findIndex((x) => x.id === m.id || (m.clientId && x.clientId === m.clientId && x.senderId === m.senderId));
    const next = idx >= 0 ? list.map((x, i) => (i === idx ? m : x)) : [...list, m];
    this.set({ messages: { ...this.state.messages, [m.conversationId]: dedupe(next) } });
  }

  private bumpConversation(m: Message) {
    const c = this.state.conversations.find((x) => x.id === m.conversationId);
    if (!c) { void this.refreshConversation(m.conversationId); return; }
    const mine = m.senderId === this.meId;
    const updated = { ...c, lastMessage: m, updatedAt: m.createdAt, unread: mine || m.kind === 'system' ? c.unread : c.unread + 1 };
    this.set({ conversations: [updated, ...this.state.conversations.filter((x) => x.id !== c.id)] });
  }

  sendText(conversationId: number, body: string, opts: { replyTo?: number | null; aiAssisted?: boolean } = {}) {
    const o: Outgoing = { clientId: uid(), conversationId, kind: 'text', body, replyTo: opts.replyTo ?? null, aiAssisted: opts.aiAssisted };
    this.enqueue(o);
  }

  sendVoice(conversationId: number, wav: Blob, durationMs: number, levels: number[], replyTo?: number | null) {
    this.enqueue({ clientId: uid(), conversationId, kind: 'voice', wav, durationMs, levels, replyTo: replyTo ?? null });
  }

  private enqueue(o: Outgoing) {
    this.outbox.set(o.clientId, o);
    this.persistOutbox();
    const temp: UIMessage = {
      id: -Date.now() - Math.random(), conversationId: o.conversationId, senderId: this.meId, kind: o.kind, body: o.body ?? null,
      replyTo: o.replyTo ? this.findReply(o.conversationId, o.replyTo) : null, clientId: o.clientId, aiAssisted: !!o.aiAssisted, sourceLanguage: null,
      createdAt: new Date().toISOString(), editedAt: null, deletedAt: null, reactions: [], local: 'pending',
      voice: o.kind === 'voice' ? { durationMs: o.durationMs!, waveform: bucket(o.levels ?? [], 48), transcriptStatus: 'none', transcriptLang: null, transcriptError: null } : null,
    };
    this.putMessage(temp);
    void this.deliver(o);
  }

  private findReply(convId: number, id: number) {
    const m = this.state.messages[convId]?.find((x) => x.id === id);
    return m ? { id: m.id, senderId: m.senderId, body: m.body, kind: m.kind } : null;
  }

  private async deliver(o: Outgoing) {
    try {
      let msg: Message;
      if (o.kind === 'text') {
        msg = await api<Message>(`/conversations/${o.conversationId}/messages`, { method: 'POST', json: { body: o.body, clientId: o.clientId, replyTo: o.replyTo, aiAssisted: o.aiAssisted } });
      } else {
        const q = new URLSearchParams({ clientId: o.clientId, ...(o.replyTo ? { replyTo: String(o.replyTo) } : {}) });
        msg = await api<Message>(`/conversations/${o.conversationId}/voice?${q}`, { method: 'POST', headers: { 'Content-Type': 'audio/wav' }, body: o.wav });
      }
      this.outbox.delete(o.clientId);
      this.persistOutbox();
      this.putMessage(msg);
      this.bumpConversation(msg);
    } catch (e) {
      const err = e as ApiError;
      const list = this.state.messages[o.conversationId] ?? [];
      const temp = list.find((m) => m.clientId === o.clientId && m.local);
      if (!temp) return;
      if (err.offline) this.putMessage({ ...temp, local: 'pending' }); // stays queued; flushed on reconnect
      else { this.outbox.delete(o.clientId); this.persistOutbox(); this.failed.set(o.clientId, o); this.putMessage({ ...temp, local: 'failed', localError: err.message }); }
    }
  }
  private failed = new Map<string, Outgoing>();

  retry(clientId: string) {
    const o = this.failed.get(clientId) ?? this.outbox.get(clientId);
    if (!o) return;
    this.failed.delete(clientId); this.outbox.set(clientId, o); this.persistOutbox();
    const temp = this.state.messages[o.conversationId]?.find((m) => m.clientId === clientId);
    if (temp) this.putMessage({ ...temp, local: 'pending', localError: undefined });
    void this.deliver(o);
  }
  discard(clientId: string) {
    const o = this.failed.get(clientId) ?? this.outbox.get(clientId);
    this.failed.delete(clientId); this.outbox.delete(clientId); this.persistOutbox();
    if (!o) return;
    const list = this.state.messages[o.conversationId] ?? [];
    this.set({ messages: { ...this.state.messages, [o.conversationId]: list.filter((m) => m.clientId !== clientId || !m.local) } });
  }
  private flushOutbox() { for (const o of this.outbox.values()) void this.deliver(o); }

  async react(messageId: number, emoji: string) {
    const m = await api<Message>(`/messages/${messageId}/reactions`, { method: 'POST', json: { emoji } });
    this.putMessage(m);
  }

  markRead(conversationId: number) {
    const list = this.state.messages[conversationId];
    const last = list?.filter((m) => m.id > 0).at(-1);
    const c = this.state.conversations.find((x) => x.id === conversationId);
    if (!last || !c) return;
    const rs = this.state.readState[conversationId] ?? {};
    if (c.unread === 0 && (rs[this.meId] ?? 0) >= last.id) return;
    this.set({
      conversations: this.state.conversations.map((x) => (x.id === conversationId ? { ...x, unread: 0 } : x)),
      readState: { ...this.state.readState, [conversationId]: { ...rs, [this.meId]: last.id } },
    });
    void api(`/conversations/${conversationId}/read`, { method: 'POST', json: { messageId: last.id } }).catch(() => {});
  }

  typing(conversationId: number) {
    const now = Date.now();
    if (now - this.lastTypingSent < 2500) return;
    this.lastTypingSent = now;
    this.rt?.send('typing', { conversationId });
  }
  private sweepTyping() {
    const now = Date.now();
    let changed = false;
    const next: ChatState['typing'] = {};
    for (const [cid, users] of Object.entries(this.state.typing)) {
      const keep = Object.fromEntries(Object.entries(users).filter(([, exp]) => exp > now));
      if (Object.keys(keep).length !== Object.keys(users).length) changed = true;
      if (Object.keys(keep).length) next[Number(cid)] = keep;
    }
    if (changed) this.set({ typing: next });
  }

  /* ---------------- realtime events ---------------- */
  private onEvent = (event: string, data: any) => {
    switch (event) {
      case 'hello': this.set({ online: new Set<number>(data.online) }); break;
      case 'presence': {
        const s = new Set(this.state.online);
        if (data.online) s.add(data.userId); else s.delete(data.userId);
        this.set({ online: s });
        break;
      }
      case 'message.new': {
        const m = data as Message;
        if (m.senderId !== this.meId && m.kind !== 'system') this.incoming.forEach((l) => l(m));
        this.putMessage(m);
        if (!this.state.conversations.find((c) => c.lastMessage?.id === m.id)) this.bumpConversation(m);
        const t = this.state.typing[m.conversationId];
        if (t?.[m.senderId ?? 0]) { const { [m.senderId ?? 0]: _drop, ...rest } = t; void _drop; this.set({ typing: { ...this.state.typing, [m.conversationId]: rest } }); }
        break;
      }
      case 'message.updated':
      case 'message.deleted': this.putMessage(data as Message); break;
      case 'conversation.updated': void this.refreshConversation(data.conversationId); break;
      case 'read': {
        const rs = this.state.readState[data.conversationId] ?? {};
        this.set({ readState: { ...this.state.readState, [data.conversationId]: { ...rs, [data.userId]: Math.max(rs[data.userId] ?? 0, data.messageId) } } });
        break;
      }
      case 'typing': {
        const t = this.state.typing[data.conversationId] ?? {};
        this.set({ typing: { ...this.state.typing, [data.conversationId]: { ...t, [data.userId]: Date.now() + 4000 } } });
        break;
      }
    }
  };
}

function dedupe(list: UIMessage[]): UIMessage[] {
  const seen = new Map<string, UIMessage>();
  for (const m of list) {
    const key = m.id > 0 ? `id:${m.id}` : `c:${m.clientId}`;
    // A confirmed server message replaces its pending twin.
    if (m.id > 0 && m.clientId) seen.delete(`c:${m.clientId}`);
    if (m.id < 0 && m.clientId && [...seen.values()].some((x) => x.id > 0 && x.clientId === m.clientId)) continue;
    seen.set(key, m);
  }
  return [...seen.values()].sort((a, b) => (a.id > 0 && b.id > 0 ? a.id - b.id : a.createdAt.localeCompare(b.createdAt)));
}

function bucket(levels: number[], bins: number) {
  if (!levels.length) return new Array(bins).fill(0.1);
  const per = Math.max(1, Math.floor(levels.length / bins));
  const out = Array.from({ length: bins }, (_, i) => { const s = levels.slice(i * per, (i + 1) * per); return s.length ? Math.max(...s) : 0; });
  const peak = Math.max(...out, 1e-6);
  return out.map((v) => v / peak);
}

export const chat = new ChatStore();
export function useChat<T>(select: (s: ChatState) => T): T {
  const s = useSyncExternalStore(chat.subscribe, chat.getSnapshot);
  return select(s);
}
export { API_BASE };
