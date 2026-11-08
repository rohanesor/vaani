/**
 * Context construction: decides WHICH messages go to the model and HOW they are rendered.
 * Only messages from a conversation the caller has already been authorized for reach here.
 */
import type { MessageDTO, Store, UserDTO } from '../store.js';

/* ---------------- privacy filter ---------------- */

const EMAIL = /[\p{L}\p{N}._%+-]+@[\p{L}\p{N}.-]+\.[\p{L}]{2,}/gu;
// +91 98765 43210, 098765-43210, 9876543210, (044) 2345 6789
const PHONE = /(?<![\p{N}])(?:\+?\d{1,3}[\s-]?)?(?:\(?\d{2,5}\)?[\s-]?)?\d{3,5}[\s-]?\d{4,5}(?![\p{N}])/gu;
const IN_MOBILE = /(?<![\p{N}])(?:\+91[\s-]?|0)?[6-9]\d{4}[\s-]?\d{5}(?![\p{N}])/gu;
const LONG_NUMBER = /(?<![\p{N}])\d(?:[\s-]?\d){11,18}(?![\p{N}])/gu; // card / account-like
const OTP = /\b(otp|pin|cvv|password|passcode)\b\s*(?:is|:|-)?\s*\S+/giu;

export interface RedactionReport { emails: number; phones: number; numbers: number; secrets: number }

export function redact(text: string, report: RedactionReport): string {
  return text
    .replace(OTP, (m, label: string) => { report.secrets++; return `${label} [redacted]`; })
    .replace(EMAIL, () => { report.emails++; return '[email]'; })
    .replace(IN_MOBILE, () => { report.phones++; return '[phone]'; })
    .replace(LONG_NUMBER, () => { report.numbers++; return '[number]'; })
    .replace(PHONE, (m) => {
      const digits = m.replace(/\D/g, '');
      if (digits.length < 8) return m; // years, times, small quantities
      report.phones++; return '[phone]';
    });
}

export const emptyReport = (): RedactionReport => ({ emails: 0, phones: 0, numbers: 0, secrets: 0 });

/* ---------------- transcript rendering ---------------- */

export interface RenderOptions { redactContacts: boolean; selfId: number; maxCharsPerMessage?: number }

export function renderTranscript(messages: MessageDTO[], people: Map<number, UserDTO>, opts: RenderOptions, report = emptyReport()): string {
  const max = opts.maxCharsPerMessage ?? 1200;
  return messages.map((m) => {
    const who = m.senderId === null ? 'System' : m.senderId === opts.selfId ? `${people.get(m.senderId)?.displayName ?? 'Me'} (me)` : people.get(m.senderId)?.displayName ?? 'Unknown';
    let body = m.kind === 'voice'
      ? (m.body ? `[voice note, transcribed] ${m.body}` : '[voice note, not transcribed]')
      : (m.body ?? '');
    if (body.length > max) body = body.slice(0, max) + '…';
    if (opts.redactContacts) body = redact(body, report);
    const reply = m.replyTo ? ` (replying to #${m.replyTo.id})` : '';
    return `[#${m.id}] ${m.createdAt.slice(0, 16).replace('T', ' ')} UTC — ${who}${reply}: ${body}`;
  }).join('\n');
}

/* ---------------- retrieval ---------------- */

const STOP = new Set(('a an the and or but if of to in on at for with about from by is are was were be been being do does did what which who whom ' +
  'when where why how we us our you your i me my they them their it its this that these those did decide decided say said ' +
  'any some there here have has had will would should could can just tell please regarding').split(' '));

export function keywords(question: string): string[] {
  const terms = (question.normalize('NFC').toLowerCase().match(/[\p{L}\p{M}\p{N}]+/gu) ?? [])
    .filter((t) => t.length > 2 && !STOP.has(t));
  return [...new Set(terms)];
}

/**
 * Build context for a question about the conversation: ranked keyword hits with
 * one neighbour on each side, plus the most recent messages for recency.
 * Never returns more than `budget` messages.
 */
export function retrieveForQuestion(store: Store, conversationId: number, question: string, budget: number): MessageDTO[] {
  const hits = store.retrieveInConversation(conversationId, keywords(question), Math.ceil(budget / 3));
  const ids = new Set<number>();
  for (const h of hits) for (const n of store.neighbours(conversationId, h, 1)) ids.add(n);
  const recent = store.recentMessages(conversationId, Math.max(4, Math.floor(budget / 3)));
  for (const m of recent) ids.add(m.id);
  const ordered = [...ids].sort((a, b) => a - b);
  // Keep hits first if over budget, then the newest of the rest.
  const keep = new Set<number>(hits);
  for (const id of ordered.slice().reverse()) { if (keep.size >= budget) break; keep.add(id); }
  return store.messagesByIds([...keep].sort((a, b) => a - b));
}

/** Messages around a target (for explain / translate context). */
export function windowAround(store: Store, conversationId: number, messageId: number, radius: number): MessageDTO[] {
  return store.messagesByIds(store.neighbours(conversationId, messageId, radius));
}
