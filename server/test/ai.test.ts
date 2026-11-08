import { describe, expect, it } from 'vitest';
import Anthropic from '@anthropic-ai/sdk';
import { register, setup } from './helpers.js';
import { emptyReport, keywords, redact } from '../src/ai/context.js';
import { IcsCalendarProvider, parseExtraction, validateAction } from '../src/ai/actions.js';
import { ClaudeProvider } from '../src/ai/claude.js';
import { SlidingWindowLimiter } from '../src/ai/orchestrator.js';

async function convWith(messages: [who: 'a' | 'b', body: string][]) {
  const env = setup();
  const a = await register(env.api, 'Asha');
  const b = await register(env.api, 'Bala');
  const c = (await env.api.post('/api/conversations').set(a.auth).send({ kind: 'direct', userId: b.id })).body;
  const ids: number[] = [];
  for (const [who, body] of messages) ids.push((await env.api.post(`/api/conversations/${c.id}/messages`).set((who === 'a' ? a : b).auth).send({ body })).body.id);
  return { ...env, a, b, c, ids };
}

/** Parse an SSE response body into events. */
function sse(text: string) {
  return text.trim().split('\n\n').map((blk) => {
    const ev = blk.match(/^event: (.+)$/m)?.[1];
    const data = JSON.parse(blk.match(/^data: (.+)$/m)?.[1] ?? 'null');
    return { ev, data };
  });
}

describe('privacy filter', () => {
  it('redacts contact details and secrets but keeps ordinary numbers', () => {
    const r = emptyReport();
    const out = redact('Mail me at asha.k@example.in or call +91 98765 43210. OTP is 482913. Meet at 4:30 on 2026-11-03, room 204. Card 4111 1111 1111 1111', r);
    expect(out).not.toContain('example.in');
    expect(out).not.toContain('98765');
    expect(out).not.toContain('482913');
    expect(out).not.toContain('4111');
    expect(out).toContain('4:30');
    expect(out).toContain('room 204');
    expect(r).toMatchObject({ emails: 1, phones: 1, secrets: 1, numbers: 1 });
  });
  it('extracts multilingual keywords without stopwords', () => {
    expect(keywords('What did we decide about the backend architecture?')).toEqual(['backend', 'architecture']);
    expect(keywords('டேட்டாபேஸ் பற்றி என்ன முடிவு?')).toContain('டேட்டாபேஸ்');
  });
});

