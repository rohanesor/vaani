/**
 * One utterance in the transcript. Originals are never replaced: translation, explanation and plans
 * are annotations beneath the message, each attributed (Iris hairline = AI).
 */
import { memo, useEffect, useRef, useState } from 'react';
import { CornerUpLeft, Languages, SmilePlus, Ellipsis, Copy, ListChecks, Volume2, Clock, Check, CheckCheck, CircleAlert, RotateCw, Trash2, LoaderCircle, Square, X } from 'lucide-react';
import type { Translation, UIMessage, User } from '../lib/types';
import { clock, langLabel } from '../lib/format';
import { api, ApiError, fetchBlobUrl } from '../lib/api';
import { chat } from '../state/chat';
import { VoicePlayer } from './Voice';
import { InlineExplain, InlinePlans } from './AI';
import { AIMark, IconButton, Label, useToast } from './ui';

export const QUICK_REACTIONS = ['👍', '❤️', '😂', '🙏', '😮'];

interface Props {
  m: UIMessage; me: User; members: Map<number, User>; conversationId: number; mine: boolean; last: boolean;
  readByOthers: boolean; highlighted: boolean; aiEnabled: boolean;
  onReply(m: UIMessage): void; onJump(id: number): void;
}

type TState = { s: 'idle' } | { s: 'loading' } | { s: 'done'; t: Translation } | { s: 'error'; message: string };

