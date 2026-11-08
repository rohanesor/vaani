/**
 * Data access. Every conversation-scoped read goes through `assertMember`,
 * which is the single authorization gate for conversation content.
 */
import type { DB } from './db.js';
import { tx } from './db.js';
import { HttpError } from './http-utils.js';

export interface UserDTO { id: number; email?: string; displayName: string; avatarHue: number; preferredLanguage: string; createdAt: string; updatedAt: string }
export interface SettingsDTO {
  aiEnabled: boolean; aiContextMessages: number; aiRedactContacts: boolean; voiceTranscription: boolean;
  theme: 'system' | 'light' | 'dark'; onboarded: boolean; notifyMessages: boolean;
}
export interface ReactionDTO { emoji: string; userIds: number[] }
export interface VoiceDTO { durationMs: number; waveform: number[]; transcriptStatus: string; transcriptLang: string | null; transcriptError: string | null }
export interface MessageDTO {
  id: number; conversationId: number; senderId: number | null; kind: 'text' | 'voice' | 'system';
  body: string | null; replyTo: { id: number; senderId: number | null; body: string | null; kind: string } | null;
  clientId: string | null; aiAssisted: boolean; sourceLanguage: string | null; createdAt: string; editedAt: string | null; deletedAt: string | null; reactions: ReactionDTO[]; voice: VoiceDTO | null;
}
export interface ConversationDTO {
  id: number; kind: 'direct' | 'group'; title: string; createdBy: number; updatedAt: string;
  members: (UserDTO & { role: string })[]; lastMessage: MessageDTO | null; unread: number; myRole: string;
}

type Row = Record<string, unknown>;

const userFromRow = (r: Row, withEmail = false): UserDTO => ({
  id: r.id as number,
  ...(withEmail ? { email: r.email as string } : {}),
  displayName: r.display_name as string,
  avatarHue: r.avatar_hue as number,
  preferredLanguage: r.preferred_language as string,
  createdAt: r.created_at as string,
  updatedAt: r.updated_at as string,
});

export class Store {
  constructor(private db: DB) {}

  /** Database readiness check: runs a trivial query to verify connectivity. */
  isReady(): boolean {
    this.db.prepare('SELECT 1').get();
    return true;
  }

  /** Close the database connection. */
  close(): void {
    this.db.close();
  }

  /* ---------- users ---------- */
  createUser(email: string, passwordHash: string, displayName: string, preferredLanguage: string): UserDTO {
    const hue = Math.floor(Math.random() * 360);
    try {
      const r = this.db.prepare('INSERT INTO users (email, password_hash, display_name, preferred_language, avatar_hue) VALUES (?,?,?,?,?)')
        .run(email, passwordHash, displayName, preferredLanguage, hue);
      const id = Number(r.lastInsertRowid);
      this.db.prepare('INSERT INTO user_settings (user_id) VALUES (?)').run(id);
      return this.getUser(id, true);
    } catch (e) {
      if (String(e).includes('UNIQUE')) throw new HttpError(409, 'email_taken', 'An account with this email already exists.');
      throw e;
    }
  }

  findAuthByEmail(email: string): { id: number; passwordHash: string } | null {
    const r = this.db.prepare('SELECT id, password_hash FROM users WHERE email = ?').get(email) as Row | undefined;
    return r ? { id: r.id as number, passwordHash: r.password_hash as string } : null;
  }

  getUser(id: number, withEmail = false): UserDTO {
    const r = this.db.prepare('SELECT * FROM users WHERE id = ?').get(id) as Row | undefined;
    if (!r) throw new HttpError(404, 'not_found', 'User not found.');
    return userFromRow(r, withEmail);
  }

  updateProfile(id: number, patch: { displayName?: string; preferredLanguage?: string; avatarHue?: number }): UserDTO {
    const cur = this.getUser(id, true);
    this.db.prepare(`UPDATE users SET display_name = ?, preferred_language = ?, avatar_hue = ?,
      updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') WHERE id = ?`).run(
      patch.displayName ?? cur.displayName, patch.preferredLanguage ?? cur.preferredLanguage, patch.avatarHue ?? cur.avatarHue, id);
    return this.getUser(id, true);
  }

