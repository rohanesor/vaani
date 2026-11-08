import { useCallback, useEffect, useMemo, useState } from 'react';
import { ChevronLeft, Info, LoaderCircle, LogOut, Pencil, Users } from 'lucide-react';
import { chat, useChat } from '../state/chat';
import { useSession } from '../state/session';
import type { Conversation, UIMessage, User } from '../lib/types';
import { langLabel } from '../lib/format';
import { api } from '../lib/api';
import { go } from '../lib/router';
import { Transcript } from './Transcript';
import { Composer } from './Composer';
import { ContextPane } from './AI';
import { AIMark, Avatar, Button, EmptyState, IconButton, Label, LangGlyph, Sheet, useToast } from './ui';
import { ConnectionIndicator } from './ConnectionIndicator';

export function ConversationView({ id, jumpTo }: { id: number; jumpTo?: number }) {
  const { user, settings } = useSession();
  const me = user!;
  const conv = useChat((s) => s.conversations.find((c) => c.id === id));
  const loaded = useChat((s) => s.conversationsLoaded);
  const messages = useChat((s) => s.messages[id]);
  const loading = useChat((s) => s.loading[id]);
  const loadError = useChat((s) => s.loadError[id]);
  const hasMore = useChat((s) => s.hasMore[id]);
  const readState = useChat((s) => s.readState[id]);
  const typing = useChat((s) => s.typing[id]);
  const online = useChat((s) => s.online);
  const aiEnabled = settings?.aiEnabled ?? false;

  const [replyTo, setReplyTo] = useState<UIMessage | null>(null);
  const [pane, setPane] = useState<null | 'brief' | 'ask'>(null);
  const [info, setInfo] = useState(false);
  const [highlight, setHighlight] = useState<number | null>(null);

  useEffect(() => { void chat.loadMessages(id); setReplyTo(null); setPane(null); }, [id]);
  const members = useMemo(() => new Map<number, User>((conv?.members ?? []).map((m) => [m.id, m])), [conv?.members]);

  const jump = useCallback((mid: number) => {
    const el = document.getElementById(`m-${mid}`);
    if (el) {
      el.scrollIntoView({ block: 'center', behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
      setHighlight(mid); setTimeout(() => setHighlight(null), 2400);
      if (window.innerWidth < 1280) setPane(null);
    } else if (hasMore) void chat.loadMessages(id, true).then(() => setTimeout(() => jump(mid), 60));
  }, [hasMore, id]);
  useEffect(() => { if (jumpTo && messages?.length) setTimeout(() => jump(jumpTo), 80); }, [jumpTo, messages === undefined]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!conv) {
    return <main className="convo"><div className="center">{loaded
      ? <EmptyState title="Conversation not found" action={<Button onClick={() => go('/chats')}>Back to conversations</Button>}>It may have been removed, or you’re no longer a member.</EmptyState>
      : <LoaderCircle className="spin" aria-label="Loading" />}</div></main>;
  }

  const other = conv.kind === 'direct' ? conv.members.find((m) => m.id !== me.id) : undefined;
  const typers = Object.keys(typing ?? {}).map(Number).filter((u) => u !== me.id).map((u) => members.get(u)?.displayName.split(' ')[0]).filter(Boolean) as string[];
  const here = conv.members.filter((m) => m.id !== me.id && online.has(m.id)).length;
  const status = typers.length ? `${typers.join(', ')} ${typers.length > 1 ? 'are' : 'is'} typing…`
    : conv.kind === 'group' ? `${conv.members.length} people${here ? ` · ${here} here now` : ''}`
      : other && online.has(other.id) ? 'Here now' : other ? `Reads ${langLabel(other.preferredLanguage)}` : '';

  return (
    <div className={`conv-wrap${pane ? ' with-pane' : ''}`}>
      <main className="convo" aria-label={`Conversation with ${conv.title}`}>
        <header className="conv-head">
          <IconButton label="Back to conversations" className="only-narrow" onClick={() => go('/chats')}><ChevronLeft size={22} /></IconButton>
          <button className="conv-id" onClick={() => setInfo(true)} aria-label={`${conv.title} — details`}>
            <Avatar name={conv.title} hue={other?.avatarHue ?? (conv.id * 47) % 360} online={other ? online.has(other.id) : undefined} group={conv.kind === 'group'} size={38} />
            <span className="conv-id-text">
              <span className="conv-name">{conv.title}{other && other.preferredLanguage !== me.preferredLanguage && <LangGlyph code={other.preferredLanguage} title={`Reads ${langLabel(other.preferredLanguage)}`} />}</span>
              <span className={`conv-status${typers.length ? ' live' : ''}`}>{status}</span>
            </span>
          </button>
          <div className="conv-actions">
            {aiEnabled && (
              <button className={`ctx-key${pane ? ' on' : ''}`} aria-pressed={!!pane} onClick={() => setPane(pane ? null : 'brief')}>
                <AIMark size={16} /><span className="ctx-key-label">Context</span>
              </button>
            )}
            <IconButton label="Conversation details" onClick={() => setInfo(true)}><Info size={19} /></IconButton>
          </div>
        </header>
        <div className="conv-conn"><ConnectionIndicator compact /></div>

        <Transcript id={id} conv={conv} me={me} messages={messages} loading={!!loading} loadError={loadError ?? null} hasMore={!!hasMore}
          readState={readState ?? {}} members={members} highlight={highlight} aiEnabled={aiEnabled} onReply={setReplyTo} onJump={jump} />

        <Composer conv={conv} me={me} members={members} replyTo={replyTo} clearReply={() => setReplyTo(null)} aiEnabled={aiEnabled} onContext={(t) => setPane(t)} />
      </main>

      {pane && (
        <>
          <div className="pane-scrim" onClick={() => setPane(null)} aria-hidden="true" />
          <aside className="pane"><ContextPane conversationId={id} tab={pane} setTab={setPane} onClose={() => setPane(null)} onJump={jump} /></aside>
        </>
      )}
      <Details open={info} conv={conv} me={me} onClose={() => setInfo(false)} />
    </div>
  );
}

function Details({ open, conv, me, onClose }: { open: boolean; conv: Conversation; me: User; onClose: () => void }) {
  const toast = useToast();
  const online = useChat((s) => s.online);
  const [title, setTitle] = useState(conv.title);
  const [editing, setEditing] = useState(false);
  useEffect(() => setTitle(conv.title), [conv.title]);
  async function rename() {
    try { chat.upsertConversation(await api<Conversation>(`/conversations/${conv.id}`, { method: 'PATCH', json: { title } })); setEditing(false); }
    catch (e) { toast((e as Error).message, 'error'); }
  }
  async function leave() {
    try { await api(`/conversations/${conv.id}/members/me`, { method: 'DELETE' }); onClose(); await chat.loadConversations(); go('/chats'); }
    catch (e) { toast((e as Error).message, 'error'); }
  }
  const langs = [...new Set(conv.members.map((m) => m.preferredLanguage))];
  return (
    <Sheet open={open} onClose={onClose} title={conv.kind === 'group' ? 'Group' : 'Person'}>
      <div className="stack gap-5">
        <div className="details-id">
          <Avatar name={conv.title} hue={conv.members.find((m) => m.id !== me.id)?.avatarHue ?? (conv.id * 47) % 360} size={64} group={conv.kind === 'group'} />
          {editing ? (
            <form className="row gap-2" onSubmit={(e) => { e.preventDefault(); void rename(); }}>
              <label htmlFor="g-title" className="sr-only">Group name</label>
              <input id="g-title" value={title} maxLength={80} onChange={(e) => setTitle(e.target.value)} autoFocus />
              <Button type="submit" variant="primary" size="sm">Save</Button>
            </form>
          ) : <h3 className="title">{conv.title}{conv.kind === 'group' && <IconButton label="Rename group" className="xs" onClick={() => setEditing(true)}><Pencil size={14} /></IconButton>}</h3>}
          <p className="muted small">Languages here: {langs.map(langLabel).join(', ')}</p>
        </div>
        <section>
          <Label as="h3"><Users size={12} /> {conv.members.length} {conv.members.length === 1 ? 'person' : 'people'}</Label>
          <ul className="people">
            {conv.members.map((m) => (
              <li key={m.id} className="person-row">
                <Avatar name={m.displayName} hue={m.avatarHue} size={34} online={m.id !== me.id && online.has(m.id)} />
                <span className="grow"><span className="person-name">{m.displayName}{m.id === me.id && ' (you)'}</span><span className="muted small">Reads {langLabel(m.preferredLanguage)}{m.id !== me.id && online.has(m.id) ? ' · here now' : ''}</span></span>
                {m.role === 'owner' && conv.kind === 'group' && <span className="tag">Owner</span>}
              </li>
            ))}
          </ul>
          {conv.kind === 'group' && <p className="muted small mt-2">Adding people to an existing group isn’t available in the app yet.</p>}
        </section>
        {conv.kind === 'group' && <Button variant="danger" icon={<LogOut size={16} />} onClick={leave}>Leave group</Button>}
      </div>
    </Sheet>
  );
}
