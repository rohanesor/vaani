/**
 * The conversation as a transcript: one reading column, speaker turns instead of left/right bubbles.
 * Your turns carry the Peacock rule; others carry a quiet rule in their own hue.
 */
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { ArrowDown, LoaderCircle, RotateCw } from 'lucide-react';
import { chat } from '../state/chat';
import type { Conversation, UIMessage, User } from '../lib/types';
import { clock, dayLabel, langLabel } from '../lib/format';
import { Message } from './Message';
import { Avatar, Button, EmptyState, LangGlyph } from './ui';

interface Turn { key: string; senderId: number | null; system: boolean; day: string; messages: UIMessage[] }

function toTurns(list: UIMessage[]): Turn[] {
  const turns: Turn[] = [];
  for (const m of list) {
    const day = new Date(m.createdAt).toDateString();
    const prev = turns.at(-1);
    const lastMsg = prev?.messages.at(-1);
    const joins = prev && !prev.system && m.kind !== 'system' && prev.senderId === m.senderId && prev.day === day
      && lastMsg && new Date(m.createdAt).getTime() - new Date(lastMsg.createdAt).getTime() < 5 * 60_000;
    if (joins) prev.messages.push(m);
    else turns.push({ key: m.clientId && m.local ? `c-${m.clientId}` : `m-${m.id}`, senderId: m.senderId, system: m.kind === 'system', day, messages: [m] });
  }
  return turns;
}