  /** People directory. VAANI has no phone-book sync, so the directory is the accounts on this server. */
  searchUsers(selfId: number, q: string, limit = 30): UserDTO[] {
    const like = `%${q.replace(/[%_]/g, '')}%`;
    return (this.db.prepare(`SELECT * FROM users WHERE id != ? AND (display_name LIKE ? OR email LIKE ?) ORDER BY display_name LIMIT ?`)
      .all(selfId, like, like, limit) as Row[]).map((r) => userFromRow(r));
  }

  recentContacts(selfId: number, limit = 8): UserDTO[] {
    return (this.db.prepare(`
      SELECT u.*, MAX(c.updated_at) AS last FROM members me
      JOIN conversations c ON c.id = me.conversation_id
      JOIN members m ON m.conversation_id = c.id AND m.user_id != me.user_id
      JOIN users u ON u.id = m.user_id
      WHERE me.user_id = ? GROUP BY u.id ORDER BY last DESC LIMIT ?`).all(selfId, limit) as Row[]).map((r) => userFromRow(r));
  }

  /* ---------- authenticated sessions ---------- */
  createSession(id: string, userId: number, expiresAt: number) {
    this.db.prepare('INSERT INTO auth_sessions (id, user_id, expires_at) VALUES (?,?,?)').run(id, userId, expiresAt);
  }

  isSessionActive(id: string, userId: number): boolean {
    const row = this.db.prepare(`SELECT 1 FROM auth_sessions
      WHERE id = ? AND user_id = ? AND revoked_at IS NULL AND expires_at >= ?`).get(id, userId, Math.floor(Date.now() / 1000));
    return Boolean(row);
  }

  revokeSession(id: string, userId: number): boolean {
    const changed = this.db.prepare(`UPDATE auth_sessions SET revoked_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')
      WHERE id = ? AND user_id = ? AND revoked_at IS NULL`).run(id, userId);
    return Number(changed.changes) === 1;
  }

  /* ---------- settings ---------- */
  getSettings(userId: number): SettingsDTO {
    const r = this.db.prepare('SELECT * FROM user_settings WHERE user_id = ?').get(userId) as Row;
    return {
      aiEnabled: !!r.ai_enabled, aiContextMessages: r.ai_context_messages as number, aiRedactContacts: !!r.ai_redact_contacts,
      voiceTranscription: !!r.voice_transcription, theme: r.theme as SettingsDTO['theme'], onboarded: !!r.onboarded, notifyMessages: !!r.notify_messages,
    };
  }

  updateSettings(userId: number, patch: Partial<SettingsDTO>): SettingsDTO {
    const s = { ...this.getSettings(userId), ...patch };
    if (!['system', 'light', 'dark'].includes(s.theme)) throw new HttpError(400, 'invalid_request', 'Unknown theme.');
    if (!Number.isInteger(s.aiContextMessages) || s.aiContextMessages < 5 || s.aiContextMessages > 100)
      throw new HttpError(400, 'invalid_request', 'AI context must be between 5 and 100 messages.');
    this.db.prepare(`UPDATE user_settings SET ai_enabled=?, ai_context_messages=?, ai_redact_contacts=?, voice_transcription=?, theme=?, onboarded=?, notify_messages=? WHERE user_id=?`)
      .run(+s.aiEnabled, s.aiContextMessages, +s.aiRedactContacts, +s.voiceTranscription, s.theme, +s.onboarded, +s.notifyMessages, userId);
    return this.getSettings(userId);
  }

