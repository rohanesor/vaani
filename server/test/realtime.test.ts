import { describe, expect, it } from 'vitest';
import http from 'node:http';
import type { AddressInfo } from 'node:net';
import WebSocket from 'ws';
import request from 'supertest';
import { attachRealtime } from '../src/realtime.js';
import { register, setup } from './helpers.js';

function nextEvent(ws: WebSocket, name: string): Promise<any> {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error(`timeout waiting for ${name}`)), 2000);
    const on = (raw: WebSocket.RawData) => {
      const m = JSON.parse(String(raw));
      if (m.event === name) { clearTimeout(t); ws.off('message', on); resolve(m.data); }
    };
    ws.on('message', on);
  });
}

describe('realtime', () => {
  it('delivers messages, typing and presence only to members', async () => {
    const env = setup();
    const server = http.createServer(env.app);
    env.deps.hub = attachRealtime(server, env.store, env.cfg.jwtSecret); // same wiring as index.ts
    await new Promise<void>((r) => server.listen(0, r));
    const port = (server.address() as AddressInfo).port;
    const url = (t: string) => `ws://localhost:${port}/ws?token=${t}`;
    const api = request(server);
    const a = await register(api, 'Asha');
    const b = await register(api, 'Bala');
    const eve = await register(api, 'Eve');
    const c = (await api.post('/api/conversations').set(a.auth).send({ kind: 'direct', userId: b.id })).body;

    const bad = new WebSocket(url('nope'));
    await new Promise((r) => bad.on('error', r)); // 401 on upgrade

    const wsB = new WebSocket(url(b.token)); await nextEvent(wsB, 'hello');
    const wsE = new WebSocket(url(eve.token)); await nextEvent(wsE, 'hello');
    const presence = nextEvent(wsB, 'presence');
    const wsA = new WebSocket(url(a.token)); await nextEvent(wsA, 'hello');
    expect(await presence).toEqual({ userId: a.id, online: true });

    const eveEvents: string[] = [];
    wsE.on('message', (raw) => eveEvents.push(JSON.parse(String(raw)).event));

    const typing = nextEvent(wsB, 'typing');
    wsA.send(JSON.stringify({ event: 'typing', data: { conversationId: c.id } }));
    expect(await typing).toEqual({ conversationId: c.id, userId: a.id });
    wsE.send(JSON.stringify({ event: 'typing', data: { conversationId: c.id } })); // spoof attempt: ignored

    const incoming = nextEvent(wsB, 'message.new');
    const sent = await api.post(`/api/conversations/${c.id}/messages`).set(a.auth).send({ body: 'live hello' });
    expect((await incoming).id).toBe(sent.body.id);

    const read = nextEvent(wsA, 'read');
    await api.post(`/api/conversations/${c.id}/read`).set(b.auth).send({ messageId: sent.body.id });
    expect(await read).toEqual({ conversationId: c.id, userId: b.id, messageId: sent.body.id });

    const offline = nextEvent(wsB, 'presence');
    wsA.close();
    expect(await offline).toEqual({ userId: a.id, online: false });
    await new Promise((r) => setTimeout(r, 50));
    expect(eveEvents).toEqual([]);

    wsB.close(); wsE.close();
    env.deps.hub!.close();
    await new Promise((r) => server.close(r));
  });
});
