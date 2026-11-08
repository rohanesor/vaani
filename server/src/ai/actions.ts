/**
 * Structured AI actions. The model PROPOSES; the user CONFIRMS; only then do executors run.
 * Every payload is validated here before it is stored or executed.
 */

export type ActionType = 'create_event' | 'create_task' | 'send_message';

export interface CreateEventAction { type: 'create_event'; title: string; date: string; time: string | null; duration_minutes: number; participants: string[]; notes: string | null; requires_confirmation: true }
export interface CreateTaskAction { type: 'create_task'; title: string; due_date: string | null; assignee: string | null; requires_confirmation: true }
export interface SendMessageAction { type: 'send_message'; text: string; requires_confirmation: true }
export type VaaniAction = CreateEventAction | CreateTaskAction | SendMessageAction;

export interface ExtractionResult { intent: string; summary: string; actions: VaaniAction[] }

/** JSON schema sent to the model (structured outputs). Kept flat: one object type with nullable fields. */
export const EXTRACTION_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['intent', 'summary', 'actions'],
  properties: {
    intent: { type: 'string', description: 'One short phrase naming what the speaker wants.' },
    summary: { type: 'string', description: 'One sentence restating the message.' },
    actions: {
      type: 'array',
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['type', 'title', 'date', 'time', 'duration_minutes', 'participants', 'notes', 'text'],
        properties: {
          type: { type: 'string', enum: ['create_event', 'create_task', 'send_message'] },
          title: { type: ['string', 'null'] },
          date: { type: ['string', 'null'], description: 'YYYY-MM-DD, resolved from the reference date. null if not stated.' },
          time: { type: ['string', 'null'], description: '24h HH:MM. null if not stated.' },
          duration_minutes: { type: ['integer', 'null'] },
          participants: { type: 'array', items: { type: 'string' } },
          notes: { type: ['string', 'null'] },
          text: { type: ['string', 'null'], description: 'For send_message only: the message to send.' },
        },
      },
    },
  },
} as const;

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;

function validDate(s: unknown): s is string {
  if (typeof s !== 'string' || !DATE.test(s)) return false;
  const d = new Date(`${s}T00:00:00Z`);
  return !Number.isNaN(d.getTime()) && d.toISOString().startsWith(s);
}

const clean = (s: unknown, max: number): string | null => (typeof s === 'string' && s.trim() ? s.trim().slice(0, max) : null);

export class ActionValidationError extends Error {}

/** Validate and normalise one raw action. Throws ActionValidationError when unusable. */
export function validateAction(raw: unknown): VaaniAction {
  if (!raw || typeof raw !== 'object') throw new ActionValidationError('Action must be an object.');
  const r = raw as Record<string, unknown>;
  switch (r.type) {
    case 'create_event': {
      const title = clean(r.title, 140);
      if (!title) throw new ActionValidationError('Event needs a title.');
      if (!validDate(r.date)) throw new ActionValidationError('Event needs a valid date (YYYY-MM-DD).');
      const time = r.time == null ? null : String(r.time);
      if (time !== null && !TIME.test(time)) throw new ActionValidationError('Event time must be HH:MM.');
      const dur = r.duration_minutes == null ? 60 : Number(r.duration_minutes);
      if (!Number.isInteger(dur) || dur < 5 || dur > 24 * 60) throw new ActionValidationError('Duration must be 5–1440 minutes.');
      const participants = Array.isArray(r.participants) ? r.participants.map((p) => clean(p, 80)).filter((p): p is string => !!p).slice(0, 50) : [];
      return { type: 'create_event', title, date: r.date, time, duration_minutes: dur, participants, notes: clean(r.notes, 1000), requires_confirmation: true };
    }
    case 'create_task': {
      const title = clean(r.title, 200);
      if (!title) throw new ActionValidationError('Task needs a title.');
      const due = r.due_date ?? r.date ?? null;
      if (due !== null && !validDate(due)) throw new ActionValidationError('Task due date must be YYYY-MM-DD.');
      const assignee = clean(r.assignee ?? (Array.isArray(r.participants) ? r.participants[0] : null), 80);
      return { type: 'create_task', title, due_date: (due as string | null), assignee, requires_confirmation: true };
    }
    case 'send_message': {
      const text = clean(r.text, 4000);
      if (!text) throw new ActionValidationError('Message text is empty.');
      return { type: 'send_message', text, requires_confirmation: true };
    }
    default:
      throw new ActionValidationError(`Unsupported action type: ${String(r.type)}`);
  }
}

