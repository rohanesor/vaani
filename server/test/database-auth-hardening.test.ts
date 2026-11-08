import { DatabaseSync } from 'node:sqlite';
import { describe, expect, it } from 'vitest';
import { MIGRATIONS, migrate } from '../src/db.js';
import { signToken, verifyToken } from '../src/auth.js';
import { register, setup } from './helpers.js';

describe('database migrations v2/v3', () => {
  it('upgrades a v1 schema additively with user timestamps and revocable sessions', () => {
    const db = new DatabaseSync(':memory:');
    db.exec('PRAGMA foreign_keys = ON');
    db.exec(MIGRATIONS[0]);
    db.exec('PRAGMA user_version = 1');
    migrate(db);
    expect((db.prepare('PRAGMA user_version').get() as { user_version: number }).user_version).toBe(3);
    const columns = db.prepare('PRAGMA table_info(users)').all() as { name: string }[];
    expect(columns.map((c) => c.name)).toContain('updated_at');
    const messageColumns = db.prepare('PRAGMA table_info(messages)').all() as { name: string }[];
    expect(messageColumns.map((c) => c.name)).toEqual(expect.arrayContaining(['source_language', 'edited_at', 'deleted_at']));
    const sessions = db.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'auth_sessions'").get();
    expect(sessions).toBeTruthy();
  });

  it('enforces unique identity and session foreign keys', () => {
    const env = setup();
    const user = env.store.createUser('identity@example.com', 'hash', 'Identity', 'en-IN');
    expect(() => env.store.createUser('IDENTITY@example.com', 'hash', 'Duplicate', 'en-IN')).toThrow('An account with this email already exists.');
    expect(() => env.store.createSession('bad-session', user.id + 999, Math.floor(Date.now() / 1000) + 60)).toThrow();
  });
});

describe('server-side session revocation', () => {
  it('rejects unsigned session IDs, revokes logout tokens, and preserves profile authorization', async () => {
    const { api, cfg } = setup();
    const a = await register(api, 'Asha');
    expect((await api.get('/api/me').set(a.auth)).status).toBe(200);
    expect((await api.post('/api/auth/logout').set(a.auth)).status).toBe(204);
    expect((await api.get('/api/me').set(a.auth)).status).toBe(401);
    const legacy = signToken(a.id, cfg.jwtSecret);
    expect((await api.get('/api/me').set({ Authorization: `Bearer ${legacy}` })).status).toBe(401);
    const b = await register(api, 'Bala');
    const sessionId = verifyToken(b.token, cfg.jwtSecret)?.sid!;
    const expired = signToken(b.id, cfg.jwtSecret, -1, sessionId);
    expect((await api.get('/api/me').set({ Authorization: `Bearer ${expired}` })).status).toBe(401);
  });
});