  /* ---------- authorization ---------- */
  /** Throws 404 (not 403) so non-members cannot probe which conversations exist. */
  assertMember(conversationId: number, userId: number): { role: string } {
    const r = this.db.prepare('SELECT role FROM members WHERE conversation_id = ? AND user_id = ?').get(conversationId, userId) as Row | undefined;
    if (!r) throw new HttpError(404, 'not_found', 'Conversation not found.');
    return { role: r.role as string };
  }

  memberIds(conversationId: number): number[] {
    return (this.db.prepare('SELECT user_id FROM members WHERE conversation_id = ?').all(conversationId) as Row[]).map((r) => r.user_id as number);
  }

  /* ---------- conversations ---------- */
  createDirect(selfId: number, otherId: number): ConversationDTO {
    if (selfId === otherId) throw new HttpError(400, 'invalid_request', 'You cannot start a conversation with yourself.');
    this.getUser(otherId);
    const key = [selfId, otherId].sort((a, b) => a - b).join(':');
    const existing = this.db.prepare('SELECT id FROM conversations WHERE direct_key = ?').get(key) as Row | undefined;
    if (existing) return this.getConversation(existing.id as number, selfId);
    const id = tx(this.db, () => {
      const r = this.db.prepare(`INSERT INTO conversations (kind, created_by, direct_key) VALUES ('direct', ?, ?)`).run(selfId, key);
      const cid = Number(r.lastInsertRowid);
      const add = this.db.prepare(`INSERT INTO members (conversation_id, user_id, role) VALUES (?, ?, 'member')`);
      add.run(cid, selfId); add.run(cid, otherId);
      return cid;
    });
    return this.getConversation(id, selfId);
  }

  createGroup(selfId: number, title: string, memberIds: number[]): ConversationDTO {
    const ids = [...new Set(memberIds.filter((m) => m !== selfId))];
    if (ids.length < 1) throw new HttpError(400, 'invalid_request', 'Add at least one other person to the group.');
    if (ids.length > 255) throw new HttpError(400, 'invalid_request', 'Groups can have at most 256 members.');
    ids.forEach((m) => this.getUser(m));
    const id = tx(this.db, () => {
      const r = this.db.prepare(`INSERT INTO conversations (kind, title, created_by) VALUES ('group', ?, ?)`).run(title, selfId);
      const cid = Number(r.lastInsertRowid);
      this.db.prepare(`INSERT INTO members (conversation_id, user_id, role) VALUES (?, ?, 'owner')`).run(cid, selfId);
      const add = this.db.prepare(`INSERT INTO members (conversation_id, user_id) VALUES (?, ?)`);
      ids.forEach((m) => add.run(cid, m));
      return cid;
    });
    const me = this.getUser(selfId);
    this.insertMessage({ conversationId: id, senderId: null, kind: 'system', body: `${me.displayName} created “${title}”` });
    return this.getConversation(id, selfId);
  }

  addMembers(conversationId: number, selfId: number, userIds: number[]): ConversationDTO {
    const { role } = this.assertMember(conversationId, selfId);
    const conv = this.db.prepare('SELECT kind FROM conversations WHERE id = ?').get(conversationId) as Row;
    if (conv.kind !== 'group') throw new HttpError(400, 'invalid_request', 'People can only be added to groups.');
    if (role !== 'owner') throw new HttpError(403, 'forbidden', 'Only the group owner can add people.');
    const add = this.db.prepare(`INSERT OR IGNORE INTO members (conversation_id, user_id) VALUES (?, ?)`);
    for (const u of userIds) {
      const user = this.getUser(u);
      if (Number(add.run(conversationId, u).changes) > 0)
        this.insertMessage({ conversationId, senderId: null, kind: 'system', body: `${user.displayName} was added` });
    }
    return this.getConversation(conversationId, selfId);
  }

  leaveGroup(conversationId: number, selfId: number) {
    this.assertMember(conversationId, selfId);
    const me = this.getUser(selfId);
    this.db.prepare('DELETE FROM members WHERE conversation_id = ? AND user_id = ?').run(conversationId, selfId);
    this.insertMessage({ conversationId, senderId: null, kind: 'system', body: `${me.displayName} left` });
  }

