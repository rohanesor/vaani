import { describe, expect, it } from 'vitest';
import { hashPassword, signToken, verifyPassword, verifyToken } from '../src/auth.js';
import { register, setup, toneWav } from './helpers.js';

describe('auth primitives', () => {
  it('hashes and verifies passwords', () => {
    const h = hashPassword('s3cret-pass');
    expect(h).not.toContain('s3cret');
    expect(verifyPassword('s3cret-pass', h)).toBe(true);
    expect(verifyPassword('wrong', h)).toBe(false);
  });
  it('rejects tampered and expired tokens', () => {
    const secret = 'k'.repeat(40);
    const t = signToken(7, secret);
    expect(verifyToken(t, secret)?.sub).toBe(7);
    expect(verifyToken(t, 'z'.repeat(40))).toBeNull();
    const [h, , s] = t.split('.');
    const forged = Buffer.from(JSON.stringify({ sub: 1, iat: 0, exp: 9e9 })).toString('base64url');
    expect(verifyToken(`${h}.${forged}.${s}`, secret)).toBeNull();
    expect(verifyToken(signToken(7, secret, -10), secret)).toBeNull();
  });
});

describe('startup + accounts', () => {
  it('reports health without leaking secrets', async () => {
    const { api } = setup();
    const res = await api.get('/api/health');
    expect(res.status).toBe(200);
    expect(res.body.product).toBe('VAANI');
    expect(JSON.stringify(res.body)).not.toContain('xxxxxxxx');
  });
  it('validates registration and logs in', async () => {
    const { api } = setup();
    expect((await api.post('/api/auth/register').send({ email: 'bad', password: 'longenough', displayName: 'A' })).status).toBe(400);
    expect((await api.post('/api/auth/register').send({ email: 'a@b.co', password: 'short', displayName: 'A' })).body.error.details.field).toBe('password');
    await register(api, 'Asha', 'ta-IN');
    expect((await api.post('/api/auth/register').send({ email: 'asha@example.com', password: 'correct horse', displayName: 'X' })).status).toBe(409);
    expect((await api.post('/api/auth/login').send({ email: 'asha@example.com', password: 'nope' })).status).toBe(401);
    const ok = await api.post('/api/auth/login').send({ email: 'ASHA@example.com', password: 'correct horse' });
    expect(ok.status).toBe(200);
    expect(ok.body.user.preferredLanguage).toBe('ta-IN');
    expect(ok.body.user).not.toHaveProperty('passwordHash');
  });
  it('requires auth for protected routes', async () => {
    const { api } = setup();
    expect((await api.get('/api/conversations')).status).toBe(401);
    expect((await api.get('/api/conversations').set({ Authorization: 'Bearer junk' })).status).toBe(401);
  });
  it('validates settings', async () => {
    const { api } = setup();
    const a = await register(api, 'Asha');
    expect((await api.patch('/api/me/settings').set(a.auth).send({ aiContextMessages: 1000 })).status).toBe(400);
    const s = await api.patch('/api/me/settings').set(a.auth).send({ aiEnabled: false, theme: 'dark', onboarded: true });
    expect(s.body).toMatchObject({ aiEnabled: false, theme: 'dark', onboarded: true });
  });
});

