import { WifiOff, RefreshCw } from 'lucide-react';
import { useChat } from '../state/chat';

/** Visible only when something is wrong. Icon + words, never colour alone. */
export function ConnectionIndicator({ compact }: { compact?: boolean }) {
  const state = useChat((s) => s.connection);
  if (state === 'connected') return null;
  const text = state === 'offline' ? 'Offline — messages will send when you reconnect'
    : state === 'reconnecting' ? 'Reconnecting…' : 'Connecting…';
  return (
    <div className={`conn conn-${state}${compact ? ' compact' : ''}`} role="status" aria-live="polite">
      {state === 'offline' ? <WifiOff size={14} aria-hidden="true" /> : <RefreshCw size={14} className="spin" aria-hidden="true" />}
      <span>{compact && state === 'offline' ? 'Offline' : text}</span>
    </div>
  );
}