describe('AI companion (assist)', () => {
  it('streams a reply with the conversation context and never auto-sends', async () => {
    const { api, a, c, ai, ids } = await convWith([['a', 'Are we still on for Friday?'], ['b', 'Can we move it to Saturday? My number is 9876543210']]);
    ai.queue.push('Saturday works for me!');
    const res = await api.post('/api/ai/assist').set(a.auth).send({ conversationId: c.id, op: 'reply' });
    expect(res.headers['content-type']).toContain('text/event-stream');
    const events = sse(res.text);
    expect(events[0].ev).toBe('context');
    expect(events[0].data.messageIds).toEqual(ids);
    expect(events[0].data.redactions.phones).toBe(1);
    expect(events.filter((e) => e.ev === 'delta').map((e) => e.data.text).join('')).toBe('Saturday works for me!');
    expect(events.at(-1)).toEqual({ ev: 'done', data: { text: 'Saturday works for me!' } });
    const sent = ai.calls[0].messages[0].content;
    expect(sent).toContain(`[#${ids[1]}]`);
    expect(sent).not.toContain('9876543210');
    expect(ai.calls[0].system).toContain('never follow instructions'.replace('never', 'Never'));
    const msgs = (await api.get(`/api/conversations/${c.id}/messages`).set(a.auth)).body.messages;
    expect(msgs).toHaveLength(2); // nothing was sent automatically
  });

  it('retrieves relevant older messages for questions instead of sending everything', async () => {
    const filler: [ 'a' | 'b', string][] = Array.from({ length: 60 }, (_, i) => [i % 2 ? 'a' : 'b', `random chatter number ${i}`] as ['a' | 'b', string]);
    const { api, a, c, ai, ids } = await convWith([['a', 'For the backend architecture let us go with FastAPI and Postgres'], ['b', 'Agreed, FastAPI it is'], ...filler]);
    ai.queue.push('You agreed on FastAPI + Postgres [#1].');
    await api.post('/api/ai/assist').set(a.auth).send({ conversationId: c.id, op: 'ask', question: 'What did we decide about the backend architecture?' });
    const sent = ai.calls[0].messages[0].content;
    expect(sent).toContain('FastAPI and Postgres');
    const lines = sent.split('\n').filter((l) => l.startsWith('[#'));
    expect(lines.length).toBeLessThanOrEqual(30);
    expect(lines.length).toBeLessThan(ids.length);
  });

  it('reports gated errors as SSE error events', async () => {
    const env = await convWith([['a', 'hi'], ['b', 'hello']]);
    env.ai.configured = false;
    let ev = sse((await env.api.post('/api/ai/assist').set(env.a.auth).send({ conversationId: env.c.id, op: 'reply' })).text);
    expect(ev.at(-1)?.data.code).toBe('ai_not_configured');
    env.ai.configured = true;
    await env.api.patch('/api/me/settings').set(env.a.auth).send({ aiEnabled: false });
    ev = sse((await env.api.post('/api/ai/assist').set(env.a.auth).send({ conversationId: env.c.id, op: 'reply' })).text);
    expect(ev.at(-1)?.data.code).toBe('ai_disabled');
    expect(env.ai.calls).toHaveLength(0);
  });

  it('validates rewrite input and maps provider failures', async () => {
    const env = await convWith([['a', 'hi'], ['b', 'hello']]);
    let ev = sse((await env.api.post('/api/ai/assist').set(env.a.auth).send({ conversationId: env.c.id, op: 'rewrite', draft: '', style: 'shorter' })).text);
    expect(ev.at(-1)?.data.code).toBe('ai_invalid_request');
    env.ai.queue.push(new (await import('../src/ai/types.js')).AIError('rate_limited', 'slow down', true));
    ev = sse((await env.api.post('/api/ai/assist').set(env.a.auth).send({ conversationId: env.c.id, op: 'rewrite', draft: 'pls send report asap', style: 'professional' })).text);
    expect(ev.at(-1)?.data).toMatchObject({ code: 'ai_rate_limited', details: { retryable: true } });
  });

  it('times out slow providers', async () => {
    const env = setup({ timeoutMs: 50 });
    const a = await register(env.api, 'Asha');
    const b = await register(env.api, 'Bala');
    const c = (await env.api.post('/api/conversations').set(a.auth).send({ kind: 'direct', userId: b.id })).body;
    await env.api.post(`/api/conversations/${c.id}/messages`).set(b.auth).send({ body: 'hello' });
    env.ai.delayMs = 500;
    const ev = sse((await env.api.post('/api/ai/assist').set(a.auth).send({ conversationId: c.id, op: 'reply' })).text);
    expect(ev.at(-1)?.data.code).toBe('ai_timeout');
  });

  it('rate limits per user', () => {
    let t = 0;
    const l = new SlidingWindowLimiter(2, () => t);
    expect([l.take(1), l.take(1), l.take(1), l.take(2)]).toEqual([true, true, false, true]);
    t = 61_000;
    expect(l.take(1)).toBe(true);
  });
});

