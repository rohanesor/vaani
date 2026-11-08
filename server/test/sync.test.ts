/**
 * Sync endpoint tests: POST /api/sync
 * Covers the M7 contract for Android cloud sync.
 */
import { describe, it, expect } from 'vitest';
import { setup, register } from './helpers.js';

describe('sync endpoint', () => {
  describe('protocol version', () => {
    it('requires X-Vaani-Sync-Version header', async () => {
      const { api } = setup();
      const alice = await register(api, 'Alice');

      const res = await api.post('/api/sync')
        .set(alice.auth)
        .send({ cursor: null, upload: [], readCursors: [], edits: [], deletions: [] });

      expect(res.status).toBe(400);
      expect(res.body.code).toBe('sync_version_required');
    });

    it('rejects unsupported sync version', async () => {
      const { api } = setup();
      const alice = await register(api, 'Alice');

      const res = await api.post('/api/sync')
        .set(alice.auth)
        .set('X-Vaani-Sync-Version', '99')
        .send({ cursor: null, upload: [], readCursors: [], edits: [], deletions: [] });

      expect(res.status).toBe(400);
      expect(res.body.code).toBe('unsupported_sync_version');
      expect(res.body.supportedVersions).toContain(1);
    });

    it('accepts version 1', async () => {
      const { api } = setup();
      const alice = await register(api, 'Alice');

      const res = await api.post('/api/sync')
        .set(alice.auth)
        .set('X-Vaani-Sync-Version', '1')
        .send({ cursor: null, upload: [], readCursors: [], edits: [], deletions: [] });

      expect(res.status).toBe(200);
      expect(res.body.cursor).toBeDefined();
      expect(res.body.serverTime).toBeDefined();
    });
  });

  describe('message upload', () => {
    it('uploads a message and returns canonical ID', async () => {
      const { api } = setup();
      const alice = await register(api, 'Alice');
      const bob = await register(api, 'Bob');

      // Create a conversation
      const conv = await api.post('/api/conversations')
        .set(alice.auth)
        .send({ kind: 'direct', userId: bob.id });
      expect(conv.status).toBe(201);

      // Upload a message via sync
      const res = await api.post('/api/sync')
        .set(alice.auth)
        .set('X-Vaani-Sync-Version', '1')
        .send({
          cursor: null,
          upload: [{
            clientId: 'test-uuid-123',
            conversationCloudId: conv.body.id,
            body: 'Hello from Android!',
            kind: 'text',
            clientTimestamp: new Date().toISOString(),
          }],
          readCursors: [],
          edits: [],
          deletions: [],
        });

      expect(res.status).toBe(200);
      expect(res.body.uploadResults).toHaveLength(1);
      expect(res.body.uploadResults[0].clientId).toBe('test-uuid-123');
      expect(res.body.uploadResults[0].status).toBe('created');
      expect(res.body.uploadResults[0].cloudId).toBeGreaterThan(0);
    });

    it('returns existing message on duplicate upload (idempotent)', async () => {
      const { api } = setup();
      const alice = await register(api, 'Alice');
      const bob = await register(api, 'Bob');

      const conv = await api.post('/api/conversations')
        .set(alice.auth)
        .send({ kind: 'direct', userId: bob.id });

      // First upload
      const res1 = await api.post('/api/sync')
        .set(alice.auth)
        .set('X-Vaani-Sync-Version', '1')
        .send({
          cursor: null,
          upload: [{
            clientId: 'idempotent-uuid',
            conversationCloudId: conv.body.id,
            body: 'Hello!',
            kind: 'text',
            clientTimestamp: new Date().toISOString(),
          }],
          readCursors: [],
          edits: [],
          deletions: [],
        });

      expect(res1.body.uploadResults[0].status).toBe('created');
      const firstCloudId = res1.body.uploadResults[0].cloudId;

      // Second upload with same clientId
      const res2 = await api.post('/api/sync')
        .set(alice.auth)
        .set('X-Vaani-Sync-Version', '1')
        .send({
          cursor: null,
          upload: [{
            clientId: 'idempotent-uuid',
            conversationCloudId: conv.body.id,
            body: 'Hello!',
            kind: 'text',
            clientTimestamp: new Date().toISOString(),
          }],
          readCursors: [],
          edits: [],
          deletions: [],
        });

      // Should return same cloudId, not create duplicate
      expect(res2.body.uploadResults[0].status).toBe('exists');
      expect(res2.body.uploadResults[0].cloudId).toBe(firstCloudId);
    });

    it('rejects upload to non-member conversation', async () => {
      const { api } = setup();
      const alice = await register(api, 'Alice');
      const bob = await register(api, 'Bob');
      const charlie = await register(api, 'Charlie');

      // Create a conversation between Alice and Bob (Charlie is not a member)
      const conv = await api.post('/api/conversations')
        .set(alice.auth)
        .send({ kind: 'direct', userId: bob.id });

      // Charlie tries to upload to that conversation
      const res = await api.post('/api/sync')
        .set(charlie.auth)
        .set('X-Vaani-Sync-Version', '1')
        .send({
          cursor: null,
          upload: [{
            clientId: 'charlie-msg',
            conversationCloudId: conv.body.id,
            body: 'Sneaky message',
            kind: 'text',
            clientTimestamp: new Date().toISOString(),
          }],
          readCursors: [],
          edits: [],
          deletions: [],
        });

      expect(res.status).toBe(200);
      expect(res.body.uploadResults[0].status).toBe('error');
      expect(res.body.uploadResults[0].error.code).toBe('conversation_not_found');
      expect(res.body.uploadResults[0].error.retryable).toBe(false);
    });
  });

  describe('cursor-based download', () => {
    it('returns conversations and messages on initial sync', async () => {
      const { api } = setup();
      const alice = await register(api, 'Alice');
      const bob = await register(api, 'Bob');

      // Create a conversation with messages
      const conv = await api.post('/api/conversations')
        .set(alice.auth)
        .send({ kind: 'direct', userId: bob.id });
      await api.post(`/api/conversations/${conv.body.id}/messages`)
        .set(alice.auth)
        .send({ body: 'Message 1', clientId: 'msg1' });
      await api.post(`/api/conversations/${conv.body.id}/messages`)
        .set(bob.auth)
        .send({ body: 'Message 2', clientId: 'msg2' });

      // Initial sync
      const res = await api.post('/api/sync')
        .set(alice.auth)
        .set('X-Vaani-Sync-Version', '1')
        .send({ cursor: null, upload: [], readCursors: [], edits: [], deletions: [] });

      expect(res.status).toBe(200);
      expect(res.body.conversations).toHaveLength(1);
      expect(res.body.conversations[0].id).toBe(conv.body.id);
      expect(res.body.messages.length).toBeGreaterThanOrEqual(2);
    });

    it('returns only new messages on incremental sync', async () => {
      const { api } = setup();
      const alice = await register(api, 'Alice');
      const bob = await register(api, 'Bob');

      const conv = await api.post('/api/conversations')
        .set(alice.auth)
        .send({ kind: 'direct', userId: bob.id });
      await api.post(`/api/conversations/${conv.body.id}/messages`)
        .set(alice.auth)
        .send({ body: 'Old message', clientId: 'old' });

      // Initial sync
      const res1 = await api.post('/api/sync')
        .set(alice.auth)
        .set('X-Vaani-Sync-Version', '1')
        .send({ cursor: null, upload: [], readCursors: [], edits: [], deletions: [] });

      const cursor = res1.body.cursor;

      // Add a new message
      await api.post(`/api/conversations/${conv.body.id}/messages`)
        .set(bob.auth)
        .send({ body: 'New message', clientId: 'new' });

      // Incremental sync
      const res2 = await api.post('/api/sync')
        .set(alice.auth)
        .set('X-Vaani-Sync-Version', '1')
        .send({ cursor, upload: [], readCursors: [], edits: [], deletions: [] });

      expect(res2.status).toBe(200);
      // Should only have the new message
      expect(res2.body.messages.some((m: { body: string }) => m.body === 'New message')).toBe(true);
      // Old message should not be included
      expect(res2.body.messages.some((m: { body: string }) => m.body === 'Old message')).toBe(false);
    });
  });

  describe('read cursor updates', () => {
    it('updates read cursor via sync', async () => {
      const { api } = setup();
      const alice = await register(api, 'Alice');
      const bob = await register(api, 'Bob');

      const conv = await api.post('/api/conversations')
        .set(alice.auth)
        .send({ kind: 'direct', userId: bob.id });
      const msg = await api.post(`/api/conversations/${conv.body.id}/messages`)
        .set(bob.auth)
        .send({ body: 'Read me', clientId: 'readme' });

      // Update read cursor via sync
      await api.post('/api/sync')
        .set(alice.auth)
        .set('X-Vaani-Sync-Version', '1')
        .send({
          cursor: null,
          upload: [],
          readCursors: [{ conversationCloudId: conv.body.id, lastReadMessageId: msg.body.id }],
          edits: [],
          deletions: [],
        });

      // Check that unread count is 0
      const convRes = await api.get(`/api/conversations/${conv.body.id}`).set(alice.auth);
      expect(convRes.body.unread).toBe(0);
    });
  });

  describe('edits and deletions', () => {
    it('processes edit via sync', async () => {
      const { api } = setup();
      const alice = await register(api, 'Alice');
      const bob = await register(api, 'Bob');

      const conv = await api.post('/api/conversations')
        .set(alice.auth)
        .send({ kind: 'direct', userId: bob.id });
      const msg = await api.post(`/api/conversations/${conv.body.id}/messages`)
        .set(alice.auth)
        .send({ body: 'Original', clientId: 'edit-test' });

      // Edit via sync
      await api.post('/api/sync')
        .set(alice.auth)
        .set('X-Vaani-Sync-Version', '1')
        .send({
          cursor: null,
          upload: [],
          readCursors: [],
          edits: [{ messageCloudId: msg.body.id, newBody: 'Edited', clientTimestamp: new Date().toISOString() }],
          deletions: [],
        });

      // Check the message was edited
      const convMsgs = await api.get(`/api/conversations/${conv.body.id}/messages`).set(alice.auth);
      const editedMsg = convMsgs.body.messages.find((m: { id: number }) => m.id === msg.body.id);
      expect(editedMsg.body).toBe('Edited');
      expect(editedMsg.editedAt).not.toBeNull();
    });

    it('processes deletion via sync', async () => {
      const { api } = setup();
      const alice = await register(api, 'Alice');
      const bob = await register(api, 'Bob');

      const conv = await api.post('/api/conversations')
        .set(alice.auth)
        .send({ kind: 'direct', userId: bob.id });
      const msg = await api.post(`/api/conversations/${conv.body.id}/messages`)
        .set(alice.auth)
        .send({ body: 'Delete me', clientId: 'delete-test' });

      // Delete via sync
      await api.post('/api/sync')
        .set(alice.auth)
        .set('X-Vaani-Sync-Version', '1')
        .send({
          cursor: null,
          upload: [],
          readCursors: [],
          edits: [],
          deletions: [{ messageCloudId: msg.body.id, clientTimestamp: new Date().toISOString() }],
        });

      // Check the message was soft-deleted
      const convMsgs = await api.get(`/api/conversations/${conv.body.id}/messages`).set(alice.auth);
      const deletedMsg = convMsgs.body.messages.find((m: { id: number }) => m.id === msg.body.id);
      expect(deletedMsg.deletedAt).not.toBeNull();
      expect(deletedMsg.body).toBeNull();
    });
  });
});
