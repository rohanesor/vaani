/**
 * SQLite persistence via Node's built-in `node:sqlite` (no native build step).
 * Requires Node >= 22.13. Schema is versioned with PRAGMA user_version.
 */
import { DatabaseSync } from 'node:sqlite';
import fs from 'node:fs';
import path from 'node:path';

export type DB = DatabaseSync;

export const MIGRATIONS: string[] = [
  `
  CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    email TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT NOT NULL,
    display_name TEXT NOT NULL,
    avatar_hue INTEGER NOT NULL DEFAULT 180,
    preferred_language TEXT NOT NULL DEFAULT 'en-IN',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
  );
  CREATE TABLE user_settings (
    user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    ai_enabled INTEGER NOT NULL DEFAULT 1,
    ai_context_messages INTEGER NOT NULL DEFAULT 30,
    ai_redact_contacts INTEGER NOT NULL DEFAULT 1,
    voice_transcription INTEGER NOT NULL DEFAULT 1,
    theme TEXT NOT NULL DEFAULT 'system',
    onboarded INTEGER NOT NULL DEFAULT 0,
    notify_messages INTEGER NOT NULL DEFAULT 1
  );
  CREATE TABLE conversations (
    id INTEGER PRIMARY KEY,
    kind TEXT NOT NULL CHECK (kind IN ('direct','group')),
    title TEXT,
    created_by INTEGER NOT NULL REFERENCES users(id),
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    direct_key TEXT UNIQUE
  );
  CREATE TABLE members (
    conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('owner','member')),
    joined_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    last_read_id INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (conversation_id, user_id)
  );
  CREATE INDEX members_user ON members(user_id);
  CREATE TABLE messages (
    id INTEGER PRIMARY KEY,
    conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id INTEGER REFERENCES users(id),
    kind TEXT NOT NULL CHECK (kind IN ('text','voice','system')),
    body TEXT,
    reply_to INTEGER REFERENCES messages(id),
    client_id TEXT,
    ai_assisted INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    UNIQUE (sender_id, client_id)
  );
  CREATE INDEX messages_conv ON messages(conversation_id, id);
  CREATE VIRTUAL TABLE messages_fts USING fts5(body, content='messages', content_rowid='id', tokenize='unicode61');
  CREATE TRIGGER messages_ai AFTER INSERT ON messages WHEN new.body IS NOT NULL BEGIN
    INSERT INTO messages_fts(rowid, body) VALUES (new.id, new.body);
  END;
  CREATE TRIGGER messages_au AFTER UPDATE OF body ON messages BEGIN
    INSERT INTO messages_fts(messages_fts, rowid, body) SELECT 'delete', old.id, old.body WHERE old.body IS NOT NULL;
    INSERT INTO messages_fts(rowid, body) SELECT new.id, new.body WHERE new.body IS NOT NULL;
  END;
  CREATE TRIGGER messages_ad AFTER DELETE ON messages WHEN old.body IS NOT NULL BEGIN
    INSERT INTO messages_fts(messages_fts, rowid, body) VALUES ('delete', old.id, old.body);
  END;
  CREATE TABLE reactions (
    message_id INTEGER NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji TEXT NOT NULL,
    PRIMARY KEY (message_id, user_id, emoji)
  );
  CREATE TABLE voice_notes (
    message_id INTEGER PRIMARY KEY REFERENCES messages(id) ON DELETE CASCADE,
    mime TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    waveform TEXT NOT NULL,
    audio BLOB NOT NULL,
    transcript_status TEXT NOT NULL DEFAULT 'none' CHECK (transcript_status IN ('none','pending','done','failed','unavailable')),
    transcript_lang TEXT,
    transcript_error TEXT
  );
  CREATE TABLE translations (
    message_id INTEGER NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    target_lang TEXT NOT NULL,
    source_lang TEXT,
    text TEXT NOT NULL,
    note TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    PRIMARY KEY (message_id, target_lang)
  );
  CREATE TABLE ai_usage (
    id INTEGER PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    feature TEXT NOT NULL,
    model TEXT NOT NULL,
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
  );
  CREATE INDEX ai_usage_user_day ON ai_usage(user_id, created_at);
  CREATE TABLE ai_actions (
    id INTEGER PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    source_message_id INTEGER REFERENCES messages(id) ON DELETE SET NULL,
    type TEXT NOT NULL,
    payload TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'proposed' CHECK (status IN ('proposed','executed','dismissed','failed')),
    result TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
  );
  CREATE TABLE tasks (
    id INTEGER PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id INTEGER REFERENCES conversations(id) ON DELETE SET NULL,
    title TEXT NOT NULL,
    due_at TEXT,
    done INTEGER NOT NULL DEFAULT 0,
    source_action_id INTEGER REFERENCES ai_actions(id) ON DELETE SET NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
  );
  `,
  // V2: additive identity/session hardening. Existing user IDs and records remain unchanged.
  `
  ALTER TABLE users ADD COLUMN updated_at TEXT;
  UPDATE users SET updated_at = created_at WHERE updated_at IS NULL;
  CREATE TABLE auth_sessions (
    id TEXT PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at INTEGER NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    revoked_at TEXT
  );
  CREATE INDEX auth_sessions_active ON auth_sessions(user_id, expires_at) WHERE revoked_at IS NULL;
  `,
  // V3: additive persistent text-message lifecycle metadata.
  `
  ALTER TABLE messages ADD COLUMN source_language TEXT;
  ALTER TABLE messages ADD COLUMN edited_at TEXT;
  ALTER TABLE messages ADD COLUMN deleted_at TEXT;
  CREATE INDEX messages_conv_created ON messages(conversation_id, created_at, id);
  `,
];

export function openDb(file: string): DB {
  if (file !== ':memory:') fs.mkdirSync(path.dirname(path.resolve(file)), { recursive: true });
  const db = new DatabaseSync(file);
  db.exec('PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON; PRAGMA busy_timeout = 3000;');
  migrate(db);
  return db;
}

export function migrate(db: DB) {
  const row = db.prepare('PRAGMA user_version').get() as { user_version: number };
  for (let v = row.user_version; v < MIGRATIONS.length; v++) {
    db.exec('BEGIN');
    try {
      db.exec(MIGRATIONS[v]);
      db.exec(`PRAGMA user_version = ${v + 1}`);
      db.exec('COMMIT');
    } catch (e) {
      db.exec('ROLLBACK');
      throw e;
    }
  }
}

export function tx<T>(db: DB, fn: () => T): T {
  db.exec('BEGIN');
  try {
    const out = fn();
    db.exec('COMMIT');
    return out;
  } catch (e) {
    db.exec('ROLLBACK');
    throw e;
  }
}
