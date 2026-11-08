/**
 * Android cloud-sync endpoint: POST /api/sync
 * Implements the M7 sync contract for idempotent message upload and cursor-based download.
 */
import { Router } from 'express';
import type { Deps } from '../app.js';
import { HttpError, reqInt, reqString, reqLanguage } from '../http-utils.js';

const SYNC_VERSION = 1;
const SUPPORTED_VERSIONS = [1];

interface SyncUploadMessage {
  clientId: string;
  conversationCloudId: number;
  body: string;
  kind: 'text' | 'voice';
  replyTo?: number;
  sourceLanguage?: string;
  clientTimestamp: string;
}

interface SyncReadCursor {
  conversationCloudId: number;
  lastReadMessageId: number;
}

interface SyncEdit {
  messageCloudId: number;
  newBody: string;
  clientTimestamp: string;
}

interface SyncDeletion {
  messageCloudId: number;
  clientTimestamp: string;
}

interface SyncUploadResult {
  clientId: string;
  status: 'created' | 'exists' | 'error';
  cloudId?: number;
  error?: { code: string; message: string; retryable: boolean };
}

interface SyncCursor {
  v: number;
  ts: string;
  msgId: number;
  convVer: string;
}

function parseCursor(cursor: string | null): SyncCursor | null {
  if (!cursor) return null;
  try {
    const json = Buffer.from(cursor, 'base64').toString('utf-8');
    const data = JSON.parse(json);
    if (data.v !== 1) return null;
    return data as SyncCursor;
  } catch {
    return null;
  }
}

function encodeCursor(cursor: SyncCursor): string {
  return Buffer.from(JSON.stringify(cursor)).toString('base64');
}

