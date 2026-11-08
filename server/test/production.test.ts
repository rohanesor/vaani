/**
 * Production hardening tests: health, readiness, config validation,
 * rate limiting, and budget enforcement.
 */
import { describe, it, expect, beforeEach } from 'vitest';
import request from 'supertest';
import { loadConfig, validateProductionConfig } from '../src/config.js';
import { openDb } from '../src/db.js';
import { Store } from '../src/store.js';
import { SlidingWindowLimiter } from '../src/ai/orchestrator.js';
import { setup, register } from './helpers.js';

describe('health and readiness', () => {
  it('GET /api/health returns liveness status', async () => {
    const { api } = setup();
    const res = await api.get('/api/health');
    expect(res.status).toBe(200);
    expect(res.body.ok).toBe(true);
    expect(res.body.product).toBe('VAANI');
    expect(res.body.claude).toBeDefined();
    expect(res.body.gnani).toBeDefined();
  });

  it('GET /api/ready returns database readiness', async () => {
    const { api } = setup();
    const res = await api.get('/api/ready');
    expect(res.status).toBe(200);
    expect(res.body.ready).toBe(true);
    expect(res.body.database).toBe('ok');
  });

  it('health response does not expose secrets', async () => {
    const { api } = setup();
    const res = await api.get('/api/health');
    const body = JSON.stringify(res.body);
    expect(body).not.toContain('apiKey');
    expect(body).not.toContain('secret');
    expect(body).not.toContain('password');
  });
});

describe('config validation', () => {
  it('accepts valid development config', () => {
    const cfg = loadConfig({ dbPath: ':memory:', jwtSecret: 'x'.repeat(40) });
    // In non-production, ephemeral JWT is allowed
    const errors = validateProductionConfig(cfg);
    // Filter out non-blocking warnings
    const blocking = errors.filter(e => !e.startsWith('WARNING'));
    expect(blocking).toEqual([]);
  });

  it('validates CORS origin format', () => {
    const cfg = loadConfig({ dbPath: ':memory:', jwtSecret: 'x'.repeat(40), corsOrigin: 'invalid-origin' });
    const errors = validateProductionConfig(cfg);
    expect(errors.some(e => e.includes('CORS'))).toBe(true);
  });

  it('accepts valid HTTP origin in dev', () => {
    const cfg = loadConfig({ dbPath: ':memory:', jwtSecret: 'x'.repeat(40), corsOrigin: 'http://localhost:5173' });
    const errors = validateProductionConfig(cfg);
    const blocking = errors.filter(e => !e.startsWith('WARNING'));
    expect(blocking).toEqual([]);
  });
});

describe('rate limiting', () => {
  it('SlidingWindowLimiter enforces per-minute limit', () => {
    let time = 0;
    const limiter = new SlidingWindowLimiter(3, () => time);

    // First 3 requests succeed
    expect(limiter.take(1)).toBe(true);
    expect(limiter.take(1)).toBe(true);
    expect(limiter.take(1)).toBe(true);

    // 4th request is blocked
    expect(limiter.take(1)).toBe(false);

    // Different user is not affected
    expect(limiter.take(2)).toBe(true);

    // After 60 seconds, window slides and new requests succeed
    time = 60_001;
    expect(limiter.take(1)).toBe(true);
  });

  it('AI endpoint returns 429 when rate limited', async () => {
    const { api, ai } = setup({ aiRpm: 1 });
    const alice = await register(api, 'Alice');
    const bob = await register(api, 'Bob');
    ai.queue.push('response 1', 'response 2');

    // Create a direct conversation
    const conv = await api.post('/api/conversations').set(alice.auth).send({ kind: 'direct', userId: bob.id });
    expect(conv.status).toBe(201);
    const conversationId = conv.body.id;

    // Bob sends a message that Alice can reply to
    await api.post(`/api/conversations/${conversationId}/messages`).set(bob.auth).send({ body: 'Hello Alice!' });

    // First request succeeds (streaming response returns 200 for SSE)
    const res1 = await api.post('/api/ai/assist').set(alice.auth)
      .send({ conversationId, op: 'reply' });
    expect(res1.status).toBe(200);
    expect(res1.text).toContain('event: done');

    // Second request is rate limited - check SSE error event
    const res2 = await api.post('/api/ai/assist').set(alice.auth)
      .send({ conversationId, op: 'reply' });
    expect(res2.status).toBe(200); // SSE always returns 200
    expect(res2.text).toContain('event: error');
    expect(res2.text).toContain('rate_limited');
  });
});

describe('daily token budget', () => {
  it('enforces daily token budget per user', async () => {
    const { api, ai, store } = setup({ budget: 100 });
    const alice = await register(api, 'Alice');
    const bob = await register(api, 'Bob');

    // Create a direct conversation
    const conv = await api.post('/api/conversations').set(alice.auth).send({ kind: 'direct', userId: bob.id });
    expect(conv.status).toBe(201);
    const conversationId = conv.body.id;

    // Bob sends a message that Alice can reply to
    await api.post(`/api/conversations/${conversationId}/messages`).set(bob.auth).send({ body: 'Hello Alice!' });

    // Manually record usage to exhaust budget (100 tokens)
    store.recordUsage(alice.id, 'assist', 'fake', 50, 51, 'success');

    ai.queue.push('response');

    // Request should be rejected due to budget (SSE error event)
    const res = await api.post('/api/ai/assist').set(alice.auth)
      .send({ conversationId, op: 'reply' });
    expect(res.status).toBe(200); // SSE always returns 200
    expect(res.text).toContain('event: error');
    expect(res.text).toContain('budget_exceeded');
  });

  it('token budget is per-user', async () => {
    const { api, ai, store } = setup({ budget: 100 });
    const alice = await register(api, 'Alice');
    const bob = await register(api, 'Bob');

    // Create a direct conversation
    const conv = await api.post('/api/conversations').set(alice.auth).send({ kind: 'direct', userId: bob.id });
    expect(conv.status).toBe(201);
    const conversationId = conv.body.id;

    // Alice sends a message that Bob can reply to
    await api.post(`/api/conversations/${conversationId}/messages`).set(alice.auth).send({ body: 'Hello Bob!' });

    // Exhaust Alice's budget
    store.recordUsage(alice.id, 'assist', 'fake', 50, 51, 'success');

    ai.queue.push('bob response');

    // Bob should still be able to make requests (not affected by Alice's budget)
    const res = await api.post('/api/ai/assist').set(bob.auth)
      .send({ conversationId, op: 'reply' });
    expect(res.status).toBe(200);
    // Should get the actual response, not an error
    expect(res.text).toContain('event: done');
    expect(res.text).not.toContain('event: error');
  });
});

describe('store operations', () => {
  it('isReady returns true for healthy database', () => {
    const store = new Store(openDb(':memory:'));
    expect(store.isReady()).toBe(true);
  });

  it('close method exists', () => {
    const store = new Store(openDb(':memory:'));
    expect(typeof store.close).toBe('function');
    // Note: calling close would make subsequent operations fail
  });
});

describe('request correlation', () => {
  it('responses include X-Request-Id header for API routes', async () => {
    const { api } = setup();
    const alice = await register(api, 'Alice');
    // Test with authenticated endpoint since health comes before correlation middleware
    const res = await api.get('/api/me').set(alice.auth);
    expect(res.headers['x-request-id']).toBeDefined();
    expect(res.headers['x-request-id']).toMatch(/^[0-9a-f-]{36}$/);
  });
});
