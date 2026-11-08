import { useMemo, useState } from 'react';
import { Search, RefreshCw, Users, ArrowRight } from 'lucide-react';
import { chat, useChat } from '../state/chat';
import { useSession } from '../state/session';
import { go } from '../lib/router';
import { duration, langLabel, listTime } from '../lib/format';
import type { Conversation } from '../lib/types';
import { Avatar, Button, EmptyState, LangGlyph, VoiceMark } from './ui';

type Filter = 'all' | 'people' | 'groups' | 'unread';
const FILTER_KEY = 'vaani.listFilter';

export function ConversationList({ activeId }: { activeId?: number }) {
  const { user } = useSession();
  const me = user!;
  const { conversations, conversationsLoaded, conversationsError, online, typing } = useChat((s) => s);
  const [q, setQ] = useState('');
  const [filter, setFilterState] = useState<Filter>(() => { try { return (localStorage.getItem(FILTER_KEY) as Filter) || 'all'; } catch { return 'all'; } });
  const setFilter = (f: Filter) => { setFilterState(f); try { localStorage.setItem(FILTER_KEY, f); } catch { /* ignore */ } };

  const counts = useMemo(() => ({
    all: conversations.length,
    people: conversations.filter((c) => c.kind === 'direct').length,
    groups: conversations.filter((c) => c.kind === 'group').length,
    unread: conversations.filter((c) => c.unread > 0).length,
  }), [conversations]);

  const visible = useMemo(() => {
    const term = q.trim().toLowerCase();
    return conversations
      .filter((c) => filter === 'all' || (filter === 'people' ? c.kind === 'direct' : filter === 'groups' ? c.kind === 'group' : c.unread > 0))
      .filter((c) => !term || c.title.toLowerCase().includes(term) || c.members.some((m) => m.displayName.toLowerCase().includes(term)));
  }, [conversations, filter, q]);

  const tabs: { id: Filter; label: string }[] = [{ id: 'all', label: 'All' }, { id: 'people', label: 'People' }, { id: 'groups', label: 'Groups' }, { id: 'unread', label: 'Unread' }];

  return (
    <section className="index-body" aria-label="Conversations">
      <form className="index-search" role="search" onSubmit={(e) => { e.preventDefault(); go(`/search?q=${encodeURIComponent(q.trim())}`); }}>
        <Search size={16} aria-hidden="true" />
        <input type="search" placeholder="Search or find a chat" aria-label="Find a chat or search messages" value={q} onChange={(e) => setQ(e.target.value)} />
        <kbd className="kbd only-wide" aria-hidden="true">Ctrl K</kbd>
      </form>
      {q.trim() && (
        <button className="search-through" onClick={() => go(`/search?q=${encodeURIComponent(q.trim())}`)}>
          Search all messages for “{q.trim()}” <ArrowRight size={14} aria-hidden="true" />
        </button>
      )}
      <div className="filters" role="tablist" aria-label="Show">
        {tabs.map((t) => (
          <button key={t.id} role="tab" aria-selected={filter === t.id} className={filter === t.id ? 'on' : ''} onClick={() => setFilter(t.id)}>
            {t.label}{t.id !== 'all' && counts[t.id] > 0 && <span className="filter-count">{counts[t.id]}</span>}
          </button>
        ))}
      </div>

      <div className="index-scroll">
        {!conversationsLoaded ? <ListSkeleton /> : conversationsError && !conversations.length ? (
          <EmptyState title="Couldn’t load your conversations" action={<Button icon={<RefreshCw size={16} />} onClick={() => chat.loadConversations()}>Try again</Button>}>{conversationsError}</EmptyState>
        ) : !conversations.length ? (
          <EmptyState title="Start your first conversation" action={<Button variant="primary" onClick={() => go('/new')}>New conversation</Button>}>
            Message anyone on this VAANI server. Write or speak in your own language.
          </EmptyState>
        ) : !visible.length ? (
          <p className="index-note">{q.trim() ? `No chats named “${q.trim()}”.` : filter === 'unread' ? 'You’re all caught up.' : filter === 'groups' ? 'No groups yet.' : 'No conversations here.'}
            {filter === 'groups' && !q && <> <a className="link" href="#/new/group"><Users size={13} /> Create a group</a></>}</p>
        ) : (
          <ul className="conv-list">
            {visible.map((c) => <Row key={c.id} c={c} active={c.id === activeId} meId={me.id} myLang={me.preferredLanguage} online={online}
              typers={Object.keys(typing[c.id] ?? {}).map(Number).filter((id) => id !== me.id)} />)}
          </ul>
        )}
      </div>
    </section>
  );
}

function Row({ c, active, meId, myLang, online, typers }: { c: Conversation; active: boolean; meId: number; myLang: string; online: Set<number>; typers: number[] }) {
  const other = c.kind === 'direct' ? c.members.find((m) => m.id !== meId) : undefined;
  const lm = c.lastMessage;
  const firstName = (id: number | null) => c.members.find((m) => m.id === id)?.displayName.split(' ')[0] ?? '';
  const who = lm && lm.senderId === meId ? 'You' : lm && c.kind === 'group' && lm.senderId ? firstName(lm.senderId) : '';
  const onlineCount = c.members.filter((m) => m.id !== meId && online.has(m.id)).length;

  let preview: React.ReactNode = <span className="muted-i">No messages yet</span>;
  if (typers.length) preview = <span className="activity"><VoiceMark size={12} live /> {typers.map(firstName).join(', ')} {typers.length > 1 ? 'are' : 'is'} typing</span>;
  else if (lm?.kind === 'voice') preview = (
    <span className="preview-voice"><VoiceMark size={12} /> {who && `${who} · `}Voice {duration(lm.voice?.durationMs ?? 0)}{lm.body ? <span className="preview-trans"> — {lm.body}</span> : null}</span>
  );
  else if (lm?.kind === 'system') preview = <span className="muted-i">{lm.body}</span>;
  else if (lm) preview = <>{who && <span className="preview-who">{who}: </span>}<span dir="auto">{lm.body}</span></>;

  return (
    <li>
      <a href={`#/chats/${c.id}`} className={`conv${active ? ' active' : ''}${c.unread ? ' unread' : ''}`} aria-current={active ? 'page' : undefined}>
        <Avatar name={c.title} hue={other?.avatarHue ?? (c.id * 47) % 360} online={other ? online.has(other.id) : undefined} group={c.kind === 'group'} size={42} />
        <span className="conv-main">
          <span className="conv-line">
            <span className="conv-title">{c.title}</span>
            {other && other.preferredLanguage !== myLang && <LangGlyph code={other.preferredLanguage} title={`Reads ${langLabel(other.preferredLanguage)}`} />}
            {c.kind === 'group' && <span className="conv-meta">{c.members.length}{onlineCount ? ` · ${onlineCount} here` : ''}</span>}
            {lm && <time className="conv-time" dateTime={lm.createdAt}>{listTime(lm.createdAt)}</time>}
          </span>
          <span className="conv-line">
            <span className="conv-preview">{preview}</span>
            {c.unread > 0 && <span className="unread-count" aria-label={`${c.unread} unread`}>{c.unread > 99 ? '99+' : c.unread}</span>}
          </span>
        </span>
      </a>
    </li>
  );
}

function ListSkeleton() {
  return <ul className="conv-list" aria-busy="true" aria-label="Loading conversations">{Array.from({ length: 6 }, (_, i) => (
    <li key={i} className="conv sk-row"><span className="sk sk-avatar" /><span className="sk-lines"><span className="sk sk-line" /><span className="sk sk-line short" /></span></li>))}</ul>;
}
