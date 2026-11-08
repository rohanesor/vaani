/**
 * DEVELOPMENT SEED — creates demo accounts and conversations in the local database so the
 * UI can be exercised. This is not used by the server at runtime and is never run automatically.
 * Usage: npm run seed   (password for every demo account: vaani-demo)
 */
import { loadConfig } from '../src/config.js';
import { openDb } from '../src/db.js';
import { Store } from '../src/store.js';
import { hashPassword } from '../src/auth.js';

const cfg = loadConfig();
const store = new Store(openDb(cfg.dbPath));
const pw = hashPassword('vaani-demo');

function user(email: string, name: string, lang: string) {
  const existing = store.findAuthByEmail(email);
  if (existing) return existing.id;
  const u = store.createUser(email, pw, name, lang);
  store.updateSettings(u.id, { onboarded: true });
  return u.id;
}

const rose = user('rose@vaani.dev', 'Rose', 'en-IN');
const meena = user('meena@vaani.dev', 'Meena Sundaram', 'ta-IN');
const arjun = user('arjun@vaani.dev', 'Arjun Mehta', 'hi-IN');
const kavya = user('kavya@vaani.dev', 'Kavya Reddy', 'te-IN');

const dm = store.createDirect(rose, meena);
if (!dm.lastMessage) {
  const say = (who: number, body: string) => store.insertMessage({ conversationId: dm.id, senderId: who, kind: 'text', body });
  say(meena, 'Rose, நாளைக்கு demo-க்கு ready-ஆ?');
  say(rose, 'Almost! The voice notes flow works, still polishing the summary screen.');
  say(meena, 'சூப்பர். Slides-ஐ இன்னிக்கு ராத்திரிக்குள்ள அனுப்பிடு, நான் review பண்றேன்.');
}

const group = store.listConversations(rose).find((c) => c.kind === 'group' && c.title === 'Hackathon squad')
  ?? store.createGroup(rose, 'Hackathon squad', [meena, arjun, kavya]);
if ((group.lastMessage?.kind ?? 'system') === 'system') {
  const say = (who: number, body: string) => store.insertMessage({ conversationId: group.id, senderId: who, kind: 'text', body });
  say(arjun, 'Backend ke liye FastAPI ya Node? Mujhe Node better lag raha hai kyunki realtime easy hai.');
  say(kavya, 'Node is fine with me. Postgres or SQLite for the demo?');
  say(rose, 'SQLite for the demo, Postgres later. Let’s lock Node + SQLite.');
  say(meena, 'Agreed 👍 Node + SQLite final.');
  say(arjun, 'Theek hai. Who is doing the pitch deck?');
  say(kavya, 'నేను deck చేస్తాను, but I need the architecture diagram by Wednesday.');
  say(rose, 'I’ll send the diagram by Wednesday evening.');
}
console.log('[seed] demo accounts: rose@ meena@ arjun@ kavya@vaani.dev — password: vaani-demo');