  renameGroup(conversationId: number, selfId: number, title: string): ConversationDTO {
    this.assertMember(conversationId, selfId);
    this.db.prepare(`UPDATE conversations SET title = ? WHERE id = ? AND kind = 'group'`).run(title, conversationId);
    return this.getConversation(conversationId, selfId);
  }

  getConversation(id: number, selfId: number): ConversationDTO {
    const { role } = this.assertMember(id, selfId);
    const c = this.db.prepare('SELECT * FROM conversations WHERE id = ?').get(id) as Row;
    const members = (this.db.prepare('SELECT u.*, m.role FROM members m JOIN users u ON u.id = m.user_id WHERE m.conversation_id = ? ORDER BY u.display_name').all(id) as Row[])
      .map((r) => ({ ...userFromRow(r), role: r.role as string }));
    const lastRow = this.db.prepare('SELECT id FROM messages WHERE conversation_id = ? ORDER BY id DESC LIMIT 1').get(id) as Row | undefined;
    const me = this.db.prepare('SELECT last_read_id FROM members WHERE conversation_id = ? AND user_id = ?').get(id, selfId) as Row;
    const unread = (this.db.prepare('SELECT COUNT(*) AS n FROM messages WHERE conversation_id = ? AND id > ? AND (sender_id IS NULL OR sender_id != ?) AND kind != \'system\'')
      .get(id, me.last_read_id as number, selfId) as Row).n as number;
    const title = c.kind === 'group' ? (c.title as string) : (members.find((m) => m.id !== selfId)?.displayName ?? 'Conversation');
    return {
      id, kind: c.kind as 'direct' | 'group', title, createdBy: c.created_by as number, updatedAt: c.updated_at as string,
      members, lastMessage: lastRow ? this.messagesByIds([lastRow.id as number])[0] : null, unread, myRole: role,
    };
  }

  listConversations(selfId: number): ConversationDTO[] {
    const ids = (this.db.prepare(`SELECT c.id FROM conversations c JOIN members m ON m.conversation_id = c.id WHERE m.user_id = ? ORDER BY c.updated_at DESC`)
      .all(selfId) as Row[]).map((r) => r.id as number);
    return ids.map((id) => this.getConversation(id, selfId));
  }

  markRead(conversationId: number, selfId: number, messageId: number) {
    this.assertMember(conversationId, selfId);
    this.db.prepare('UPDATE members SET last_read_id = MAX(last_read_id, ?) WHERE conversation_id = ? AND user_id = ?').run(messageId, conversationId, selfId);
  }

  /** Highest message id each member has read, for read receipts. */
  readState(conversationId: number): Record<number, number> {
    const out: Record<number, number> = {};
    for (const r of this.db.prepare('SELECT user_id, last_read_id FROM members WHERE conversation_id = ?').all(conversationId) as Row[])
      out[r.user_id as number] = r.last_read_id as number;
    return out;
  }

  /* ---------- messages ---------- */
  /** Find a sender-scoped client id after membership authorization. */
  findMessageByClientId(conversationId: number, senderId: number, clientId: string): MessageDTO | null {
    this.assertMember(conversationId, senderId);
    const row = this.db.prepare('SELECT id FROM messages WHERE conversation_id = ? AND sender_id = ? AND client_id = ?').get(conversationId, senderId, clientId) as Row | undefined;
    return row ? this.messagesByIds([row.id as number])[0] : null;
  }

  /** Atomically persist a canonical message, activity timestamp and sender read cursor. */
  insertMessage(m: { conversationId: number; senderId: number | null; kind: 'text' | 'voice' | 'system'; body: string | null; replyTo?: number | null; clientId?: string | null; aiAssisted?: boolean; sourceLanguage?: string | null }): MessageDTO {
    return tx(this.db, () => this.insertMessageInner(m));
  }

