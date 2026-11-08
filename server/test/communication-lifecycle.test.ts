import { describe, expect, it } from 'vitest';
import { register, setup } from './helpers.js';

describe('persistent message lifecycle', () => {
  it('paginates stably, supports after recovery, and keeps idempotent submission canonical', async () => {
    const { api } = setup();
    const a = await register(api, 'Asha'); const b = await register(api, 'Bala');
    const c = (await api.post('/api/conversations').set(a.auth).send({ kind: 'direct', userId: b.id })).body;
    const ids: number[] = [];
    for (let i = 0; i < 4; i++) ids.push((await api.post(`/api/conversations/${c.id}/messages`).set(a.auth).send({ body: `m${i}`, clientId: `retry-${i}` })).body.id);
    const retry = await api.post(`/api/conversations/${c.id}/messages`).set(a.auth).send({ body: 'm2', clientId: 'retry-2' });
    expect(retry.body.id).toBe(ids[2]);
    const newest = await api.get(`/api/conversations/${c.id}/messages?limit=2`).set(b.auth);
    expect(newest.body.messages.map((m: { id: number }) => m.id)).toEqual(ids.slice(2));
    const older = await api.get(`/api/conversations/${c.id}/messages?before=${ids[2]}&limit=2`).set(b.auth);
    expect(older.body.messages.map((m: { id: number }) => m.id)).toEqual(ids.slice(0, 2));
    const recovered = await api.get(`/api/conversations/${c.id}/messages?after=${ids[1]}&limit=10`).set(b.auth);
    expect(recovered.body.messages.map((m: { id: number }) => m.id)).toEqual(ids.slice(2));
  });

  it('allows only the sender to edit or soft-delete a message', async () => {
    const { api } = setup();
    const a = await register(api, 'Asha'); const b = await register(api, 'Bala');
    const c = (await api.post('/api/conversations').set(a.auth).send({ kind: 'direct', userId: b.id })).body;
    const m = (await api.post(`/api/conversations/${c.id}/messages`).set(a.auth).send({ body: 'original', clientId: 'edit-1', sourceLanguage: 'ta-IN' })).body;
    expect((await api.patch(`/api/messages/${m.id}`).set(b.auth).send({ body: 'intrusion' })).status).toBe(403);
    const edited = await api.patch(`/api/messages/${m.id}`).set(a.auth).send({ body: 'edited' });
    expect(edited.body).toMatchObject({ body: 'edited', sourceLanguage: 'ta-IN' });
    expect(edited.body.editedAt).toBeTruthy();
    const deleted = await api.delete(`/api/messages/${m.id}`).set(a.auth);
    expect(deleted.status).toBe(200);
    expect(deleted.body.body).toBeNull();
    expect(deleted.body.deletedAt).toBeTruthy();
    expect((await api.patch(`/api/messages/${m.id}`).set(a.auth).send({ body: 'again' })).status).toBe(403);
    expect((await api.delete(`/api/messages/${m.id}`).set(b.auth)).status).toBe(403);
  });
});
