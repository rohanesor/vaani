/* Mirrors server DTOs (server/src/store.ts). */
export interface User { id: number; email?: string; displayName: string; avatarHue: number; preferredLanguage: string; createdAt: string; updatedAt: string; online?: boolean }
export interface Settings {
  aiEnabled: boolean; aiContextMessages: number; aiRedactContacts: boolean; voiceTranscription: boolean;
  theme: 'system' | 'light' | 'dark'; onboarded: boolean; notifyMessages: boolean;
}
export interface Reaction { emoji: string; userIds: number[] }
export interface Voice { durationMs: number; waveform: number[]; transcriptStatus: 'none' | 'pending' | 'done' | 'failed' | 'unavailable'; transcriptLang: string | null; transcriptError: string | null }
export interface Message {
  id: number; conversationId: number; senderId: number | null; kind: 'text' | 'voice' | 'system';
  body: string | null; replyTo: { id: number; senderId: number | null; body: string | null; kind: string } | null;
  clientId: string | null; aiAssisted: boolean; sourceLanguage: string | null; createdAt: string; editedAt: string | null; deletedAt: string | null; reactions: Reaction[]; voice: Voice | null;
}
/** Client-side outbox state layered on top of server messages. */
export type LocalStatus = 'pending' | 'failed';
export interface UIMessage extends Message { local?: LocalStatus; localError?: string }

export interface Conversation {
  id: number; kind: 'direct' | 'group'; title: string; createdBy: number; updatedAt: string;
  members: (User & { role: string })[]; lastMessage: Message | null; unread: number; myRole: string;
}

export interface ContextInfo { messageIds: number[]; messageCount: number; redactions: { emails: number; phones: number; numbers: number; secrets: number }; provider: string; feature: string }
export interface Translation { messageId: number; targetLanguage: string; sourceLanguage: string | null; text: string; note: string | null; cached: boolean }
export interface Ref { messageIds: number[] }
export interface Summary {
  conversationId: number; generatedAt: string; messageRange: { from: number; to: number; count: number };
  redactions: ContextInfo['redactions']; summary: string; keyPoints: string[];
  decisions: ({ text: string; status: 'confirmed' | 'proposed' } & Ref)[];
  tasks: ({ text: string; owner: string | null; due: string | null } & Ref)[];
  openQuestions: ({ text: string } & Ref)[];
}
export type ActionPayload =
  | { type: 'create_event'; title: string; date: string; time: string | null; duration_minutes: number; participants: string[]; notes: string | null }
  | { type: 'create_task'; title: string; due_date: string | null; assignee: string | null }
  | { type: 'send_message'; text: string };
export type ProposedAction = ActionPayload & { id: number; status: string; requires_confirmation: true };
export interface Extraction { messageId: number; intent: string; summary: string; actions: ProposedAction[]; rejected: string[]; timezone: string }
export interface AIStatus {
  provider: string; configured: boolean; enabledForUser: boolean; tokensToday: number; dailyTokenBudget: number; requestsPerMinute: number; calendar: string;
  usage30d: { feature: string; requests: number; inputTokens: number; outputTokens: number }[];
  speech: { recognition: { provider: string; configured: boolean }; synthesis: { provider: string; configured: boolean } };
}
export interface Task { id: number; conversationId: number | null; title: string; dueAt: string | null; done: boolean; createdAt: string }
export interface SearchResult { query: string; messages: { message: Message; snippet: string }[]; conversations: { id: number; title: string; kind: string }[] }
