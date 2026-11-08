/**
 * The composer “stage”: text, voice and VAANI live in one instrument.
 * VAANI key → a strip of contextual AI actions; drafts appear above as a proposal you adopt and edit.
 */
import { useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react';
import { ArrowUp, CornerUpLeft, X } from 'lucide-react';
import { chat } from '../state/chat';
import type { Conversation, UIMessage, User } from '../lib/types';
import { langLabel } from '../lib/format';
import { VoiceStage } from './Voice';
import { DraftProposal, STYLE_LABEL, type AssistInput } from './AI';
import { AIMark, Banner, IconButton, VoiceMark } from './ui';

export function Composer({ conv, me, members, replyTo, clearReply, aiEnabled, onContext }: {
  conv: Conversation; me: User; members: Map<number, User>; replyTo: UIMessage | null; clearReply(): void; aiEnabled: boolean;
  onContext(tab: 'brief' | 'ask'): void;
}) {
  const [text, setText] = useState('');
  const [aiSource, setAiSource] = useState<string | null>(null);
  const aiDrafted = !!aiSource && overlap(text, aiSource) >= 0.5;
  const [recording, setRecording] = useState(false);
  const [strip, setStrip] = useState(false);
  const [proposal, setProposal] = useState<(AssistInput & { label: string }) | null>(null);
  const ta = useRef<HTMLTextAreaElement>(null);

  useEffect(() => { setText(''); setAiSource(null); setProposal(null); setStrip(false); setRecording(false); }, [conv.id]);
  useEffect(() => { if (replyTo) ta.current?.focus(); }, [replyTo]);
  useLayoutEffect(() => {
    const el = ta.current; if (!el) return;
    const fit = () => { el.style.height = 'auto'; el.style.height = `${Math.min(el.scrollHeight, 176)}px`; };
    fit();
    const ro = new ResizeObserver(fit); ro.observe(el.parentElement!);
    return () => ro.disconnect();
  }, [text, recording]);

  const other = conv.kind === 'direct' ? conv.members.find((m) => m.id !== me.id) : undefined;
  const targetLang = other && other.preferredLanguage !== me.preferredLanguage ? other.preferredLanguage : null;

  function send() {
    const body = text.trim();
    if (!body || body.length > 4000) return;
    // Attribute to VAANI only if the sent text still substantially is the AI draft.
    const aiAssisted = !!aiSource && overlap(body, aiSource) >= 0.5;
    chat.sendText(conv.id, body, { replyTo: replyTo?.id ?? null, aiAssisted });
    setText(''); setAiSource(null); clearReply();
  }
  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); send(); }
    if (e.key === 'Escape' && strip) setStrip(false);
  };
  const propose = (r: AssistInput & { label: string }) => { setProposal({ ...r }); setStrip(false); };
  const replyName = replyTo ? (replyTo.senderId === me.id ? 'yourself' : members.get(replyTo.senderId ?? 0)?.displayName.split(' ')[0] ?? 'message') : '';
  const draft = text.trim();

  return (
    <div className="composer">
      {proposal && (
        <DraftProposal conversationId={conv.id} request={proposal} onClose={() => setProposal(null)}
          onUse={(t) => { setText(t); setAiSource(t); setProposal(null); requestAnimationFrame(() => ta.current?.focus()); }} />
      )}

      {strip && aiEnabled && !recording && (
        <div className="ai-strip" role="toolbar" aria-label="VAANI actions">
          <button className="strip-chip" onClick={() => propose({ op: 'reply', messageId: replyTo?.id, label: replyTo ? `reply to ${replyName}` : 'reply' })}>Draft a reply</button>
          {Object.entries(STYLE_LABEL).map(([k, v]) => (
            <button key={k} className="strip-chip" disabled={!draft} title={draft ? undefined : 'Type a draft first'} onClick={() => propose({ op: 'rewrite', draft, style: k, label: v.toLowerCase() })}>{v}</button>
          ))}
          {targetLang && <button className="strip-chip" disabled={!draft} title={draft ? undefined : 'Type a draft first'}
            onClick={() => propose({ op: 'rewrite', draft, instruction: `Translate the draft into ${langLabel(targetLang)} (${targetLang}), natural and conversational. Keep names as they are.`, label: `in ${langLabel(targetLang)}` })}>
            In {langLabel(targetLang)}</button>}
          <span className="strip-sep" aria-hidden="true" />
          <button className="strip-chip quiet" onClick={() => { setStrip(false); onContext('brief'); }}>Brief me</button>
          <button className="strip-chip quiet" onClick={() => { setStrip(false); onContext('ask'); }}>Ask…</button>
        </div>
      )}

      {replyTo && !recording && (
        <div className="reply-ctx">
          <CornerUpLeft size={14} aria-hidden="true" />
          <div className="reply-ctx-text"><span>Replying to {replyName}</span><p dir="auto">{replyTo.kind === 'voice' ? (replyTo.body ?? 'Voice note') : replyTo.body}</p></div>
          <IconButton label="Cancel reply" className="xs" onClick={clearReply}><X size={14} /></IconButton>
        </div>
      )}
      {aiDrafted && text && <p className="drafted-note"><AIMark size={12} /> Drafted with VAANI — edit freely. Others will see “Drafted with VAANI”.</p>}
      {text.length > 3800 && <Banner tone={text.length > 4000 ? 'error' : 'warning'}>{text.length} / 4000 characters</Banner>}

      {recording ? (
        <VoiceStage conversationId={conv.id} replyTo={replyTo?.id ?? null} onDone={() => { setRecording(false); clearReply(); }} />
      ) : (
        <div className={`stage${strip ? ' ai-open' : ''}`}>
          {aiEnabled && (
            <button className={`vaani-key${strip ? ' on' : ''}`} aria-pressed={strip} aria-label="VAANI actions" onClick={() => setStrip(!strip)}>
              <AIMark size={16} /><span className="vaani-key-label">VAANI</span>
            </button>
          )}
          <label htmlFor="composer-input" className="sr-only">Message {conv.title}</label>
          <textarea id="composer-input" ref={ta} rows={1} dir="auto" placeholder={`Message ${conv.kind === 'group' ? conv.title : conv.title.split(' ')[0]}`}
            value={text} onKeyDown={onKey}
            onChange={(e) => { setText(e.target.value); if (!e.target.value) setAiSource(null); chat.typing(conv.id); }} />
          {draft ? (
            <button className="send-key" onClick={send} disabled={text.length > 4000} aria-label="Send"><ArrowUp size={20} /></button>
          ) : (
            <button className="voice-key" onClick={() => setRecording(true)} aria-label="Record a voice note"><VoiceMark size={18} /><span className="voice-key-label">Speak</span></button>
          )}
        </div>
      )}
      {!recording && <p className="stage-hint only-wide">Enter to send · Shift + Enter for a new line</p>}
    </div>
  );
}

/** Share of the AI draft's words still present in the text (0..1). */
function overlap(text: string, ai: string): number {
  const words = (s: string) => new Set(s.toLowerCase().match(/[\p{L}\p{M}\p{N}]+/gu) ?? []);
  const a = words(ai); if (!a.size) return 0;
  const t = words(text); let hit = 0;
  for (const w of a) if (t.has(w)) hit++;
  return hit / a.size;
}
