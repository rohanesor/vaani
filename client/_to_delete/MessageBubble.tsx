import { memo, useEffect, useRef, useState } from 'react';
import { Reply, Languages, SmilePlus, EllipsisVertical, Copy, Sparkles, ListChecks, Volume2, Clock, Check, CheckCheck, CircleAlert, RotateCw, Trash2, LoaderCircle, Square } from 'lucide-react';
import type { Translation, UIMessage, User } from '../lib/types';
import { clock, langLabel } from '../lib/format';
import { api, ApiError, fetchBlobUrl } from '../lib/api';
import { chat } from '../state/chat';
import { VoiceNote } from './Voice';
import { useToast } from './ui';

export const QUICK_REACTIONS = ['👍', '❤️', '😂', '🙏', '😮'];

export interface BubbleHandlers {
  onReply(m: UIMessage): void;
  onExplain(m: UIMessage): void;
  onActions(m: UIMessage): void;
  onJump(id: number): void;
}

interface Props extends BubbleHandlers {
  m: UIMessage; me: User; sender?: User; showSender: boolean; groupStart: boolean; groupEnd: boolean;
  readByOthers: boolean; highlighted: boolean; isGroup: boolean; members: Map<number, User>; aiEnabled: boolean;
}

type TState = { status: 'idle' } | { status: 'loading' } | { status: 'done'; t: Translation; showOriginal: boolean } | { status: 'error'; message: string };

