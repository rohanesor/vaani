import { useEffect, useRef, useState, type ReactNode } from 'react';
import { ChevronLeft, Search as SearchIcon, Check, X, User as UserIcon, Languages, Palette, ShieldCheck, Bell, Plug, LogOut, Sun, Moon, Monitor, ChevronRight, RotateCw, CalendarClock } from 'lucide-react';
import { api, ApiError } from '../lib/api';
import type { AIStatus, Conversation, SearchResult, Task, User } from '../lib/types';
import { go } from '../lib/router';
import { LANGUAGES, dayLabel, langLabel, listTime } from '../lib/format';
import { chat, useChat } from '../state/chat';
import { useSession } from '../state/session';
import { AIMark, Avatar, Banner, Button, EmptyState, IconButton, Label, LangGlyph, Segmented, Spinner, Toggle, VoiceMark, useToast } from '../components/ui';

function SubHead({ title, back, children, narrowOnly }: { title: string; back?: string; children?: ReactNode; narrowOnly?: boolean }) {
  return (
    <header className={`sub-head${narrowOnly && !title ? ' only-narrow-head' : ''}`}>
      {back && <IconButton label="Back" className={narrowOnly ? 'only-narrow' : ''} onClick={() => go(back)}><ChevronLeft size={20} /></IconButton>}
      <h1 className="sub-title">{title}</h1>
      {children}
    </header>
  );
}