  private insertMessageInner(m: { conversationId: number; senderId: number | null; kind: 'text' | 'voice' | 'system'; body: string | null; replyTo?: number | null; clientId?: string | null; aiAssisted?: boolean; sourceLanguage?: string | null }): MessageDTO {
    if (m.replyTo) {
      const parent = this.db.prepare('SELECT conversation_id FROM messages WHERE id = ?').get(m.replyTo) as Row | undefined;
      if (!parent || parent.conversation_id !== m.conversationId) throw new HttpError(400, 'invalid_request', 'Reply target is not in this conversation.');
    }
    if (m.clientId && m.senderId) {
      const dupe = this.db.prepare('SELECT id FROM messages WHERE sender_id = ? AND client_id = ?').get(m.senderId, m.clientId) as Row | undefined;
      if (dupe) return this.messagesByIds([dupe.id as number])[0]; // idempotent retry
    }
    const r = this.db.prepare('INSERT INTO messages (conversation_id, sender_id, kind, body, reply_to, client_id, ai_assisted, source_language) VALUES (?,?,?,?,?,?,?,?)')
      .run(m.conversationId, m.senderId, m.kind, m.body, m.replyTo ?? null, m.clientId ?? null, m.aiAssisted ? 1 : 0, m.sourceLanguage ?? null);
    const id = Number(r.lastInsertRowid);
    this.db.prepare(`UPDATE conversations SET updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') WHERE id = ?`).run(m.conversationId);
    if (m.senderId) this.db.prepare('UPDATE members SET last_read_id = ? WHERE conversation_id = ? AND user_id = ?').run(id, m.conversationId, m.senderId);
    return this.messagesByIds([id])[0];
  }

  messagesByIds(ids: number[]): MessageDTO[] {
    if (!ids.length) return [];
    const ph = ids.map(() => '?').join(',');
    const rows = this.db.prepare(`SELECT * FROM messages WHERE id IN (${ph}) ORDER BY id`).all(...ids) as Row[];
    const reacts = this.db.prepare(`SELECT message_id, emoji, user_id FROM reactions WHERE message_id IN (${ph})`).all(...ids) as Row[];
    const voices = this.db.prepare(`SELECT message_id, duration_ms, waveform, transcript_status, transcript_lang, transcript_error FROM voice_notes WHERE message_id IN (${ph})`).all(...ids) as Row[];
    const replyIds = rows.map((r) => r.reply_to).filter(Boolean) as number[];
    const replies = replyIds.length
      ? (this.db.prepare(`SELECT id, sender_id, body, kind FROM messages WHERE id IN (${replyIds.map(() => '?').join(',')})`).all(...replyIds) as Row[])
      : [];
    return rows.map((r) => {
      const reactions: ReactionDTO[] = [];
      for (const x of reacts.filter((x) => x.message_id === r.id)) {
        const e = reactions.find((y) => y.emoji === x.emoji);
        if (e) e.userIds.push(x.user_id as number); else reactions.push({ emoji: x.emoji as string, userIds: [x.user_id as number] });
      }
      const v = voices.find((x) => x.message_id === r.id);
      const rep = replies.find((x) => x.id === r.reply_to);
      return {
        id: r.id as number, conversationId: r.conversation_id as number, senderId: (r.sender_id as number) ?? null,
        kind: r.kind as MessageDTO['kind'], body: (r.body as string) ?? null,
        replyTo: rep ? { id: rep.id as number, senderId: (rep.sender_id as number) ?? null, body: (rep.body as string) ?? null, kind: rep.kind as string } : null,
        clientId: (r.client_id as string) ?? null, aiAssisted: !!r.ai_assisted, sourceLanguage: (r.source_language as string) ?? null,
        createdAt: r.created_at as string, editedAt: (r.edited_at as string) ?? null, deletedAt: (r.deleted_at as string) ?? null, reactions,
        voice: v ? { durationMs: v.duration_ms as number, waveform: JSON.parse(v.waveform as string), transcriptStatus: v.transcript_status as string,
          transcriptLang: (v.transcript_lang as string) ?? null, transcriptError: (v.transcript_error as string) ?? null } : null,
      };
    });
  }