export const MessageBubble = memo(function MessageBubble(p: Props) {
  const { m, me } = p;
  const mine = m.senderId === me.id;
  const toast = useToast();
  const [tr, setTr] = useState<TState>({ status: 'idle' });
  const [menu, setMenu] = useState(false);
  const [picker, setPicker] = useState(false);
  const [speaking, setSpeaking] = useState<'idle' | 'loading' | 'playing'>('idle');
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!menu && !picker) return;
    const close = (e: MouseEvent | KeyboardEvent) => {
      if (e instanceof KeyboardEvent ? e.key === 'Escape' : !menuRef.current?.contains(e.target as Node)) { setMenu(false); setPicker(false); }
    };
    document.addEventListener('mousedown', close); document.addEventListener('keydown', close);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', close); };
  }, [menu, picker]);

  useEffect(() => () => { audioRef.current?.pause(); }, []);

  if (m.kind === 'system') return <div className="system-msg" id={`m-${m.id}`}><span>{m.body}</span></div>;

  const confirmed = m.id > 0 && !m.local;
  const hasText = !!m.body;

  async function translate() {
    if (tr.status === 'done') { setTr({ status: 'idle' }); return; }
    setTr({ status: 'loading' });
    try {
      const t = await api<Translation>('/ai/translate', { method: 'POST', json: { messageId: m.id, targetLanguage: me.preferredLanguage } });
      setTr({ status: 'done', t, showOriginal: false });
    } catch (e) { setTr({ status: 'error', message: (e as ApiError).message }); }
  }

  async function speak() {
    if (speaking === 'playing') { audioRef.current?.pause(); setSpeaking('idle'); return; }
    const text = tr.status === 'done' && !tr.showOriginal ? tr.t.text : m.body;
    const language = tr.status === 'done' && !tr.showOriginal ? tr.t.targetLanguage
      : (m.voice?.transcriptLang ?? (tr.status === 'done' ? tr.t.sourceLanguage : null) ?? p.sender?.preferredLanguage ?? 'en-IN');
    if (!text) return;
    setSpeaking('loading');
    try {
      const url = await fetchBlobUrl('/tts', { method: 'POST', json: { text, language: normalizeLang(language) } });
      const a = new Audio(url); audioRef.current = a;
      a.onended = () => { setSpeaking('idle'); URL.revokeObjectURL(url); };
      await a.play(); setSpeaking('playing');
    } catch (e) { setSpeaking('idle'); toast((e as Error).message, 'error'); }
  }

  const copy = async () => {
    try { await navigator.clipboard.writeText(m.body ?? ''); toast('Copied'); } catch { toast('Couldn’t copy', 'error'); }
    setMenu(false);
  };

  const ticks = mine && (m.local === 'pending' ? <Clock size={13} aria-label="Sending" />
    : m.local === 'failed' ? <CircleAlert size={13} aria-label="Not sent" className="tick-failed" />
      : p.readByOthers ? <CheckCheck size={14} aria-label="Read" className="tick-read" /> : <Check size={14} aria-label="Sent" />);

  const cls = ['msg', mine ? 'out' : 'in', p.groupStart ? 'g-start' : '', p.groupEnd ? 'g-end' : '', p.highlighted ? 'highlight' : '', m.local ? `local-${m.local}` : ''].join(' ');

  return (
    <div className={cls} id={`m-${m.id}`} data-mid={m.id}>
      {p.isGroup && !mine && p.groupStart && p.sender && <div className="msg-sender" style={{ ['--hue' as string]: p.sender.avatarHue }}>{p.sender.displayName}</div>}
      <div className="msg-row">
        <div className="bubble" tabIndex={0} aria-label={`${mine ? 'You' : p.sender?.displayName ?? 'Message'}, ${clock(m.createdAt)}`}>
          {m.replyTo && (
            <button className="quote" onClick={() => p.onJump(m.replyTo!.id)} aria-label="Go to the quoted message">
              <span className="quote-who">{m.replyTo.senderId === me.id ? 'You' : p.members.get(m.replyTo.senderId ?? 0)?.displayName ?? 'Message'}</span>
              <span className="quote-text">{m.replyTo.kind === 'voice' ? (m.replyTo.body ? `Voice note: ${m.replyTo.body}` : 'Voice note') : m.replyTo.body}</span>
            </button>
          )}
          {m.kind === 'voice' && m.voice ? <VoiceNote m={m} mine={mine} /> : (
            tr.status === 'done' && !tr.showOriginal ? null : <p className="msg-text" dir="auto">{m.body}</p>
          )}
          {tr.status === 'loading' && <p className="tr-state"><LoaderCircle size={13} className="spin" aria-hidden="true" /> Translating…</p>}
          {tr.status === 'error' && <p className="tr-state tr-error">{tr.message} <button className="link" onClick={translate}>Retry</button></p>}
          {tr.status === 'done' && (
            <div className={`translation${m.kind !== 'voice' && !tr.showOriginal ? ' replaced' : ''}`} lang={tr.t.targetLanguage.split('-')[0]}>
              {(m.kind === 'voice' || !tr.showOriginal) && <p className="msg-text" dir="auto">{tr.t.text}</p>}
              <div className="tr-meta">
                <span className="ai-tag"><Sparkles size={11} aria-hidden="true" /> {tr.showOriginal ? 'Original' : `Translated from ${langLabel(tr.t.sourceLanguage)}`}</span>
                {m.kind !== 'voice' && <button className="link" onClick={() => setTr({ ...tr, showOriginal: !tr.showOriginal })}>{tr.showOriginal ? 'Show translation' : 'Show original'}</button>}
              </div>
              {tr.t.note && !tr.showOriginal && <p className="tr-note">{tr.t.note}</p>}
            </div>
          )}
          {m.reactions.length > 0 && (
            <div className="reactions">
              {m.reactions.map((r) => (
                <button key={r.emoji} className={`reaction${r.userIds.includes(me.id) ? ' mine' : ''}`} onClick={() => chat.react(m.id, r.emoji).catch((e) => toast(e.message, 'error'))}
                  aria-label={`${r.emoji} ${r.userIds.length}${r.userIds.includes(me.id) ? ', including you. Remove' : '. Add'}`} aria-pressed={r.userIds.includes(me.id)}>
                  <span aria-hidden="true">{r.emoji}</span>{r.userIds.length > 1 && <span>{r.userIds.length}</span>}
                </button>
              ))}
            </div>
          )}
          <div className="msg-meta">
            {m.aiAssisted && <span className="ai-tag subtle" title="Drafted with VAANI AI, reviewed and sent by the sender"><Sparkles size={10} aria-hidden="true" /> AI-assisted</span>}
            <time dateTime={m.createdAt}>{clock(m.createdAt)}</time>
            {ticks}
          </div>
        </div>

        {confirmed && (
          <div className="msg-tools" ref={menuRef}>
            <button className="tool" aria-label="React" aria-expanded={picker} onClick={() => { setPicker(!picker); setMenu(false); }}><SmilePlus size={16} /></button>
            <button className="tool" aria-label="Reply" onClick={() => p.onReply(m)}><Reply size={16} /></button>
            {hasText && p.aiEnabled && <button className="tool" aria-label={tr.status === 'done' ? 'Hide translation' : `Translate to ${langLabel(me.preferredLanguage)}`} aria-pressed={tr.status === 'done'} onClick={translate}><Languages size={16} /></button>}
            <button className="tool" aria-label="More actions" aria-haspopup="menu" aria-expanded={menu} onClick={() => { setMenu(!menu); setPicker(false); }}><EllipsisVertical size={16} /></button>
            {picker && (
              <div className="popover react-picker" role="menu" aria-label="Reactions">
                {QUICK_REACTIONS.map((e) => <button key={e} role="menuitem" aria-label={`React ${e}`} onClick={() => { setPicker(false); chat.react(m.id, e).catch((x) => toast(x.message, 'error')); }}>{e}</button>)}
              </div>
            )}
            {menu && (
              <div className="popover menu" role="menu">
                {hasText && <button role="menuitem" onClick={copy}><Copy size={15} /> Copy text</button>}
                {hasText && p.aiEnabled && <button role="menuitem" onClick={() => { setMenu(false); p.onExplain(m); }}><Sparkles size={15} /> Explain this message</button>}
                {hasText && p.aiEnabled && <button role="menuitem" onClick={() => { setMenu(false); p.onActions(m); }}><ListChecks size={15} /> Find plans &amp; tasks</button>}
                {hasText && <button role="menuitem" onClick={() => { setMenu(false); void speak(); }}>{speaking === 'playing' ? <><Square size={15} /> Stop reading</> : speaking === 'loading' ? <><LoaderCircle size={15} className="spin" /> Preparing audio…</> : <><Volume2 size={15} /> Read aloud</>}</button>}
              </div>
            )}
          </div>
        )}
      </div>
      {m.local === 'failed' && (
        <div className="send-failed" role="alert">
          <span>{m.localError ?? 'Not sent.'}</span>
          <button className="link" onClick={() => chat.retry(m.clientId!)}><RotateCw size={12} /> Retry</button>
          <button className="link danger" onClick={() => chat.discard(m.clientId!)}><Trash2 size={12} /> Delete</button>
        </div>
      )}
    </div>
  );
});

/** Map detector output like "ta" or "ta-IN" onto a supported TTS code. */
function normalizeLang(code: string): string {
  const base = code.split('-')[0];
  const supported = ['en', 'hi', 'ta', 'te', 'kn', 'ml', 'bn', 'gu', 'mr'];
  return supported.includes(base) ? `${base}-IN` : 'en-IN';
}