export function syncRoutes(d: Deps) {
  const r = Router();
  const uid = (req: { userId?: number }) => req.userId!;

  r.post('/sync', (req, res) => {
    // Protocol version check
    const versionHeader = req.headers['x-vaani-sync-version'];
    if (!versionHeader) {
      return res.status(400).json({
        code: 'sync_version_required',
        message: 'X-Vaani-Sync-Version header is required',
      });
    }
    const version = parseInt(String(versionHeader), 10);
    if (!SUPPORTED_VERSIONS.includes(version)) {
      return res.status(400).json({
        code: 'unsupported_sync_version',
        message: `Sync version ${version} is not supported`,
        supportedVersions: SUPPORTED_VERSIONS,
      });
    }

    const userId = uid(req);
    const body = req.body ?? {};
    const cursor = parseCursor(body.cursor ?? null);
    const upload: SyncUploadMessage[] = Array.isArray(body.upload) ? body.upload : [];
    const readCursors: SyncReadCursor[] = Array.isArray(body.readCursors) ? body.readCursors : [];
    const edits: SyncEdit[] = Array.isArray(body.edits) ? body.edits : [];
    const deletions: SyncDeletion[] = Array.isArray(body.deletions) ? body.deletions : [];

    // Process uploads
    const uploadResults: SyncUploadResult[] = [];
    for (const msg of upload.slice(0, 50)) { // Batch limit
      try {
        if (!msg.clientId || typeof msg.clientId !== 'string') {
          throw new Error('clientId is required');
        }
        const conversationId = reqInt(msg.conversationCloudId, 'conversationCloudId');
        const body = reqString(msg, 'body', { min: 1, max: 4000 });
        const kind = msg.kind === 'voice' ? 'voice' : 'text';
        const replyTo = msg.replyTo ? reqInt(msg.replyTo, 'replyTo') : null;
        const sourceLanguage = msg.sourceLanguage ? reqLanguage(msg.sourceLanguage, 'sourceLanguage') : null;

        // Authorization check and replay detection are sender-scoped.
        d.store.assertMember(conversationId, userId);
        const existing = d.store.findMessageByClientId(conversationId, userId, msg.clientId);
        if (existing) {
          uploadResults.push({ clientId: msg.clientId, status: 'exists', cloudId: existing.id });
          continue;
        }

        // Insert (the Store also enforces idempotency at the database boundary).
        const result = d.store.insertMessage({
          conversationId,
          senderId: userId,
          kind,
          body,
          replyTo,
          clientId: msg.clientId,
          sourceLanguage,
        });

        uploadResults.push({
          clientId: msg.clientId,
          status: 'created',
          cloudId: result.id,
        });

        // Notify via WebSocket only for a newly-created message.
        d.hub.toConversation(conversationId, 'message.new', result);
      } catch (e) {
        const err = e as Error & { code?: string };
        const isNotFound = err.code === 'not_found' || err.message?.includes('not found');
        const isForbidden = err.code === 'forbidden';
        uploadResults.push({
          clientId: msg.clientId,
          status: 'error',
          error: {
            code: isNotFound ? 'conversation_not_found' : isForbidden ? 'conversation_access_denied' : 'invalid_body',
            message: err.message ?? 'Upload failed',
            retryable: !isNotFound && !isForbidden,
          },
        });
      }
    }

    // Process read cursor updates
    for (const rc of readCursors) {
      try {
        const convId = reqInt(rc.conversationCloudId, 'conversationCloudId');
        const msgId = reqInt(rc.lastReadMessageId, 'lastReadMessageId');
        d.store.markRead(convId, userId, msgId);
        d.hub.toConversation(convId, 'read', { conversationId: convId, userId, messageId: msgId }, userId);
      } catch {
        // Ignore read cursor errors silently
      }
    }

    // Process edits
    for (const edit of edits.slice(0, 20)) {
      try {
        const msgId = reqInt(edit.messageCloudId, 'messageCloudId');
        const newBody = reqString(edit, 'newBody', { min: 1, max: 4000 });
        const updated = d.store.editMessage(msgId, userId, newBody);
        d.hub.toConversation(updated.conversationId, 'message.updated', updated);
      } catch {
        // Edit errors are not fatal to sync
      }
    }

    // Process deletions
    for (const del of deletions.slice(0, 20)) {
      try {
        const msgId = reqInt(del.messageCloudId, 'messageCloudId');
        const deleted = d.store.deleteMessage(msgId, userId);
        d.hub.toConversation(deleted.conversationId, 'message.deleted', deleted);
      } catch {
        // Deletion errors are not fatal to sync
      }
    }

    // Build download response
    const conversations = d.store.listConversations(userId).map(c => ({
      id: c.id,
      kind: c.kind,
      title: c.title,
      members: c.members.map(m => ({
        id: m.id,
        displayName: m.displayName,
        avatarHue: m.avatarHue,
      })),
      lastMessageId: c.lastMessage?.id ?? null,
      updatedAt: c.updatedAt,
      myRole: c.myRole,
    }));

    // Get one globally-ordered page across all member conversations. This avoids
    // advancing a global cursor past unseen messages in another conversation.
    interface SyncMessage {
      id: number;
      conversationId: number;
      senderId: number | null;
      senderName: string;
      kind: string;
      body: string | null;
      replyTo: number | null;
      clientId: string | null;
      sourceLanguage: string | null;
      createdAt: string;
      editedAt: string | null;
      deletedAt: string | null;
    }
    const afterMsgId = cursor?.msgId ?? 0;
    const memberNames = new Map(conversations.flatMap(c => c.members).map(member => [member.id, member.displayName]));
    const page = d.store.listMessagesAfterForUser(userId, afterMsgId, 201);
    const hasMore = page.length > 200;
    const messages: SyncMessage[] = page.slice(0, 200).map(m => ({
      id: m.id,
      conversationId: m.conversationId,
      senderId: m.senderId,
      senderName: memberNames.get(m.senderId ?? -1) ?? 'Unknown',
      kind: m.kind,
      body: m.body,
      replyTo: m.replyTo?.id ?? null,
      clientId: m.clientId,
      sourceLanguage: m.sourceLanguage,
      createdAt: m.createdAt,
      editedAt: m.editedAt,
      deletedAt: m.deletedAt,
    }));

    // Advance only to the highest message actually returned.
    const highestMsgId = messages.reduce((max, m) => Math.max(max, m.id), afterMsgId);

    const newCursor = encodeCursor({
      v: SYNC_VERSION,
      ts: new Date().toISOString(),
      msgId: highestMsgId,
      convVer: new Date().toISOString(),
    });

    res.json({
      cursor: newCursor,
      uploadResults,
      messages,
      conversations,
      hasMore,
      serverTime: new Date().toISOString(),
    });
  });

  return r;
}