  listMessages(conversationId: number, selfId: number, opts: { before?: number; after?: number; limit?: number } = {}): MessageDTO[] {
    this.assertMember(conversationId, selfId);
    const limit = Math.min(Math.max(opts.limit ?? 50, 1), 200);
    const ids = opts.after
      ? (this.db.prepare(`SELECT id FROM messages WHERE conversation_id = ? AND id > ? ORDER BY id LIMIT ?`).all(conversationId, opts.after, limit) as Row[]).map((r) => r.id as number)
      : (this.db.prepare(`SELECT id FROM messages WHERE conversation_id = ? AND id < ? ORDER BY id DESC LIMIT ?`)
        .all(conversationId, opts.before ?? Number.MAX_SAFE_INTEGER, limit) as Row[]).map((r) => r.id as number).reverse();
    return this.messagesByIds(ids);
  }

  /** Incremental messages across all conversations visible to a user, ordered by canonical ID. */
  listMessagesAfterForUser(selfId: number, afterId = 0, limit = 200): MessageDTO[] {
    const bounded = Math.min(Math.max(limit, 1), 201);
    const rows = this.db.prepare(`
      SELECT m.id FROM messages m
      JOIN members mb ON mb.conversation_id = m.conversation_id AND mb.user_id = ?
      WHERE m.id > ?
      ORDER BY m.id ASC LIMIT ?`).all(selfId, afterId, bounded) as Row[];
    return this.messagesByIds(rows.map((r) => r.id as number));
  }

  /** Most recent N messages, oldest first. Caller must have authorized access. */
  recentMessages(conversationId: number, n: number): MessageDTO[] {
    const ids = (this.db.prepare('SELECT id FROM messages WHERE conversation_id = ? ORDER BY id DESC LIMIT ?').all(conversationId, n) as Row[])
      .map((r) => r.id as number).reverse();
    return this.messagesByIds(ids);
  }

  editMessage(messageId: number, selfId: number, body: string): MessageDTO {
    const msg = this.getMessage(messageId, selfId);
    if (msg.senderId !== selfId || msg.kind !== 'text' || msg.deletedAt) throw new HttpError(403, 'forbidden', 'You can only edit your active text messages.');
    this.db.prepare(`UPDATE messages SET body = ?, edited_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') WHERE id = ?`).run(body, messageId);
    return this.messagesByIds([messageId])[0];
  }

  deleteMessage(messageId: number, selfId: number): MessageDTO {
    const msg = this.getMessage(messageId, selfId);
    if (msg.senderId !== selfId || msg.deletedAt) throw new HttpError(403, 'forbidden', 'You can only delete your active messages.');
    this.db.prepare(`UPDATE messages SET body = NULL, deleted_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') WHERE id = ?`).run(messageId);
    return this.messagesByIds([messageId])[0];
  }

  getMessage(messageId: number, selfId: number): MessageDTO {
    const r = this.db.prepare('SELECT conversation_id FROM messages WHERE id = ?').get(messageId) as Row | undefined;
    if (!r) throw new HttpError(404, 'not_found', 'Message not found.');
    this.assertMember(r.conversation_id as number, selfId);
    return this.messagesByIds([messageId])[0];
  }

  toggleReaction(messageId: number, selfId: number, emoji: string): MessageDTO {
    const msg = this.getMessage(messageId, selfId);
    if ([...emoji].length > 4 || !emoji.trim()) throw new HttpError(400, 'invalid_request', 'Invalid reaction.');
    const del = this.db.prepare('DELETE FROM reactions WHERE message_id = ? AND user_id = ? AND emoji = ?').run(messageId, selfId, emoji);
    if (Number(del.changes) === 0) this.db.prepare('INSERT INTO reactions (message_id, user_id, emoji) VALUES (?,?,?)').run(messageId, selfId, emoji);
    return this.messagesByIds([msg.id])[0];
  }