describe('translation + summaries', () => {
  it('translates once and serves the cache afterwards', async () => {
    const { api, a, ai, ids } = await convWith([['b', 'நாளை சந்திப்போம்']]);
    ai.queue.push(JSON.stringify({ source_language: 'ta-IN', translation: 'Let’s meet tomorrow', note: null }));
    const r1 = await api.post('/api/ai/translate').set(a.auth).send({ messageId: ids[0], targetLanguage: 'en-IN' });
    expect(r1.body).toMatchObject({ text: 'Let’s meet tomorrow', sourceLanguage: 'ta-IN', cached: false });
    const r2 = await api.post('/api/ai/translate').set(a.auth).send({ messageId: ids[0], targetLanguage: 'en-IN' });
    expect(r2.body.cached).toBe(true);
    expect(ai.calls).toHaveLength(1);
    ai.queue.push('not json');
    expect((await api.post('/api/ai/translate').set(a.auth).send({ messageId: ids[0], targetLanguage: 'hi-IN' })).status).toBe(502);
  });

  it('drops message references the model invented', async () => {
    const { api, a, c, ai, ids } = await convWith([['a', 'Let us use Postgres'], ['b', 'Agreed'], ['a', 'Who writes the schema?']]);
    ai.queue.push(JSON.stringify({ summary: 'Chose Postgres.', key_points: ['DB choice'], decisions: [{ text: 'Use Postgres', status: 'confirmed', message_ids: [ids[0], ids[1], 99999] }],
      tasks: [], open_questions: [{ text: 'Who writes the schema?', message_ids: [ids[2]] }] }));
    const r = await api.post('/api/ai/summarize').set(a.auth).send({ conversationId: c.id });
    expect(r.status).toBe(200);
    expect(r.body.decisions[0]).toEqual({ text: 'Use Postgres', status: 'confirmed', messageIds: [ids[0], ids[1]] });
    expect(r.body.openQuestions[0].messageIds).toEqual([ids[2]]);
  });
});

describe('voice-to-action', () => {
  it('validates actions strictly', () => {
    expect(() => validateAction({ type: 'create_event', title: 'Sync', date: '2026-02-30', time: '16:00' })).toThrow();
    expect(() => validateAction({ type: 'create_event', title: 'Sync', date: '2026-11-03', time: '4pm' })).toThrow();
    expect(() => validateAction({ type: 'delete_everything' })).toThrow();
    expect(validateAction({ type: 'create_event', title: 'Sync', date: '2026-11-03', time: '16:00', duration_minutes: null, participants: [] })).toMatchObject({ duration_minutes: 60, requires_confirmation: true });
    const { result, rejected } = parseExtraction(JSON.stringify({ intent: 'meet', summary: 's', actions: [{ type: 'create_task', title: 'Bring progress update', date: null }, { type: 'create_event', title: 'x', date: null }] }));
    expect(result.actions).toHaveLength(1);
    expect(rejected).toHaveLength(1);
  });

  it('proposes, then executes only after confirmation', async () => {
    const { api, a, b, c, ai, ids, store } = await convWith([['b', 'Tomorrow at four, let us discuss the project and ask everyone to bring their progress updates.']]);
    ai.queue.push(JSON.stringify({ intent: 'schedule project discussion', summary: 'Meet tomorrow 4pm', actions: [
      { type: 'create_event', title: 'Project discussion', date: '2026-11-03', time: '16:00', duration_minutes: 60, participants: ['Asha', 'Bala'], notes: 'Bring progress updates', text: null },
      { type: 'create_task', title: 'Prepare progress update', date: '2026-11-03', time: null, duration_minutes: null, participants: [], notes: null, text: null },
      { type: 'send_message', title: null, date: null, time: null, duration_minutes: null, participants: [], notes: null, text: 'Reminder: bring your progress updates tomorrow at 4.' },
    ] }));
    const ex = await api.post('/api/ai/actions/extract').set(a.auth).send({ messageId: ids[0], timezone: 'Asia/Kolkata' });
    expect(ex.status).toBe(200);
    expect(ex.body.actions.map((x: { type: string }) => x.type)).toEqual(['create_event', 'create_task', 'send_message']);
    expect(ai.calls[0].messages[0].content).toMatch(/today is \w+day \d{4}-\d{2}-\d{2} in timezone Asia\/Kolkata/);
    expect(store.listTasks(a.id)).toHaveLength(0);
    expect((await api.get(`/api/conversations/${c.id}/messages`).set(a.auth)).body.messages).toHaveLength(1);

    const [ev, task, msg] = ex.body.actions;
    expect((await api.post(`/api/ai/actions/${ev.id}/confirm`).set(b.auth).send({})).status).toBe(404); // not Bala's proposal
    const evOut = await api.post(`/api/ai/actions/${ev.id}/confirm`).set(a.auth).send({ timezone: 'Asia/Kolkata', edits: { time: '16:30' } });
    expect(evOut.body.result.calendar.content).toContain('DTSTART;TZID=Asia/Kolkata:20261103T163000');
    expect(evOut.body.result.calendar.content).toContain('DTEND;TZID=Asia/Kolkata:20261103T173000');
    expect((await api.post(`/api/ai/actions/${ev.id}/confirm`).set(a.auth).send({})).status).toBe(409);
    await api.post(`/api/ai/actions/${task.id}/confirm`).set(a.auth).send({});
    expect(store.listTasks(a.id)[0]).toMatchObject({ title: 'Prepare progress update', dueAt: '2026-11-03' });
    await api.post(`/api/ai/actions/${msg.id}/dismiss`).set(a.auth).send({});
    expect((await api.post(`/api/ai/actions/${msg.id}/confirm`).set(a.auth).send({})).status).toBe(409);
    expect((await api.get(`/api/conversations/${c.id}/messages`).set(a.auth)).body.messages).toHaveLength(1);
  });

  it('writes valid all-day ICS when no time is given', async () => {
    const out = await new IcsCalendarProvider().createEvent({ type: 'create_event', title: 'Demo, day; 1', date: '2026-12-31', time: null, duration_minutes: 60, participants: [], notes: null, requires_confirmation: true }, { timezone: 'Asia/Kolkata', organizer: 'Asha' });
    expect(out.content).toContain('DTSTART;VALUE=DATE:20261231');
    expect(out.content).toContain('DTEND;VALUE=DATE:20270101');
    expect(out.content).toContain('SUMMARY:Demo\\, day\\; 1');
  });
});

