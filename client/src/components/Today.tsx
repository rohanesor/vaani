import { useEffect, useState } from 'react';
import { ArrowUpRight, SquarePen, Users, CircleCheck } from 'lucide-react';
import { useChat } from '../state/chat';
import { useSession } from '../state/session';
import { api } from '../lib/api';
import type { Task } from '../lib/types';
import { duration, listTime } from '../lib/format';
import { AIMark, Avatar, Label, VoiceMark } from './ui';

/** The empty stage on wide screens: what needs you, from real data — not a dashboard of widgets. */
export function Today() {
  const { user } = useSession();
  const conversations = useChat((s) => s.conversations);
  const [tasks, setTasks] = useState<Task[] | null>(null);
  useEffect(() => { api<Task[]>('/tasks').then((t) => setTasks(t.filter((x) => !x.done))).catch(() => setTasks([])); }, []);

  const waiting = conversations.filter((c) => c.unread > 0).slice(0, 5);
  const hour = new Date().getHours();
  const greet = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';

  return (
    <main className="today" aria-labelledby="today-h">
      <div className="today-inner">
        <p className="today-tag">Every language. One conversation.</p>
        <h1 id="today-h" className="display">{greet}, {user!.displayName.split(' ')[0]}.</h1>

        <section className="today-sec">
          <Label as="h2">Waiting for you</Label>
          {waiting.length === 0 ? <p className="today-quiet"><CircleCheck size={16} aria-hidden="true" /> Nothing unread. You’re caught up.</p> : (
            <ul className="today-list">
              {waiting.map((c) => {
                const lm = c.lastMessage;
                return (
                  <li key={c.id}>
                    <a href={`#/chats/${c.id}`} className="today-item">
                      <Avatar name={c.title} hue={c.kind === 'direct' ? c.members.find((m) => m.id !== user!.id)?.avatarHue ?? 0 : (c.id * 47) % 360} group={c.kind === 'group'} size={34} />
                      <span className="today-item-main">
                        <span className="today-item-title">{c.title} <span className="unread-count">{c.unread}</span></span>
                        <span className="today-item-text" dir="auto">{lm?.kind === 'voice' ? <><VoiceMark size={12} /> Voice {duration(lm.voice?.durationMs ?? 0)}</> : lm?.body}</span>
                      </span>
                      {lm && <time dateTime={lm.createdAt}>{listTime(lm.createdAt)}</time>}
                    </a>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        {tasks && tasks.length > 0 && (
          <section className="today-sec">
            <Label as="h2" tone="ai">Open plans from your chats</Label>
            <ul className="today-tasks">
              {tasks.slice(0, 4).map((t) => <li key={t.id}><span className="dot" aria-hidden="true" /> {t.title}{t.dueAt && <span className="muted"> · due {t.dueAt}</span>}</li>)}
            </ul>
            <a className="link" href="#/companion">All plans <ArrowUpRight size={14} /></a>
          </section>
        )}

        <section className="today-actions" aria-label="Start">
          <a className="today-action" href="#/new"><SquarePen size={18} aria-hidden="true" /><span><strong>New conversation</strong><span>Write or speak — in any language</span></span></a>
          <a className="today-action" href="#/new/group"><Users size={18} aria-hidden="true" /><span><strong>New group</strong><span>VAANI can brief everyone later</span></span></a>
          <a className="today-action ai" href="#/companion"><AIMark size={18} /><span><strong>Companion</strong><span>Plans, tasks and what AI has read</span></span></a>
        </section>
      </div>
    </main>
  );
}