export const Message = memo(function Message(p: Props) {
  const { m, me } = p;
  const toast = useToast();
  const [tr, setTr] = useState<TState>({ s: 'idle' });
  const [annot, setAnnot] = useState<null | 'explain' | 'plans'>(null);
  const [menu, setMenu] = useState<null | 'react' | 'more'>(null);
  const [speaking, setSpeaking] = useState<'idle' | 'loading' | 'playing'>('idle');
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const toolsRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!menu) return;
    const close = (e: MouseEvent | KeyboardEvent) => {
      if (e instanceof KeyboardEvent ? e.key === 'Escape' : !toolsRef.current?.contains(e.target as Node)) setMenu(null);
    };
    document.addEventListener('mousedown', close); document.addEventListener('keydown', close);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', close); };
  }, [menu]);
  useEffect(() => () => { audioRef.current?.pause(); }, []);

  const confirmed = m.id > 0 && !m.local;
  const hasText = !!m.body;

  async function translate() {
    if (tr.s === 'done') { setTr({ s: 'idle' }); return; }
    setTr({ s: 'loading' });
    try { setTr({ s: 'done', t: await api<Translation>('/ai/translate', { method: 'POST', json: { messageId: m.id, targetLanguage: me.preferredLanguage } }) }); }
    catch (e) { setTr({ s: 'error', message: (e as ApiError).message }); }
  }

  async function speak(which: 'original' | 'translation') {
    if (speaking === 'playing') { audioRef.current?.pause(); setSpeaking('idle'); return; }
    const isTr = which === 'translation' && tr.s === 'done';
    const text = isTr ? (tr as { t: Translation }).t.text : m.body;
    const language = isTr ? (tr as { t: Translation }).t.targetLanguage
      : m.voice?.transcriptLang ?? (tr.s === 'done' ? tr.t.sourceLanguage : null) ?? p.members.get(m.senderId ?? 0)?.preferredLanguage ?? 'en-IN';
    if (!text) return;
    setSpeaking('loading'); setMenu(null);
    try {
      const url = await fetchBlobUrl('/tts', { method: 'POST', json: { text, language: normalizeLang(language ?? 'en-IN') } });
      const a = new Audio(url); audioRef.current = a;
      a.onended = () => { setSpeaking('idle'); URL.revokeObjectURL(url); };
      await a.play(); setSpeaking('playing');
    } catch (e) { setSpeaking('idle'); toast((e as Error).message, 'error'); }
  }

  const status = p.mine && p.last && (
    m.local === 'pending' ? <span className="msg-status pending"><Clock size={12} aria-hidden="true" /> Sending</span>
      : m.local === 'failed' ? null
        : p.readByOthers ? <span className="msg-status read"><CheckCheck size={13} aria-hidden="true" /> Read</span>
          : <span className="msg-status"><Check size={13} aria-hidden="true" /> Sent</span>);

  return (
    <div className={`msg${p.highlighted ? ' highlight' : ''}${m.local ? ` local-${m.local}` : ''}`} id={`m-${m.id}`}>
      <div className="msg-body" tabIndex={0} aria-label={`${p.mine ? 'You' : p.members.get(m.senderId ?? 0)?.displayName ?? 'Message'} at ${clock(m.createdAt)}`}>
        {m.replyTo && (
          <button className="msg-quote" onClick={() => p.onJump(m.replyTo!.id)} aria-label="Go to the quoted message">
            <CornerUpLeft size={12} aria-hidden="true" />
            <span className="quote-who">{m.replyTo.senderId === me.id ? 'You' : p.members.get(m.replyTo.senderId ?? 0)?.displayName.split(' ')[0] ?? 'Message'}</span>
            <span className="quote-text" dir="auto">{m.replyTo.kind === 'voice' ? (m.replyTo.body ? `Voice: ${m.replyTo.body}` : 'Voice note') : m.replyTo.body}</span>
          </button>
        )}
        {m.kind === 'voice' && m.voice ? <VoicePlayer m={m} /> : <p className="msg-text" dir="auto">{m.body}</p>}

        {m.aiAssisted && <span className="ai-assisted" title="Drafted with VAANI, reviewed and sent by the sender"><AIMark size={11} /> Drafted with VAANI</span>}

        {tr.s === 'loading' && <p className="inline-state ai"><LoaderCircle size={13} className="spin" aria-hidden="true" /> Translating…</p>}
        {tr.s === 'error' && <p className="inline-state error"><CircleAlert size={13} aria-hidden="true" /> {tr.message} <button className="link small" onClick={translate}>Retry</button></p>}
        {tr.s === 'done' && (
          <div className="annot gloss" lang={tr.t.targetLanguage.split('-')[0]}>
            <div className="annot-head">
              <Label tone="ai">{langLabel(tr.t.sourceLanguage)} → {langLabel(tr.t.targetLanguage)} · VAANI</Label>
              <span className="annot-tools">
                <button className="link small" onClick={() => speak('translation')} aria-label="Listen to translation">{speaking === 'playing' ? <Square size={11} /> : <Volume2 size={12} />} Listen</button>
                <IconButton label="Hide translation" className="xs" onClick={() => setTr({ s: 'idle' })}><X size={14} /></IconButton>
              </span>
            </div>
            <p className="gloss-text" dir="auto">{tr.t.text}</p>
            {tr.t.note && <p className="gloss-note">{tr.t.note}</p>}
          </div>
        )}
        {annot === 'explain' && <InlineExplain conversationId={p.conversationId} messageId={m.id} onClose={() => setAnnot(null)} />}
        {annot === 'plans' && <InlinePlans messageId={m.id} onClose={() => setAnnot(null)} />}

        {m.reactions.length > 0 && (
          <div className="reactions">
            {m.reactions.map((r) => {
              const mineR = r.userIds.includes(me.id);
              return (
                <button key={r.emoji} className={`reaction${mineR ? ' mine' : ''}`} aria-pressed={mineR}
                  aria-label={`${r.emoji} ${r.userIds.length}${mineR ? ', including you. Remove' : '. Add yours'}`}
                  onClick={() => chat.react(m.id, r.emoji).catch((e) => toast(e.message, 'error'))}>
                  <span aria-hidden="true">{r.emoji}</span>{r.userIds.length > 1 && <span>{r.userIds.length}</span>}
                </button>
              );
            })}
          </div>
        )}
        {status}
      </div>

      {confirmed && (
        <div className="msg-tools" ref={toolsRef} role="toolbar" aria-label="Message actions">
          {hasText && p.aiEnabled && (
            <button className={`tool tool-ai${tr.s === 'done' ? ' on' : ''}`} onClick={translate} aria-pressed={tr.s === 'done'}>
              <Languages size={15} aria-hidden="true" /><span>Translate</span>
            </button>
          )}
          <button className="tool" aria-label="Reply" title="Reply" onClick={() => p.onReply(m)}><CornerUpLeft size={15} /></button>
          <button className="tool" aria-label="React" title="React" aria-expanded={menu === 'react'} onClick={() => setMenu(menu === 'react' ? null : 'react')}><SmilePlus size={15} /></button>
          <button className="tool" aria-label="More" title="More" aria-haspopup="menu" aria-expanded={menu === 'more'} onClick={() => setMenu(menu === 'more' ? null : 'more')}><Ellipsis size={15} /></button>
          {menu === 'react' && (
            <div className="popover reacts" role="menu" aria-label="Reactions">
              {QUICK_REACTIONS.map((e) => <button key={e} role="menuitem" aria-label={`React ${e}`} onClick={() => { setMenu(null); chat.react(m.id, e).catch((x) => toast(x.message, 'error')); }}>{e}</button>)}
            </div>
          )}
          {menu === 'more' && (
            <div className="popover menu" role="menu">
              {hasText && p.aiEnabled && <button role="menuitem" className="ai" onClick={() => { setMenu(null); setAnnot('explain'); }}><AIMark size={15} /> Explain</button>}
              {hasText && p.aiEnabled && <button role="menuitem" className="ai" onClick={() => { setMenu(null); setAnnot('plans'); }}><ListChecks size={15} /> Find plans &amp; tasks</button>}
              {hasText && <button role="menuitem" onClick={() => speak('original')}>{speaking === 'loading' ? <LoaderCircle size={15} className="spin" /> : <Volume2 size={15} />} {speaking === 'playing' ? 'Stop reading' : 'Read aloud'}</button>}
              {hasText && <button role="menuitem" onClick={async () => { setMenu(null); try { await navigator.clipboard.writeText(m.body ?? ''); toast('Copied'); } catch { toast('Couldn’t copy', 'error'); } }}><Copy size={15} /> Copy text</button>}
              {!hasText && <p className="menu-note">Nothing to copy or translate until this voice note is transcribed.</p>}
            </div>
          )}
        </div>
      )}

      {m.local === 'failed' && (
        <div className="send-failed" role="alert">
          <CircleAlert size={13} aria-hidden="true" /> <span>Not sent — {m.localError ?? 'try again.'}</span>
          <button className="link small" onClick={() => chat.retry(m.clientId!)}><RotateCw size={12} /> Retry</button>
          <button className="link small danger" onClick={() => chat.discard(m.clientId!)}><Trash2 size={12} /> Delete</button>
        </div>
      )}
    </div>
  );
});

function normalizeLang(code: string): string {
  const base = code.split('-')[0];
  return ['en', 'hi', 'ta', 'te', 'kn', 'ml', 'bn', 'gu', 'mr'].includes(base) ? `${base}-IN` : 'en-IN';
}
