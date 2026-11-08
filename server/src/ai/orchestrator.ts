/**
 * AIOrchestrator: the only entry point routes use for AI.
 * Responsibilities: authorization (via Store), feature gating, rate limiting, token budget,
 * context construction + privacy filtering, prompt selection, timeouts, output validation,
 * usage accounting, and the propose → confirm → execute lifecycle for actions.
 */
import type { VaaniConfig } from '../config.js';
import type { MessageDTO, Store, UserDTO } from '../store.js';
import { HttpError, SUPPORTED_LANGUAGES } from '../http-utils.js';
import { AIError, type AICompletionRequest, type AICompletionResult, type AIProvider } from './types.js';
import { emptyReport, renderTranscript, retrieveForQuestion, windowAround, type RedactionReport } from './context.js';
import { REWRITE_STYLES, SUMMARY_SCHEMA, TRANSLATE_SCHEMA, langName, systemFor } from './prompts.js';
import { EXTRACTION_SCHEMA, parseExtraction, validateAction, ActionValidationError, type CalendarProvider, type VaaniAction } from './actions.js';

export type AssistOp = 'explain' | 'reply' | 'rewrite' | 'ask';
export interface AssistInput { conversationId: number; op: AssistOp; messageId?: number; draft?: string; style?: string; instruction?: string; question?: string }
export interface ContextInfo { messageIds: number[]; messageCount: number; redactions: RedactionReport; provider: string; feature: string }
export interface StreamHooks { signal?: AbortSignal; onContext?: (c: ContextInfo) => void; onText?: (d: string) => void }

export class SlidingWindowLimiter {
  private hits = new Map<number, number[]>();
  constructor(private perMinute: number, private now = () => Date.now()) {}
  take(key: number): boolean {
    const t = this.now();
    const arr = (this.hits.get(key) ?? []).filter((x) => t - x < 60_000);
    if (arr.length >= this.perMinute) { this.hits.set(key, arr); return false; }
    arr.push(t); this.hits.set(key, arr); return true;
  }
}

export class AIOrchestrator {
  private limiter: SlidingWindowLimiter;
  constructor(private store: Store, private provider: AIProvider, private cfg: Pick<VaaniConfig, 'aiRequestsPerMinute' | 'aiDailyTokenBudget' | 'aiTimeoutMs'>, private calendar: CalendarProvider) {
    this.limiter = new SlidingWindowLimiter(cfg.aiRequestsPerMinute);
  }

  status(userId: number) {
    const s = this.store.getSettings(userId);
    return {
      provider: this.provider.name, configured: this.provider.isConfigured(), enabledForUser: s.aiEnabled,
      tokensToday: this.store.tokensToday(userId), dailyTokenBudget: this.cfg.aiDailyTokenBudget, requestsPerMinute: this.cfg.aiRequestsPerMinute,
      calendar: this.calendar.name, usage30d: this.store.usageSummary(userId),
    };
  }

  /* ---------------- gates ---------------- */
  private gate(userId: number) {
    const s = this.store.getSettings(userId);
    if (!s.aiEnabled) throw new AIError('disabled', 'AI assistance is turned off in your privacy settings.');
    if (!this.provider.isConfigured()) throw new AIError('not_configured', 'AI is not configured on this server yet. An administrator needs to set ANTHROPIC_API_KEY.');
    if (this.store.tokensToday(userId) >= this.cfg.aiDailyTokenBudget) throw new AIError('budget_exceeded', 'You’ve reached today’s AI usage limit. It resets at midnight UTC.');
    if (!this.limiter.take(userId)) throw new AIError('rate_limited', 'Too many AI requests in the last minute. Wait a moment and try again.', true);
    return s;
  }

  private people(conversationId: number, userId: number): Map<number, UserDTO> {
    const conv = this.store.getConversation(conversationId, userId); // authorization
    return new Map(conv.members.map((m) => [m.id, m]));
  }

  private async run(userId: number, feature: string, req: AICompletionRequest): Promise<AICompletionResult> {
    const timeout = AbortSignal.timeout(this.cfg.aiTimeoutMs);
    const signal = req.signal ? AbortSignal.any([req.signal, timeout]) : timeout;
    try {
      const res = await this.provider.complete({ ...req, signal });
      this.store.recordUsage(userId, feature, res.model, res.inputTokens, res.outputTokens, 'ok');
      return res;
    } catch (e) {
      const err = timeout.aborted && !req.signal?.aborted ? new AIError('timeout', 'The AI took too long to respond.', true)
        : e instanceof AIError ? e : new AIError('upstream', 'Unexpected AI error.', true);
      this.store.recordUsage(userId, feature, 'n/a', 0, 0, err.code);
      throw err;
    }
  }

