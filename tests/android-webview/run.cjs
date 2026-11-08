#!/usr/bin/env node
/*
 * VAANI Android WebView — BROWSER SIMULATION harness.
 *
 * Loads app/src/main/assets/www/index.html in Chromium with a recording fake of window.ITantraNative and
 * fixture snapshots shaped like ITantraNativeBridge.buildStateJson(). It verifies the presentation layer:
 * layout at phone widths, light/dark, navigation and back, rendering of every connectivity/sync state, and that
 * every interaction calls an existing bridge method with the right arguments.
 *
 * SIMULATION ONLY. Online/offline states here are fixture values pushed into the page. They are NOT proof of
 * Android connectivity, sync, Bluetooth or Wi-Fi Direct behaviour. Native validation is tests/android/run.mjs.
 *
 *   node tests/android-webview/run.cjs [--out docs/android-ui/screens]
 */
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const F = require('./fixtures.cjs');

const ROOT = path.resolve(__dirname, '..', '..');
const WWW = path.join(ROOT, 'app', 'src', 'main', 'assets', 'www');
const URL = 'file://' + path.join(WWW, 'index.html');
const outArg = process.argv.indexOf('--out');
const OUT = path.resolve(ROOT, outArg > 0 ? process.argv[outArg + 1] : 'docs/android-ui/screens');
fs.mkdirSync(OUT, { recursive: true });
const FAKE = fs.readFileSync(path.join(__dirname, 'fake-native.js'), 'utf8');
const WIDTHS = [320, 360, 375, 390, 412, 430];

const results = [];
let browser;

async function open({ width = 390, height = 800, theme = 'light', fixture = F.base(), onboarded = true, noBridge = false } = {}) {
  const ctx = await browser.newContext({ viewport: { width, height }, deviceScaleFactor: 2, colorScheme: theme, hasTouch: true, isMobile: true });
  await ctx.addInitScript(({ fixture, onboarded, noBridge }) => {
    window.__fixture = fixture; window.__noBridge = noBridge;
    try { if (onboarded) localStorage.setItem('it_onboarded_v2', 'true'); else localStorage.removeItem('it_onboarded_v2'); } catch (e) { /* file:// */ }
  }, { fixture, onboarded, noBridge });
  await ctx.addInitScript(FAKE);
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  await page.goto(URL);
  await page.evaluate(() => document.fonts.ready);
  page.__errors = errors;
  return page;
}
const push = (page, state) => page.evaluate((s) => window.onAppStateUpdate(JSON.stringify(s)), state);
const calls = (page) => page.evaluate(() => window.__calls.filter((c) => c.m !== 'getInitialState'));
const callStr = async (page) => (await calls(page)).map((x) => x.m + ':' + x.args.join(','));
const tap = (page, sel) => page.locator(sel).first().click();
const mode = (page) => page.evaluate(() => document.getElementById('app').dataset.mode);
const text = (page, sel) => page.locator(sel).first().textContent();
async function shot(page, name) { const file = `${name}.png`; await page.waitForTimeout(60); await page.screenshot({ path: path.join(OUT, file), animations: 'disabled' }); return file; }
async function test(name, fn) {
  const t0 = Date.now();
  try { const shots = (await fn()) || []; results.push({ name, ok: true, ms: Date.now() - t0, shots }); console.log('PASS', name); }
  catch (e) { results.push({ name, ok: false, ms: Date.now() - t0, error: String((e && e.message) || e) }); console.log('FAIL', name, '\n   ', e && e.message); }
}
function assert(c, msg) { if (!c) throw new Error(msg); }
const openMeera = async (page) => { await tap(page, '[data-act="open-chat"][data-peer="peer-meera-7f2a"]'); };

