import { useEffect } from 'react';
import { SquarePen } from 'lucide-react';
import { SessionProvider, useSession } from './state/session';
import { chat, useChat } from './state/chat';
import { useRoute, go } from './lib/router';
import { AuthScreen, Onboarding, Splash } from './screens/Entry';
import { NewConversation, SearchScreen, SettingsScreen, CompanionScreen } from './screens/Screens';
import { ConversationList } from './components/ConversationList';
import { ConversationView } from './components/ConversationView';
import { Today } from './components/Today';
import { AIMark, Avatar, Logo, ToastProvider } from './components/ui';
import { ConnectionIndicator } from './components/ConnectionIndicator';

export default function App() {
  return (
    <SessionProvider>
      <ToastProvider>
        <Gate />
      </ToastProvider>
    </SessionProvider>
  );
}

function Gate() {
  const { status, user, settings } = useSession();
  useEffect(() => {
    if (status === 'authed' && user) chat.start(user.id);
    return () => chat.stop();
  }, [status, user?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  if (status === 'loading' || status === 'error') return <Splash />;
  if (status === 'anon') return <AuthScreen />;
  if (!settings?.onboarded) return <Onboarding />;
  return <Shell />;
}

/**
 * Layout: an INDEX column (identity, search, filters, conversations) and a STAGE (the open conversation or page).
 * No icon rail, no bottom tab bar — destinations live in the index header on every screen size.
 */
function Shell() {
  const route = useRoute();
  const { user, settings } = useSession();
  const unread = useChat((s) => s.conversations.reduce((n, c) => n + c.unread, 0));
  const conversations = useChat((s) => s.conversations);

  useEffect(() => { document.title = unread ? `(${unread}) VAANI` : 'VAANI'; }, [unread]);

  useEffect(() => chat.onIncoming((m) => {
    if (!settings?.notifyMessages || !document.hidden || typeof Notification === 'undefined' || Notification.permission !== 'granted') return;
    const c = conversations.find((x) => x.id === m.conversationId);
    const n = new Notification(c?.title ?? 'VAANI', { body: m.kind === 'voice' ? 'Voice note' : (m.body ?? '').slice(0, 120), tag: `c${m.conversationId}` });
    n.onclick = () => { window.focus(); window.location.hash = `/chats/${m.conversationId}`; };
  }), [settings?.notifyMessages, conversations]);

  // Ctrl/⌘+K → search, anywhere.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); go('/search'); } };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const convId = route.name === 'chats' ? route.id : undefined;
  const jump = route.query.get('m');
  const focus = convId ? 'stage' : route.name === 'companion' || route.name === 'settings' ? 'stage' : 'index';

  return (
    <div className="shell" data-focus={focus}>
      <a href="#stage" className="skip-link">Skip to content</a>
      <aside className="index" aria-label="Conversations and navigation">
        <header className="index-head">
          <a href="#/chats" className="index-brand" aria-label="VAANI — conversations"><Logo size={26} withWord /></a>
          <nav className="index-nav" aria-label="Primary">
            <a href="#/companion" className={`nav-key${route.name === 'companion' ? ' active' : ''}`} aria-current={route.name === 'companion' ? 'page' : undefined} title="Companion — plans, tasks and AI activity">
              <AIMark size={18} /><span className="nav-key-label">Companion</span>
            </a>
            <a href="#/new" className={`nav-key${route.name === 'new' ? ' active' : ''}`} aria-current={route.name === 'new' ? 'page' : undefined} title="New conversation">
              <SquarePen size={18} aria-hidden="true" /><span className="nav-key-label">New</span>
            </a>
            <a href="#/settings" className={`nav-me${route.name === 'settings' ? ' active' : ''}`} aria-label="Profile and settings" aria-current={route.name === 'settings' ? 'page' : undefined}>
              <Avatar name={user!.displayName} hue={user!.avatarHue} size={32} />
            </a>
          </nav>
        </header>
        <ConnectionIndicator />
        {route.name === 'new' ? <NewConversation group={!!route.group} />
          : route.name === 'search' ? <SearchScreen initial={route.query.get('q') ?? ''} />
            : <ConversationList activeId={convId} />}
      </aside>

      <div className="stage-area" id="stage">
        {convId ? <ConversationView key={convId} id={convId} jumpTo={jump ? Number(jump) : undefined} />
          : route.name === 'companion' ? <CompanionScreen />
            : route.name === 'settings' ? <SettingsScreen section={route.section} />
              : <Today />}
      </div>
    </div>
  );
}