  private block(transcript: string, label = 'conversation') {
    return `<${label}>\n${transcript}\n</${label}>`;
  }

  /* ---------------- assist (streaming text) ---------------- */
  async assist(userId: number, input: AssistInput, hooks: StreamHooks = {}): Promise<{ text: string; context: ContextInfo }> {
    const settings = this.gate(userId);
    const me = this.store.getUser(userId);
    const people = this.people(input.conversationId, userId);
    const budget = settings.aiContextMessages;
    let context: MessageDTO[] = [];
    let userPrompt = '';
    let tier: 'reasoning' | 'fast' = 'reasoning';
    let maxTokens = 700;

    switch (input.op) {
      case 'explain': {
        const target = this.targetMessage(input.messageId, input.conversationId, userId);
        context = windowAround(this.store, input.conversationId, target.id, 6);
        userPrompt = `Explain message [#${target.id}].`;
        tier = 'fast';
        break;
      }
      case 'reply': {
        context = this.store.recentMessages(input.conversationId, Math.min(budget, 20));
        const target = input.messageId ? this.targetMessage(input.messageId, input.conversationId, userId) : [...context].reverse().find((m) => m.senderId !== userId && m.kind !== 'system');
        if (!target) throw new AIError('invalid_request', 'There is no message to reply to yet.');
        userPrompt = `I am ${me.displayName}. Draft my reply to [#${target.id}].${input.instruction ? ` Guidance from me: ${input.instruction.slice(0, 300)}` : ''}`;
        maxTokens = 400;
        break;
      }
      case 'rewrite': {
        const draft = (input.draft ?? '').trim();
        if (!draft) throw new AIError('invalid_request', 'Type a draft first, then ask VAANI to rewrite it.');
        if (draft.length > 4000) throw new AIError('invalid_request', 'Draft is too long to rewrite (max 4000 characters).');
        const style = input.style && REWRITE_STYLES[input.style] ? REWRITE_STYLES[input.style] : null;
        const instruction = style ?? (input.instruction?.trim().slice(0, 300) || null);
        if (!instruction) throw new AIError('invalid_request', 'Choose how to rewrite the draft.');
        context = this.store.recentMessages(input.conversationId, 8);
        userPrompt = `Rewrite request: ${instruction}\n<draft>\n${draft}\n</draft>`;
        tier = 'fast';
        maxTokens = 600;
        break;
      }
      case 'ask': {
        const q = (input.question ?? '').trim();
        if (!q) throw new AIError('invalid_request', 'Ask a question about this conversation.');
        if (q.length > 500) throw new AIError('invalid_request', 'Question is too long (max 500 characters).');
        context = retrieveForQuestion(this.store, input.conversationId, q, budget);
        userPrompt = `Question: ${q}`;
        maxTokens = 900;
        break;
      }
      default:
        throw new AIError('invalid_request', 'Unknown assist operation.');
    }

    const report = emptyReport();
    const transcript = renderTranscript(context, people, { redactContacts: settings.aiRedactContacts, selfId: userId }, report);
    const info: ContextInfo = { messageIds: context.map((m) => m.id), messageCount: context.length, redactions: report, provider: this.provider.name, feature: input.op };
    hooks.onContext?.(info);
    const res = await this.run(userId, `assist.${input.op}`, {
      system: systemFor(input.op, me.preferredLanguage), tier, maxTokens, signal: hooks.signal, onText: hooks.onText,
      messages: [{ role: 'user', content: `${this.block(transcript)}\n\n${userPrompt}` }],
    });
    if (!res.text.trim()) throw new AIError('invalid_response', 'The AI returned an empty response.', true);
    return { text: res.text.trim(), context: info };
  }

  private targetMessage(id: number | undefined, conversationId: number, userId: number): MessageDTO {
    if (!id) throw new AIError('invalid_request', 'Choose a message first.');
    const m = this.store.getMessage(id, userId);
    if (m.conversationId !== conversationId) throw new HttpError(404, 'not_found', 'Message not found.');
    if (!m.body) throw new AIError('invalid_request', m.kind === 'voice' ? 'This voice note has no transcript yet.' : 'This message has no text.');
    return m;
  }