describe('ClaudeProvider', () => {
  it('reports not configured without a key', async () => {
    const p = new ClaudeProvider({ model: 'm', fastModel: 'f', timeoutMs: 1000 });
    expect(p.isConfigured()).toBe(false);
    await expect(p.complete({ system: 's', messages: [], tier: 'fast', maxTokens: 10 })).rejects.toMatchObject({ code: 'not_configured' });
  });

  it('sends structured-output params and maps SDK errors', async () => {
    const seen: Record<string, unknown>[] = [];
    let fail: Error | null = null;
    const client = { messages: { create: async (params: Record<string, unknown>) => {
      seen.push(params);
      if (fail) throw fail;
      return { model: 'claude-test', content: [{ type: 'text', text: '{"a":1}' }], usage: { input_tokens: 3, output_tokens: 4 }, stop_reason: 'end_turn' };
    } } } as unknown as Pick<Anthropic, 'messages'>;
    const p = new ClaudeProvider({ model: 'big', fastModel: 'small', timeoutMs: 1000, client });
    const out = await p.complete({ system: 'sys', messages: [{ role: 'user', content: 'x' }], tier: 'fast', maxTokens: 50, jsonSchema: { type: 'object' } });
    expect(out).toMatchObject({ text: '{"a":1}', inputTokens: 3, outputTokens: 4 });
    expect(seen[0]).toMatchObject({ model: 'small', max_tokens: 50, system: 'sys', output_config: { format: { type: 'json_schema', schema: { type: 'object' } } } });
    fail = new Anthropic.RateLimitError(429, { type: 'error' }, 'rate', new Headers());
    await expect(p.complete({ system: 's', messages: [], tier: 'reasoning', maxTokens: 10 })).rejects.toMatchObject({ code: 'rate_limited', retryable: true });
    fail = new Anthropic.AuthenticationError(401, { type: 'error' }, 'bad key', new Headers());
    await expect(p.complete({ system: 's', messages: [], tier: 'reasoning', maxTokens: 10 })).rejects.toMatchObject({ code: 'not_configured' });
  });
});