  /* ---------- voice ---------- */
  saveVoice(messageId: number, v: { mime: string; durationMs: number; waveform: number[]; audio: Buffer; transcriptStatus: string }) {
    this.db.prepare('INSERT INTO voice_notes (message_id, mime, duration_ms, waveform, audio, transcript_status) VALUES (?,?,?,?,?,?)')
      .run(messageId, v.mime, v.durationMs, JSON.stringify(v.waveform), v.audio, v.transcriptStatus);
  }

  getVoiceAudio(messageId: number, selfId: number): { mime: string; audio: Uint8Array } {
    this.getMessage(messageId, selfId);
    const r = this.db.prepare('SELECT mime, audio FROM voice_notes WHERE message_id = ?').get(messageId) as Row | undefined;
    if (!r) throw new HttpError(404, 'not_found', 'Voice note not found.');
    return { mime: r.mime as string, audio: r.audio as Uint8Array };
  }

  setTranscript(messageId: number, status: string, text: string | null, lang: string | null, error: string | null) {
    tx(this.db, () => {
      this.db.prepare('UPDATE voice_notes SET transcript_status = ?, transcript_lang = ?, transcript_error = ? WHERE message_id = ?').run(status, lang, error, messageId);
      if (text !== null) this.db.prepare('UPDATE messages SET body = ? WHERE id = ?').run(text, messageId);
    });
  }

  /* ---------- search ---------- */
  /** Full-text search restricted to conversations the user belongs to. */
  searchMessages(selfId: number, q: string, conversationId?: number, limit = 40): { message: MessageDTO; snippet: string }[] {
    const terms = q.normalize('NFC').match(/[\p{L}\p{M}\p{N}]+/gu) ?? [];
    if (!terms.length) return [];
    const match = terms.slice(0, 8).map((t) => `"${t}"*`).join(' ');
    const rows = this.db.prepare(`
      SELECT m.id, snippet(messages_fts, 0, '[[', ']]', '…', 12) AS snip
      FROM messages_fts f JOIN messages m ON m.id = f.rowid
      JOIN members mb ON mb.conversation_id = m.conversation_id AND mb.user_id = ?
      WHERE messages_fts MATCH ? ${conversationId ? 'AND m.conversation_id = ?' : ''} AND m.kind != 'system'
      ORDER BY bm25(messages_fts), m.id DESC LIMIT ?`)
      .all(...([selfId, match, ...(conversationId ? [conversationId] : []), limit] as (number | string)[])) as Row[];
    const msgs = this.messagesByIds(rows.map((r) => r.id as number));
    return rows.map((r) => ({ message: msgs.find((m) => m.id === r.id)!, snippet: r.snip as string }));
  }

  /** Ranked any-term retrieval inside one conversation (caller must have authorized access). */
  retrieveInConversation(conversationId: number, terms: string[], limit = 12): number[] {
    if (!terms.length) return [];
    const match = terms.slice(0, 12).map((t) => `"${t}"*`).join(' OR ');
    return (this.db.prepare(`
      SELECT m.id FROM messages_fts f JOIN messages m ON m.id = f.rowid
      WHERE messages_fts MATCH ? AND m.conversation_id = ? AND m.kind != 'system'
      ORDER BY bm25(messages_fts) LIMIT ?`).all(match, conversationId, limit) as Row[]).map((r) => r.id as number);
  }

  /** Neighbouring message ids (for context around a retrieved hit). */
  neighbours(conversationId: number, id: number, radius = 1): number[] {
    const before = (this.db.prepare('SELECT id FROM messages WHERE conversation_id = ? AND id < ? ORDER BY id DESC LIMIT ?').all(conversationId, id, radius) as Row[]).map((r) => r.id as number);
    const after = (this.db.prepare('SELECT id FROM messages WHERE conversation_id = ? AND id > ? ORDER BY id LIMIT ?').all(conversationId, id, radius) as Row[]).map((r) => r.id as number);
    return [...before, id, ...after];
  }