  /* ---------------- translate (cached, structured) ---------------- */
  async translate(userId: number, messageId: number, targetLang: string, signal?: AbortSignal) {
    if (!(targetLang in SUPPORTED_LANGUAGES)) throw new AIError('invalid_request', 'Unsupported target language.');
    const msg = this.store.getMessage(messageId, userId); // authorization
    if (!msg.body) throw new AIError('invalid_request', msg.kind === 'voice' ? 'This voice note has no transcript yet.' : 'Nothing to translate.');
    const cached = this.store.getTranslation(messageId, targetLang);
    if (cached) return { messageId, targetLanguage: targetLang, sourceLanguage: cached.source_lang as string | null, text: cached.text as string, note: (cached.note as string) ?? null, cached: true };
    const settings = this.gate(userId);
    const people = this.people(msg.conversationId, userId);
    const ctx = windowAround(this.store, msg.conversationId, messageId, 3);
    const transcript = renderTranscript(ctx.filter((m) => m.id !== messageId), people, { redactContacts: settings.aiRedactContacts, selfId: userId });
    const res = await this.run(userId, 'translate', {
      system: systemFor('translate', targetLang), tier: 'fast', maxTokens: 1500, jsonSchema: TRANSLATE_SCHEMA as unknown as Record<string, unknown>, signal,
      // The target text itself is not redacted: the user explicitly asked to translate it.
      messages: [{ role: 'user', content: `${this.block(transcript, 'surrounding_messages')}\n\nTranslate into ${langName(targetLang)} (${targetLang}):\n<target>\n${msg.body}\n</target>` }],
    });
    const parsed = safeJson(res.text) as { source_language?: unknown; translation?: unknown; note?: unknown } | null;
    if (!parsed || typeof parsed.translation !== 'string' || !parsed.translation.trim()) throw new AIError('invalid_response', 'The translation came back malformed. Try again.', true);
    const src = typeof parsed.source_language === 'string' ? parsed.source_language.slice(0, 16) : null;
    const note = typeof parsed.note === 'string' && parsed.note.trim() ? parsed.note.trim().slice(0, 300) : null;
    this.store.saveTranslation(messageId, targetLang, src, parsed.translation.trim(), note);
    return { messageId, targetLanguage: targetLang, sourceLanguage: src, text: parsed.translation.trim(), note, cached: false };
  }

  /* ---------------- summarize (structured, references validated) ---------------- */
  async summarize(userId: number, conversationId: number, opts: { sinceMessageId?: number; signal?: AbortSignal } = {}) {
    const settings = this.gate(userId);
    const me = this.store.getUser(userId);
    const people = this.people(conversationId, userId);
    const limit = Math.min(Math.max(settings.aiContextMessages * 2, 20), 150);
    let msgs = this.store.recentMessages(conversationId, limit);
    if (opts.sinceMessageId) msgs = msgs.filter((m) => m.id > opts.sinceMessageId!);
    msgs = msgs.filter((m) => m.kind !== 'system');
    if (msgs.length < 2) throw new AIError('invalid_request', 'There isn’t enough conversation to summarise yet.');
    const report = emptyReport();
    const transcript = renderTranscript(msgs, people, { redactContacts: settings.aiRedactContacts, selfId: userId, maxCharsPerMessage: 600 }, report);
    const res = await this.run(userId, 'summarize', {
      system: systemFor('summarize', me.preferredLanguage), tier: 'reasoning', maxTokens: 2000, jsonSchema: SUMMARY_SCHEMA as unknown as Record<string, unknown>, signal: opts.signal,
      messages: [{ role: 'user', content: `${this.block(transcript)}\n\nSummarise this conversation.` }],
    });
    const raw = safeJson(res.text) as Record<string, unknown> | null;
    if (!raw || typeof raw.summary !== 'string') throw new AIError('invalid_response', 'The summary came back malformed. Try again.', true);
    const known = new Set(msgs.map((m) => m.id));
    const refs = (v: unknown) => (Array.isArray(v) ? v.filter((x): x is number => Number.isInteger(x) && known.has(x as number)) : []);
    const arr = (v: unknown) => (Array.isArray(v) ? v : []) as Record<string, unknown>[];
    const str = (v: unknown) => (typeof v === 'string' && v.trim() ? v.trim() : null);
    return {
      conversationId, generatedAt: new Date().toISOString(), messageRange: { from: msgs[0].id, to: msgs[msgs.length - 1].id, count: msgs.length },
      redactions: report,
      summary: raw.summary.trim(),
      keyPoints: arr(raw.key_points).map(String).filter(Boolean),
      decisions: arr(raw.decisions).filter((d) => str(d.text)).map((d) => ({ text: str(d.text)!, status: d.status === 'confirmed' ? 'confirmed' : 'proposed', messageIds: refs(d.message_ids) })),
      tasks: arr(raw.tasks).filter((t) => str(t.text)).map((t) => ({ text: str(t.text)!, owner: str(t.owner), due: str(t.due), messageIds: refs(t.message_ids) })),
      openQuestions: arr(raw.open_questions).filter((q) => str(q.text)).map((q) => ({ text: str(q.text)!, messageIds: refs(q.message_ids) })),
    };
  }