/** Parse model output, keep valid actions, report the rejected ones (never silently invent). */
export function parseExtraction(json: string): { result: ExtractionResult; rejected: string[] } {
  let raw: unknown;
  try { raw = JSON.parse(json); } catch { throw new ActionValidationError('Model did not return valid JSON.'); }
  const r = raw as Record<string, unknown>;
  const actions: VaaniAction[] = [];
  const rejected: string[] = [];
  for (const a of Array.isArray(r.actions) ? r.actions.slice(0, 8) : []) {
    try { actions.push(validateAction(a)); } catch (e) { rejected.push((e as Error).message); }
  }
  return { result: { intent: clean(r.intent, 120) ?? 'unknown', summary: clean(r.summary, 400) ?? '', actions }, rejected };
}

/* ---------------- calendar executor (ICS) ---------------- */

/** Calendar integration seam. The default implementation produces a standards-compliant .ics file. */
export interface CalendarProvider {
  readonly name: string;
  createEvent(e: CreateEventAction, ctx: { timezone: string; organizer: string }): Promise<{ kind: 'ics'; filename: string; content: string } | { kind: 'remote'; url: string }>;
}

const icsEscape = (s: string) => s.replace(/\\/g, '\\\\').replace(/\n/g, '\\n').replace(/[,;]/g, (m) => `\\${m}`);
const fold = (line: string) => line.length <= 74 ? line : line.match(/.{1,73}/g)!.join('\r\n ');

export class IcsCalendarProvider implements CalendarProvider {
  readonly name = 'ics-download';
  async createEvent(e: CreateEventAction, ctx: { timezone: string; organizer: string }) {
    const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
    const d = e.date.replace(/-/g, '');
    const lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//VAANI//Voice-to-Action//EN', 'CALSCALE:GREGORIAN', 'BEGIN:VEVENT',
      `UID:${crypto.randomUUID()}@vaani`, `DTSTAMP:${stamp}`];
    if (e.time) {
      const [h, m] = e.time.split(':').map(Number);
      const end = new Date(Date.UTC(2000, 0, 1, h, m + e.duration_minutes));
      const dayOffset = end.getUTCDate() - 1;
      const endDate = new Date(`${e.date}T00:00:00Z`); endDate.setUTCDate(endDate.getUTCDate() + dayOffset);
      lines.push(`DTSTART;TZID=${ctx.timezone}:${d}T${e.time.replace(':', '')}00`,
        `DTEND;TZID=${ctx.timezone}:${endDate.toISOString().slice(0, 10).replace(/-/g, '')}T${String(end.getUTCHours()).padStart(2, '0')}${String(end.getUTCMinutes()).padStart(2, '0')}00`);
    } else {
      const next = new Date(`${e.date}T00:00:00Z`); next.setUTCDate(next.getUTCDate() + 1);
      lines.push(`DTSTART;VALUE=DATE:${d}`, `DTEND;VALUE=DATE:${next.toISOString().slice(0, 10).replace(/-/g, '')}`);
    }
    lines.push(`SUMMARY:${icsEscape(e.title)}`);
    const desc = [e.notes, e.participants.length ? `Participants: ${e.participants.join(', ')}` : null, `Created with VAANI by ${ctx.organizer}`].filter(Boolean).join('\n');
    lines.push(`DESCRIPTION:${icsEscape(desc)}`, 'END:VEVENT', 'END:VCALENDAR');
    const safe = e.title.replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-|-$/g, '').slice(0, 40) || 'event';
    return { kind: 'ics' as const, filename: `${safe}.ics`, content: lines.map(fold).join('\r\n') + '\r\n' };
  }
}
