/**
 * UI VERIFICATION HARNESS — not part of the product.
 * Boots the real VAANI app (real DB, routes, realtime, orchestrator, privacy filter, validation) but with
 * SCRIPTED AI/speech providers, so every UI state can be rendered and screenshotted without live credentials.
 * Responses are canned and clearly fake. Run: npx tsx test/e2e-server.ts
 */
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadConfig } from '../src/config.js';
import { openDb } from '../src/db.js';
import { Store } from '../src/store.js';
import { hashPassword } from '../src/auth.js';
import { AIOrchestrator } from '../src/ai/orchestrator.js';
import { IcsCalendarProvider } from '../src/ai/actions.js';
import type { AICompletionRequest, AIProvider } from '../src/ai/types.js';
import { createApp } from '../src/app.js';
import { attachRealtime } from '../src/realtime.js';
import { FakeSTT, FakeTTS } from './helpers.js';

class ScriptedAI implements AIProvider {
  readonly name = 'scripted-for-ui-tests';
  isConfigured() { return true; }
  async complete(req: AICompletionRequest) {
    const s = req.system;
    const ids = [...req.messages[0].content.matchAll(/\[#(\d+)\]/g)].map((m) => Number(m[1]));
    let text = 'OK';
    if (s.includes('Write ONE reply')) text = 'Sounds good — I’ll have the architecture diagram ready by Wednesday evening.';
    else if (s.includes('Rewrite the user')) text = 'Hi team, could you please share your progress updates before tomorrow’s 4 PM sync? Thank you!';
    else if (s.includes('Explain the target')) text = '“Theek hai” is Hindi for “okay / alright”. Arjun is agreeing and moving the discussion on to who will own the pitch deck. The tone is casual and friendly.';
    else if (s.includes('Answer the user')) text = `The group agreed to use Node for the backend with SQLite for the demo [#${ids[2] ?? 1}] [#${ids[3] ?? 1}]. Postgres was mentioned as a later option, but nobody committed to it.`;
    else if (s.includes('Translate the target')) text = JSON.stringify({ source_language: 'te-IN', translation: 'I’ll make the deck, but I need the architecture diagram by Wednesday.', note: null });
    else if (s.includes('Summarise')) text = JSON.stringify({
      summary: 'The squad picked the stack for the demo and split up the pitch work.',
      key_points: ['Backend stack chosen for the demo', 'Kavya owns the pitch deck'],
      decisions: [{ text: 'Use Node + SQLite for the demo', status: 'confirmed', message_ids: ids.slice(2, 4) }, { text: 'Move to Postgres later', status: 'proposed', message_ids: ids.slice(2, 3) }],
      tasks: [{ text: 'Send the architecture diagram', owner: 'Rose', due: 'Wednesday evening', message_ids: ids.slice(-1) }, { text: 'Build the pitch deck', owner: 'Kavya', due: null, message_ids: ids.slice(-2, -1) }],
      open_questions: [],
    });
    else if (s.includes('structured actions')) {
      const d = new Date(Date.now() + 86_400_000).toISOString().slice(0, 10);
      text = JSON.stringify({ intent: 'Schedule a project discussion', summary: 'Meet tomorrow at 4 PM; everyone brings progress updates.', actions: [
        { type: 'create_event', title: 'Project discussion', date: d, time: '16:00', duration_minutes: 60, participants: ['Hackathon squad'], notes: 'Bring progress updates', text: null },
        { type: 'create_task', title: 'Prepare my progress update', date: d, time: null, duration_minutes: null, participants: [], notes: null, text: null },
      ] });
    }
    if (req.onText) for (const chunk of text.match(/.{1,6}/gs) ?? []) { req.onText(chunk); await new Promise((r) => setTimeout(r, 12)); }
    return { text, model: 'scripted', inputTokens: 100, outputTokens: 40, stopReason: 'end_turn' };
  }
}

const cfg = loadConfig({ dbPath: ':memory:', jwtSecret: 'e'.repeat(40), port: Number(process.env.PORT ?? 8790) });
const store = new Store(openDb(':memory:'));
const pw = hashPassword('vaani-demo');
const mk = (e: string, n: string, l: string) => { const u = store.createUser(e, pw, n, l); store.updateSettings(u.id, { onboarded: e !== 'new@vaani.dev' }); return u.id; };
const rose = mk('rose@vaani.dev', 'Rose', 'en-IN');
const meena = mk('meena@vaani.dev', 'Meena Sundaram', 'ta-IN');
const arjun = mk('arjun@vaani.dev', 'Arjun Mehta', 'hi-IN');
const kavya = mk('kavya@vaani.dev', 'Kavya Reddy', 'te-IN');
mk('new@vaani.dev', 'New Person', 'en-IN');
const dm = store.createDirect(rose, meena);
store.insertMessage({ conversationId: dm.id, senderId: meena, kind: 'text', body: 'Rose, நாளைக்கு demo-க்கு ready-ஆ?' });
store.insertMessage({ conversationId: dm.id, senderId: rose, kind: 'text', body: 'Almost! Voice notes work, polishing the summary screen.' });
store.insertMessage({ conversationId: dm.id, senderId: meena, kind: 'text', body: 'சூப்பர். Slides-ஐ இன்னிக்கு ராத்திரிக்குள்ள அனுப்பிடு, நான் review பண்றேன்.' });
const g = store.createGroup(rose, 'Hackathon squad', [meena, arjun, kavya]);
const say = (who: number, body: string) => store.insertMessage({ conversationId: g.id, senderId: who, kind: 'text', body });
say(arjun, 'Backend ke liye FastAPI ya Node? Mujhe Node better lag raha hai kyunki realtime easy hai.');
say(kavya, 'Node is fine with me. Postgres or SQLite for the demo?');
say(rose, 'SQLite for the demo, Postgres later. Let’s lock Node + SQLite.');
const agreed = say(meena, 'Agreed. Node + SQLite final.');
store.toggleReaction(agreed.id, arjun, '👍'); store.toggleReaction(agreed.id, kavya, '👍');
say(arjun, 'Theek hai. Who is doing the pitch deck?');
say(kavya, 'నేను deck చేస్తాను, but I need the architecture diagram by Wednesday.');
const v = store.insertMessage({ conversationId: g.id, senderId: arjun, kind: 'voice', body: null });
const wf = Array.from({ length: 48 }, (_, i) => Math.round((0.25 + 0.75 * Math.abs(Math.sin(i / 3.1)) * (0.6 + 0.4 * Math.sin(i / 7))) * 100) / 100);
store.saveVoice(v.id, { mime: 'audio/wav', durationMs: 7400, waveform: wf, audio: Buffer.alloc(44), transcriptStatus: 'done' });
store.setTranscript(v.id, 'done', 'Tomorrow at four, let’s discuss the project and ask everyone to bring their progress updates.', 'en-IN', null);

const deps: Parameters<typeof createApp>[0] = { cfg, store, ai: new AIOrchestrator(store, new ScriptedAI(), cfg, new IcsCalendarProvider()), stt: new FakeSTT(), tts: new FakeTTS() };
const here = path.dirname(fileURLToPath(import.meta.url));
const app = createApp(deps, { staticDir: path.resolve(here, '../../client/dist') });
const server = http.createServer(app);
deps.hub = attachRealtime(server, store, cfg.jwtSecret);
server.listen(cfg.port, () => console.log(`[e2e] VAANI UI harness on http://localhost:${cfg.port} (group ${g.id}, dm ${dm.id}, voice ${v.id})`));
