/**
 * VAANI's AI surfaces. One rule: AI is an annotation layer on the conversation (Iris hairline + label),
 * never a chatbot. Every result says what it read, and nothing is sent without the user.
 */
import { Fragment, useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Copy, RotateCw, Square, X, ArrowUp, ShieldCheck, CircleAlert, CalendarPlus, ListTodo, Send, Check, Settings2, PenLine } from 'lucide-react';
import { api, ApiError, streamSSE } from '../lib/api';
import type { ContextInfo, Extraction, ProposedAction, Summary } from '../lib/types';
import { go } from '../lib/router';
import { timezone } from '../lib/format';
import { AIMark, Banner, Button, IconButton, Label, useToast } from './ui';

export type AssistOp = 'reply' | 'rewrite' | 'explain' | 'ask';
export interface AssistInput { op: AssistOp; messageId?: number; draft?: string; style?: string; instruction?: string; question?: string }
export type Phase = 'idle' | 'processing' | 'streaming' | 'done' | 'error' | 'cancelled';
export interface AIErr { code: string; message: string; retryable?: boolean }

export const STYLE_LABEL: Record<string, string> = { professional: 'Professional', shorter: 'Shorter', friendlier: 'Warmer', clearer: 'Clearer', formal: 'Formal' };

export function redactionText(r: ContextInfo['redactions']) {
  const n = r.emails + r.phones + r.numbers + r.secrets;
  return n ? `${n} private detail${n > 1 ? 's' : ''} hidden` : 'nothing private found';
}

/** Streams one /ai/assist request. Idle → processing → streaming → done | error | cancelled. */
export function useAssist(conversationId: number) {
  const [phase, setPhase] = useState<Phase>('idle');
  const [text, setText] = useState('');
  const [ctx, setCtx] = useState<ContextInfo | null>(null);
  const [err, setErr] = useState<AIErr | null>(null);
  const abort = useRef<AbortController | null>(null);
  const last = useRef<AssistInput | null>(null);

  const run = useCallback(async (input?: AssistInput) => {
    const req = input ?? last.current; if (!req) return;
    last.current = req;
    abort.current?.abort();
    const ac = new AbortController(); abort.current = ac;
    setPhase('processing'); setText(''); setErr(null); setCtx(null);
    let ended = false;
    try {
      await streamSSE('/ai/assist', { conversationId, ...req }, (ev, data) => {
        if (ev === 'context') setCtx(data);
        else if (ev === 'delta') { setPhase('streaming'); setText((t) => t + data.text); }
        else if (ev === 'done') { ended = true; setText(data.text); setPhase('done'); }
        else if (ev === 'error') { ended = true; setErr({ code: data.code, message: data.message, retryable: data.details?.retryable }); setPhase('error'); }
      }, ac.signal);
      if (!ended) { setErr({ code: 'ai_invalid_response', message: 'The answer ended early.' }); setPhase('error'); }
    } catch (e) {
      if ((e as Error).name === 'AbortError') { setPhase('cancelled'); return; }
      const a = e as ApiError; setErr({ code: a.code, message: a.message }); setPhase('error');
    }
  }, [conversationId]);

  const stop = useCallback(() => abort.current?.abort(), []);
  const reset = useCallback(() => { abort.current?.abort(); setPhase('idle'); setText(''); setErr(null); setCtx(null); }, []);
  useEffect(() => () => abort.current?.abort(), []);
  return { phase, text, ctx, err, run, stop, reset, busy: phase === 'processing' || phase === 'streaming' };
}