  /* ---------------- voice-to-action ---------------- */
  async extractActions(userId: number, messageId: number, timezone: string, signal?: AbortSignal) {
    const tz = validTimezone(timezone);
    const msg = this.store.getMessage(messageId, userId);
    if (!msg.body) throw new AIError('invalid_request', msg.kind === 'voice' ? 'This voice note has no transcript yet.' : 'This message has no text.');
    const settings = this.gate(userId);
    const people = this.people(msg.conversationId, userId);
    const ctx = windowAround(this.store, msg.conversationId, messageId, 3);
    const now = new Date();
    const localDate = new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
    const weekday = new Intl.DateTimeFormat('en-US', { timeZone: tz, weekday: 'long' }).format(now);
    const sentLocal = new Intl.DateTimeFormat('en-CA', { timeZone: tz, dateStyle: 'short', timeStyle: 'short', hourCycle: 'h23' }).format(new Date(msg.createdAt));
    const transcript = renderTranscript(ctx, people, { redactContacts: settings.aiRedactContacts, selfId: userId });
    const res = await this.run(userId, 'extract_actions', {
      system: systemFor('extract', this.store.getUser(userId).preferredLanguage), tier: 'reasoning', maxTokens: 1200, jsonSchema: EXTRACTION_SCHEMA as unknown as Record<string, unknown>, signal,
      messages: [{ role: 'user', content: `${this.block(transcript)}\n\nReference: today is ${weekday} ${localDate} in timezone ${tz}. The target message was sent ${sentLocal} local time.\nExtract actions from message [#${messageId}].` }],
    });
    let parsed;
    try { parsed = parseExtraction(res.text); } catch (e) { throw new AIError('invalid_response', (e as Error).message, true); }
    const actions = parsed.result.actions.map((a) => ({ id: this.store.createAction(userId, msg.conversationId, messageId, a.type, a), status: 'proposed', ...a }));
    return { messageId, intent: parsed.result.intent, summary: parsed.result.summary, actions, rejected: parsed.rejected, timezone: tz };
  }

  /** Execute a proposed action after explicit user confirmation, optionally with user edits. */
  async confirmAction(userId: number, actionId: number, edits: Record<string, unknown> | undefined, timezone: string) {
    const action = this.store.getAction(actionId, userId);
    if (action.status !== 'proposed') throw new HttpError(409, 'conflict', `This action was already ${action.status}.`);
    this.store.getConversation(action.conversationId, userId); // still a member?
    let payload: VaaniAction;
    try { payload = validateAction({ ...action.payload, ...(edits ?? {}), type: action.type }); }
    catch (e) { if (e instanceof ActionValidationError) throw new HttpError(400, 'invalid_request', e.message); throw e; }
    const me = this.store.getUser(userId);
    try {
      let result: Record<string, unknown>;
      switch (payload.type) {
        case 'create_task': {
          const taskId = this.store.createTask(userId, action.conversationId, payload.assignee ? `${payload.title} (${payload.assignee})` : payload.title, payload.due_date, actionId);
          result = { kind: 'task', taskId };
          break;
        }
        case 'create_event': {
          const out = await this.calendar.createEvent(payload, { timezone: validTimezone(timezone), organizer: me.displayName });
          result = { kind: 'event', calendar: out };
          break;
        }
        case 'send_message': {
          const message = this.store.insertMessage({ conversationId: action.conversationId, senderId: userId, kind: 'text', body: payload.text, aiAssisted: true });
          result = { kind: 'message', message };
          break;
        }
      }
      this.store.setActionStatus(actionId, 'executed', { ...result, payload });
      return { id: actionId, status: 'executed', type: payload.type, payload, result };
    } catch (e) {
      this.store.setActionStatus(actionId, 'failed', { error: (e as Error).message });
      throw e;
    }
  }

  dismissAction(userId: number, actionId: number) {
    const a = this.store.getAction(actionId, userId);
    if (a.status !== 'proposed') throw new HttpError(409, 'conflict', `This action was already ${a.status}.`);
    this.store.setActionStatus(actionId, 'dismissed', null);
    return { id: actionId, status: 'dismissed' };
  }
}

function safeJson(text: string): unknown {
  try { return JSON.parse(text); } catch {
    const m = text.match(/\{[\s\S]*\}/);
    if (!m) return null;
    try { return JSON.parse(m[0]); } catch { return null; }
  }
}

export function validTimezone(tz: string | undefined): string {
  const z = tz || 'Asia/Kolkata';
  try { new Intl.DateTimeFormat('en-US', { timeZone: z }); return z; } catch { throw new HttpError(400, 'invalid_request', 'Unknown timezone.'); }
}