/* ============ new conversation / group (index column) ============ */
export function NewConversation({ group }: { group: boolean }) {
  const { user } = useSession();
  const [q, setQ] = useState('');
  const [people, setPeople] = useState<User[] | null>(null);
  const [recent, setRecent] = useState<User[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [picked, setPicked] = useState<User[]>([]);
  const [title, setTitle] = useState('');
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  useEffect(() => { api<User[]>('/users/recent').then(setRecent).catch(() => {}); }, []);
  useEffect(() => {
    const t = setTimeout(() => { setErr(null); api<User[]>(`/users?q=${encodeURIComponent(q.trim())}`).then(setPeople).catch((e: ApiError) => setErr(e.message)); }, 180);
    return () => clearTimeout(t);
  }, [q]);

  async function startDirect(u: User) {
    setBusy(true);
    try { const c = await api<Conversation>('/conversations', { method: 'POST', json: { kind: 'direct', userId: u.id } }); chat.upsertConversation(c); go(`/chats/${c.id}`); }
    catch (e) { toast((e as Error).message, 'error'); } finally { setBusy(false); }
  }
  async function createGroup() {
    setBusy(true);
    try { const c = await api<Conversation>('/conversations', { method: 'POST', json: { kind: 'group', title: title.trim(), memberIds: picked.map((p) => p.id) } }); chat.upsertConversation(c); go(`/chats/${c.id}`); }
    catch (e) { toast((e as Error).message, 'error'); } finally { setBusy(false); }
  }
  const toggle = (u: User) => setPicked((p) => (p.some((x) => x.id === u.id) ? p.filter((x) => x.id !== u.id) : [...p, u]));
  const langs = [...new Set([user!.preferredLanguage, ...picked.map((p) => p.preferredLanguage)])];

  const Row = ({ u }: { u: User }) => {
    const on = picked.some((x) => x.id === u.id);
    return (
      <li>
        <button className={`person${on ? ' on' : ''}`} onClick={() => (group ? toggle(u) : startDirect(u))} disabled={busy} aria-pressed={group ? on : undefined}>
          <Avatar name={u.displayName} hue={u.avatarHue} size={38} online={u.online} />
          <span className="grow"><span className="person-name">{u.displayName}</span><span className="muted small">Reads {langLabel(u.preferredLanguage)}{u.online ? ' · here now' : ''}</span></span>
          {u.preferredLanguage !== user!.preferredLanguage && <LangGlyph code={u.preferredLanguage} title={langLabel(u.preferredLanguage)} />}
          {group && <span className={`check${on ? ' on' : ''}`} aria-hidden="true">{on && <Check size={13} />}</span>}
        </button>
      </li>
    );
  };

  return (
    <section className="index-body" aria-label={group ? 'New group' : 'New conversation'}>
      <SubHead title={group ? 'New group' : 'New conversation'} back="/chats" />
      <div className="seg-switch" role="tablist" aria-label="Type">
        <a role="tab" aria-selected={!group} href="#/new" className={!group ? 'on' : ''}>One person</a>
        <a role="tab" aria-selected={group} href="#/new/group" className={group ? 'on' : ''}>Group</a>
      </div>
      {group && (
        <div className="group-setup">
          <div className="field"><label htmlFor="gname">Group name</label><input id="gname" maxLength={80} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Hackathon squad" /></div>
          {picked.length > 0 && <div className="chips">{picked.map((u) => <button key={u.id} className="chip" onClick={() => toggle(u)} aria-label={`Remove ${u.displayName}`}>{u.displayName.split(' ')[0]} <X size={12} /></button>)}</div>}
          {picked.length > 0 && <p className="muted small">Languages in this group: {langs.map(langLabel).join(', ')}. Everyone can translate any message into their own.</p>}
          <Button variant="primary" disabled={!title.trim() || !picked.length} loading={busy} onClick={createGroup}>Create group{picked.length ? ` · ${picked.length + 1} people` : ''}</Button>
        </div>
      )}
      <div className="index-search"><SearchIcon size={16} aria-hidden="true" /><input type="search" autoFocus placeholder="Name or email" aria-label="Search people" value={q} onChange={(e) => setQ(e.target.value)} /></div>
      <div className="index-scroll">
        {!q && recent.length > 0 && <><Label as="h2">Recent</Label><ul className="people">{recent.map((u) => <Row key={u.id} u={u} />)}</ul></>}
        <Label as="h2">{q ? 'Results' : 'Everyone on this server'}</Label>
        {err ? <Banner tone="error">{err}</Banner> : people === null ? <div className="pad"><Spinner /></div>
          : people.length === 0 ? <EmptyState title={q ? `No one matches “${q}”` : 'No one else is here yet'}>People appear here once they create a VAANI account on this server.</EmptyState>
            : <ul className="people">{people.map((u) => <Row key={u.id} u={u} />)}</ul>}
      </div>
    </section>
  );
}

/* ============ search (index column) ============ */
const RECENT_KEY = 'vaani.recentSearches';
const readRecent = (): string[] => { try { return JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]'); } catch { return []; } };

export function SearchScreen({ initial }: { initial: string }) {
  const [q, setQ] = useState(initial);
  const [res, setRes] = useState<SearchResult | null>(null);
  const [state, setState] = useState<'idle' | 'loading' | 'error'>('idle');
  const [err, setErr] = useState('');
  const [recent, setRecent] = useState<string[]>(readRecent);
  const [attempt, setAttempt] = useState(0);
  const conversations = useChat((s) => s.conversations);
  const ac = useRef<AbortController | null>(null);

  useEffect(() => {
    const term = q.trim();
    if (!term) { setRes(null); setState('idle'); return; }
    const t = setTimeout(async () => {
      ac.current?.abort(); const c = new AbortController(); ac.current = c;
      setState('loading');
      try { setRes(await api<SearchResult>(`/search?q=${encodeURIComponent(term)}`, { signal: c.signal })); setState('idle'); }
      catch (e) { if ((e as Error).name !== 'AbortError') { setErr((e as Error).message); setState('error'); } }
    }, 220);
    return () => clearTimeout(t);
  }, [q, attempt]);

  const remember = (term: string) => {
    const next = [term, ...recent.filter((r) => r !== term)].slice(0, 6);
    setRecent(next); try { localStorage.setItem(RECENT_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  };
  const titleOf = (id: number) => conversations.find((c) => c.id === id)?.title ?? 'Conversation';

  return (
    <section className="index-body" aria-label="Search">
      <SubHead title="Search" back="/chats" />
      <div className="index-search big"><SearchIcon size={18} aria-hidden="true" />
        <input type="search" autoFocus placeholder="Words, names, transcripts — any script" aria-label="Search messages and conversations" value={q} onChange={(e) => setQ(e.target.value)} />
        {state === 'loading' && <Spinner label="Searching" />}
      </div>
      <div className="index-scroll">
        {!q.trim() && (recent.length ? (
          <><Label as="h2">Recent searches</Label><div className="chips pad-x">{recent.map((r) => <button key={r} className="chip" onClick={() => setQ(r)}>{r}</button>)}</div></>
        ) : <EmptyState icon={<SearchIcon size={22} />} title="Search every conversation">Finds words in any script, including transcribed voice notes. Searching “vanakkam” won’t match “வணக்கம்” yet.</EmptyState>)}
        {state === 'error' && <Banner tone="error" action={<Button size="sm" icon={<RotateCw size={14} />} onClick={() => setAttempt((n) => n + 1)}>Retry</Button>}>{err}</Banner>}
        {res && q.trim() && (
          <>
            {res.conversations.length > 0 && <><Label as="h2">Conversations</Label><ul className="people">{res.conversations.map((c) => (
              <li key={c.id}><a className="person" href={`#/chats/${c.id}`} onClick={() => remember(q.trim())}><Avatar name={c.title} hue={(c.id * 47) % 360} size={34} group={c.kind === 'group'} /><span className="person-name grow">{c.title}</span></a></li>))}</ul></>}
            <Label as="h2">Messages{res.messages.length ? ` · ${res.messages.length}` : ''}</Label>
            {res.messages.length === 0 ? <p className="index-note">No messages match “{res.query}”.</p> : (
              <ul className="results">
                {res.messages.map(({ message: m, snippet }) => (
                  <li key={m.id}>
                    <a href={`#/chats/${m.conversationId}?m=${m.id}`} className="result" onClick={() => remember(q.trim())}>
                      <span className="result-top"><span className="result-conv">{titleOf(m.conversationId)}</span>{m.kind === 'voice' && <span className="result-kind"><VoiceMark size={11} /> transcript</span>}<time dateTime={m.createdAt}>{dayLabel(m.createdAt) === 'Today' ? listTime(m.createdAt) : dayLabel(m.createdAt)}</time></span>
                      <span className="result-snip" dir="auto"><Snippet s={snippet} /></span>
                    </a>
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </div>
    </section>
  );
}
function Snippet({ s }: { s: string }) {
  return <>{s.split(/(\[\[.*?\]\])/g).map((p, i) => p.startsWith('[[') ? <mark key={i}>{p.slice(2, -2)}</mark> : <span key={i}>{p}</span>)}</>;
}

/* ============ Companion (stage) — plans, tasks and what AI has done, from real data ============ */
export function CompanionScreen() {
  const { settings } = useSession();
  const [tasks, setTasks] = useState<Task[] | null>(null);
  const [status, setStatus] = useState<AIStatus | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const conversations = useChat((s) => s.conversations);
  const load = () => api<Task[]>('/tasks').then(setTasks).catch((e) => setErr(e.message));
  useEffect(() => { void load(); api<AIStatus>('/ai/status').then(setStatus).catch(() => {}); }, []);
  const toggle = async (t: Task) => {
    setTasks((ts) => ts!.map((x) => (x.id === t.id ? { ...x, done: !x.done } : x)));
    try { setTasks(await api<Task[]>(`/tasks/${t.id}`, { method: 'PATCH', json: { done: !t.done } })); } catch (e) { setErr((e as Error).message); void load(); }
  };
  const open = tasks?.filter((t) => !t.done) ?? [];
  const done = tasks?.filter((t) => t.done) ?? [];
  const pct = status ? Math.min(100, Math.round((status.tokensToday / Math.max(1, status.dailyTokenBudget)) * 100)) : 0;

  return (
    <main className="page" aria-labelledby="comp-h">
      <SubHead title="" back="/chats" narrowOnly />
      <div className="page-col">
        <p className="page-eyebrow"><AIMark size={14} /> Companion</p>
        <h1 id="comp-h" className="display">What VAANI is holding for you.</h1>
        <p className="lede">Plans you confirmed from conversations, and an honest record of what AI has read. VAANI never acts on its own.</p>
        {!settings?.aiEnabled && <Banner tone="warning" action={<Button size="sm" onClick={() => go('/settings/privacy')}>Turn on</Button>}>AI assistance is off. Companion shows existing plans only.</Banner>}
        {err && <Banner tone="error">{err}</Banner>}

        <section className="page-sec">
          <Label as="h2">Open plans {open.length > 0 && `· ${open.length}`}</Label>
          {tasks === null ? <Spinner /> : open.length === 0 ? (
            <p className="page-empty"><CalendarClock size={16} aria-hidden="true" /> No open plans. In any conversation, open a message’s <strong>More</strong> menu and choose <strong>Find plans &amp; tasks</strong>.</p>
          ) : <TaskList tasks={open} onToggle={toggle} conversations={conversations} />}
          {done.length > 0 && <details className="done-tasks"><summary>Done · {done.length}</summary><TaskList tasks={done} onToggle={toggle} conversations={conversations} /></details>}
        </section>

        <section className="page-sec">
          <Label as="h2" tone="ai">AI activity · last 30 days</Label>
          {!status ? <Spinner /> : (
            <>
              <div className="meter" role="img" aria-label={`${pct}% of today’s AI budget used`}><span style={{ width: `${pct}%` }} /></div>
              <p className="muted small">{status.tokensToday.toLocaleString()} of {status.dailyTokenBudget.toLocaleString()} tokens today · resets at midnight UTC</p>
              {status.usage30d.length === 0 ? <p className="page-empty">You haven’t used AI yet.</p> : (
                <ul className="ledger">{status.usage30d.map((u) => (
                  <li key={u.feature}><span>{FEATURE_NAME[u.feature] ?? u.feature}</span><span className="muted">{u.requests} {u.requests === 1 ? 'time' : 'times'}</span></li>))}</ul>
              )}
            </>
          )}
        </section>

        <section className="page-sec">
          <Label as="h2">What AI can see</Label>
          <ul className="principles">
            <li><strong>Only when you ask.</strong> Nothing runs in the background.</li>
            <li><strong>Only the conversation you’re in</strong>, and only up to {settings?.aiContextMessages ?? 30} messages of it.</li>
            <li><strong>{settings?.aiRedactContacts ? 'Contact details hidden' : 'Contact details NOT hidden'}</strong> — phone numbers, emails, OTPs and account numbers {settings?.aiRedactContacts ? 'are replaced before sending' : 'are sent as written'}.</li>
            <li><strong>Provider:</strong> Claude (Anthropic) for language; Gnani for speech. Keys stay on the server.</li>
          </ul>
          <a className="link" href="#/settings/privacy">Change in Privacy &amp; AI <ChevronRight size={14} /></a>
        </section>
      </div>
    </main>
  );
}
const FEATURE_NAME: Record<string, string> = {
  'assist.reply': 'Drafted replies', 'assist.rewrite': 'Rewrote drafts', 'assist.explain': 'Explained messages', 'assist.ask': 'Answered questions',
  translate: 'Translated messages', summarize: 'Briefs', extract_actions: 'Looked for plans',
};

function TaskList({ tasks, onToggle, conversations }: { tasks: Task[]; onToggle: (t: Task) => void; conversations: Conversation[] }) {
  return (
    <ul className="tasks">
      {tasks.map((t) => (
        <li key={t.id} className={t.done ? 'done' : ''}>
          <input type="checkbox" id={`t-${t.id}`} checked={t.done} onChange={() => onToggle(t)} />
          <label htmlFor={`t-${t.id}`} className="grow">
            <span className="task-title">{t.title}</span>
            <span className="muted small">{[t.dueAt && `Due ${new Date(t.dueAt + 'T00:00').toLocaleDateString([], { weekday: 'short', day: 'numeric', month: 'short' })}`, t.conversationId && `from ${conversations.find((c) => c.id === t.conversationId)?.title ?? 'a conversation'}`].filter(Boolean).join(' · ')}</span>
          </label>
          {t.conversationId && <a className="link small" href={`#/chats/${t.conversationId}`}>Open</a>}
        </li>
      ))}
    </ul>
  );
}

/* ============ settings (stage) ============ */
const SECTIONS = [
  { id: 'profile', label: 'Profile', icon: UserIcon },
  { id: 'language', label: 'Language', icon: Languages },
  { id: 'appearance', label: 'Appearance', icon: Palette },
  { id: 'privacy', label: 'Privacy & AI', icon: ShieldCheck },
  { id: 'notifications', label: 'Notifications', icon: Bell },
  { id: 'services', label: 'Connected services', icon: Plug },
];

export function SettingsScreen({ section }: { section?: string }) {
  const { user, logout } = useSession();
  const current = SECTIONS.find((s) => s.id === section);
  return (
    <main className={`settings${section ? ' has-section' : ''}`} aria-label="Settings">
      <nav className="settings-nav" aria-label="Settings sections">
        <SubHead title="Settings" back="/chats" />
        <a className="settings-me" href="#/settings/profile">
          <Avatar name={user!.displayName} hue={user!.avatarHue} size={44} />
          <span><strong>{user!.displayName}</strong><span className="muted small">{user!.email} · reads {langLabel(user!.preferredLanguage)}</span></span>
        </a>
        <ul>
          {SECTIONS.map((s) => (
            <li key={s.id}><a href={`#/settings/${s.id}`} className={section === s.id ? 'active' : ''} aria-current={section === s.id ? 'page' : undefined}>
              <s.icon size={17} aria-hidden="true" /><span className="grow">{s.label}</span><ChevronRight size={15} className="only-narrow" aria-hidden="true" /></a></li>
          ))}
        </ul>
        <Button variant="ghost" className="signout" icon={<LogOut size={16} />} onClick={() => { chat.stop(); logout(); }}>Sign out</Button>
      </nav>
      <section className="settings-detail">
        {current ? <SubHead title={current.label} back="/settings" /> : null}
        <div className="page-col">
          {!section && <EmptyState title="Settings">Choose a section.</EmptyState>}
          {section === 'profile' && <ProfileSettings />}
          {section === 'language' && <LanguageSettings />}
          {section === 'appearance' && <AppearanceSettings />}
          {section === 'privacy' && <PrivacySettings />}
          {section === 'notifications' && <NotificationSettings />}
          {section === 'services' && <ServicesSettings />}
        </div>
      </section>
    </main>
  );
}

function ProfileSettings() {
  const { user, updateProfile } = useSession();
  const [name, setName] = useState(user!.displayName);
  const [hue, setHue] = useState(user!.avatarHue);
  const [busy, setBusy] = useState(false);
  const toast = useToast();
  const save = async () => { setBusy(true); try { await updateProfile({ displayName: name.trim(), avatarHue: hue }); toast('Profile saved', 'success'); } catch (e) { toast((e as Error).message, 'error'); } finally { setBusy(false); } };
  return (
    <form className="form" onSubmit={(e) => { e.preventDefault(); void save(); }}>
      <div className="row gap-4"><Avatar name={name || '?'} hue={hue} size={72} />
        <div className="field grow"><label htmlFor="hue">Colour</label><input id="hue" type="range" min={0} max={359} value={hue} onChange={(e) => setHue(Number(e.target.value))} /></div></div>
      <p className="muted small">Profile photos aren’t supported yet — VAANI shows your initials.</p>
      <div className="field"><label htmlFor="dn">Display name</label><input id="dn" maxLength={60} value={name} onChange={(e) => setName(e.target.value)} required /></div>
      <div className="field"><label htmlFor="em">Email</label><input id="em" value={user!.email ?? ''} disabled /></div>
      <div><Button type="submit" variant="primary" loading={busy} disabled={!name.trim() || (name.trim() === user!.displayName && hue === user!.avatarHue)}>Save profile</Button></div>
    </form>
  );
}

function LanguageSettings() {
  const { user, updateProfile } = useSession();
  const toast = useToast();
  return (
    <div className="form">
      <p className="lede">The language VAANI translates into, and the one others see you read.</p>
      <ul className="lang-grid" role="radiogroup" aria-label="Preferred language">
        {LANGUAGES.map((l) => (
          <li key={l.code}><button role="radio" aria-checked={user!.preferredLanguage === l.code} className={user!.preferredLanguage === l.code ? 'on' : ''}
            onClick={() => updateProfile({ preferredLanguage: l.code }).catch((e) => toast(e.message, 'error'))}>
            <span className="lang-native" lang={l.code.split('-')[0]}>{l.native}</span><span className="muted small">{l.english}</span>
            {user!.preferredLanguage === l.code && <Check size={16} className="lang-check" aria-hidden="true" />}
          </button></li>
        ))}
      </ul>
      <p className="muted small">You can still write in any language, or mix them — Tanglish and Hinglish are fine.</p>
    </div>
  );
}

function AppearanceSettings() {
  const { settings, updateSettings } = useSession();
  return (
    <div className="form">
      <Segmented label="Theme" value={settings!.theme} onChange={(theme) => void updateSettings({ theme })}
        options={[{ value: 'system', label: 'System', icon: <Monitor size={15} /> }, { value: 'light', label: 'Light', icon: <Sun size={15} /> }, { value: 'dark', label: 'Dark', icon: <Moon size={15} /> }]} />
      <p className="muted small">Motion follows your device’s reduced-motion setting.</p>
    </div>
  );
}

function PrivacySettings() {
  const { settings, updateSettings } = useSession();
  const s = settings!;
  return (
    <div className="form">
      <Toggle id="ai-on" checked={s.aiEnabled} onChange={(v) => void updateSettings({ aiEnabled: v })} label="AI assistance"
        description="Drafts, rewrites, translation, explanations, briefs and plan-finding. Off means VAANI never sends your messages to an AI provider." />
      <div className="field">
        <label htmlFor="ctx">Messages AI may read per request — <strong>{s.aiContextMessages}</strong></label>
        <input id="ctx" type="range" min={5} max={100} step={5} value={s.aiContextMessages} disabled={!s.aiEnabled} onChange={(e) => void updateSettings({ aiContextMessages: Number(e.target.value) })} />
        <p className="field-hint">Briefs may read up to twice this. Fewer messages: more private, less context.</p>
      </div>
      <Toggle id="ai-redact" disabled={!s.aiEnabled} checked={s.aiRedactContacts} onChange={(v) => void updateSettings({ aiRedactContacts: v })} label="Hide contact details from AI"
        description="Replaces phone numbers, emails, OTPs and card or account numbers with placeholders before anything is sent." />
      <Toggle id="vt" checked={s.voiceTranscription} onChange={(v) => void updateSettings({ voiceTranscription: v })} label="Transcribe my voice notes"
        description="Sends your voice notes to Gnani speech recognition so they become searchable text. Cloud service — needs internet." />
      <div className="note-box">
        <Label as="h3">How it works</Label>
        <ul className="principles">
          <li><strong>Only when you ask</strong> — no background reading.</li>
          <li><strong>Only the conversation you’re in</strong> — never other chats.</li>
          <li><strong>Every result shows</strong> how many messages were read and what was hidden.</li>
          <li><strong>Nothing is sent for you</strong> — drafts wait for your Send.</li>
        </ul>
        <a className="link small" href="#/companion">See your AI activity <ChevronRight size={13} /></a>
      </div>
    </div>
  );
}

function NotificationSettings() {
  const { settings, updateSettings } = useSession();
  const [perm, setPerm] = useState(typeof Notification === 'undefined' ? 'unsupported' : Notification.permission);
  return (
    <div className="form">
      <Toggle id="notif" checked={settings!.notifyMessages} onChange={(v) => void updateSettings({ notifyMessages: v })} label="New message alerts"
        description="Browser notifications for new messages while VAANI is open in a background tab." />
      {perm === 'default' && <div><Button onClick={() => Notification.requestPermission().then(setPerm)}>Allow browser notifications</Button></div>}
      {perm === 'denied' && <Banner tone="warning">Notifications are blocked for this site in your browser settings.</Banner>}
      {perm === 'unsupported' && <Banner tone="info">This browser doesn’t support notifications.</Banner>}
      <p className="muted small">Alerts when VAANI is closed (push) aren’t available yet.</p>
    </div>
  );
}

function ServicesSettings() {
  const [s, setS] = useState<AIStatus | null>(null);
  const [err, setErr] = useState<string | null>(null);
  useEffect(() => { api<AIStatus>('/ai/status').then(setS).catch((e) => setErr(e.message)); }, []);
  const Row = ({ name, role, ok, note, mark }: { name: string; role: string; ok: boolean; note: string; mark: ReactNode }) => (
    <li className="service">{mark}<span className="grow"><strong>{name}</strong><span className="muted small">{role}{!ok && ` — ${note}`}</span></span>
      <span className={`pill ${ok ? 'confirmed' : 'off'}`}>{ok ? <><Check size={11} /> Connected</> : 'Not configured'}</span></li>
  );
  if (err) return <Banner tone="error">{err}</Banner>;
  if (!s) return <Spinner />;
  return (
    <div className="form">
      <p className="lede">Configured by whoever runs your VAANI server. Keys never reach your device.</p>
      <ul className="services">
        <Row mark={<span className="svc ai"><AIMark size={16} /></span>} name="Claude · Anthropic" role="Drafts, rewrites, translation, briefs, plans" ok={s.configured} note="needs ANTHROPIC_API_KEY" />
        <Row mark={<span className="svc voice"><VoiceMark size={16} /></span>} name="Gnani Prisma" role="Voice transcription" ok={s.speech.recognition.configured} note="needs GNANI_API_KEY" />
        <Row mark={<span className="svc voice"><VoiceMark size={16} /></span>} name="Gnani Timbre" role="Read aloud" ok={s.speech.synthesis.configured} note="needs GNANI_API_KEY" />
        <Row mark={<span className="svc"><CalendarClock size={16} aria-hidden="true" /></span>} name="Calendar" role="Events as .ics files for any calendar app" ok={true} note="" />
      </ul>
      <p className="muted small">Direct Google or Outlook calendar sync isn’t connected.</p>
    </div>
  );
}


