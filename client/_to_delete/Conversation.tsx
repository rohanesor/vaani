import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { ChevronLeft, Sparkles, ScrollText, Mic, SendHorizontal, X, ArrowDown, Users, MessageCircleQuestion, Reply, Wand2, LoaderCircle, RotateCw, Info, LogOut, Pencil } from 'lucide-react';
import { chat, useChat } from '../state/chat';
import { useSession } from '../state/session';
import type { Conversation, UIMessage, User } from '../lib/types';
import { dayLabel, langLabel } from '../lib/format';
import { api } from '../lib/api';
import { go } from '../lib/router';
import { MessageBubble } from './MessageBubble';
import { Recorder } from './Voice';
import { AICompanion, ActionsSheet, STYLE_LABEL, SummarySheet, type AIRequest, type AIRequestInput } from './AI';
import { Avatar, Banner, Button, EmptyState, IconButton, Sheet, useToast } from './ui';
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

  const [replyTo, setReplyTo] = useState<UIMessage | null>(null);
  const [ai, setAi] = useState<AIRequest | null>(null);
  const [summary, setSummary] = useState(false);
  const [actionsFor, setActionsFor] = useState<number | null>(null);
  const [info, setInfo] = useState(false);
  const [highlight, setHighlight] = useState<number | null>(null);
  const [draft, setDraft] = useState<{ text: string; ai: boolean }>({ text: '', ai: false });
  const aiEnabled = settings?.aiEnabled ?? false;

  useEffect(() => { void chat.loadMessages(id); setReplyTo(null); setAi(null); setDraft({ text: '', ai: false }); }, [id]);

  const members = useMemo(() => new Map<number, User>((conv?.members ?? []).map((m) => [m.id, m])), [conv?.members]);

  const jump = useCallback((mid: number) => {
    const el = document.getElementById(`m-${mid}`);
    if (el) { el.scrollIntoView({ block: 'center', behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' }); setHighlight(mid); setTimeout(() => setHighlight(null), 2200); }
    else if (hasMore) void chat.loadMessages(id, true).then(() => setTimeout(() => jump(mid), 50));
  }, [hasMore, id]);

  useEffect(() => { if (jumpTo && messages?.length) setTimeout(() => jump(jumpTo), 60); }, [jumpTo, messages?.length === undefined]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!conv) {
    return loaded ? <main className="conv-pane"><EmptyState title="Conversation not found" action={<Button onClick={() => go('/chats')}>Back to chats</Button>}>It may have been removed, or you’re no longer a member.</EmptyState></main>
      : <main className="conv-pane"><div className="center"><LoaderCircle className="spin" /></div></main>;
  }

  const other = conv.kind === 'direct' ? conv.members.find((m) => m.id !== me.id) : undefined;
  const typers = Object.keys(typing ?? {}).map(Number).filter((u) => u !== me.id).map((u) => members.get(u)?.displayName.split(' ')[0]).filter(Boolean) as string[];
  const onlineCount = conv.members.filter((m) => m.id !== me.id && online.has(m.id)).length;
  const status = typers.length ? `${typers.join(', ')} ${typers.length > 1 ? 'are' : 'is'} typing…`
    : conv.kind === 'group' ? `${conv.members.length} members${onlineCount ? ` · ${onlineCount} online` : ''}`
      : other && online.has(other.id) ? 'Online' : other ? `Reads ${langLabel(other.preferredLanguage)}` : '';

  const openAI = (r: AIRequestInput) => setAi({ ...r, nonce: Date.now() } as AIRequest);

  return (
    <div className={`conv-layout${ai ? ' with-ai' : ''}`}>
      <main className="conv-pane" aria-label={`Conversation with ${conv.title}`}>
        <header className="conv-head">
          <IconButton label="Back to chats" className="only-mobile" onClick={() => go('/chats')}><ChevronLeft size={22} /></IconButton>
          <button className="conv-id" onClick={() => setInfo(true)} aria-label={`${conv.title} — conversation details`}>
            <Avatar name={conv.title} hue={other?.avatarHue ?? (conv.id * 47) % 360} online={other ? online.has(other.id) : undefined} group={conv.kind === 'group'} size={40} />
            <span className="conv-id-text"><span className="conv-name">{conv.title}</span><span className={`conv-status${typers.length ? ' typing' : ''}`}>{status}</span></span>
          </button>
          <div className="row gap-1">
            {aiEnabled && <IconButton label="Ask about this chat" tone="ai" onClick={() => openAI({ op: 'ask' })}><MessageCircleQuestion size={20} /></IconButton>}
            {aiEnabled && <IconButton label="Summarize conversation" tone="ai" onClick={() => setSummary(true)}><ScrollText size={20} /></IconButton>}
            <IconButton label="Conversation details" onClick={() => setInfo(true)}><Info size={20} /></IconButton>
          </div>
        </header>
        <div className="conv-conn"><ConnectionIndicator compact /></div>

        <MessageList id={id} conv={conv} me={me} messages={messages} loading={!!loading} loadError={loadError ?? null} hasMore={!!hasMore} readState={readState ?? {}}
          members={members} highlight={highlight} aiEnabled={aiEnabled}
          onReply={(m) => setReplyTo(m)} onExplain={(m) => openAI({ op: 'explain', messageId: m.id })} onActions={(m) => setActionsFor(m.id)} onJump={jump} />

        <Composer conv={conv} me={me} replyTo={replyTo} clearReply={() => setReplyTo(null)} members={members} aiEnabled={aiEnabled}
          draft={draft} setDraft={setDraft} onAI={openAI} onSummary={() => setSummary(true)} />
      </main>

      {ai && (
        <div className="ai-dock">
          <AICompanion key={ai.nonce} conversationId={id} request={ai} onClose={() => setAi(null)} onJump={jump}
            onAsk={(q) => openAI({ op: 'ask', question: q })}
            onUse={(text, mode) => { setDraft((d) => ({ text: mode === 'replace' || !d.text.trim() ? text : `${d.text.trimEnd()} ${text}`, ai: true })); setAi(null); requestAnimationFrame(() => document.getElementById('composer-input')?.focus()); }} />
        </div>
      )}
      <SummarySheet open={summary} conversationId={id} onClose={() => setSummary(false)} onJump={jump} />
      <ActionsSheet open={actionsFor !== null} messageId={actionsFor} onClose={() => setActionsFor(null)} />
      <ConversationInfo open={info} conv={conv} me={me} onClose={() => setInfo(false)} />
    </div>
  );
}

/* ---------------- message list ---------------- */
function MessageList(p: {
  id: number; conv: Conversation; me: User; messages?: UIMessage[]; loading: boolean; loadError: string | null; hasMore: boolean; readState: Record<number, number>;
  members: Map<number, User>; highlight: number | null; aiEnabled: boolean;
  onReply(m: UIMessage): void; onExplain(m: UIMessage): void; onActions(m: UIMessage): void; onJump(id: number): void;
}) {
  const scroller = useRef<HTMLDivElement>(null);
  const atBottom = useRef(true);
  const prevHeight = useRef(0);
  const prevFirst = useRef<number | undefined>(undefined);
  const [newBelow, setNewBelow] = useState(0);
  const lastCount = useRef(0);
  const list = p.messages;

  // Keep position when older messages are prepended; stick to bottom for new ones.
  useLayoutEffect(() => {
    const el = scroller.current; if (!el || !list) return;
    const first = list[0]?.id;
    if (prevFirst.current !== undefined && first !== prevFirst.current && prevHeight.current) {
      el.scrollTop += el.scrollHeight - prevHeight.current;
    } else if (atBottom.current || list.at(-1)?.senderId === p.me.id && list.length > lastCount.current) {
      el.scrollTop = el.scrollHeight; setNewBelow(0);
    } else if (list.length > lastCount.current) {
      setNewBelow((n) => n + (list.length - lastCount.current));
    }
    prevFirst.current = first; prevHeight.current = el.scrollHeight; lastCount.current = list.length;
  }, [list, p.me.id]);

  useEffect(() => { atBottom.current = true; lastCount.current = 0; prevFirst.current = undefined; setNewBelow(0); }, [p.id]);
  // Stay pinned to the latest message when the pane resizes (AI panel opening, keyboard, rotation).
  useEffect(() => {
    const el = scroller.current; if (!el) return;
    const ro = new ResizeObserver(() => { if (atBottom.current) el.scrollTop = el.scrollHeight; });
    ro.observe(el);
    return () => ro.disconnect();
  }, [list === undefined]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (atBottom.current && document.visibilityState === 'visible') chat.markRead(p.id); }, [list, p.id]);

  const onScroll = () => {
    const el = scroller.current!;
    atBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
    if (atBottom.current) { setNewBelow(0); chat.markRead(p.id); }
    prevHeight.current = el.scrollHeight;
    if (el.scrollTop < 120 && p.hasMore && !p.loading) void chat.loadMessages(p.id, true);
  };

  const othersRead = (mid: number) => p.conv.members.some((m) => m.id !== p.me.id && (p.readState[m.id] ?? 0) >= mid);

  if (!list) {
    return <div className="msg-scroll center">{p.loadError
      ? <EmptyState title="Couldn’t load messages" action={<Button icon={<RotateCw size={16} />} onClick={() => chat.loadMessages(p.id)}>Retry</Button>}>{p.loadError}</EmptyState>
      : <LoaderCircle className="spin" aria-label="Loading messages" />}</div>;
  }

  return (
    <div className="msg-scroll" ref={scroller} onScroll={onScroll} role="log" aria-label="Messages" aria-live="polite" aria-relevant="additions">
      <div className="msg-inner">
        {p.hasMore && <div className="older">{p.loading ? <LoaderCircle size={16} className="spin" aria-label="Loading older messages" /> : <button className="link" onClick={() => chat.loadMessages(p.id, true)}>Load older messages</button>}</div>}
        {!p.hasMore && (
          <div className="conv-start">
            <Avatar name={p.conv.title} hue={(p.conv.members.find((m) => m.id !== p.me.id)?.avatarHue) ?? (p.conv.id * 47) % 360} size={56} group={p.conv.kind === 'group'} />
            <p><strong>{p.conv.title}</strong></p>
            <p className="muted small">{p.conv.kind === 'group' ? `Group with ${p.conv.members.map((m) => m.id === p.me.id ? 'you' : m.displayName.split(' ')[0]).join(', ')}` : 'Messages are stored on your VAANI server. AI only reads them when you ask.'}</p>
          </div>
        )}
        {list.length === 0 && <p className="muted center-text">Say hello — in any language.</p>}
        {list.map((m, i) => {
          const prev = list[i - 1]; const next = list[i + 1];
          const newDay = !prev || new Date(prev.createdAt).toDateString() !== new Date(m.createdAt).toDateString();
          const same = (a?: UIMessage, b?: UIMessage) => !!a && !!b && a.senderId === b.senderId && a.kind !== 'system' && b.kind !== 'system'
            && Math.abs(new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()) < 5 * 60_000 && new Date(a.createdAt).toDateString() === new Date(b.createdAt).toDateString();
          return (
            <div key={m.clientId && m.local ? `c-${m.clientId}` : m.id}>
              {newDay && <div className="day-sep"><span>{dayLabel(m.createdAt)}</span></div>}
              <MessageBubble m={m} me={p.me} sender={m.senderId ? p.members.get(m.senderId) : undefined} showSender={p.conv.kind === 'group'}
                groupStart={newDay || !same(prev, m)} groupEnd={!same(m, next)} readByOthers={m.id > 0 && othersRead(m.id)} highlighted={p.highlight === m.id}
                isGroup={p.conv.kind === 'group'} members={p.members} aiEnabled={p.aiEnabled}
                onReply={p.onReply} onExplain={p.onExplain} onActions={p.onActions} onJump={p.onJump} />
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

/* ---------------- composer ---------------- */
function Composer(p: {
  conv: Conversation; me: User; replyTo: UIMessage | null; clearReply(): void; members: Map<number, User>; aiEnabled: boolean;
  draft: { text: string; ai: boolean }; setDraft(d: { text: string; ai: boolean }): void;
  onAI(r: AIRequestInput): void; onSummary(): void;
}) {
  const [recording, setRecording] = useState(false);
  const [menu, setMenu] = useState(false);
  const ta = useRef<HTMLTextAreaElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const text = p.draft.text;

  useLayoutEffect(() => {
    const el = ta.current; if (!el) return;
    const fit = () => { el.style.height = 'auto'; el.style.height = `${Math.min(el.scrollHeight, 168)}px`; };
    fit();
    const ro = new ResizeObserver(fit); // re-fit when the width changes (panel opens, rotation)
    ro.observe(el.parentElement!);
    return () => ro.disconnect();
  }, [text, recording]);
  useEffect(() => { if (p.replyTo) ta.current?.focus(); }, [p.replyTo]);
  useEffect(() => {
    if (!menu) return;
    const close = (e: MouseEvent | globalThis.KeyboardEvent) => { if (e instanceof globalThis.KeyboardEvent ? e.key === 'Escape' : !menuRef.current?.contains(e.target as Node)) setMenu(false); };
    document.addEventListener('mousedown', close); document.addEventListener('keydown', close);
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', close); };
  }, [menu]);

  function send() {
    const body = text.trim();
    if (!body) return;
    if (body.length > 4000) return;
    chat.sendText(p.conv.id, body, { replyTo: p.replyTo?.id ?? null, aiAssisted: p.draft.ai });
    p.setDraft({ text: '', ai: false });
    p.clearReply();
  }
  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); send(); }
  };

  const replyName = p.replyTo ? (p.replyTo.senderId === p.me.id ? 'yourself' : p.members.get(p.replyTo.senderId ?? 0)?.displayName ?? 'message') : '';
  const pick = (r: AIRequestInput) => { setMenu(false); p.onAI(r); };

  return (
    <div className="composer-wrap">
      {p.replyTo && (
        <div className="reply-bar">
          <Reply size={16} aria-hidden="true" />
          <div className="reply-text"><span>Replying to {replyName}</span><p dir="auto">{p.replyTo.kind === 'voice' ? (p.replyTo.body ?? 'Voice note') : p.replyTo.body}</p></div>
          <IconButton label="Cancel reply" onClick={p.clearReply}><X size={16} /></IconButton>
        </div>
      )}
      {p.draft.ai && text && <div className="ai-draft-note"><Sparkles size={12} aria-hidden="true" /> AI-drafted — edit freely. It’s marked “AI-assisted” when sent.</div>}
      {text.length > 3800 && <Banner tone={text.length > 4000 ? 'error' : 'warning'}>{text.length}/4000 characters</Banner>}
      {recording ? (
        <Recorder conversationId={p.conv.id} replyTo={p.replyTo?.id ?? null} onDone={() => { setRecording(false); p.clearReply(); }} />
      ) : (
        <div className="composer">
          {p.aiEnabled && (
            <div className="ai-menu-wrap" ref={menuRef}>
              <IconButton label="VAANI AI" tone="ai" aria-haspopup="menu" aria-expanded={menu} onClick={() => setMenu(!menu)}><Sparkles size={20} /></IconButton>
              {menu && (
                <div className="popover menu ai-menu" role="menu">
                  <button role="menuitem" onClick={() => pick({ op: 'reply', messageId: p.replyTo?.id })}><Sparkles size={15} /> Suggest a reply</button>
                  <div className="menu-label" id="rw-label">Rewrite my draft</div>
                  {Object.entries(STYLE_LABEL).map(([k, v]) => (
                    <button key={k} role="menuitem" disabled={!text.trim()} onClick={() => pick({ op: 'rewrite', draft: text, style: k })} aria-describedby="rw-label"><Wand2 size={15} /> {v}</button>
                  ))}
                  <div className="menu-sep" />
                  <button role="menuitem" onClick={() => pick({ op: 'ask' })}><MessageCircleQuestion size={15} /> Ask about this chat</button>
                  <button role="menuitem" onClick={() => { setMenu(false); p.onSummary(); }}><ScrollText size={15} /> Summarize</button>
                </div>
              )}
            </div>
          )}
          <label htmlFor="composer-input" className="sr-only">Message {p.conv.title}</label>
          <textarea id="composer-input" ref={ta} rows={1} placeholder="Message" value={text} dir="auto"
            onChange={(e) => { p.setDraft({ text: e.target.value, ai: p.draft.ai && e.target.value.length > 0 }); chat.typing(p.conv.id); }} onKeyDown={onKey} />
          {text.trim() ? (
            <IconButton label="Send" tone="primary" className="send-btn" onClick={send} disabled={text.length > 4000}><SendHorizontal size={20} /></IconButton>
          ) : (
            <IconButton label="Record voice note" tone="voice" className="send-btn" onClick={() => setRecording(true)}><Mic size={20} /></IconButton>
          )}
        </div>
      )}
    </div>
  );
}

/* ---------------- details ---------------- */
function ConversationInfo({ open, conv, me, onClose }: { open: boolean; conv: Conversation; me: User; onClose: () => void }) {
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

  return (
    <Sheet open={open} onClose={onClose} title={conv.kind === 'group' ? 'Group details' : 'Contact'} side="right">
      <div className="stack gap-4">
        <div className="info-id">
          <Avatar name={conv.title} hue={conv.members.find((m) => m.id !== me.id)?.avatarHue ?? (conv.id * 47) % 360} size={72} group={conv.kind === 'group'} />
          {editing ? (
            <form className="row gap-2" onSubmit={(e) => { e.preventDefault(); void rename(); }}>
              <label htmlFor="g-title" className="sr-only">Group name</label>
              <input id="g-title" value={title} maxLength={80} onChange={(e) => setTitle(e.target.value)} autoFocus />
              <Button type="submit" variant="primary" size="sm">Save</Button>
            </form>
          ) : <h3 className="title">{conv.title} {conv.kind === 'group' && <IconButton label="Rename group" onClick={() => setEditing(true)}><Pencil size={15} /></IconButton>}</h3>}
        </div>
        <section>
          <h4 className="section-label"><Users size={14} aria-hidden="true" /> {conv.members.length} members</h4>
          <ul className="member-list">
            {conv.members.map((m) => (
              <li key={m.id}>
                <Avatar name={m.displayName} hue={m.avatarHue} size={36} online={m.id === me.id ? undefined : online.has(m.id)} />
                <span className="grow"><span className="member-name">{m.displayName}{m.id === me.id && ' (you)'}</span><span className="muted small">Reads {langLabel(m.preferredLanguage)}</span></span>
                {m.role === 'owner' && <span className="tag">Owner</span>}
              </li>
            ))}
          </ul>
        </section>
        {conv.kind === 'group' && <Button variant="danger" icon={<LogOut size={16} />} onClick={leave}>Leave group</Button>}
      </div>
    </Sheet>
  );
}