/** “[#12]” citations → buttons that jump to the cited message. */
export function Cited({ text, onJump }: { text: string; onJump: (id: number) => void }) {
  const parts = text.split(/(\[#\d+\](?:\s*\[#\d+\])*)/g);
  return <>{parts.map((p, i) => {
    if (!/^\[#\d+\]/.test(p)) return <Fragment key={i}>{p}</Fragment>;
    return <Fragment key={i}>{[...p.matchAll(/#(\d+)/g)].map((x) => Number(x[1])).map((id) => (
      <button key={id} className="cite" onClick={() => onJump(id)} aria-label={`Go to message ${id}`}>{id}</button>))}</Fragment>;
  })}</>;
}

export function Thinking({ ctx, children }: { ctx?: ContextInfo | null; children?: ReactNode }) {
  return <p className="thinking"><AIMark size={14} className="orbit" /> {children ?? (ctx ? 'Writing…' : 'Reading the conversation…')}</p>;
}

export function ContextLine({ ctx }: { ctx: ContextInfo }) {
  return <p className="ctx-line"><ShieldCheck size={12} aria-hidden="true" /> Read {ctx.messageCount} message{ctx.messageCount === 1 ? '' : 's'} from this chat · {redactionText(ctx.redactions)} · Claude</p>;
}

export function AIErrorView({ err, onRetry, compact }: { err: AIErr; onRetry?: () => void; compact?: boolean }) {
  const known: Record<string, { title: string; body: ReactNode; action?: ReactNode }> = {
    ai_not_configured: { title: 'AI isn’t set up on this server', body: 'An administrator needs to add ANTHROPIC_API_KEY. Messaging is unaffected.' },
    ai_disabled: { title: 'AI assistance is off', body: 'Nothing is shared with an AI provider while it’s off.', action: <Button size="sm" icon={<Settings2 size={14} />} onClick={() => go('/settings/privacy')}>Privacy & AI</Button> },
    ai_rate_limited: { title: 'Too many requests', body: err.message },
    ai_budget_exceeded: { title: 'Daily AI limit reached', body: err.message },
    ai_timeout: { title: 'That took too long', body: 'No answer in time. Try again.' },
    ai_invalid_response: { title: 'Unexpected answer', body: 'VAANI couldn’t use what came back. Try again.' },
    network: { title: 'You’re offline', body: 'Reconnect to use AI.' },
  };
  const k = known[err.code] ?? { title: 'Couldn’t complete that', body: err.message };
  const retryable = onRetry && !['ai_not_configured', 'ai_disabled', 'ai_budget_exceeded'].includes(err.code);
  return (
    <div className={`ai-error${compact ? ' compact' : ''}`} role="alert">
      <CircleAlert size={16} aria-hidden="true" />
      <div><strong>{k.title}.</strong> <span>{k.body}</span>
        {(k.action || retryable) && <div className="row gap-2 mt-2">{k.action}{retryable && <Button size="sm" icon={<RotateCw size={14} />} onClick={onRetry}>Try again</Button>}</div>}
      </div>
    </div>
  );
}

/* ================= Inline explanation (annotation under a message) ================= */
export function InlineExplain({ conversationId, messageId, onClose }: { conversationId: number; messageId: number; onClose: () => void }) {
  const a = useAssist(conversationId);
  const toast = useToast();
  useEffect(() => { void a.run({ op: 'explain', messageId }); }, [messageId]); // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <div className="annot annot-ai" aria-live="polite">
      <div className="annot-head">
        <Label tone="ai"><AIMark size={12} /> Explained by VAANI</Label>
        <span className="annot-tools">
          {a.busy && <button className="link small" onClick={a.stop}><Square size={11} /> Stop</button>}
          {a.phase === 'done' && <button className="link small" onClick={async () => { try { await navigator.clipboard.writeText(a.text); toast('Copied'); } catch { toast('Couldn’t copy', 'error'); } }}><Copy size={12} /> Copy</button>}
          <IconButton label="Close explanation" className="xs" onClick={() => { a.stop(); onClose(); }}><X size={14} /></IconButton>
        </span>
      </div>
      {a.phase === 'processing' && <Thinking ctx={a.ctx} />}
      {a.text && <p className={`annot-text${a.phase === 'streaming' ? ' caret' : ''}`} dir="auto">{a.text}</p>}
      {a.phase === 'cancelled' && <p className="inline-state">Stopped.</p>}
      {a.phase === 'error' && a.err && <AIErrorView err={a.err} onRetry={() => a.run()} compact />}
    </div>
  );
}

/* ================= Draft proposal (above the composer) ================= */
export function DraftProposal({ conversationId, request, onUse, onClose }: {
  conversationId: number; request: AssistInput & { label: string }; onUse: (text: string) => void; onClose: () => void;
}) {
  const a = useAssist(conversationId);
  const toast = useToast();
  useEffect(() => { void a.run(request); }, [request]); // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <section className="proposal" aria-label="VAANI draft" aria-live="polite">
      <header className="proposal-head">
        <Label tone="ai"><AIMark size={12} /> VAANI draft · {request.label}</Label>
        <IconButton label="Discard draft" className="xs" onClick={() => { a.stop(); onClose(); }}><X size={14} /></IconButton>
      </header>
      {request.draft && <p className="proposal-src" dir="auto"><span className="sr-only">Your draft: </span>{request.draft}</p>}
      {a.phase === 'processing' && <Thinking ctx={a.ctx} />}
      {a.text && <p className={`proposal-text${a.phase === 'streaming' ? ' caret' : ''}`} dir="auto">{a.text}</p>}
      {a.phase === 'cancelled' && !a.text && <p className="inline-state">Stopped.</p>}
      {a.phase === 'error' && a.err && <AIErrorView err={a.err} onRetry={() => a.run()} compact />}
      {a.ctx && a.phase !== 'error' && <ContextLine ctx={a.ctx} />}
      <footer className="proposal-foot">
        {a.busy ? <Button size="sm" icon={<Square size={13} />} onClick={a.stop}>Stop</Button> : (a.phase === 'done' || (a.phase === 'cancelled' && a.text)) && (
          <>
            <Button size="sm" variant="ai" icon={<PenLine size={14} />} onClick={() => onUse(a.text)}>Use &amp; edit</Button>
            <Button size="sm" variant="ghost" icon={<RotateCw size={14} />} onClick={() => a.run()}>Regenerate</Button>
            <Button size="sm" variant="ghost" icon={<Copy size={14} />} onClick={async () => { try { await navigator.clipboard.writeText(a.text); toast('Copied'); } catch { toast('Couldn’t copy', 'error'); } }}>Copy</Button>
            <span className="proposal-note">You send it — VAANI never does.</span>
          </>
        )}
      </footer>
    </section>
  );
}

/* ================= Context pane: Brief (summary) + Ask ================= */
interface QA { id: number; q: string }
export function ContextPane({ conversationId, tab, setTab, onClose, onJump }: {
  conversationId: number; tab: 'brief' | 'ask'; setTab: (t: 'brief' | 'ask') => void; onClose: () => void; onJump: (id: number) => void;
}) {
  return (
    <section className="ctx-pane" aria-label="VAANI context">
      <header className="ctx-head">
        <div className="ctx-title"><AIMark size={18} /><span>Context</span></div>
        <div className="ctx-tabs" role="tablist" aria-label="Context">
          <button role="tab" aria-selected={tab === 'brief'} className={tab === 'brief' ? 'on' : ''} onClick={() => setTab('brief')}>Brief</button>
          <button role="tab" aria-selected={tab === 'ask'} className={tab === 'ask' ? 'on' : ''} onClick={() => setTab('ask')}>Ask</button>
        </div>
        <IconButton label="Close context" onClick={onClose}><X size={18} /></IconButton>
      </header>
      <p className="ctx-scope"><ShieldCheck size={12} aria-hidden="true" /> Uses only this conversation. Contact details are hidden before anything is sent.</p>
      {tab === 'brief' ? <Brief conversationId={conversationId} onJump={onJump} /> : <Ask conversationId={conversationId} onJump={onJump} />}
    </section>
  );
}

function Brief({ conversationId, onJump }: { conversationId: number; onJump: (id: number) => void }) {
  const [state, setState] = useState<{ s: 'loading' } | { s: 'done'; data: Summary } | { s: 'error'; err: AIErr }>({ s: 'loading' });
  const abort = useRef<AbortController | null>(null);
  const load = useCallback(async () => {
    abort.current?.abort(); const ac = new AbortController(); abort.current = ac;
    setState({ s: 'loading' });
    try { setState({ s: 'done', data: await api<Summary>('/ai/summarize', { method: 'POST', json: { conversationId }, signal: ac.signal }) }); }
    catch (e) { if ((e as Error).name !== 'AbortError') setState({ s: 'error', err: { code: (e as ApiError).code, message: (e as Error).message } }); }
  }, [conversationId]);
  useEffect(() => { void load(); return () => abort.current?.abort(); }, [load]);
  const Refs = ({ ids }: { ids: number[] }) => ids.length ? <span className="refs">{ids.map((id) => <button key={id} className="cite" onClick={() => onJump(id)} aria-label={`Go to message ${id}`}>{id}</button>)}</span> : null;

  return (
    <div className="ctx-body" aria-live="polite" aria-busy={state.s === 'loading'}>
      {state.s === 'loading' && <div className="stack gap-3"><Thinking>Reading the conversation…</Thinking><span className="sk sk-line" /><span className="sk sk-line" /><span className="sk sk-line short" /></div>}
      {state.s === 'error' && <AIErrorView err={state.err} onRetry={load} />}
      {state.s === 'done' && (
        <article className="brief">
          <p className="brief-lead" dir="auto">{state.data.summary}</p>
          <p className="ctx-line"><ShieldCheck size={12} aria-hidden="true" /> From the last {state.data.messageRange.count} messages · {redactionText(state.data.redactions)}</p>
          <section><Label as="h3">Decided</Label>
            {state.data.decisions.length ? <ul className="brief-list">{state.data.decisions.map((d, i) => (
              <li key={i} className={d.status}><span className={`pill ${d.status}`}>{d.status === 'confirmed' ? <><Check size={11} /> Agreed</> : 'Suggested'}</span> <span dir="auto">{d.text}</span> <Refs ids={d.messageIds} /></li>))}</ul>
              : <p className="muted small">No decisions yet.</p>}
          </section>
          <section><Label as="h3">To do</Label>
            {state.data.tasks.length ? <ul className="brief-list">{state.data.tasks.map((t, i) => (
              <li key={i}><span dir="auto">{t.text}</span>{(t.owner || t.due) && <span className="muted"> — {[t.owner, t.due && `due ${t.due}`].filter(Boolean).join(', ')}</span>} <Refs ids={t.messageIds} /></li>))}</ul>
              : <p className="muted small">No tasks mentioned.</p>}
          </section>
          <section><Label as="h3">Still open</Label>
            {state.data.openQuestions.length ? <ul className="brief-list">{state.data.openQuestions.map((q, i) => <li key={i}><span dir="auto">{q.text}</span> <Refs ids={q.messageIds} /></li>)}</ul>
              : <p className="muted small">Nothing unresolved.</p>}
          </section>
          {state.data.keyPoints.length > 0 && <section><Label as="h3">Also</Label><ul className="brief-list">{state.data.keyPoints.map((k, i) => <li key={i} dir="auto">{k}</li>)}</ul></section>}
          <footer className="brief-foot"><span className="muted small">Written by Claude from the messages linked above.</span><Button size="sm" variant="ghost" icon={<RotateCw size={14} />} onClick={load}>Refresh</Button></footer>
        </article>
      )}
    </div>
  );
}

function Ask({ conversationId, onJump }: { conversationId: number; onJump: (id: number) => void }) {
  const [thread, setThread] = useState<QA[]>([]);
  const [q, setQ] = useState('');
  const [busy, setBusy] = useState(false);
  const end = useRef<HTMLDivElement>(null);
  const ask = (question: string) => { setThread((t) => [...t, { id: Date.now(), q: question }]); setQ(''); };
  useEffect(() => { end.current?.scrollIntoView({ block: 'end' }); }, [thread.length]);
  return (
    <>
      <div className="ctx-body">
        {thread.length === 0 && (
          <div className="ask-empty">
            <p className="muted">Ask anything about this conversation. Answers point to the messages they rely on.</p>
            <div className="chips">{['What did we decide?', 'What’s still unresolved?', 'Any deadlines?'].map((s) => <button key={s} className="chip" onClick={() => ask(s)}>{s}</button>)}</div>
          </div>
        )}
        {thread.map((qa) => <Answer key={qa.id} conversationId={conversationId} question={qa.q} onJump={onJump} onBusy={setBusy} />)}
        <div ref={end} />
      </div>
      <form className="ask-form" onSubmit={(e) => { e.preventDefault(); if (q.trim() && !busy) ask(q.trim()); }}>
        <label htmlFor="ask-q" className="sr-only">Ask about this conversation</label>
        <input id="ask-q" placeholder="Ask about this conversation…" value={q} maxLength={500} onChange={(e) => setQ(e.target.value)} />
        <IconButton label="Ask" tone="ai" type="submit" disabled={!q.trim() || busy}><ArrowUp size={16} /></IconButton>
      </form>
    </>
  );
}

function Answer({ conversationId, question, onJump, onBusy }: { conversationId: number; question: string; onJump: (id: number) => void; onBusy: (b: boolean) => void }) {
  const a = useAssist(conversationId);
  const toast = useToast();
  useEffect(() => { void a.run({ op: 'ask', question }); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => onBusy(a.busy), [a.busy, onBusy]);
  return (
    <div className="qa">
      <p className="qa-q" dir="auto">{question}</p>
      <div className="annot annot-ai">
        {a.phase === 'processing' && <Thinking ctx={a.ctx} />}
        {a.text && <p className={`annot-text${a.phase === 'streaming' ? ' caret' : ''}`} dir="auto"><Cited text={a.text} onJump={onJump} /></p>}
        {a.phase === 'error' && a.err && <AIErrorView err={a.err} onRetry={() => a.run()} compact />}
        {a.phase === 'cancelled' && <p className="inline-state">Stopped.</p>}
        <div className="annot-foot">
          {a.ctx && <ContextLine ctx={a.ctx} />}
          {a.busy && <button className="link small" onClick={a.stop}><Square size={11} /> Stop</button>}
          {a.phase === 'done' && <button className="link small" onClick={async () => { try { await navigator.clipboard.writeText(a.text); toast('Copied'); } catch { toast('Couldn’t copy', 'error'); } }}><Copy size={12} /> Copy</button>}
        </div>
      </div>
    </div>
  );
}

/* ================= Plans found in a message (voice-to-action, inline) ================= */
export function InlinePlans({ messageId, onClose }: { messageId: number; onClose: () => void }) {
  const [state, setState] = useState<{ s: 'loading' } | { s: 'done'; data: Extraction } | { s: 'error'; err: AIErr }>({ s: 'loading' });
  const load = useCallback(async () => {
    setState({ s: 'loading' });
    try { setState({ s: 'done', data: await api<Extraction>('/ai/actions/extract', { method: 'POST', json: { messageId, timezone: timezone() } }) }); }
    catch (e) { setState({ s: 'error', err: { code: (e as ApiError).code, message: (e as Error).message } }); }
  }, [messageId]);
  useEffect(() => { void load(); }, [load]);
  return (
    <div className="annot annot-ai" aria-live="polite">
      <div className="annot-head">
        <Label tone="ai"><AIMark size={12} /> Plans found by VAANI</Label>
        <IconButton label="Close plans" className="xs" onClick={onClose}><X size={14} /></IconButton>
      </div>
      {state.s === 'loading' && <Thinking>Looking for dates, plans and to-dos…</Thinking>}
      {state.s === 'error' && <AIErrorView err={state.err} onRetry={load} compact />}
      {state.s === 'done' && (
        <>
          <p className="annot-text" dir="auto"><strong>{state.data.intent}</strong>{state.data.summary && ` — ${state.data.summary}`}</p>
          {state.data.actions.length === 0 ? <p className="inline-state">Nothing to schedule or do here.</p>
            : <div className="plans">{state.data.actions.map((a) => <PlanCard key={a.id} a={a} />)}</div>}
          {state.data.rejected.length > 0 && <p className="inline-state">{state.data.rejected.length} suggestion{state.data.rejected.length > 1 ? 's' : ''} skipped — details were missing.</p>}
          <p className="inline-state">Nothing happens until you confirm.</p>
        </>
      )}
    </div>
  );
}

function PlanCard({ a }: { a: ProposedAction }) {
  const [draft, setDraft] = useState(a);
  const [st, setSt] = useState<{ s: 'idle' | 'busy' | 'dismissed' } | { s: 'done'; label: string } | { s: 'error'; message: string }>({ s: 'idle' });
  const toast = useToast();
  const set = (patch: Partial<ProposedAction>) => setDraft({ ...draft, ...patch } as ProposedAction);
  async function confirm() {
    setSt({ s: 'busy' });
    const edits = draft.type === 'create_event' ? { title: draft.title, date: draft.date, time: draft.time || null }
      : draft.type === 'create_task' ? { title: draft.title, due_date: draft.due_date || null } : { text: draft.text };
    try {
      const out = await api<{ result: { kind: string; calendar?: { filename: string; content: string } } }>(`/ai/actions/${a.id}/confirm`, { method: 'POST', json: { edits, timezone: timezone() } });
      if (out.result.calendar) {
        const url = URL.createObjectURL(new Blob([out.result.calendar.content], { type: 'text/calendar' }));
        const link = document.createElement('a'); link.href = url; link.download = out.result.calendar.filename; link.click();
        setTimeout(() => URL.revokeObjectURL(url), 2000);
        setSt({ s: 'done', label: 'Calendar file downloaded' });
      } else if (out.result.kind === 'task') { setSt({ s: 'done', label: 'Added to Companion' }); toast('Added to your plans', 'success'); }
      else setSt({ s: 'done', label: 'Sent' });
    } catch (e) { setSt({ s: 'error', message: (e as Error).message }); }
  }
  async function dismiss() {
    try { await api(`/ai/actions/${a.id}/dismiss`, { method: 'POST', json: {} }); setSt({ s: 'dismissed' }); } catch (e) { setSt({ s: 'error', message: (e as Error).message }); }
  }
  if (st.s === 'dismissed') return null;
  const Icon = a.type === 'create_event' ? CalendarPlus : a.type === 'create_task' ? ListTodo : Send;
  const kind = a.type === 'create_event' ? 'Event' : a.type === 'create_task' ? 'Task' : 'Message';
  const locked = st.s === 'busy' || st.s === 'done';
  const id = `plan-${a.id}`;
  return (
    <article className={`plan${st.s === 'done' ? ' done' : ''}`} aria-labelledby={`${id}-k`}>
      <span className="plan-kind" id={`${id}-k`}><Icon size={14} aria-hidden="true" /> {kind}</span>
      <fieldset disabled={locked} className="plan-fields">
        {draft.type === 'send_message'
          ? <><label htmlFor={`${id}-t`} className="sr-only">Message</label><textarea id={`${id}-t`} rows={2} value={draft.text} onChange={(e) => set({ text: e.target.value })} /></>
          : <><label htmlFor={`${id}-title`} className="sr-only">Title</label><input id={`${id}-title`} value={draft.title} onChange={(e) => set({ title: e.target.value })} /></>}
        {draft.type === 'create_event' && <div className="row gap-2">
          <label className="mini-field"><span>Date</span><input type="date" value={draft.date} onChange={(e) => set({ date: e.target.value })} /></label>
          <label className="mini-field"><span>Time</span><input type="time" value={draft.time ?? ''} onChange={(e) => set({ time: e.target.value || null })} /></label>
        </div>}
        {draft.type === 'create_task' && <label className="mini-field"><span>Due</span><input type="date" value={draft.due_date ?? ''} onChange={(e) => set({ due_date: e.target.value || null })} /></label>}
      </fieldset>
      {st.s === 'error' && <Banner tone="error">{st.message}</Banner>}
      {st.s === 'done' ? <p className="plan-done"><Check size={14} aria-hidden="true" /> {st.label}</p> : (
        <div className="plan-actions">
          <Button size="sm" variant="ghost" onClick={dismiss} disabled={locked}>Dismiss</Button>
          <Button size="sm" variant="primary" onClick={confirm} loading={st.s === 'busy'}>{a.type === 'create_event' ? 'Add to calendar' : a.type === 'create_task' ? 'Add task' : 'Send'}</Button>
        </div>
      )}
    </article>
  );
}