  /* ---------- translations ---------- */
  getTranslation(messageId: number, lang: string) {
    return this.db.prepare('SELECT * FROM translations WHERE message_id = ? AND target_lang = ?').get(messageId, lang) as Row | undefined;
  }
  saveTranslation(messageId: number, lang: string, sourceLang: string | null, text: string, note: string | null) {
    this.db.prepare('INSERT OR REPLACE INTO translations (message_id, target_lang, source_lang, text, note) VALUES (?,?,?,?,?)').run(messageId, lang, sourceLang, text, note);
  }

  /* ---------- AI usage, actions, tasks ---------- */
  recordUsage(userId: number, feature: string, model: string, inTok: number, outTok: number, status: string) {
    this.db.prepare('INSERT INTO ai_usage (user_id, feature, model, input_tokens, output_tokens, status) VALUES (?,?,?,?,?,?)').run(userId, feature, model, inTok, outTok, status);
  }
  tokensToday(userId: number): number {
    const r = this.db.prepare(`SELECT COALESCE(SUM(input_tokens + output_tokens),0) AS n FROM ai_usage WHERE user_id = ? AND created_at >= strftime('%Y-%m-%dT00:00:00Z','now')`).get(userId) as Row;
    return r.n as number;
  }
  usageSummary(userId: number) {
    return this.db.prepare(`SELECT feature, COUNT(*) AS requests, SUM(input_tokens) AS inputTokens, SUM(output_tokens) AS outputTokens FROM ai_usage WHERE user_id = ? AND created_at >= strftime('%Y-%m-%dT00:00:00Z','now','-30 days') GROUP BY feature`).all(userId);
  }

  createAction(userId: number, conversationId: number, sourceMessageId: number | null, type: string, payload: unknown): number {
    return Number(this.db.prepare('INSERT INTO ai_actions (user_id, conversation_id, source_message_id, type, payload) VALUES (?,?,?,?,?)')
      .run(userId, conversationId, sourceMessageId, type, JSON.stringify(payload)).lastInsertRowid);
  }
  getAction(id: number, userId: number) {
    const r = this.db.prepare('SELECT * FROM ai_actions WHERE id = ? AND user_id = ?').get(id, userId) as Row | undefined;
    if (!r) throw new HttpError(404, 'not_found', 'Action not found.');
    return { id: r.id as number, conversationId: r.conversation_id as number, sourceMessageId: (r.source_message_id as number) ?? null, type: r.type as string,
      payload: JSON.parse(r.payload as string), status: r.status as string, result: r.result ? JSON.parse(r.result as string) : null, createdAt: r.created_at as string };
  }
  setActionStatus(id: number, status: string, result: unknown) {
    this.db.prepare('UPDATE ai_actions SET status = ?, result = ? WHERE id = ?').run(status, JSON.stringify(result ?? null), id);
  }
  createTask(userId: number, conversationId: number | null, title: string, dueAt: string | null, actionId: number | null): number {
    return Number(this.db.prepare('INSERT INTO tasks (user_id, conversation_id, title, due_at, source_action_id) VALUES (?,?,?,?,?)').run(userId, conversationId, title, dueAt, actionId).lastInsertRowid);
  }
  listTasks(userId: number) {
    return (this.db.prepare('SELECT * FROM tasks WHERE user_id = ? ORDER BY done, COALESCE(due_at, created_at)').all(userId) as Row[]).map((r) => ({
      id: r.id, conversationId: r.conversation_id, title: r.title, dueAt: r.due_at, done: !!r.done, createdAt: r.created_at }));
  }
  setTaskDone(userId: number, id: number, done: boolean) {
    const r = this.db.prepare('UPDATE tasks SET done = ? WHERE id = ? AND user_id = ?').run(+done, id, userId);
    if (Number(r.changes) === 0) throw new HttpError(404, 'not_found', 'Task not found.');
  }
}