describe('messaging', () => {
  it('creates direct conversations once and exchanges messages', async () => {
    const { api } = setup();
    const a = await register(api, 'Asha');
    const b = await register(api, 'Bala');
    const c1 = await api.post('/api/conversations').set(a.auth).send({ kind: 'direct', userId: b.id });
    const c2 = await api.post('/api/conversations').set(b.auth).send({ kind: 'direct', userId: a.id });
    expect(c1.body.id).toBe(c2.body.id);
    expect(c1.body.title).toBe('Bala');
    const m = await api.post(`/api/conversations/${c1.body.id}/messages`).set(a.auth).send({ body: 'வணக்கம்! How are you?', clientId: 'c-1' });
    expect(m.status).toBe(201);
    expect(m.body).toMatchObject({ senderId: a.id, kind: 'text', body: 'வணக்கம்! How are you?' });
    // idempotent retry with the same clientId
    const again = await api.post(`/api/conversations/${c1.body.id}/messages`).set(a.auth).send({ body: 'வணக்கம்! How are you?', clientId: 'c-1' });
    expect(again.body.id).toBe(m.body.id);
    const reply = await api.post(`/api/conversations/${c1.body.id}/messages`).set(b.auth).send({ body: 'Fine!', replyTo: m.body.id });
    expect(reply.body.replyTo.id).toBe(m.body.id);
    const list = await api.get(`/api/conversations/${c1.body.id}/messages`).set(a.auth);
    expect(list.body.messages.map((x: { body: string }) => x.body)).toEqual(['வணக்கம்! How are you?', 'Fine!']);
    const convs = await api.get('/api/conversations').set(a.auth);
    expect(convs.body[0].unread).toBe(1);
    await api.post(`/api/conversations/${c1.body.id}/read`).set(a.auth).send({ messageId: reply.body.id });
    expect((await api.get('/api/conversations').set(a.auth)).body[0].unread).toBe(0);
  });

  it('enforces membership on every conversation route', async () => {
    const { api } = setup();
    const a = await register(api, 'Asha');
    const b = await register(api, 'Bala');
    const eve = await register(api, 'Eve');
    const c = (await api.post('/api/conversations').set(a.auth).send({ kind: 'direct', userId: b.id })).body;
    const m = (await api.post(`/api/conversations/${c.id}/messages`).set(a.auth).send({ body: 'private launch plan' })).body;
    expect((await api.get(`/api/conversations/${c.id}`).set(eve.auth)).status).toBe(404);
    expect((await api.get(`/api/conversations/${c.id}/messages`).set(eve.auth)).status).toBe(404);
    expect((await api.post(`/api/conversations/${c.id}/messages`).set(eve.auth).send({ body: 'hi' })).status).toBe(404);
    expect((await api.post(`/api/messages/${m.id}/reactions`).set(eve.auth).send({ emoji: '👍' })).status).toBe(404);
    expect((await api.get('/api/search?q=launch').set(eve.auth)).body.messages).toHaveLength(0);
    expect((await api.get('/api/search?q=launch').set(a.auth)).body.messages).toHaveLength(1);
    expect((await api.post('/api/ai/summarize').set(eve.auth).send({ conversationId: c.id })).status).toBe(404);
    expect((await api.post('/api/ai/translate').set(eve.auth).send({ messageId: m.id, targetLanguage: 'hi-IN' })).status).toBe(404);
    expect((await api.get(`/api/messages/${m.id}/audio`).set(eve.auth)).status).toBe(404);
  });

  it('supports groups, reactions and multilingual search', async () => {
    const { api } = setup();
    const a = await register(api, 'Asha');
    const b = await register(api, 'Bala');
    const c = await register(api, 'Chitra');
    const g = await api.post('/api/conversations').set(a.auth).send({ kind: 'group', title: 'Backend squad', memberIds: [b.id, c.id] });
    expect(g.status).toBe(201);
    expect(g.body.members).toHaveLength(3);
    expect(g.body.myRole).toBe('owner');
    const m = (await api.post(`/api/conversations/${g.body.id}/messages`).set(b.auth).send({ body: 'हम Postgres इस्तेमाल करेंगे' })).body;
    const r1 = await api.post(`/api/messages/${m.id}/reactions`).set(c.auth).send({ emoji: '👍' });
    expect(r1.body.reactions).toEqual([{ emoji: '👍', userIds: [c.id] }]);
    const r2 = await api.post(`/api/messages/${m.id}/reactions`).set(c.auth).send({ emoji: '👍' });
    expect(r2.body.reactions).toEqual([]);
    const s = await api.get('/api/search?q=postgres').set(a.auth);
    expect(s.body.messages[0].message.id).toBe(m.id);
    const s2 = await api.get(`/api/search?q=${encodeURIComponent('इस्तेमाल')}`).set(a.auth);
    expect(s2.body.messages).toHaveLength(1);
    expect((await api.post(`/api/conversations/${g.body.id}/members`).set(b.auth).send({ userIds: [a.id] })).status).toBe(403);
  });
});

describe('voice notes', () => {
  it('stores audio, computes a real waveform, and transcribes in the background', async () => {
    const { api, store } = setup();
    const a = await register(api, 'Asha');
    const b = await register(api, 'Bala');
    const c = (await api.post('/api/conversations').set(a.auth).send({ kind: 'direct', userId: b.id })).body;
    const wav = toneWav(2);
    const res = await api.post(`/api/conversations/${c.id}/voice?clientId=v1`).set(a.auth).set('Content-Type', 'audio/wav').send(wav);
    expect(res.status).toBe(201);
    expect(res.body.kind).toBe('voice');
    expect(res.body.voice.durationMs).toBe(2000);
    expect(res.body.voice.waveform).toHaveLength(48);
    expect(res.body.voice.waveform[0]).toBeLessThan(res.body.voice.waveform[47]); // ramped tone → rising envelope
    expect(res.body.voice.transcriptStatus).toBe('pending');
    await new Promise((r) => setTimeout(r, 20));
    const m = store.messagesByIds([res.body.id])[0];
    expect(m.voice?.transcriptStatus).toBe('done');
    expect(m.body).toContain('project');
    const audio = await api.get(`/api/messages/${res.body.id}/audio`).set(b.auth);
    expect(audio.headers['content-type']).toBe('audio/wav');
    expect(audio.body.length).toBe(wav.length);
  });
  it('rejects non-WAV and too-short audio', async () => {
    const { api } = setup();
    const a = await register(api, 'Asha');
    const b = await register(api, 'Bala');
    const c = (await api.post('/api/conversations').set(a.auth).send({ kind: 'direct', userId: b.id })).body;
    expect((await api.post(`/api/conversations/${c.id}/voice`).set(a.auth).set('Content-Type', 'audio/wav').send(Buffer.from('not a wav file at all, definitely not'))).status).toBe(400);
    expect((await api.post(`/api/conversations/${c.id}/voice`).set(a.auth).set('Content-Type', 'audio/wav').send(toneWav(0.1))).status).toBe(400);
  });
  it('marks transcription unavailable when speech is not configured', async () => {
    const { api, stt } = setup();
    stt.configured = false;
    const a = await register(api, 'Asha');
    const b = await register(api, 'Bala');
    const c = (await api.post('/api/conversations').set(a.auth).send({ kind: 'direct', userId: b.id })).body;
    const res = await api.post(`/api/conversations/${c.id}/voice`).set(a.auth).set('Content-Type', 'audio/wav').send(toneWav(1));
    expect(res.body.voice.transcriptStatus).toBe('unavailable');
    expect((await api.post(`/api/messages/${res.body.id}/transcribe`).set(a.auth).send({})).status).toBe(503);
  });
});