async function layoutChecks(page, label) {
  const r = await page.evaluate(() => {
    const W = document.documentElement.clientWidth;
    const wide = [...document.querySelectorAll('body *')].filter((el) => {
      const b = el.getBoundingClientRect(); const cs = getComputedStyle(el);
      return b.width > 0 && cs.position !== 'fixed' && !el.closest('svg') && (b.right > W + 1 || b.left < -1) && !el.closest('.filters, .ptt-steps, .segmented');
    }).map((el) => el.tagName + '.' + el.className.toString().split(' ')[0]);
    const symbols = new Set([...document.querySelectorAll('symbol')].map((s) => s.id));
    const missing = [...document.querySelectorAll('use')].map((u) => u.getAttribute('href').slice(1)).filter((id) => !symbols.has(id));
    const ligatures = document.querySelectorAll('.material-symbols-outlined, .material-icons').length;
    const small = [...document.querySelectorAll('button, [role="radio"], input, textarea, a')].filter((el) => {
      const b = el.getBoundingClientRect(); if (!b.width || getComputedStyle(el).visibility === 'hidden') return false;
      return Math.min(b.width, b.height) < 32 && !el.classList.contains('link');
    }).map((el) => (el.getAttribute('aria-label') || el.textContent || el.tagName).trim().slice(0, 24));
    return { scrollW: document.scrollingElement.scrollWidth, W, wide: wide.slice(0, 5), missing, ligatures, small: small.slice(0, 5) };
  });
  assert(r.scrollW <= r.W, `${label}: horizontal page scroll (${r.scrollW} > ${r.W})`);
  assert(!r.wide.length, `${label}: elements overflow the viewport: ${r.wide.join(', ')}`);
  assert(!r.missing.length, `${label}: icon symbols missing: ${r.missing.join(', ')}`);
  assert(!r.ligatures, `${label}: legacy icon-font elements present`);
  assert(!r.small.length, `${label}: tap targets under 32px: ${r.small.join(' | ')}`);
  assert(!page.__errors.length, `${label}: page errors: ${page.__errors.join(' | ')}`);
}