export function Transcript(p: {
  id: number; conv: Conversation; me: User; messages?: UIMessage[]; loading: boolean; loadError: string | null; hasMore: boolean;
  readState: Record<number, number>; members: Map<number, User>; highlight: number | null; aiEnabled: boolean;
  onReply(m: UIMessage): void; onJump(id: number): void;
}) {
  const scroller = useRef<HTMLDivElement>(null);
  const atBottom = useRef(true);
  const prevHeight = useRef(0);
  const prevFirst = useRef<number | undefined>(undefined);
  const lastCount = useRef(0);
  const [newBelow, setNewBelow] = useState(0);
  const list = p.messages;
  const turns = useMemo(() => toTurns(list ?? []), [list]);
  const lastMineId = useMemo(() => [...(list ?? [])].reverse().find((m) => m.senderId === p.me.id)?.id, [list, p.me.id]);

  useLayoutEffect(() => {
    const el = scroller.current; if (!el || !list) return;
    const first = list[0]?.id;
    if (prevFirst.current !== undefined && first !== prevFirst.current && prevHeight.current) el.scrollTop += el.scrollHeight - prevHeight.current;
    else if (atBottom.current || (list.at(-1)?.senderId === p.me.id && list.length > lastCount.current)) { el.scrollTop = el.scrollHeight; setNewBelow(0); }
    else if (list.length > lastCount.current) setNewBelow((n) => n + (list.length - lastCount.current));
    prevFirst.current = first; prevHeight.current = el.scrollHeight; lastCount.current = list.length;
  }, [list, p.me.id]);

  useEffect(() => { atBottom.current = true; lastCount.current = 0; prevFirst.current = undefined; setNewBelow(0); }, [p.id]);
  useEffect(() => {
    const el = scroller.current; if (!el) return;
    const ro = new ResizeObserver(() => { if (atBottom.current) el.scrollTop = el.scrollHeight; });
    ro.observe(el); if (el.firstElementChild) ro.observe(el.firstElementChild);
    return () => ro.disconnect();
  }, [list === undefined]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (atBottom.current && document.visibilityState === 'visible') chat.markRead(p.id); }, [list, p.id]);

  const onScroll = () => {
    const el = scroller.current!;
    atBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
    if (atBottom.current) { setNewBelow(0); chat.markRead(p.id); }
    prevHeight.current = el.scrollHeight;
    if (el.scrollTop < 160 && p.hasMore && !p.loading) void chat.loadMessages(p.id, true);
  };
  const othersRead = (mid: number) => p.conv.members.some((m) => m.id !== p.me.id && (p.readState[m.id] ?? 0) >= mid);

  if (!list) {
    return <div className="transcript-scroll center">{p.loadError
      ? <EmptyState title="Couldn’t load messages" action={<Button icon={<RotateCw size={16} />} onClick={() => chat.loadMessages(p.id)}>Try again</Button>}>{p.loadError}</EmptyState>
      : <LoaderCircle className="spin" aria-label="Loading messages" />}</div>;
  }

  return (
    <div className="transcript-scroll" ref={scroller} onScroll={onScroll} role="log" aria-label="Messages" aria-live="polite" aria-relevant="additions">
      <div className="transcript-col">
        {p.hasMore ? (
          <div className="older">{p.loading ? <LoaderCircle size={16} className="spin" aria-label="Loading earlier messages" /> : <button className="link small" onClick={() => chat.loadMessages(p.id, true)}>Earlier messages</button>}</div>
        ) : (
          <header className="transcript-open">
            <p className="open-title">{p.conv.kind === 'group' ? p.conv.title : `You and ${p.conv.title}`}</p>
            <p className="open-sub">{p.conv.kind === 'group'
              ? `${p.conv.members.length} people · ${[...new Set(p.conv.members.map((m) => langLabel(m.preferredLanguage)))].join(', ')}`
              : 'Write or speak in any language. VAANI only reads this conversation when you ask.'}</p>
          </header>
        )}
        {list.length === 0 && <p className="transcript-empty">Say hello — in any language.</p>}

        {turns.map((t, i) => {
          const newDay = i === 0 || turns[i - 1].day !== t.day;
          const sender = t.senderId ? p.members.get(t.senderId) : undefined;
          const mine = t.senderId === p.me.id;
          return (
            <div key={t.key}>
              {newDay && <div className="day"><span>{dayLabel(t.messages[0].createdAt)}</span></div>}
              {t.system ? (
                <p className="system-line" id={`m-${t.messages[0].id}`}>{t.messages[0].body}</p>
              ) : (
                <section className={`turn${mine ? ' self' : ''}`} style={{ ['--hue' as string]: sender?.avatarHue ?? 0 }} aria-label={`${mine ? 'You' : sender?.displayName ?? 'Someone'}, ${clock(t.messages[0].createdAt)}`}>
                  <div className="turn-gutter" aria-hidden="true">{!mine && sender && <Avatar name={sender.displayName} hue={sender.avatarHue} size={32} />}</div>
                  <div className="turn-body">
                    <header className="turn-head">
                      <span className="speaker">{mine ? 'You' : sender?.displayName ?? 'Former member'}</span>
                      {!mine && sender && sender.preferredLanguage !== p.me.preferredLanguage && <LangGlyph code={sender.preferredLanguage} title={`${sender.displayName.split(' ')[0]} reads ${langLabel(sender.preferredLanguage)}`} />}
                      <time dateTime={t.messages[0].createdAt}>{clock(t.messages[0].createdAt)}</time>
                    </header>
                    {t.messages.map((m) => (
                      <Message key={m.clientId && m.local ? `c-${m.clientId}` : m.id} m={m} me={p.me} members={p.members} conversationId={p.id} mine={mine}
                        last={m.id === lastMineId || (!!m.local && m === t.messages.at(-1))} readByOthers={m.id > 0 && othersRead(m.id)}
                        highlighted={p.highlight === m.id} aiEnabled={p.aiEnabled} onReply={p.onReply} onJump={p.onJump} />
                    ))}
                  </div>
                </section>
              )}
            </div>
          );
        })}
      </div>
      {newBelow > 0 && (
        <button className="jump-latest" onClick={() => { const el = scroller.current!; el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' }); }}>
          <ArrowDown size={14} aria-hidden="true" /> {newBelow} new
        </button>
      )}
    </div>
  );
}
