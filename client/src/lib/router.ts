/** Tiny hash router: #/chats, #/chats/12?m=345, #/new, #/new/group, #/search?q=, #/companion, #/settings/<section>. */
import { useSyncExternalStore } from 'react';

export type Route = (
  | { name: 'chats'; id?: number }
  | { name: 'new'; group?: boolean }
  | { name: 'search' }
  | { name: 'companion' }
  | { name: 'settings'; section?: string }
) & { query: URLSearchParams };

export function parse(hash: string): Route {
  const raw = hash.replace(/^#\/?/, '');
  const [path, qs = ''] = raw.split('?');
  const query = new URLSearchParams(qs);
  const parts = path.split('/').filter(Boolean);
  switch (parts[0]) {
    case 'chats': return parts[1] && /^\d+$/.test(parts[1]) ? { name: 'chats', id: Number(parts[1]), query } : { name: 'chats', query };
    case 'new': return { name: 'new', group: parts[1] === 'group', query };
    case 'search': return { name: 'search', query };
    case 'companion': case 'tasks': return { name: 'companion', query };
    case 'settings': return { name: 'settings', section: parts[1], query };
    default: return { name: 'chats', query };
  }
}

const sub = (cb: () => void) => { window.addEventListener('hashchange', cb); return () => window.removeEventListener('hashchange', cb); };
export function useRoute(): Route {
  const hash = useSyncExternalStore(sub, () => window.location.hash);
  return parse(hash);
}
export const go = (path: string) => { window.location.hash = path; };