(async () => {
  browser = await chromium.launch();

  /* ---------- static ---------- */
  await test('bridge contract: every call() names a method in the fake, and the fake matches the Kotlin bridge', async () => {
    const src = fs.readFileSync(path.join(WWW, 'vaani-app.js'), 'utf8');
    const used = [...new Set([...src.matchAll(/call\('([A-Za-z]+)'/g)].map((m) => m[1]))];
    const methods = (FAKE.match(/const METHODS = \[([\s\S]*?)\]/)[1].match(/'([A-Za-z]+)'/g) || []).map((s) => s.slice(1, -1));
    const unknown = used.filter((m) => !methods.includes(m));
    assert(!unknown.length, 'unknown bridge methods: ' + unknown.join(', '));
    const kt = path.join(ROOT, 'app/src/main/java/com/vokie/ui/bridge/ITantraNativeBridge.kt');
    if (fs.existsSync(kt)) {
      const k = [...fs.readFileSync(kt, 'utf8').matchAll(/@JavascriptInterface\s+fun\s+([A-Za-z]+)/g)].map((m) => m[1]);
      const drift = methods.filter((m) => !k.includes(m)).concat(k.filter((m) => !methods.includes(m)));
      assert(!drift.length, 'fake bridge and Kotlin bridge differ: ' + drift.join(', '));
    } else console.log('    (Kotlin bridge not present in this checkout; drift check runs on the repo)');
    console.log('    used', used.length, 'of', methods.length, 'methods; unused:', methods.filter((m) => !used.includes(m)).join(', '));
  });
  await test('static: no network APIs, no message storage, no prototype data, no icon font', async () => {
    const src = fs.readFileSync(path.join(WWW, 'vaani-app.js'), 'utf8') + fs.readFileSync(path.join(WWW, 'index.html'), 'utf8');
    for (const bad of ['fetch(', 'XMLHttpRequest', 'WebSocket', 'Math.random', 'navigator.onLine', 'iTantra', 'fonts.googleapis', 'Material Symbols', 'screen-chat-sarav']) assert(!src.includes(bad), 'found ' + bad);
    const ls = [...src.matchAll(/localStorage\.(setItem|getItem)\('([^']+)'/g)].map((m) => m[2]);
    assert(ls.every((k) => k === 'it_onboarded_v2'), 'unexpected localStorage keys: ' + ls.join(', '));
  });

  /* ---------- layout: every width x theme ---------- */
  for (const theme of ['light', 'dark']) {
    for (const w of WIDTHS) {
      await test(`layout ${w}px ${theme}: Chats, Conversation, Cloud conversation, Speak, Nearby, Settings`, async () => {
        const page = await open({ width: w, theme }); const shots = [];
        await layoutChecks(page, 'chats'); shots.push(await shot(page, `w${w}-${theme}-chats`));
        await openMeera(page); await layoutChecks(page, 'conversation'); shots.push(await shot(page, `w${w}-${theme}-conversation`));
        await page.evaluate(() => window.handleAndroidBack());
        await tap(page, '[data-act="open-cloud"][data-cloud="52"]'); await push(page, F.base({ cloudChat: F.cloudChat52() }));
        await layoutChecks(page, 'cloud conversation'); shots.push(await shot(page, `w${w}-${theme}-cloud-conversation`));
        await page.evaluate(() => window.handleAndroidBack());
        await tap(page, '[data-act="open-speak"]'); await layoutChecks(page, 'speak'); shots.push(await shot(page, `w${w}-${theme}-speak`));
        await page.evaluate(() => window.handleAndroidBack());
        await tap(page, '[data-tab="nearby"]'); await layoutChecks(page, 'nearby'); shots.push(await shot(page, `w${w}-${theme}-nearby`));
        await tap(page, '[data-tab="settings"]'); await layoutChecks(page, 'settings'); shots.push(await shot(page, `w${w}-${theme}-settings`));
        assert(await page.evaluate(() => document.documentElement.dataset.theme) === theme, 'theme');
        await page.context().close(); return shots;
      });
    }
  }

  /* ---------- navigation ---------- */
  await test('startup: title, fonts, Chats is home, three tabs', async () => {
    const page = await open();
    assert(await page.title() === 'VAANI — Every language. One conversation.', 'title');
    const fonts = await page.evaluate(async () => Promise.all([['600 16px "Plus Jakarta Sans"', 'Aa'], ['600 24px "Fraunces Variable"', 'Aa'], ['16px "Noto Sans Devanagari"', 'हि'], ['16px "Noto Sans Tamil"', 'த'], ['16px "Noto Sans Oriya"', 'ଓ']].map(async ([f, t]) => (await document.fonts.load(f, t)).some((x) => x.status === 'loaded'))));
    assert(fonts.every(Boolean), 'fonts ' + fonts);
    assert(await mode(page) === 'chats', 'home is chats');
    assert((await page.locator('#tabbar .tab').allTextContents()).map((t) => t.replace(/\d+/g, '').trim()).join('|') === 'Chats|Nearby|Settings', 'tabs');
    await page.context().close();
  });
  await test('system back: sheet → conversation → chats → exit; speak and cloud chat close', async () => {
    const page = await open();
    await openMeera(page); await tap(page, '.ctx-btn');
    const seq = [];
    for (let i = 0; i < 4; i++) seq.push(await page.evaluate(() => [window.handleAndroidBack(), document.getElementById('app').dataset.mode].join(':')));
    assert(seq.join(' ') === 'true:conversation true:chats false:chats false:chats', seq.join(' '));
    await tap(page, '[data-act="open-speak"]'); assert(await mode(page) === 'speak', 'speak opens');
    assert(await page.evaluate(() => window.handleAndroidBack()) && await mode(page) === 'chats', 'speak closes on back');
    await tap(page, '[data-act="open-cloud"][data-cloud="57"]'); await page.evaluate(() => window.handleAndroidBack());
    const c = await callStr(page);
    assert(c.includes('onOpenCloudConversation:57') && c.includes('onCloseCloudConversation:'), 'cloud open/close ' + c);
    await tap(page, '[data-tab="settings"]'); assert(await page.evaluate(() => window.handleAndroidBack()) && await mode(page) === 'chats', 'tab back');
    await page.context().close();
  });
  await test('notification tap: openNativeChat opens the conversation', async () => {
    const page = await open(); await page.evaluate(() => window.openNativeChat('peer-meera-7f2a'));
    assert(await mode(page) === 'conversation', 'mode'); await page.context().close();
  });
  await test('onboarding: 4 steps, 10 languages, name sent, persisted', async () => {
    const page = await open({ onboarded: false }); const shots = [];
    shots.push(await shot(page, 'onboarding-1-welcome'));
    await tap(page, '[data-act="ob-next"]'); assert(await page.locator('.lang-opt').count() === 10, '10 languages'); shots.push(await shot(page, 'onboarding-2-language'));
    await tap(page, '[data-code="TA"]'); await tap(page, '[data-act="ob-next"]');
    await page.fill('#name-input', 'Rose K'); shots.push(await shot(page, 'onboarding-3-name'));
    await tap(page, '[data-act="ob-name"]'); shots.push(await shot(page, 'onboarding-4-permissions'));
    assert(await page.evaluate(() => window.handleAndroidBack()) === true, 'back steps back');
    await tap(page, '[data-act="ob-name"]'); await tap(page, '[data-act="ob-finish"]');
    const c = await callStr(page);
    assert(c.includes('onSelectLanguage:TA') && c.includes('onUpdateDisplayName:Rose K'), 'calls ' + c);
    assert(await page.evaluate(() => localStorage.getItem('it_onboarded_v2')) === 'true' && await mode(page) === 'chats', 'persisted, lands on chats');
    await page.context().close(); return shots;
  });

  /* ---------- SIMULATED connectivity states (rendering only — not Android connectivity proof) ---------- */
  await test('[simulated] online: Internet "Online · Wi-Fi" and Nearby "2 connected" shown as separate indicators', async () => {
    const page = await open();
    const pills = await page.locator('.status-row .status-pill').allTextContents();
    assert(pills.length === 2 && /Online · Wi-Fi/.test(pills[0]) && /2 connected nearby/.test(pills[1]), pills.join(' | '));
    const s = await shot(page, 'sim-online-chats'); await page.context().close(); return [s];
  });
  await test('[simulated] no internet + nearby connected: nearby stays usable, sync waits, AI explains', async () => {
    const page = await open({ fixture: F.offlineNearby() }); const shots = [];
    const pills = await page.locator('.status-row .status-pill').allTextContents();
    assert(/No internet/.test(pills[0]) && /2 connected nearby/.test(pills[1]), pills.join(' | '));
    assert((await text(page, '[data-peer="peer-arjun-19bc"] .sync')).includes('3 waiting to sync · needs internet'), 'pending sync line');
    shots.push(await shot(page, 'sim-offline-nearby-chats'));
    await openMeera(page);
    assert(!(await page.locator('#ptt-inline').isDisabled()), 'push-to-talk still enabled with no internet');
    await page.fill('#composer', 'Coming'); await tap(page, '[data-act="send"]');
    assert((await callStr(page)).includes('onSendMessage:peer-meera-7f2a,Coming'), 'nearby send allowed offline');
    await tap(page, '.ctx-btn');
    assert((await text(page, '.sheet')).includes('AI needs internet'), 'AI explains');
    assert(await page.locator('.ctx-action:not([disabled])').count() === 0, 'AI actions disabled offline');
    shots.push(await shot(page, 'sim-offline-context-sheet'));
    await page.evaluate(() => window.handleAndroidBack()); await page.evaluate(() => window.handleAndroidBack());
    await tap(page, '[data-tab="settings"]');
    assert(await page.locator('[data-act="sync-now"]').isDisabled(), 'sync now disabled without validated internet');
    assert((await text(page, '#view')).includes('Waiting for internet'), 'native sync state shown');
    shots.push(await shot(page, 'sim-offline-settings'));
    await page.context().close(); return shots;
  });
  await test('[simulated] Wi-Fi connected but not validated is NOT shown as Online', async () => {
    const page = await open({ fixture: F.wifiNotValidated() });
    const pill = await text(page, '.status-row .status-pill');
    assert(/No internet yet/.test(pill) && !/Online/.test(pill), pill);
    const s = await shot(page, 'sim-wifi-not-validated'); await page.context().close(); return [s];
  });
  await test('[simulated] captive portal shows "Sign-in needed"', async () => {
    const page = await open({ fixture: F.captivePortal() });
    assert(/Sign-in needed/.test(await text(page, '.status-row .status-pill')), 'captive'); await page.context().close();
  });
  await test('[simulated] reconnect: pending count clears when native reports synced', async () => {
    const page = await open({ fixture: F.offlineNearby() });
    await push(page, F.base());
    assert(await page.locator('[data-peer="peer-arjun-19bc"] .sync').count() === 0, 'no pending line after sync');
    assert(/Online/.test(await text(page, '.status-row .status-pill')), 'online');
    await page.context().close();
  });

  /* ---------- origins: Nearby / Cloud-linked / Cloud ---------- */
  await test('origins: badges come from exported origin; filters split Nearby and Cloud', async () => {
    const page = await open(); const shots = [];
    const badge = async (sel) => (await text(page, sel + ' .origin')).trim();
    assert(await badge('[data-peer="peer-meera-7f2a"]') === 'Nearby', 'meera nearby');
    assert(await badge('[data-peer="peer-arjun-19bc"]') === 'Cloud linked', 'arjun linked');
    assert(await badge('[data-cloud="52"]') === 'Cloud', 'cloud group');
    await tap(page, '[data-f="nearby"]'); assert(await page.locator('.conv').count() === 2, 'nearby filter = 2');
    await tap(page, '[data-f="cloud"]'); assert(await page.locator('.conv').count() === 3, 'cloud filter = linked + cloud = 3');
    shots.push(await shot(page, 'chats-filter-cloud'));
    await tap(page, '[data-f="unread"]'); assert(await page.locator('.conv').count() === 2, 'unread = 2');
    await tap(page, '[data-f="all"]');
    assert((await page.locator('[data-tab="chats"] .count').textContent()).trim() === '3', 'tab unread = native sum');
    await page.fill('#chat-search', 'tanker'); assert(await page.locator('.conv').count() === 1, 'search covers cloud previews');
    await page.context().close(); return shots;
  });
  await test('origins: a local record without an account mapping is Nearby even when signed in; signed out shows no cloud rows', async () => {
    const page = await open({ fixture: F.signedOut() });
    assert(await page.locator('.origin.linked, .origin.cloud').count() === 0, 'no cloud badges when signed out');
    assert(await page.locator('.origin.nearby').count() === 3, 'all local = nearby');
    const s = await shot(page, 'chats-signed-out'); await page.context().close(); return [s];
  });
  await test('cloud conversation: read-only transcript from exported cloudChat; no composer; explains linking', async () => {
    const page = await open(); const shots = [];
    await tap(page, '[data-act="open-cloud"][data-cloud="52"]');
    assert((await callStr(page)).includes('onOpenCloudConversation:52'), 'open call');
    await push(page, F.base({ cloudChat: F.cloudChat52() }));
    assert(await page.locator('.turn').count() === 4 && await page.locator('#composer').count() === 0, 'read-only transcript');
    assert((await text(page, '.readonly-note')).includes('Read-only on Android'), 'note');
    shots.push(await shot(page, 'cloud-conversation'));
    await page.context().close(); return shots;
  });
  await test('linking: picks a stored cloud conversation; calls linkCloudConversation(conversations[].id, cloudId)', async () => {
    const page = await open(); const shots = [];
    await openMeera(page);
    await tap(page, '[data-act="link-cloud"]'); shots.push(await shot(page, 'link-sheet'));
    await tap(page, '[data-act="link-to"][data-cloud="52"]');
    assert((await callStr(page)).includes('linkCloudConversation:conv-meera,52'), 'link args');
    await page.context().close(); return shots;
  });
  await test('cloud-linked conversation header shows sync state from exported counts', async () => {
    const page = await open({ fixture: F.offlineNearby() });
    await tap(page, '[data-act="open-chat"][data-peer="peer-arjun-19bc"]');
    const f = F.offlineNearby(); const me = 'self-rose-a1'; const a = F.people.arjun.id;
    f.selectedPeer = { ...F.people.arjun, transport: 'bluetooth', connected: true };
    f.chatMessages = [
      { id: 'a1', text: 'Water stall moved next to the medical tent.', senderId: a, isOutgoing: false, time: F.NOW - 52 * 60000, status: 'DELIVERED', type: 'TEXT', language: 'EN', urgent: false, semanticLabel: 'Update', translatedText: null, translatedLanguage: null },
      { id: 'a2', text: 'Thanks — sending two volunteers there now.', senderId: me, isOutgoing: true, time: F.NOW - 9 * 60000, status: 'DELIVERED', type: 'TEXT', language: 'EN', urgent: false, semanticLabel: null, translatedText: null, translatedLanguage: null },
    ];
    await push(page, f);
    assert((await text(page, '.convo-meta')).includes('3 waiting to sync'), 'pending in header');
    const s = await shot(page, 'conversation-linked-pending'); await page.context().close(); return [s];
  });

  /* ---------- nearby ---------- */
  await test('nearby: four separate counts; scan, Wi-Fi Direct, connect, pair; outgoing "Request sent"; incoming pair', async () => {
    const page = await open(); const shots = [];
    await tap(page, '[data-tab="nearby"]');
    const stats = await page.locator('.stat').evaluateAll((els) => els.map((e) => e.querySelector('b').textContent + ' ' + e.querySelector('span').textContent));
    assert(stats.join('|') === '2 Connected|1 Nearby|3 Trusted|0 Pairing', stats.join('|'));
    await tap(page, '[data-act="scan"]'); await tap(page, '[data-act="scan-wifi"]'); await tap(page, '[data-act="connect"]'); await tap(page, '[data-act="pair"]'); await tap(page, '[data-act="connect-wifi"]');
    const c = await callStr(page);
    for (const want of ['startDiscovery:', 'onDiscoverWifiDirect:', 'connectPeer:peer-dev-6e03', 'sendPairRequest:peer-dev-6e03', 'onConnectWifiDirect:9a:3c:51:0e:22:7b']) assert(c.includes(want), want);
    shots.push(await shot(page, 'nearby'));
    await push(page, F.base({ nearby: { discovered: 1, connected: 2, trusted: 3, pairingIncoming: 1, pairingOutgoing: 1 }, outgoingPairRequests: [{ requestId: 'req-out', peerId: 'peer-dev-6e03', name: 'Dev Patil' }],
      incomingPairRequest: { requestId: 'req-55', senderPeerId: 'peer-x', displayName: 'Lakshmi Rao', initial: 'L' } }));
    assert((await text(page, '#view')).includes('Request sent'), 'outgoing state');
    assert((await page.locator('[data-tab="nearby"] .count').textContent()).trim() === '1', 'incoming pairing badge');
    shots.push(await shot(page, 'nearby-pairing'));
    await tap(page, '[data-act="pair-review"]'); shots.push(await shot(page, 'pair-request'));
    await tap(page, '[data-act="pair-accept"]');
    assert((await callStr(page)).includes('acceptPair:req-55'), 'accept');
    await push(page, F.empty()); shots.push(await shot(page, 'nearby-empty'));
    await page.context().close(); return shots;
  });

  /* ---------- speak / conversation / AI / calls / settings ---------- */
  await test('speak: PTT press/release forwarded, release outside still ends, states from native, disabled offline peer', async () => {
    const page = await open(); const shots = [];
    await tap(page, '[data-act="open-speak"]');
    const box = await page.locator('#ptt').boundingBox();
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2); await page.mouse.down();
    await push(page, F.base({ pttState: 'listening', pttLabel: 'Listening…' })); shots.push(await shot(page, 'speak-listening'));
    await page.mouse.move(5, 5); await page.mouse.up();
    const c = await callStr(page);
    assert(c.filter((x) => x === 'onPttDown:').length === 1 && c.filter((x) => x === 'onPttUp:').length === 1, c.join(' '));
    await push(page, F.base({ pttState: 'sending', pttLabel: 'Sending…' })); shots.push(await shot(page, 'speak-sending'));
    await push(page, F.base({ pttState: 'failed', pttLabel: 'Voice message failed' })); shots.push(await shot(page, 'speak-failed'));
    await push(page, F.base({ selectedPeer: { ...F.people.farah, transport: 'offline', connected: false } }));
    assert(await page.locator('#ptt').isDisabled(), 'disabled'); shots.push(await shot(page, 'speak-peer-offline'));
    await tap(page, '[data-act="pick-language"]'); assert(await page.locator('.sheet .lang-opt').count() === 10, '10'); shots.push(await shot(page, 'language-sheet'));
    await page.context().close(); return shots;
  });
  await test('conversation: turns, gloss, delivery, voice playback state, mark read, composer send, keyboard', async () => {
    const page = await open(); const shots = [];
    await openMeera(page);
    const c = await callStr(page);
    for (const m of ['onSelectPeer', 'onMarkAsRead', 'onDismissNotification']) assert(c.includes(`${m}:peer-meera-7f2a`), m);
    assert(await page.locator('.turn').count() === 3 && (await text(page, '.gloss')).includes('gate number two'), 'turns+gloss');
    assert((await page.locator('.delivery').allTextContents()).join('|').includes('Retrying'), 'retrying');
    assert(await page.locator('.voice-strip').evaluateAll((els) => els.length === 2 && els.every((e) => !/\d+:\d\d/.test(e.textContent))), 'no invented durations');
    shots.push(await shot(page, 'conversation'));
    await tap(page, '.voice-disc'); assert((await callStr(page)).includes('playMessageAudio:m2,हाँ, मैं गेट नंबर दो पर हूँ, नीली जैकेट में।,HI'), 'play args');
    await push(page, F.base({ messageAudio: { m2: 'SYNTHESIZING' } })); assert((await text(page, '.voice-strip')).includes('Preparing voice'), 'tts state');
    await page.fill('#composer', 'See you at gate two'); await page.press('#composer', 'Enter');
    assert((await callStr(page)).includes('onSendMessage:peer-meera-7f2a,See you at gate two'), 'send');
    await page.setViewportSize({ width: 390, height: 460 }); await page.focus('#composer'); await page.waitForTimeout(250);
    const b = await page.locator('.composer').boundingBox(); assert(b.y + b.height <= 461, 'composer visible with keyboard');
    shots.push(await shot(page, 'conversation-keyboard'));
    await page.context().close(); return shots;
  });
  await test('AI Context sheet: four supported ops; draft enters composer; never sends; Stop; unconfigured explained', async () => {
    const page = await open(); const shots = [];
    await openMeera(page); await tap(page, '.ctx-btn');
    assert(await page.locator('.ctx-action').count() === 4, 'four ops');
    shots.push(await shot(page, 'context-sheet'));
    await tap(page, '.ctx-action[data-op="REPLY"]');
    let req = (await calls(page)).find((x) => x.m === 'onRequestAi'); assert(req.args[0] === 'REPLY' && req.args[2] === null, 'REPLY');
    await push(page, F.base({ ai: { loading: true, error: null, text: null, actionsRequireConfirmation: true } })); shots.push(await shot(page, 'ai-thinking'));
    await tap(page, '[data-act="ai-cancel"]'); assert((await callStr(page)).includes('onCancelAiRequest:'), 'stop');
    await push(page, F.base({ ai: { loading: false, error: null, text: 'Yes — I’ll keep a spot by the water stall. Look for the blue jacket.', actionsRequireConfirmation: true } }));
    shots.push(await shot(page, 'ai-proposal'));
    await tap(page, '[data-act="ai-use"]');
    assert((await page.inputValue('#composer')).startsWith('Yes — I’ll keep a spot') && await page.locator('.drafted').count() === 1, 'draft in composer');
    assert(!(await callStr(page)).some((x) => x.startsWith('onSendMessage')), 'AI must not send');
    shots.push(await shot(page, 'ai-draft-in-composer'));
    await tap(page, '.vaani-key'); await tap(page, '.ctx-action[data-op="TRANSLATE"]'); await tap(page, '.sheet [data-code="HI"]');
    req = (await calls(page)).filter((x) => x.m === 'onRequestAi').pop(); assert(req.args[0] === 'TRANSLATE' && req.args[2] === 'HI' && req.args[1].startsWith('Yes'), 'translate');
    await page.context().close();
    const p2 = await open({ fixture: F.unconfigured() }); await openMeera(p2); await tap(p2, '.ctx-btn');
    assert((await text(p2, '.sheet')).includes('isn’t configured') && await p2.locator('.ctx-action:not([disabled])').count() === 0, 'unconfigured');
    shots.push(await shot(p2, 'context-unconfigured')); await p2.context().close();
    return shots;
  });
  await test('calls: start, cancel, accept, mute, speaker, end; failure reason', async () => {
    const page = await open(); const shots = [];
    await openMeera(page); await tap(page, '[data-act="start-call"]');
    const call = (o) => F.base({ call: { callId: 'call-9', peerId: 'peer-meera-7f2a', peerName: 'Meera Iyer', peerInitial: 'M', failureReason: null, ...o } });
    await push(page, call({ state: 'CALLING', direction: 'OUTGOING' })); shots.push(await shot(page, 'call-outgoing')); await tap(page, '[data-act="call-cancel"]');
    await push(page, call({ state: 'RINGING', direction: 'INCOMING' })); shots.push(await shot(page, 'call-incoming')); await tap(page, '[data-act="call-accept"]');
    await push(page, call({ state: 'CONNECTED', direction: 'INCOMING' })); await tap(page, '[data-act="call-mute"]'); await tap(page, '[data-act="call-speaker"]');
    shots.push(await shot(page, 'call-connected')); await tap(page, '[data-act="call-end"]');
    const c = await callStr(page);
    for (const want of ['onStartCall:peer-meera-7f2a', 'onCancelCall:call-9', 'onAcceptCall:call-9', 'onSetCallMuted:true', 'onSetCallSpeakerphone:true', 'onEndCall:call-9']) assert(c.includes(want), want);
    await push(page, call({ state: 'FAILED', direction: 'OUTGOING', failureReason: 'Peer went out of range' }));
    assert((await text(page, '#call')).includes('Peer went out of range'), 'reason'); shots.push(await shot(page, 'call-failed'));
    await page.context().close(); return shots;
  });
  await test('settings: name, speed, connectivity rows, cloud sign-in (password never in DOM), sync opt-in, unconfigured', async () => {
    const page = await open({ fixture: F.signedOut() }); const shots = [];
    await tap(page, '[data-tab="settings"]');
    await tap(page, '[data-act="edit-name"]'); await page.fill('#name-input', 'Rose M'); await tap(page, '[data-act="save-name"]');
    await tap(page, '[data-act="tts-speed"][data-v="1.25"]');
    await page.fill('#cloud-email', 'test@example.invalid'); await page.fill('#cloud-password', 'not-a-real-secret');
    await page.locator('#cloud-form button[type="submit"]').click(); shots.push(await shot(page, 'settings-signing-in'));
    await page.evaluate(() => window.onCloudAuthResult(false, 'Email or password is incorrect.'));
    assert((await text(page, '#cloud-form')).includes('incorrect'), 'auth error'); shots.push(await shot(page, 'settings-signin-error'));
    await push(page, F.base({ cloud: { ...F.base().cloud, syncEnabled: false } })); await page.locator('#sync-toggle').check();
    await push(page, F.base()); await tap(page, '[data-act="sync-now"]'); shots.push(await shot(page, 'settings-signed-in-synced'));
    const c = await callStr(page);
    for (const want of ['onUpdateDisplayName:Rose M', 'onSetTtsSpeed:1.25', 'cloudLogin:test@example.invalid,<redacted>', 'setCloudSyncEnabled:true', 'syncCloudNow:']) assert(c.includes(want), want);
    assert(!(await page.content()).includes('not-a-real-secret'), 'password leaked');
    await page.context().close();
    const p2 = await open({ fixture: F.unconfigured() }); await tap(p2, '[data-tab="settings"]');
    assert((await text(p2, '#view')).includes('isn’t configured in this build') && await p2.locator('#cloud-form').count() === 0, 'unconfigured');
    shots.push(await shot(p2, 'settings-cloud-unconfigured')); await p2.context().close();
    return shots;
  });
  await test('empty and loading states; missing bridge', async () => {
    const page = await open({ fixture: F.empty() }); const shots = [];
    shots.push(await shot(page, 'chats-empty')); await page.context().close();
    const p2 = await open({ noBridge: true });
    assert(await p2.locator('[aria-busy="true"]').count() === 1 && (await text(p2, '#view')).includes('isn’t connected'), 'loading + no bridge');
    shots.push(await shot(p2, 'chats-loading-no-bridge')); await p2.context().close();
    return shots;
  });
  await test('dark: offline chats, context sheet, incoming call', async () => {
    const page = await open({ theme: 'dark', fixture: F.offlineNearby() }); const shots = [];
    shots.push(await shot(page, 'dark-offline-chats'));
    await openMeera(page); await tap(page, '.ctx-btn'); shots.push(await shot(page, 'dark-context-sheet'));
    await page.evaluate(() => window.handleAndroidBack());
    await push(page, F.base({ call: { callId: 'c2', state: 'RINGING', direction: 'INCOMING', peerId: 'peer-arjun-19bc', peerName: 'Arjun Reddy', peerInitial: 'A', failureReason: null } }));
    shots.push(await shot(page, 'dark-call-incoming'));
    await page.context().close(); return shots;
  });

  await browser.close();
  const passed = results.filter((r) => r.ok).length;
  const summary = { kind: 'browser-simulation', note: 'Chromium + fake bridge + fixture states. Not Android runtime validation.', ranAt: new Date().toISOString(), passed, failed: results.length - passed, results };
  fs.writeFileSync(path.join(OUT, 'results.json'), JSON.stringify(summary, null, 2));
  console.log(`\n${passed}/${results.length} passed · screenshots in ${path.relative(ROOT, OUT)}`);
  process.exit(passed === results.length ? 0 : 1);
})();
