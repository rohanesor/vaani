/** Auth, profile, settings, people, conversations, messages, search, tasks. */
import crypto from 'node:crypto';
import { Router } from 'express';
import type { Deps } from '../app.js';
import { hashPassword, signToken, verifyPassword } from '../auth.js';
import { HttpError, SUPPORTED_LANGUAGES, reqInt, reqLanguage, reqString } from '../http-utils.js';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const SESSION_TTL_SECONDS = 60 * 60 * 24 * 14;

function issueSession(d: Deps, userId: number) {
  const id = crypto.randomUUID();
  d.store.createSession(id, userId, Math.floor(Date.now() / 1000) + SESSION_TTL_SECONDS);
  return signToken(userId, d.cfg.jwtSecret, SESSION_TTL_SECONDS, id);
}

/** Simple per-IP limiter for auth endpoints (brute-force protection). */
function authLimiter() {
  const hits = new Map<string, number[]>();
  return (ip: string) => {
    const now = Date.now();
    const arr = (hits.get(ip) ?? []).filter((t) => now - t < 10 * 60_000);
    arr.push(now); hits.set(ip, arr);
    if (arr.length > 30) throw new HttpError(429, 'rate_limited', 'Too many attempts. Wait a few minutes and try again.');
  };
}

export function publicRoutes(d: Deps) {
  const r = Router();
  const limit = authLimiter();

  r.post('/auth/register', (req, res) => {
    limit(req.ip ?? 'unknown');
    const email = reqString(req.body, 'email', { max: 200 }).toLowerCase();
    if (!EMAIL_RE.test(email)) throw new HttpError(400, 'invalid_request', 'Enter a valid email address.', { field: 'email' });
    const password = typeof req.body?.password === 'string' ? req.body.password : '';
    if (password.length < 8) throw new HttpError(400, 'invalid_request', 'Password must be at least 8 characters.', { field: 'password' });
    if (password.length > 200) throw new HttpError(400, 'invalid_request', 'Password is too long.', { field: 'password' });
    const displayName = reqString(req.body, 'displayName', { min: 1, max: 60 });
    const lang = req.body?.preferredLanguage ? reqLanguage(req.body.preferredLanguage, 'preferredLanguage') : 'en-IN';
    const user = d.store.createUser(email, hashPassword(password), displayName, lang);
    res.status(201).json({ token: issueSession(d, user.id), user, settings: d.store.getSettings(user.id) });
  });

  r.post('/auth/login', (req, res) => {
    limit(req.ip ?? 'unknown');
    const email = reqString(req.body, 'email', { max: 200 }).toLowerCase();
    const password = typeof req.body?.password === 'string' ? req.body.password : '';
    const auth = d.store.findAuthByEmail(email);
    if (!auth || !verifyPassword(password, auth.passwordHash)) throw new HttpError(401, 'invalid_credentials', 'Email or password is incorrect.');
    res.json({ token: issueSession(d, auth.id), user: d.store.getUser(auth.id, true), settings: d.store.getSettings(auth.id) });
  });

  r.get('/languages', (_req, res) => res.json(Object.entries(SUPPORTED_LANGUAGES).map(([code, label]) => ({ code, label }))));
  return r;
}

export function coreRoutes(d: Deps) {
  const r = Router();
  const uid = (req: { userId?: number }) => req.userId!;

  /* ---- session and profile ---- */
  r.post('/auth/logout', (req, res) => {
    const sessionId = req.sessionId;
    if (sessionId) d.store.revokeSession(sessionId, uid(req));
    res.status(204).end();
  });

  /* ---- me ---- */
  r.get('/me', (req, res) => res.json({ user: d.store.getUser(uid(req), true), settings: d.store.getSettings(uid(req)) }));
  r.patch('/me', (req, res) => {
    const b = req.body ?? {};
    const user = d.store.updateProfile(uid(req), {
      displayName: b.displayName !== undefined ? reqString(b, 'displayName', { min: 1, max: 60 }) : undefined,
      preferredLanguage: b.preferredLanguage !== undefined ? reqLanguage(b.preferredLanguage, 'preferredLanguage') : undefined,
      avatarHue: b.avatarHue !== undefined ? Math.abs(Math.trunc(Number(b.avatarHue))) % 360 : undefined,
    });
    res.json({ user });
  });
  r.get('/me/settings', (req, res) => res.json(d.store.getSettings(uid(req))));
  r.patch('/me/settings', (req, res) => {
    const allowed = ['aiEnabled', 'aiContextMessages', 'aiRedactContacts', 'voiceTranscription', 'theme', 'onboarded', 'notifyMessages'];
    const patch = Object.fromEntries(Object.entries(req.body ?? {}).filter(([k]) => allowed.includes(k)));
    res.json(d.store.updateSettings(uid(req), patch));
  });

  /* ---- people ---- */
  r.get('/users', (req, res) => {
    const q = typeof req.query.q === 'string' ? req.query.q.slice(0, 60) : '';
    res.json(d.store.searchUsers(uid(req), q).map((u) => ({ ...u, online: d.hub.isOnline(u.id) })));
  });
  r.get('/users/recent', (req, res) => res.json(d.store.recentContacts(uid(req)).map((u) => ({ ...u, online: d.hub.isOnline(u.id) }))));

  /* ---- conversations ---- */
  r.get('/conversations', (req, res) => res.json(d.store.listConversations(uid(req))));
  r.post('/conversations', (req, res) => {
    const b = req.body ?? {};
    let conv;
    if (b.kind === 'direct') conv = d.store.createDirect(uid(req), reqInt(b.userId, 'userId'));
    else if (b.kind === 'group') {
      const title = reqString(b, 'title', { min: 1, max: 80 });
      if (!Array.isArray(b.memberIds)) throw new HttpError(400, 'invalid_request', 'memberIds must be a list.');
      conv = d.store.createGroup(uid(req), title, b.memberIds.map((x: unknown) => reqInt(x, 'memberIds[]')));
    } else throw new HttpError(400, 'invalid_request', 'kind must be "direct" or "group".');
    for (const m of conv.members) if (m.id !== uid(req)) d.hub.toUser(m.id, 'conversation.updated', { conversationId: conv.id });
    res.status(201).json(conv);
  });
  r.get('/conversations/:id', (req, res) => res.json(d.store.getConversation(reqInt(req.params.id, 'id'), uid(req))));
  r.patch('/conversations/:id', (req, res) => {
    const id = reqInt(req.params.id, 'id');
    const conv = d.store.renameGroup(id, uid(req), reqString(req.body, 'title', { min: 1, max: 80 }));
    d.hub.toConversation(id, 'conversation.updated', { conversationId: id });
    res.json(conv);
  });
  r.post('/conversations/:id/members', (req, res) => {
    const id = reqInt(req.params.id, 'id');
    const ids = Array.isArray(req.body?.userIds) ? req.body.userIds.map((x: unknown) => reqInt(x, 'userIds[]')) : [];
    const conv = d.store.addMembers(id, uid(req), ids);
    d.hub.toConversation(id, 'conversation.updated', { conversationId: id });
    res.json(conv);
  });
  r.delete('/conversations/:id/members/me', (req, res) => {
    const id = reqInt(req.params.id, 'id');
    d.store.leaveGroup(id, uid(req));
    d.hub.toConversation(id, 'conversation.updated', { conversationId: id });
    res.status(204).end();
  });

  /* ---- messages ---- */
  r.get('/conversations/:id/messages', (req, res) => {
    const id = reqInt(req.params.id, 'id');
    const before = req.query.before ? reqInt(req.query.before, 'before') : undefined;
    const after = req.query.after ? reqInt(req.query.after, 'after') : undefined;
    if (before && after) throw new HttpError(400, 'invalid_request', 'Use either before or after, not both.');
    const limit = req.query.limit ? reqInt(req.query.limit, 'limit') : 50;
    res.json({ messages: d.store.listMessages(id, uid(req), { before, after, limit }), readState: d.store.readState(id) });
  });
  r.post('/conversations/:id/messages', (req, res) => {
    const id = reqInt(req.params.id, 'id');
    d.store.assertMember(id, uid(req));
    const body = reqString(req.body, 'body', { min: 1, max: 4000 });
    const clientId = reqString(req.body, 'clientId', { max: 64, optional: true }) ?? null;
    const replyTo = req.body?.replyTo ? reqInt(req.body.replyTo, 'replyTo') : null;
    const sourceLanguage = req.body?.sourceLanguage ? reqLanguage(req.body.sourceLanguage, 'sourceLanguage') : null;
    const msg = d.store.insertMessage({ conversationId: id, senderId: uid(req), kind: 'text', body, replyTo, clientId, aiAssisted: req.body?.aiAssisted === true, sourceLanguage });
    d.hub.toConversation(id, 'message.new', msg);
    res.status(201).json(msg);
  });
  r.post('/conversations/:id/read', (req, res) => {
    const id = reqInt(req.params.id, 'id');
    const messageId = reqInt(req.body?.messageId, 'messageId');
    d.store.markRead(id, uid(req), messageId);
    d.hub.toConversation(id, 'read', { conversationId: id, userId: uid(req), messageId }, uid(req));
    res.status(204).end();
  });
  r.patch('/messages/:id', (req, res) => {
    const msg = d.store.editMessage(reqInt(req.params.id, 'id'), uid(req), reqString(req.body, 'body', { min: 1, max: 4000 }));
    d.hub.toConversation(msg.conversationId, 'message.updated', msg);
    res.json(msg);
  });
  r.delete('/messages/:id', (req, res) => {
    const msg = d.store.deleteMessage(reqInt(req.params.id, 'id'), uid(req));
    d.hub.toConversation(msg.conversationId, 'message.deleted', msg);
    res.json(msg);
  });
  r.post('/messages/:id/reactions', (req, res) => {
    const msg = d.store.toggleReaction(reqInt(req.params.id, 'id'), uid(req), reqString(req.body, 'emoji', { max: 16 }));
    d.hub.toConversation(msg.conversationId, 'message.updated', msg);
    res.json(msg);
  });

  /* ---- search ---- */
  r.get('/search', (req, res) => {
    const q = typeof req.query.q === 'string' ? req.query.q.slice(0, 200) : '';
    const conversationId = req.query.conversationId ? reqInt(req.query.conversationId, 'conversationId') : undefined;
    if (conversationId) d.store.assertMember(conversationId, uid(req));
    const messages = q.trim() ? d.store.searchMessages(uid(req), q, conversationId) : [];
    const lower = q.trim().toLowerCase();
    const conversations = lower && !conversationId
      ? d.store.listConversations(uid(req)).filter((c) => c.title.toLowerCase().includes(lower) || c.members.some((m) => m.displayName.toLowerCase().includes(lower))).map((c) => ({ id: c.id, title: c.title, kind: c.kind }))
      : [];
    res.json({ query: q, messages, conversations });
  });

  /* ---- tasks (created by confirmed voice-to-action) ---- */
  r.get('/tasks', (req, res) => res.json(d.store.listTasks(uid(req))));
  r.patch('/tasks/:id', (req, res) => {
    d.store.setTaskDone(uid(req), reqInt(req.params.id, 'id'), req.body?.done === true);
    res.json(d.store.listTasks(uid(req)));
  });
  return r;
}
