/*
 * VAANI Android — presentation layer for the WebView.
 *
 * Contract (docs/ANDROID_BRIDGE_STATE_CONTRACT.md): all behaviour lives in native code. This file
 *   - renders `window.onAppStateUpdate(state)` snapshots built by ITantraNativeBridge.buildStateJson(), and
 *   - calls existing `window.ITantraNative.*` @JavascriptInterface methods.
 * It never simulates progress or connectivity, fabricates data, stores messages, or talks to the network.
 * Internet status and Nearby status are separate: a phone can be offline from the internet and still talk nearby.
 */
(function () {
  'use strict';

  /* ================= bridge ================= */
  const Native = () => window.ITantraNative;
  function call(method, ...args) {
    const n = Native();
    if (!n || typeof n[method] !== 'function') { console.warn('[vaani] bridge method unavailable:', method); return undefined; }
    try { return n[method](...args); } catch (e) { console.error('[vaani] bridge call failed:', method, e); return undefined; }
  }

  /* ================= constants ================= */
  const LANGS = [
    { code: 'EN', native: 'English', english: 'English', lang: 'en' },
    { code: 'HI', native: 'हिन्दी', english: 'Hindi', lang: 'hi' },
    { code: 'TA', native: 'தமிழ்', english: 'Tamil', lang: 'ta' },
    { code: 'TE', native: 'తెలుగు', english: 'Telugu', lang: 'te' },
    { code: 'KN', native: 'ಕನ್ನಡ', english: 'Kannada', lang: 'kn' },
    { code: 'BN', native: 'বাংলা', english: 'Bengali', lang: 'bn' },
    { code: 'GU', native: 'ગુજરાતી', english: 'Gujarati', lang: 'gu' },
    { code: 'MR', native: 'मराठी', english: 'Marathi', lang: 'mr' },
    { code: 'ML', native: 'മലയാളം', english: 'Malayalam', lang: 'ml' },
    { code: 'OR', native: 'ଓଡ଼ିଆ', english: 'Odia', lang: 'or' },
  ];
  const GLYPH = { EN: 'En', HI: 'हि', TA: 'த', TE: 'తె', KN: 'ಕ', BN: 'বা', GU: 'ગુ', MR: 'म', ML: 'മ', OR: 'ଓ' };
  const langOf = (code) => LANGS.find((l) => l.code === String(code || '').toUpperCase().split('-')[0]);
  const langName = (code) => (langOf(code) || { english: code || 'Unknown' }).english;

  const DELIVERY = {
    QUEUED: { text: 'Queued', tone: 'warn', icon: 'clock' },
    TRANSMITTING: { text: 'Sending', tone: 'warn', icon: 'clock' },
    RETRYING: { text: 'Retrying', tone: 'warn', icon: 'rotate-cw' },
    RECEIVED_BY_PEER: { text: 'Received', tone: 'ok', icon: 'check' },
    RELAYED: { text: 'Relayed', tone: 'ok', icon: 'check' },
    DELIVERED: { text: 'Delivered', tone: 'ok', icon: 'check-check' },
    FAILED: { text: 'Not delivered', tone: 'err', icon: 'circle-alert' },
  };
  const PTT_STEPS = ['listening', 'understanding', 'sending', 'delivered'];
  const CALL_ACTIVE = ['CALLING', 'CONNECTING', 'CONNECTED', 'RECONNECTING'];
  const CALL_TERMINAL = { REJECTED: 'Call declined', MISSED: 'Missed call', FAILED: 'Call failed', ENDED: 'Call ended', UNAVAILABLE: 'Calling isn’t available' };
  const SYNC = {
    Idle: ['Up to date', 'off'], Syncing: ['Syncing…', 'brand'], Synced: ['Synced', 'ok'],
    WaitingForConnection: ['Waiting for internet', 'warn'], RetryPending: ['Will retry soon', 'warn'],
    AuthRequired: ['Sign in again to sync', 'err'], Error: ['Sync failed', 'err'], PartialError: ['Some items didn’t sync', 'warn'],
  };
  // Internet: only "online" (validated by Android) counts as internet. A connected Wi-Fi alone is not online.
  const NET = {
    online: { label: 'Online', tone: 'ok', icon: 'cloud' },
    connecting: { label: 'No internet yet', tone: 'warn', icon: 'cloud-off', hint: 'Connected to a network, but Android hasn’t confirmed internet access.' },
    limited: { label: 'Sign-in needed', tone: 'warn', icon: 'cloud-off', hint: 'This network needs a browser sign-in before it reaches the internet.' },
    none: { label: 'No internet', tone: 'off', icon: 'cloud-off', hint: 'Nearby messaging still works.' },
  };
  const TTS_SPEEDS = [0.75, 1, 1.25, 1.5];
  const TABS = [['chats', 'Chats', 'messages-square'], ['nearby', 'Nearby', 'radar'], ['settings', 'Settings', 'settings']];

  /* ================= ui state (navigation + transient only) ================= */
  let S = null;
  const ui = {
    tab: 'chats', chatPeerId: null, cloudChatId: null, speak: false, onboarding: null, overlay: null,
    search: '', filter: 'all', composer: '', ai: null, aiSource: null, pressing: false, reminder: null,
    playing: new Set(), nameDraft: null, callNotice: null, authError: '', authBusy: false, muted: false, speaker: false,
  };
  const seenIncoming = new Set();
  let firstChatSnapshot = true;
  let lastCallKey = '';

  /* ================= helpers ================= */
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const icon = (name, cls = '') => `<svg class="i ${cls}" aria-hidden="true"><use href="#i-${name}"/></svg>`;
  const hue = (id) => { let h = 0; for (const ch of String(id || '')) h = (h * 31 + ch.charCodeAt(0)) % 360; return h; };
  const initials = (name) => {
    const p = String(name || '').split(/\s+/).map((w) => (w.match(/[\p{L}\p{N}]/u) || [''])[0]).filter(Boolean);
    return ((p[0] || '?') + (p.length > 1 ? p[p.length - 1] : '')).toUpperCase();
  };
  const avatar = (name, id, size = 40, online = false, group = false) =>
    `<span class="avatar" style="width:${size}px;height:${size}px;font-size:${Math.round(size * .36)}px;--hue:${hue(id || name)}" aria-hidden="true">${group ? icon('users', 'sm') : esc(initials(name))}${online ? '<span class="dot"></span>' : ''}</span>`;
  const glyph = (code) => { const l = langOf(code); return l ? `<span class="lang-glyph" lang="${l.lang}" title="${esc(l.english)}">${GLYPH[l.code]}</span>` : ''; };
  const clock = (ms) => new Date(ms).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  function listTime(ms) {
    const d = new Date(ms), now = new Date();
    if (d.toDateString() === now.toDateString()) return clock(ms);
    return (now - d) / 86400000 < 6 ? d.toLocaleDateString([], { weekday: 'short' }) : d.toLocaleDateString([], { day: 'numeric', month: 'short' });
  }
  function ago(ms) {
    const m = Math.round((Date.now() - ms) / 60000);
    return m < 1 ? 'just now' : m < 60 ? `${m} min ago` : m < 1440 ? `${Math.round(m / 60)} h ago` : listTime(ms);
  }
  function dayLabel(ms) {
    const d = new Date(ms), now = new Date();
    const start = (x) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
    const diff = Math.round((start(now) - start(d)) / 86400000);
    return diff === 0 ? 'Today' : diff === 1 ? 'Yesterday' : d.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'short' });
  }
  const isVoice = (m) => m.type === 'VOICE' || m.type === 'VOICE_TEXT';
  const peers = () => (S && S.activePeers) || [];
  const trusted = () => (S && S.trustedPeers) || [];
  const isActive = (id) => peers().some((p) => p.id === id);
  const cloud = () => (S && S.cloud) || {};
  const netState = () => (S && S.network && S.network.state) || null; // null = this build doesn't report it
  function peerInfo(id) {
    const a = peers().find((p) => p.id === id); if (a) return { ...a, connected: true };
    const c = ((S && S.conversations) || []).find((x) => x.peerId === id);
    const t = trusted().find((p) => p.id === id);
    const sel = S && S.selectedPeer && S.selectedPeer.id === id ? S.selectedPeer : null;
    return { id, name: (c && c.name) || (t && t.name) || (sel && sel.name) || 'Nearby peer', connected: false, transport: 'offline' };
  }
  const transportLabel = (t) => (t === 'wifi' || t === 'Wi-Fi Direct' ? 'Wi-Fi' : t === 'bluetooth' || t === 'Bluetooth' ? 'Bluetooth' : 'Offline');
  const cloudTitle = (c) => c.title || (c.members && c.members.length ? c.members.join(', ') : 'Cloud conversation');
  const localConv = (peerId) => ((S && S.conversations) || []).find((c) => c.peerId === peerId);
  function toast(text) {
    const el = document.getElementById('toast'); if (!el) return;
    el.textContent = text; el.hidden = false; el.style.animation = 'none'; void el.offsetWidth; el.style.animation = '';
    clearTimeout(toast.t); toast.t = setTimeout(() => { el.hidden = true; }, 3200);
  }

  /* ================= native → ui ================= */
  function applyState(state) {
    if (typeof state === 'string') { try { state = JSON.parse(state); } catch { return; } }
    if (!state || typeof state !== 'object') return;
    S = state;
    window.currentAppState = state;
    (state.chatMessages || []).forEach((m) => {
      if (!firstChatSnapshot && !m.isOutgoing && !seenIncoming.has(m.id) && ui.chatPeerId !== m.senderId) ui.reminder = { peerId: m.senderId, text: m.text, urgent: !!m.urgent };
      seenIncoming.add(m.id);
    });
    firstChatSnapshot = false;
    if (state.messageAudio) for (const id of [...ui.playing]) { const st = state.messageAudio[id]; if (st === 'COMPLETED' || st === 'FAILED') ui.playing.delete(id); }
    const c = state.call || {};
    const key = `${c.callId}:${c.state}`;
    if (key !== lastCallKey) { lastCallKey = key; if (CALL_TERMINAL[c.state]) ui.callNotice = { text: CALL_TERMINAL[c.state] + (c.failureReason ? ` — ${c.failureReason}` : ''), at: Date.now() }; }
    if (ui.authBusy && state.cloud && state.cloud.signedIn) ui.authBusy = false;
    render();
  }
  window.onAppStateUpdate = applyState;
  window.ITantraApp = { onStateUpdate: applyState };
  window.onCloudAuthResult = function (ok, message) {
    ui.authBusy = false; ui.authError = ok ? '' : (message || 'Sign-in failed.');
    if (ok) { const p = document.getElementById('cloud-password'); if (p) p.value = ''; toast('Signed in'); }
    render();
  };
  window.openNativeChat = function (peerId) { openChat(peerId); };

  /* ================= navigation ================= */
  function closeCloudChat() { if (ui.cloudChatId != null) { ui.cloudChatId = null; call('onCloseCloudConversation'); } }
  function setTab(tab) { closeCloudChat(); ui.tab = tab; ui.chatPeerId = null; ui.speak = false; ui.overlay = null; ui.ai = null; render(); }
  function openChat(peerId) {
    if (!peerId) return;
    closeCloudChat();
    ui.chatPeerId = peerId; ui.tab = 'chats'; ui.speak = false; ui.overlay = null; ui.reminder = null; ui.ai = null;
    call('onSelectPeer', peerId); call('onMarkAsRead', peerId); call('onDismissNotification', peerId);
    render(); scrollTranscript(true);
  }
  function openCloudChat(cloudId) {
    ui.chatPeerId = null; ui.cloudChatId = cloudId; ui.tab = 'chats'; ui.overlay = null;
    call('onOpenCloudConversation', String(cloudId));
    render(); scrollTranscript(true);
  }
  window.handleAndroidBack = function () {
    if (ui.overlay) { ui.overlay = null; render(); return true; }
    if (ui.onboarding != null) { if (ui.onboarding > 0) { ui.onboarding--; render(); return true; } return false; }
    if (ui.speak) { ui.speak = false; render(); return true; }
    if (ui.ai) { if (S && S.ai && S.ai.loading) call('onCancelAiRequest'); ui.ai = null; render(); return true; }
    if (ui.cloudChatId != null) { closeCloudChat(); render(); return true; }
    if (ui.chatPeerId) { ui.chatPeerId = null; render(); return true; }
    if (ui.tab !== 'chats') { setTab('chats'); return true; }
    return false;
  };

  /* ================= render ================= */
  function mode() {
    if (ui.onboarding != null) return 'onboarding';
    if (ui.speak) return 'speak';
    if (ui.cloudChatId != null) return 'cloud-conversation';
    if (ui.chatPeerId) return 'conversation';
    return ui.tab;
  }
  function render() {
    const app = document.getElementById('app'); const view = document.getElementById('view');
    if (!app || !view) return;
    const focused = document.activeElement && document.activeElement.id;
    const caret = focused && document.activeElement.selectionStart;
    const m = mode(); app.dataset.mode = m;
    view.innerHTML = m === 'onboarding' ? renderOnboarding() : m === 'speak' ? renderSpeak() : m === 'cloud-conversation' ? renderCloudConversation()
      : m === 'conversation' ? renderConversation() : m === 'nearby' ? renderNearby() : m === 'settings' ? renderSettings() : renderChats();
    document.getElementById('tabbar').hidden = !['chats', 'nearby', 'settings'].includes(m);
    renderTabbar();
    document.getElementById('overlay').innerHTML = renderOverlay();
    document.getElementById('call').innerHTML = renderCall();
    if (focused) { const el = document.getElementById(focused); if (el) { el.focus(); if (caret != null && el.setSelectionRange) try { el.setSelectionRange(caret, caret); } catch { /* not text */ } } }
    fitComposer();
  }

  function renderTabbar() {
    const unread = ((S && S.conversations) || []).reduce((n, c) => n + Number(c.unreadCount || 0), 0);
    const pairing = (S && S.nearby && S.nearby.pairingIncoming) || 0;
    document.getElementById('tabbar').innerHTML = TABS.map(([id, label, ic]) => {
      const n = id === 'chats' ? unread : id === 'nearby' ? pairing : 0;
      return `<button class="tab" data-act="tab" data-tab="${id}" ${ui.tab === id ? 'aria-current="page"' : ''}>
        <span class="pill">${icon(ic)}</span>${label}${n > 0 ? `<span class="count" aria-label="${n} ${id === 'chats' ? 'unread' : 'pairing requests'}">${n > 99 ? '99+' : n}</span>` : ''}</button>`;
    }).join('');
  }

  function bridgeMissing() {
    return Native() ? '' : `<div class="banner error" role="alert" style="margin:0 16px 8px">${icon('circle-alert', 'sm')}<span>VAANI’s on-device service isn’t connected. Restart the app.</span></div>`;
  }
  const waiting = () => !S;

  /* ---------- connectivity status: Internet and Nearby are separate ---------- */
  function internetPill() {
    const st = netState();
    if (!st) return `<span class="status-pill off" title="This build doesn’t report internet status">${icon('cloud-off', 'xs')} Internet: unknown</span>`;
    const n = NET[st] || NET.none; const t = S.network.transport;
    const via = st === 'online' && t ? ` · ${t === 'wifi' ? 'Wi-Fi' : t === 'cellular' ? 'Mobile data' : t === 'ethernet' ? 'Ethernet' : 'Network'}` : '';
    return `<span class="status-pill ${n.tone}" data-net="${esc(st)}">${icon(n.icon, 'xs')} ${n.label}${via}</span>`;
  }
  function nearbyPill() {
    const nb = S && S.nearby;
    const connected = nb ? nb.connected : peers().length;
    const discovered = nb ? nb.discovered : ((S && S.discoveredPeers) || []).length;
    const text = connected > 0 ? `${connected} connected nearby` : discovered > 0 ? `${discovered} nearby, not connected` : 'Nobody nearby';
    return `<button class="status-pill ${connected > 0 ? 'ok' : discovered > 0 ? 'warn' : 'off'}" data-act="tab" data-tab="nearby">${icon('radar', 'xs')} ${text}</button>`;
  }

  /* ---------- Chats (home) ---------- */
  function chatItems() {
    const local = ((S && S.conversations) || []).map((c) => ({ kind: 'local', key: 'l:' + c.peerId, origin: c.origin || 'nearby', time: c.time || 0, c }));
    const remote = ((S && S.cloudConversations) || []).map((c) => ({ kind: 'cloud', key: 'c:' + c.cloudId, origin: 'cloud', time: (c.lastMessage && c.lastMessage.createdAt) || c.updatedAt || 0, c }));
    return local.concat(remote).sort((a, b) => b.time - a.time);
  }
  function renderChats() {
    const all = chatItems();
    const counts = { all: all.length, nearby: all.filter((i) => i.origin === 'nearby').length, cloud: all.filter((i) => i.origin !== 'nearby').length, unread: all.filter((i) => i.kind === 'local' && i.c.unreadCount > 0).length };
    const q = ui.search.trim().toLowerCase();
    const list = all.filter((i) => (ui.filter === 'all' || (ui.filter === 'nearby' && i.origin === 'nearby') || (ui.filter === 'cloud' && i.origin !== 'nearby') || (ui.filter === 'unread' && i.kind === 'local' && i.c.unreadCount > 0)))
      .filter((i) => {
        if (!q) return true;
        const hay = i.kind === 'local' ? `${i.c.name} ${i.c.lastMessage}` : `${cloudTitle(i.c)} ${(i.c.lastMessage && i.c.lastMessage.body) || ''}`;
        return hay.toLowerCase().includes(q);
      });
    const req = S && S.incomingPairRequest;
    let body;
    if (waiting()) body = `<div aria-busy="true" aria-label="Loading conversations">${[0, 1, 2, 3].map(() => `<div class="sk-row"><span class="sk" style="width:44px;height:44px;border-radius:50%"></span><span style="flex:1;display:flex;flex-direction:column;gap:8px"><span class="sk" style="height:12px"></span><span class="sk" style="height:12px;width:60%"></span></span></div>`).join('')}</div>`;
    else if (!all.length) body = `<div class="empty"><span class="ico">${icon('messages-square')}</span><h2>No conversations yet</h2><p>Find someone nearby — no internet needed — or sign in to see your cloud conversations.</p><button class="btn primary" data-act="tab" data-tab="nearby">${icon('radar', 'sm')} Find people nearby</button></div>`;
    else if (!list.length) body = `<p class="pad muted" style="padding-top:24px">${q ? `Nothing matches “${esc(ui.search)}”.` : 'No conversations in this view.'}</p>`;
    else body = `<ul>${list.map((i) => i.kind === 'local' ? localRow(i.c) : cloudRow(i.c)).join('')}</ul>`;
    const filters = [['all', 'All'], ['nearby', 'Nearby'], ['cloud', 'Cloud'], ['unread', 'Unread']];
    return `<section class="screen" aria-label="Chats">
      <header class="appbar"><div class="brand">${logo(26)}<span class="wordmark">vaani</span></div></header>
      ${bridgeMissing()}
      <div class="status-row" role="status" aria-label="Connectivity">${internetPill()}${nearbyPill()}</div>
      ${req ? `<div class="pad" style="padding-bottom:8px"><div class="banner info" style="align-items:center">${icon('user-plus', 'sm')}<span style="flex:1"><b>${esc(req.displayName)}</b> wants to pair</span><button class="btn sm primary" data-act="pair-review">Review</button></div></div>` : ''}
      ${ui.reminder ? reminderHtml() : ''}
      <label class="search">${icon('search', 'sm')}<span class="sr-only">Search chats</span><input id="chat-search" type="search" placeholder="Search or find a chat" value="${esc(ui.search)}" data-input="search"></label>
      <div class="filters" role="tablist" aria-label="Filter chats">${filters.map(([id, l]) => `<button role="tab" aria-selected="${ui.filter === id}" data-act="filter" data-f="${id}">${l}${counts[id] && id !== 'all' ? ` <span class="n">${counts[id]}</span>` : ''}</button>`).join('')}</div>
      <div class="scroll chat-list">${body}</div>
      <button class="fab" data-act="open-speak" aria-label="Speak — push to talk to someone nearby">${icon('voice')}<span>Speak</span></button>
    </section>`;
  }
  function originChip(origin) {
    if (origin === 'cloud-linked') return `<span class="origin linked" title="Nearby conversation linked to your cloud account">${icon('link', 'xs')} Cloud linked</span>`;
    if (origin === 'cloud') return `<span class="origin cloud" title="Stored from your cloud account">${icon('cloud', 'xs')} Cloud</span>`;
    return `<span class="origin nearby" title="Phone-to-phone conversation">${icon('radar', 'xs')} Nearby</span>`;
  }
  function syncLine(sync) {
    if (!sync) return '';
    if (sync.failed > 0) return `<span class="sync err">${icon('circle-alert', 'xs')} ${sync.failed} failed to sync</span>`;
    if (sync.pending > 0) {
      const waitNet = netState() && netState() !== 'online';
      return `<span class="sync warn">${icon('clock', 'xs')} ${sync.pending} waiting to sync${waitNet ? ' · needs internet' : ''}</span>`;
    }
    return '';
  }
  function localRow(c) {
    const online = isActive(c.peerId); const unread = Number(c.unreadCount || 0);
    return `<li><button class="conv${unread ? ' unread' : ''}" data-act="open-chat" data-peer="${esc(c.peerId)}">
      ${avatar(c.name, c.peerId, 46, online)}
      <span class="grow"><span class="line"><span class="name">${esc(c.name)}</span>${originChip(c.origin || 'nearby')}<time class="time">${c.time ? listTime(c.time) : ''}</time></span>
        <span class="line"><span class="preview" dir="auto">${c.lastMessage ? esc(c.lastMessage) : '<i>No messages yet</i>'}</span>${unread ? `<span class="unread-count" aria-label="${unread} unread">${unread > 99 ? '99+' : unread}</span>` : ''}</span>
        ${syncLine(c.sync)}</span></button></li>`;
  }
  function cloudRow(c) {
    const lm = c.lastMessage; const t = (lm && lm.createdAt) || c.updatedAt;
    const preview = lm ? `${esc(lm.senderName)}: ${lm.body == null ? (lm.kind === 'voice' ? 'Voice message' : '<i>Deleted</i>') : esc(lm.body)}` : '<i>No messages on this phone yet</i>';
    return `<li><button class="conv" data-act="open-cloud" data-cloud="${esc(c.cloudId)}">
      ${avatar(cloudTitle(c), 'c' + c.cloudId, 46, false, c.kind === 'group')}
      <span class="grow"><span class="line"><span class="name">${esc(cloudTitle(c))}</span>${originChip('cloud')}<time class="time">${t ? listTime(t) : ''}</time></span>
        <span class="line"><span class="preview" dir="auto">${preview}</span></span></span></button></li>`;
  }
  function reminderHtml() {
    const r = ui.reminder;
    return `<div class="reminder${r.urgent ? ' urgent' : ''}" role="status">${icon(r.urgent ? 'triangle-alert' : 'messages-square', 'sm')}
      <div class="grow"><b>${r.urgent ? 'Urgent message' : 'New message'} from ${esc(peerInfo(r.peerId).name)}</b><span dir="auto">${esc(r.text)}</span></div>
      <button class="btn sm secondary" data-act="open-chat" data-peer="${esc(r.peerId)}">View</button>
      <button class="icon-btn" data-act="dismiss-reminder" aria-label="Dismiss">${icon('x', 'sm')}</button></div>`;
  }

  /* ---------- Speak (push-to-talk overlay; nearby only, works without internet) ---------- */
  function renderSpeak() {
    const sel = S && S.selectedPeer;
    const lang = langOf(S && S.language);
    const state = (S && S.pttState) || 'idle';
    const connected = !!(sel && sel.connected);
    const label = ui.pressing && state === 'idle' ? 'Listening…' : (S && S.pttLabel) || 'Hold to speak';
    const stepIdx = PTT_STEPS.indexOf(state);
    const steps = PTT_STEPS.map((s, i) => {
      const cls = state === 'failed' ? '' : i < stepIdx ? 'done' : i === stepIdx ? 'on' : '';
      return `<span class="${cls}">${cls === 'done' ? '✓ ' : ''}${s[0].toUpperCase() + s.slice(1)}</span>`;
    }).join('');
    return `<section class="screen speak" aria-label="Speak">
      <header class="appbar sub"><button class="icon-btn" data-act="close-speak" aria-label="Close">${icon('x', 'lg')}</button><h1>Speak</h1>
        ${connected ? `<button class="icon-btn" data-act="start-call" aria-label="Call ${esc(sel.name)}">${icon('phone')}</button>` : ''}</header>
      <p class="pad small muted" style="padding-bottom:8px">${icon('radar', 'xs')} Phone to phone over Wi-Fi or Bluetooth — no internet needed.</p>
      <button class="peer-target${sel ? '' : ' empty-peer'}" data-act="pick-peer" aria-haspopup="dialog">
        ${sel ? avatar(sel.name, sel.id, 44, connected) : `<span class="ico brand" style="width:44px;height:44px;border-radius:50%;display:grid;place-items:center">${icon('user-plus')}</span>`}
        <span class="grow">${sel ? `<span class="t">${esc(sel.name)}</span><span class="status ${connected ? 'ok' : 'off'}"><span class="dot"></span>${connected ? `Connected · ${transportLabel(sel.transport)}` : 'Not in range'}</span>`
          : `<span class="t">Choose who to talk to</span><span class="small muted">Push-to-talk reaches one nearby person</span>`}</span>${icon('chevron-right', 'sm')}</button>
      <div class="stage-voice">
        <button id="ptt" class="voice-key${ui.pressing ? ' pressing' : ''}" data-state="${esc(state)}" ${connected ? '' : 'disabled'} aria-label="${connected ? `Hold to speak to ${esc(sel.name)}` : 'Push-to-talk needs a connected person nearby'}">${icon('voice')}</button>
        <div><p class="ptt-label" aria-live="polite">${esc(connected ? label : sel ? `${sel.name} isn’t in range` : 'No one selected')}</p>
          <p class="ptt-hint">${connected ? 'Hold, speak, release. VAANI transcribes on this phone and speaks it in their language.' : 'Connect to someone in Nearby, then hold here to speak.'}</p></div>
        ${connected && state !== 'idle' ? `<div class="ptt-steps" aria-label="Voice message progress">${steps}${state === 'speaking' ? '<span class="on">Speaking there</span>' : ''}${state === 'failed' ? '<span class="fail">Not sent — hold to try again</span>' : ''}</div>` : ''}
      </div>
      <button class="lang-row-btn" data-act="pick-language">${icon('languages')}<span class="grow"><span class="small muted" style="display:block">You speak</span>
        <b>${lang ? `<span lang="${lang.lang}">${esc(lang.native)}</span>${lang.native !== lang.english ? ` · ${lang.english}` : ''}` : 'Choose a language'}</b></span>${icon('chevron-right', 'sm')}</button>
    </section>`;
  }

  /* ---------- Conversation (nearby or cloud-linked; local messages) ---------- */
  function renderConversation() {
    const p = peerInfo(ui.chatPeerId); const conv = localConv(ui.chatPeerId);
    const me = (S && S.identity) || {}; const myLang = S && S.language;
    const same = S && ((S.selectedPeer && S.selectedPeer.id === ui.chatPeerId) || !S.selectedPeer);
    const msgs = same ? [...((S && S.chatMessages) || [])].sort((a, b) => a.time - b.time) : [];
    const lastOwn = [...msgs].reverse().find((m) => m.isOutgoing);
    let html = ''; let prevDay = ''; let turn = null;
    const flush = () => { if (turn) html += turnHtml(turn, p, me, myLang, lastOwn); turn = null; };
    msgs.forEach((m) => {
      const day = new Date(m.time).toDateString();
      if (day !== prevDay) { flush(); html += `<div class="day">${dayLabel(m.time)}</div>`; prevDay = day; }
      const last = turn && turn.msgs[turn.msgs.length - 1];
      if (turn && turn.out === m.isOutgoing && m.time - last.time < 5 * 60000) turn.msgs.push(m); else { flush(); turn = { out: m.isOutgoing, msgs: [m] }; }
    });
    flush();
    if (!msgs.length) html = same ? `<div class="empty"><span class="ico">${icon('messages-square')}</span><h2>Say hello</h2><p>Write below, or hold <b>Speak</b> and talk — in any language.</p></div>`
      : `<div class="empty" aria-busy="true"><span class="ico">${icon('loader-circle', 'spin')}</span><p>Opening conversation…</p></div>`;
    const origin = (conv && conv.origin) || 'nearby';
    const c = cloud();
    const canLink = origin === 'nearby' && c.signedIn && c.syncEnabled && conv && ((S && S.cloudConversations) || []).length > 0;
    const sync = conv && conv.sync;
    const syncInfo = origin === 'cloud-linked' ? `<div class="convo-meta">${originChip('cloud-linked')}${sync && (sync.pending || sync.failed) ? syncLine(sync) : `<span class="sync ok">${icon('check', 'xs')} ${sync && sync.synced ? `${sync.synced} synced` : 'Linked'}${c.lastSyncedAt ? ` · ${ago(c.lastSyncedAt)}` : ''}</span>`}</div>` : '';
    return `<section class="screen convo" aria-label="Conversation with ${esc(p.name)}">
      <header class="convo-head">
        <button class="icon-btn" data-act="back" aria-label="Back to chats">${icon('chevron-left', 'lg')}</button>
        <div class="who">${avatar(p.name, p.id, 38, p.connected)}<div class="names"><span class="n">${esc(p.name)}</span>
          <span class="status ${p.connected ? 'ok' : 'off'}"><span class="dot"></span>${p.connected ? `Nearby · ${transportLabel(p.transport)}` : 'Not in range — messages wait and retry'}</span></div></div>
        <button class="ctx-btn" data-act="open-context" aria-label="VAANI context and AI">${icon('ai', 'sm')}<span>Context</span></button>
        ${p.connected ? `<button class="icon-btn" data-act="start-call" aria-label="Call ${esc(p.name)}">${icon('phone')}</button>` : ''}
      </header>
      ${syncInfo}
      ${canLink ? `<button class="link-strip" data-act="link-cloud">${icon('link', 'xs')} Link this conversation to a cloud conversation</button>` : ''}
      <div id="transcript" class="transcript" role="log" aria-live="polite">${html}</div>
      ${renderComposer(p)}
    </section>`;
  }
  function turnHtml(t, peer, me, myLang, lastOwn) {
    const name = t.out ? 'You' : peer.name; const id = t.out ? me.peerId : peer.id; const first = t.msgs[0];
    const lang = !t.out && first.language && String(first.language).toUpperCase() !== String(myLang || '').toUpperCase() ? glyph(first.language) : '';
    return `<section class="turn${t.out ? ' self' : ''}" style="--hue:${hue(id || name)}"><div aria-hidden="true">${t.out ? '' : avatar(peer.name, peer.id, 28)}</div>
      <div class="body"><header class="turn-head"><span class="speaker">${esc(name)}</span>${lang}<time>${clock(first.time)}</time></header>
        ${t.msgs.map((m) => messageHtml(m, m === lastOwn)).join('')}</div></section>`;
  }
  function messageHtml(m, isLastOwn) {
    const flags = [m.urgent ? `<span class="flag urgent">${icon('triangle-alert', 'xs')} Urgent</span>` : '', m.semanticLabel ? `<span class="flag meaning">${esc(m.semanticLabel)}</span>` : ''].join('');
    let body;
    if (isVoice(m)) {
      const ns = S && S.messageAudio ? S.messageAudio[m.id] : undefined;
      const playing = ns === 'PLAYING' || ns === 'SYNTHESIZING' || ns === 'QUEUED' || (ns === undefined && ui.playing.has(m.id));
      const st = ns === 'SYNTHESIZING' ? 'Preparing voice…' : ns === 'QUEUED' ? 'Queued' : ns === 'FAILED' ? 'Couldn’t play' : playing ? 'Playing' : `Play in ${langName(m.language)}`;
      body = `<div class="voice-strip${playing ? ' playing' : ''}"><button class="voice-disc" data-act="${playing ? 'stop-audio' : 'play-audio'}" data-id="${esc(m.id)}" aria-label="${playing ? 'Stop' : 'Play'} voice message">${icon(playing ? 'square' : 'play', 'sm')}</button>
          <span class="vs-label">${icon('voice', 'xs')} Voice message · ${esc(st)}</span></div>
        <span class="label voice">Transcript · ${esc(langName(m.language))}</span><p class="msg-text" dir="auto">${esc(m.text)}</p>`;
    } else body = `<p class="msg-text" dir="auto">${esc(m.text)}</p>`;
    const gloss = m.translatedText ? `<div class="gloss" lang="${esc((langOf(m.translatedLanguage) || {}).lang || '')}"><span class="label ai">${icon('ai', 'xs')} ${esc(langName(m.language))} → ${esc(langName(m.translatedLanguage))} · on this phone</span><p dir="auto">${esc(m.translatedText)}</p></div>` : '';
    const d = DELIVERY[m.status];
    const delivery = m.isOutgoing && d && (isLastOwn || d.tone !== 'ok') ? `<span class="delivery ${d.tone}">${icon(d.icon, 'xs')} ${d.text}</span>` : '';
    return `<div class="msg" id="m-${esc(m.id)}">${flags ? `<div class="msg-flags">${flags}</div>` : ''}${body}${gloss}${delivery}</div>`;
  }
  function renderComposer(peer) {
    const ai = S && S.ai; const draft = ui.composer.trim(); const state = (S && S.pttState) || 'idle';
    let proposal = '';
    if (ui.ai && ai) {
      const stopped = ai.error === 'AI request cancelled.';
      proposal = `<section class="proposal" aria-live="polite" aria-label="VAANI draft">
        <div class="proposal-head"><span class="label ai">${icon('ai', 'xs')} VAANI · ${esc(ui.ai.label)}</span><button class="icon-btn" style="width:36px;height:36px" data-act="ai-close" aria-label="Discard">${icon('x', 'sm')}</button></div>
        ${ai.loading ? `<p class="thinking">${icon('ai', 'sm orbit')} Reading this conversation…</p><div class="actions"><button class="btn sm secondary" data-act="ai-cancel">${icon('square', 'xs')} Stop</button></div>`
          : stopped ? `<p class="muted small">Stopped.</p><div class="actions"><button class="btn sm ghost" data-act="ai-retry">${icon('rotate-cw', 'xs')} Try again</button></div>`
          : ai.error ? `<div class="banner error">${icon('circle-alert', 'sm')}<span>${esc(ai.error)}</span></div><div class="actions"><button class="btn sm ghost" data-act="ai-retry">${icon('rotate-cw', 'xs')} Try again</button></div>`
          : ai.text ? `<p class="text" dir="auto">${esc(ai.text)}</p><div class="actions"><button class="btn sm ai" data-act="ai-use">${icon('pencil', 'xs')} Use &amp; edit</button><button class="btn sm ghost" data-act="ai-retry">${icon('rotate-cw', 'xs')} Again</button><button class="btn sm ghost" data-act="ai-copy">${icon('copy', 'xs')} Copy</button></div><span class="note">You send it — VAANI never does.</span>`
          : `<p class="thinking">${icon('ai', 'sm orbit')} Waiting for VAANI…</p>`}</section>`;
    }
    const drafted = ui.aiSource && ui.composer && overlap(ui.composer, ui.aiSource) >= 0.5 ? `<p class="drafted">${icon('ai', 'xs')} Drafted with VAANI — edit freely before sending.</p>` : '';
    return `<div class="composer">${proposal}${drafted}
      <div class="stage">
        <button class="vaani-key wide" data-act="open-context" aria-label="VAANI actions">${icon('ai', 'sm')}<span>VAANI</span></button>
        <label class="sr-only" for="composer">Message ${esc(peer.name)}</label>
        <textarea id="composer" rows="1" dir="auto" placeholder="Message ${esc(peer.name.split(' ')[0])}" data-input="composer" maxlength="4000">${esc(ui.composer)}</textarea>
        ${draft ? `<button class="send-key" data-act="send" aria-label="Send">${icon('arrow-up')}</button>`
          : `<button id="ptt-inline" class="hold-key${ui.pressing ? ' pressing' : ''}" data-state="${esc(state)}" ${peer.connected ? '' : 'disabled'} aria-label="${peer.connected ? 'Hold to speak' : 'Speaking needs them nearby'}">${icon('voice', 'sm')}<span>${ui.pressing || state === 'listening' ? 'Listening' : 'Speak'}</span></button>`}
      </div></div>`;
  }
  function overlap(text, ai) {
    const words = (s) => new Set(String(s).toLowerCase().match(/[\p{L}\p{M}\p{N}]+/gu) || []);
    const a = words(ai); if (!a.size) return 0; const t = words(text); let hit = 0; a.forEach((w) => { if (t.has(w)) hit++; }); return hit / a.size;
  }

  /* ---------- Cloud conversation (read-only view of what's stored on this phone) ---------- */
  function renderCloudConversation() {
    const conv = ((S && S.cloudConversations) || []).find((c) => String(c.cloudId) === String(ui.cloudChatId));
    const chat = S && S.cloudChat && String(S.cloudChat.cloudId) === String(ui.cloudChatId) ? S.cloudChat : null;
    const title = conv ? cloudTitle(conv) : 'Cloud conversation';
    const c = cloud();
    let html = '';
    if (!chat) html = `<div class="empty" aria-busy="true"><span class="ico">${icon('loader-circle', 'spin')}</span><p>Opening…</p></div>`;
    else if (!chat.messages.length) html = `<div class="empty"><span class="ico">${icon('cloud')}</span><h2>No messages on this phone</h2><p>Messages appear here after a sync.</p></div>`;
    else {
      let prevDay = '';
      html = chat.messages.map((m) => {
        const day = new Date(m.createdAt).toDateString(); let dh = '';
        if (day !== prevDay) { dh = `<div class="day">${dayLabel(m.createdAt)}</div>`; prevDay = day; }
        const body = m.deleted ? '<p class="msg-text muted"><i>Message deleted</i></p>' : m.kind === 'voice' ? `<span class="label voice">${icon('voice', 'xs')} Voice message${m.body ? ' · transcript' : ''}</span>${m.body ? `<p class="msg-text" dir="auto">${esc(m.body)}</p>` : ''}` : `<p class="msg-text" dir="auto">${esc(m.body)}</p>`;
        return `${dh}<section class="turn${m.isMine ? ' self' : ''}" style="--hue:${hue(m.senderName)}"><div aria-hidden="true">${m.isMine ? '' : avatar(m.senderName, m.senderName, 28)}</div>
          <div class="body"><header class="turn-head"><span class="speaker">${m.isMine ? 'You' : esc(m.senderName)}</span>${m.sourceLanguage ? glyph(m.sourceLanguage) : ''}<time>${clock(m.createdAt)}</time></header><div class="msg">${body}</div></div></section>`;
      }).join('');
    }
    return `<section class="screen convo" aria-label="Cloud conversation ${esc(title)}">
      <header class="convo-head"><button class="icon-btn" data-act="back" aria-label="Back to chats">${icon('chevron-left', 'lg')}</button>
        <div class="who">${avatar(title, 'c' + ui.cloudChatId, 38, false, conv && conv.kind === 'group')}<div class="names"><span class="n">${esc(title)}</span>
          <span class="status off">${icon('cloud', 'xs')} Cloud${conv && conv.kind === 'group' ? ` · ${conv.members.length + 1} people` : ''}${c.lastSyncedAt ? ` · synced ${ago(c.lastSyncedAt)}` : ''}</span></div></div></header>
      <div id="transcript" class="transcript" role="log">${html}</div>
      <div class="readonly-note">${icon('info', 'sm')}<div><b>Read-only on Android</b><span>This is a copy of your cloud conversation stored on this phone. Sending into cloud-only conversations isn’t available on Android yet — link one of your Nearby conversations to it to sync messages.</span></div></div>
    </section>`;
  }

  /* ---------- Nearby ---------- */
  function renderNearby() {
    const active = peers();
    const offlineTrusted = trusted().filter((t) => !active.some((a) => a.id === t.id));
    const discovered = (S && S.discoveredPeers) || [];
    const wifi = ((S && S.wifiDirectPeers) || []).filter((p) => p.available);
    const req = S && S.incomingPairRequest;
    const outgoing = (S && S.outgoingPairRequests) || [];
    const nb = (S && S.nearby) || { connected: active.length, discovered: discovered.length, trusted: trusted().length, pairingIncoming: req ? 1 : 0, pairingOutgoing: outgoing.length };
    const row = (inner) => `<li class="item">${inner}</li>`;
    const stat = (n, label, tone) => `<div class="stat ${n > 0 ? tone : ''}"><b>${n}</b><span>${label}</span></div>`;
    return `<section class="screen" aria-label="Nearby">
      <header class="appbar"><h1>Nearby</h1><button class="btn sm secondary" data-act="scan">${icon('refresh-cw', 'xs')} Scan</button></header>${bridgeMissing()}
      <div class="scroll">
      <div class="stats" role="status" aria-label="Nearby status">${stat(nb.connected, 'Connected', 'ok')}${stat(nb.discovered, 'Nearby', 'brand')}${stat(nb.trusted, 'Trusted', 'brand')}${stat((nb.pairingIncoming || 0) + (nb.pairingOutgoing || 0), 'Pairing', 'warn')}</div>
      <p class="pad small muted" style="padding-bottom:4px">${icon('shield-check', 'xs')} Phone to phone over Wi-Fi Direct or Bluetooth. No internet or account needed${netState() && netState() !== 'online' ? ' — works right now even though you’re offline' : ''}.</p>
      ${req ? `<div class="pad" style="padding-top:8px"><div class="banner info" style="align-items:center">${icon('user-plus', 'sm')}<span style="flex:1"><b>${esc(req.displayName)}</b> wants to pair</span><button class="btn sm primary" data-act="pair-review">Review</button></div></div>` : ''}
      <h2 class="section label">Connected now · ${active.length}</h2>
      <ul class="group">${active.length ? active.map((p) => row(`${avatar(p.name, p.id, 40, true)}<span class="grow"><span class="t">${esc(p.name)}</span><span class="status ok"><span class="dot"></span>${transportLabel(p.transport)}</span></span>
        <button class="icon-btn" data-act="open-chat" data-peer="${esc(p.id)}" aria-label="Message ${esc(p.name)}">${icon('messages-square')}</button>
        <button class="icon-btn" data-act="speak-to" data-peer="${esc(p.id)}" aria-label="Speak to ${esc(p.name)}" style="color:var(--color-voice-text)">${icon('voice')}</button>`)).join('')
        : `<li class="item"><span class="s">No one connected. People appear here when they’re in Wi-Fi or Bluetooth range.</span></li>`}</ul>
      ${offlineTrusted.length ? `<h2 class="section label">Trusted, out of range · ${offlineTrusted.length}</h2><ul class="group">${offlineTrusted.map((p) => row(`${avatar(p.name, p.id, 40)}<span class="grow"><span class="t">${esc(p.name || p.id)}</span><span class="status off"><span class="dot"></span>Not in range</span></span>
        <button class="icon-btn" data-act="open-chat" data-peer="${esc(p.id)}" aria-label="Open conversation with ${esc(p.name)}">${icon('messages-square')}</button>`)).join('')}</ul>` : ''}
      <h2 class="section label">Nearby, not paired · ${discovered.length}</h2>
      <ul class="group">${discovered.length ? discovered.map((p) => {
        const sent = outgoing.some((o) => o.peerId === p.id);
        return row(`${avatar(p.name, p.id, 40)}<span class="grow"><span class="t">${esc(p.name)}</span><span class="s">Found via ${esc(p.transport)}</span></span>
          <button class="btn sm ghost" data-act="connect" data-peer="${esc(p.id)}">Connect</button>${sent ? `<span class="status warn">${icon('clock', 'xs')} Request sent</span>` : `<button class="btn sm secondary" data-act="pair" data-peer="${esc(p.id)}">${icon('user-plus', 'xs')} Pair</button>`}`);
      }).join('') : `<li class="item"><span class="s">${icon('loader-circle', 'xs spin')} Looking for people nearby…</span></li>`}</ul>
      <div class="section" style="display:flex;align-items:center;justify-content:space-between"><h2 class="label">Wi-Fi Direct · ${wifi.length}</h2><button class="link" data-act="scan-wifi">${icon('refresh-cw', 'xs')} Search</button></div>
      <ul class="group" style="margin-bottom:24px">${wifi.length ? wifi.map((p) => row(`${avatar(p.name || '?', p.address, 40)}<span class="grow"><span class="t">${esc(p.name || 'Wi-Fi Direct device')}</span><span class="s">${esc(p.address)}</span></span>
        <button class="btn sm secondary" data-act="connect-wifi" data-address="${esc(p.address)}">${icon('wifi', 'xs')} Connect</button>`)).join('')
        : `<li class="item"><span class="s">No Wi-Fi Direct devices found yet.</span></li>`}</ul>
      </div></section>`;
  }

  /* ---------- Settings ---------- */
  function renderSettings() {
    const id = (S && S.identity) || {}; const c = cloud(); const lang = langOf(S && S.language);
    const speed = Number((S && S.ttsSpeed) || 1); const sync = SYNC[c.syncState] || [c.syncState || 'Unknown', 'off'];
    const editing = ui.nameDraft != null; const speech = S && S.speech; const st = netState();
    const configured = c.configured !== false;
    let cloudBlock;
    if (c.signedIn) cloudBlock = `<div class="group" style="margin:0"><div class="item">${icon('cloud')}<span class="grow"><span class="t">${esc(c.displayName || 'Signed in')}</span><span class="s">${esc(c.email || '')}</span></span><button class="btn sm ghost" data-act="logout">${icon('log-out', 'xs')} Sign out</button></div>
        <div class="item"><label class="grow" for="sync-toggle"><span class="t">Sync linked conversations</span><span class="s">${c.syncEnabled ? 'On — linked conversations upload when there’s internet' : 'Off — nothing leaves this phone'}</span></label><input id="sync-toggle" type="checkbox" role="switch" class="switch" data-act="toggle-sync" ${c.syncEnabled ? 'checked' : ''}></div>
        ${c.syncEnabled ? `<div class="item"><span class="grow"><span class="t">Sync status</span><span class="status ${sync[1]}"><span class="dot"></span>${esc(sync[0])}${c.lastSyncedAt ? ` · last ${ago(c.lastSyncedAt)}` : ''}</span>${st && st !== 'online' ? `<span class="s">Sync runs when Android confirms internet access.</span>` : ''}</span><button class="btn sm secondary" data-act="sync-now" ${st && st !== 'online' ? 'disabled' : ''}>${icon('refresh-cw', 'xs')} Sync now</button></div>` : ''}</div>`;
    else if (!configured) cloudBlock = `<div class="banner info">${icon('info', 'sm')}<span>Cloud sign-in isn’t configured in this build of VAANI. Everything nearby works without it.</span></div>`;
    else cloudBlock = `<form id="cloud-form" class="group" style="margin:0;border:0;display:flex;flex-direction:column;gap:12px" novalidate>
        <p class="small muted">Optional. Nearby messaging works without an account. Sign in to see your cloud conversations and sync linked ones.</p>
        ${st && st !== 'online' ? `<div class="banner warning">${icon('cloud-off', 'sm')}<span>No internet right now — sign-in needs it.</span></div>` : ''}
        <div><label class="field-label" for="cloud-email">Email</label><input id="cloud-email" class="field" type="email" autocomplete="email" inputmode="email"></div>
        <div><label class="field-label" for="cloud-password">Password</label><input id="cloud-password" class="field" type="password" autocomplete="current-password"></div>
        ${ui.authError ? `<div class="banner error" role="alert">${icon('circle-alert', 'sm')}<span>${esc(ui.authError)}</span></div>` : ''}
        <button class="btn secondary block" type="submit" ${ui.authBusy ? 'disabled' : ''}>${ui.authBusy ? `${icon('loader-circle', 'sm spin')} Signing in…` : 'Sign in'}</button></form>`;
    return `<section class="screen" aria-label="Settings">
      <header class="appbar"><h1>Settings</h1></header>${bridgeMissing()}
      <div class="scroll" style="padding-bottom:24px">
      <h2 class="section label">This phone</h2>
      <div class="group">
        <div class="item">${avatar(id.name || '?', id.peerId, 48)}<span class="grow">${editing ? `<label class="sr-only" for="name-input">Display name</label><input id="name-input" class="field" maxlength="40" value="${esc(ui.nameDraft)}" data-input="name">`
          : `<span class="t">${esc(id.name || 'Unnamed device')}</span><span class="s">How nearby people see you · no account needed</span>`}</span>
          ${editing ? `<button class="btn sm primary" data-act="save-name" ${ui.nameDraft.trim() ? '' : 'disabled'}>Save</button>` : `<button class="icon-btn" data-act="edit-name" aria-label="Edit display name">${icon('pencil', 'sm')}</button>`}</div>
        <button class="item" data-act="pick-language">${icon('languages')}<span class="grow"><span class="t">Language</span><span class="s">You speak and read</span></span><span class="v">${lang ? `<span lang="${lang.lang}">${esc(lang.native)}</span>` : 'Not set'}</span>${icon('chevron-right', 'sm')}</button>
      </div>
      <h2 class="section label">Connectivity</h2>
      <div class="group">
        <div class="item">${icon(st === 'online' ? 'cloud' : 'cloud-off')}<span class="grow"><span class="t">Internet</span><span class="s">${st ? esc((NET[st] || NET.none).label) + (st !== 'online' && (NET[st] || NET.none).hint ? ` — ${esc((NET[st] || NET.none).hint)}` : '') : 'Not reported by this build'}</span></span></div>
        <button class="item" data-act="tab" data-tab="nearby">${icon('radar')}<span class="grow"><span class="t">Nearby</span><span class="s">${S && S.nearby ? `${S.nearby.connected} connected · ${S.nearby.discovered} nearby · ${S.nearby.trusted} trusted` : 'Wi-Fi Direct and Bluetooth'}</span></span>${icon('chevron-right', 'sm')}</button>
      </div>
      <h2 class="section label voice">Speech · on this phone</h2>
      <div class="group">
        <div class="item" style="flex-wrap:wrap"><span class="grow"><span class="t">Voice playback speed</span><span class="s">How fast VAANI reads messages aloud</span></span>
          <div class="segmented" role="radiogroup" aria-label="Voice playback speed" style="width:100%">${TTS_SPEEDS.map((v) => `<button role="radio" aria-checked="${Math.abs(v - speed) < 0.01}" data-act="tts-speed" data-v="${v}">${v}×</button>`).join('')}</div></div>
        ${speech ? `<div class="item">${icon('mic')}<span class="grow"><span class="t">Speech recognition</span><span class="s">Works offline · ${esc(sttText(speech.sttState))}</span></span></div>
          <div class="item">${icon('volume-2')}<span class="grow"><span class="t">Voices installed</span><span class="s">${(speech.ttsInstalled || []).length} of 10 languages · others download on first use</span></span></div>` : ''}
      </div>
      <h2 class="section label">Cloud account · optional</h2>
      <div class="pad">${cloudBlock}</div>
      <h2 class="section label">Notifications</h2>
      <div class="group"><div class="item">${icon('bell')}<span class="grow"><span class="t">Message alerts</span><span class="s">VAANI notifies you of new and urgent messages. Change this in Android Settings › Apps › VAANI › Notifications.</span></span></div></div>
      <h2 class="section label">About</h2>
      <div class="group">
        <div class="item">${logo(28)}<span class="grow"><span class="t">VAANI for Android</span><span class="s">Online, offline and nearby — every language, one conversation.</span></span></div>
        <div class="item">${icon('ai')}<span class="grow"><span class="t">AI assistance</span><span class="s">${S && S.aiConfigured === false ? 'Not configured in this build' : 'Needs internet and sign-in · drafts are never sent for you'}</span></span></div>
      </div></div></section>`;
  }
  function sttText(st) {
    return { READY: 'Ready', LISTENING: 'Listening', PROCESSING: 'Working', RESULT: 'Ready', MODEL_MISSING: 'Model not installed', IMPORTING: 'Installing model…', VALIDATING: 'Checking model…', INITIALIZING: 'Starting…', UNINITIALIZED: 'Not started', ERROR: 'Error' }[st] || st || 'Unknown';
  }

  /* ---------- Onboarding ---------- */
  const OB_STEPS = 4;
  function renderOnboarding() {
    const step = ui.onboarding;
    const dots = `<div class="dots" aria-label="Step ${step + 1} of ${OB_STEPS}">${Array.from({ length: OB_STEPS }, (_, i) => `<span class="${i === step ? 'on' : ''}"></span>`).join('')}</div>`;
    const top = `<div class="top"><span class="brand" style="display:flex;align-items:center;gap:8px">${logo(26)}<span class="wordmark">vaani</span></span>${step < OB_STEPS - 1 ? '<button class="link" data-act="ob-finish">Skip</button>' : ''}</div>`;
    let main = ''; let next = `<button class="btn primary block" data-act="ob-next">Continue</button>`;
    const nameVal = ui.nameDraft != null ? ui.nameDraft : (S && S.identity && S.identity.name) || '';
    if (step === 0) main = `<h1>Every language.<br>One conversation.</h1><p class="lede">Talk to people nearby without internet, and online when you have it — each in your own language.</p>
      <ul class="points"><li><span class="ico brand">${icon('radar')}</span><span><b>Nearby, offline.</b><br><span class="muted small">Phone to phone over Wi-Fi Direct or Bluetooth. No account, no data.</span></span></li>
        <li><span class="ico voice">${icon('voice')}</span><span><b>Hold and speak.</b><br><span class="muted small">Transcribed on this phone and spoken in their language.</span></span></li>
        <li><span class="ico ai">${icon('cloud')}</span><span><b>Online when you want it.</b><br><span class="muted small">Optional account to see cloud conversations and sync the ones you link.</span></span></li></ul>`;
    else if (step === 1) main = `<h1>Which language do you speak?</h1><p class="lede">You can change this any time in Settings.</p><div role="radiogroup" aria-label="Your language">${langOptions()}</div>`;
    else if (step === 2) { main = `<h1>What should people call you?</h1><p class="lede">Nearby people see this name when you connect. It stays on this phone.</p>
      <div><label class="field-label" for="name-input">Your name</label><input id="name-input" class="field" maxlength="40" autocomplete="name" value="${esc(nameVal)}" data-input="name"></div>`;
      next = `<button class="btn primary block" data-act="ob-name" ${nameVal.trim() ? '' : 'disabled'}>Continue</button>`; }
    else { main = `<h1>Permissions VAANI uses</h1><p class="lede">Android asks for these. You can change them later in Android Settings.</p>
      <ul class="points"><li><span class="ico voice">${icon('mic')}</span><span><b>Microphone</b><br><span class="muted small">To hear you when you hold Speak. Audio is processed on this phone.</span></span></li>
        <li><span class="ico brand">${icon('bluetooth')}</span><span><b>Nearby devices</b><br><span class="muted small">To find and connect to people over Wi-Fi Direct and Bluetooth.</span></span></li>
        <li><span class="ico brand">${icon('bell')}</span><span><b>Notifications</b><br><span class="muted small">To tell you about new and urgent messages.</span></span></li></ul>`;
      next = `<button class="btn primary block" data-act="ob-finish">Start using VAANI</button>`; }
    return `<section class="screen" aria-label="Welcome to VAANI"><div class="ob">${top}${main}<div class="foot">${dots}${next}</div></div></section>`;
  }
  function langOptions() {
    const current = String((S && S.language) || '').toUpperCase(); const speech = S && S.speech;
    return LANGS.map((l) => {
      let avail = '';
      if (speech) {
        const tts = (speech.ttsInstalled || []).map((c) => String(c).toUpperCase()).includes(l.code);
        const sttOk = speech.sttState && speech.sttState !== 'MODEL_MISSING' && speech.sttState !== 'ERROR';
        avail = `<span class="status ${sttOk && tts ? 'ok' : 'warn'}">${sttOk ? 'Listens' : 'Speech model not installed'} · ${tts ? 'Speaks' : 'Voice downloads on first use'}</span>`;
      }
      return `<button class="lang-opt" role="radio" aria-checked="${current === l.code}" data-act="set-language" data-code="${l.code}"><span class="grow"><span class="native" lang="${l.lang}">${esc(l.native)}</span><span class="small muted">${l.english}</span>${avail}</span>${current === l.code ? `<span class="check">${icon('check')}</span>` : ''}</button>`;
    }).join('');
  }

  /* ---------- overlays ---------- */
  function sheet(title, body, label) {
    return `<div class="scrim" data-act="close"></div><div class="sheet" role="dialog" aria-modal="true" aria-label="${esc(label || title)}"><div class="grab"></div>
      <div class="sheet-head"><h2>${title}</h2><button class="icon-btn" data-act="close" aria-label="Close">${icon('x')}</button></div><div class="sheet-body">${body}</div></div>`;
  }
  function renderOverlay() {
    const o = ui.overlay; const req = S && S.incomingPairRequest;
    if (req && o && o.kind === 'pair') {
      return `<div class="scrim" data-act="close"></div><div class="dialog" role="dialog" aria-modal="true" aria-labelledby="pair-h">
        <div style="display:flex;gap:12px;align-items:center">${avatar(req.displayName, req.senderPeerId, 48)}<div><h2 id="pair-h">${esc(req.displayName)} wants to pair</h2><p class="small muted">Accept to trust this phone for nearby messages and calls.</p></div></div>
        <div class="actions"><button class="btn secondary" data-act="pair-decline">Decline</button><button class="btn primary" data-act="pair-accept">Accept</button></div></div>`;
    }
    if (!o) return '';
    if (o.kind === 'language') return sheet('Your language', `<div role="radiogroup">${langOptions()}</div>${!(S && S.speech) ? '<p class="small muted" style="padding-top:12px">This build doesn’t report which speech models are installed.</p>' : ''}`);
    if (o.kind === 'translate') return sheet('Translate draft into', LANGS.map((l) => `<button class="lang-opt" data-act="ai-translate-to" data-code="${l.code}"><span class="grow"><span class="native" lang="${l.lang}">${esc(l.native)}</span><span class="small muted">${l.english}</span></span></button>`).join(''));
    if (o.kind === 'peers') {
      const list = peers().concat(trusted().filter((t) => !isActive(t.id)).map((t) => ({ ...t, transport: 'offline' })));
      return sheet('Talk to', list.length ? list.map((p) => `<button class="lang-opt" data-act="select-peer" data-peer="${esc(p.id)}">${avatar(p.name, p.id, 40, isActive(p.id))}<span class="grow"><b>${esc(p.name || p.id)}</b><span class="status ${isActive(p.id) ? 'ok' : 'off'}"><span class="dot"></span>${isActive(p.id) ? transportLabel(p.transport) : 'Not in range'}</span></span></button>`).join('')
        : `<div class="empty" style="padding:24px 8px"><p>No one nearby yet.</p><button class="btn primary" data-act="tab" data-tab="nearby">${icon('radar', 'sm')} Find people</button></div>`, 'Choose who to talk to');
    }
    if (o.kind === 'context') {
      const off = S && S.aiConfigured === false; const st = netState(); const noNet = st && st !== 'online'; const draft = ui.composer.trim();
      const why = off ? 'AI assistance isn’t configured in this build of VAANI.' : noNet ? 'AI needs internet. Nearby messaging, speech and on-device translation still work.' : '';
      const act = (op, label, desc, needsDraft) => `<button class="ctx-action" data-act="${op === 'TRANSLATE' ? 'ai-translate' : 'ai'}" data-op="${op}" ${off || noNet || (needsDraft && !draft) ? 'disabled' : ''}>${icon('ai', 'sm')}<span class="grow"><b>${label}</b><span>${needsDraft && !draft ? 'Type a draft first' : desc}</span></span></button>`;
      return sheet(`${icon('ai', 'sm')} VAANI`, `${why ? `<div class="banner info" style="margin-bottom:12px">${icon('info', 'sm')}<span>${why}</span></div>` : ''}
        ${act('REPLY', 'Draft a reply', 'Suggests what you could say next', false)}${act('REWRITE', 'Rewrite my draft', 'Clearer and politer, same meaning', true)}
        ${act('TRANSLATE', 'Translate my draft', 'Into a language you choose', true)}${act('SUMMARIZE', 'Summarize', 'Decisions, questions, next steps', false)}
        <p class="small muted" style="padding-top:12px">${icon('shield-check', 'xs')} VAANI drafts; you decide what’s sent.</p>`, 'VAANI context');
    }
    if (o.kind === 'link') {
      const list = (S && S.cloudConversations) || [];
      return sheet('Link to a cloud conversation', `<p class="small muted" style="padding-bottom:8px">New messages in this nearby conversation will also upload to the cloud conversation you pick, when there’s internet. Existing nearby messages stay on this phone.</p>
        ${list.map((c) => `<button class="lang-opt" data-act="link-to" data-cloud="${esc(c.cloudId)}">${avatar(cloudTitle(c), 'c' + c.cloudId, 40, false, c.kind === 'group')}<span class="grow"><b>${esc(cloudTitle(c))}</b><span class="small muted">${c.kind === 'group' ? 'Group' : 'Direct'} · cloud</span></span></button>`).join('')}`);
    }
    return '';
  }

  function renderCall() {
    const c = S && S.call;
    if (ui.callNotice && Date.now() - ui.callNotice.at < 4000 && !(c && CALL_ACTIVE.includes(c.state))) {
      setTimeout(() => { ui.callNotice = null; const el = document.getElementById('call'); if (el) el.innerHTML = renderCall(); }, 4100);
      return `<div class="toast" role="status" style="bottom:auto;top:calc(var(--safe-top) + 16px)">${icon('phone-off', 'xs')} ${esc(ui.callNotice.text)}</div>`;
    }
    if (!c || !c.state) return '';
    const name = c.peerName || 'Nearby peer';
    if (c.state === 'RINGING' && c.direction === 'INCOMING') {
      return `<div class="call" role="dialog" aria-modal="true" aria-label="Incoming call"><span class="state">${icon('phone-incoming', 'xs')} Incoming call</span>${avatar(name, c.peerId, 96)}<h2>${esc(name)}</h2><p class="muted small">Nearby voice call · no internet needed</p><div class="spacer"></div>
        <div class="call-controls"><button class="call-btn end" data-act="call-reject"><span class="disc">${icon('phone-off', 'lg')}</span>Decline</button><button class="call-btn accept" data-act="call-accept"><span class="disc">${icon('phone', 'lg')}</span>Accept</button></div></div>`;
    }
    if (![...CALL_ACTIVE, 'RINGING'].includes(c.state)) return '';
    const label = { CALLING: 'Calling…', RINGING: 'Ringing…', CONNECTING: 'Connecting…', CONNECTED: 'Connected', RECONNECTING: 'Reconnecting…' }[c.state];
    const pending = c.direction === 'OUTGOING' && ['CALLING', 'RINGING', 'CONNECTING'].includes(c.state);
    return `<div class="call" role="dialog" aria-modal="true" aria-label="Call with ${esc(name)}"><span class="state${c.state === 'RECONNECTING' ? ' err' : ''}">${esc(label)}</span>${avatar(name, c.peerId, 96)}<h2>${esc(name)}</h2><p class="muted small">Nearby voice call</p><div class="spacer"></div>
      <div class="call-controls">
        <button class="call-btn" data-act="call-mute" aria-pressed="${ui.muted}"><span class="disc">${icon(ui.muted ? 'mic-off' : 'mic', 'lg')}</span>${ui.muted ? 'Muted' : 'Mute'}</button>
        <button class="call-btn" data-act="call-speaker" aria-pressed="${ui.speaker}"><span class="disc">${icon('volume-2', 'lg')}</span>Speaker</button>
        <button class="call-btn end" data-act="${pending ? 'call-cancel' : 'call-end'}"><span class="disc">${icon('phone-off', 'lg')}</span>${pending ? 'Cancel' : 'End'}</button></div></div>`;
  }

  function logo(size) {
    return `<svg width="${size}" height="${size}" viewBox="0 0 40 40" aria-hidden="true"><path d="M20 3c9.4 0 17 6.9 17 15.4 0 8.5-7.6 15.4-17 15.4-1.7 0-3.3-.2-4.8-.6L7.5 37l1.3-7.1C5.2 27.1 3 22.9 3 18.4 3 9.9 10.6 3 20 3Z" fill="var(--color-brand-primary)"/><g stroke="var(--color-on-brand)" stroke-width="2.6" stroke-linecap="round"><path d="M11.5 14.5v3"/><path d="M15.75 15v6.5"/><path d="M20 16.5v8"/><path d="M24.25 15v6.5"/><path d="M28.5 14.5v3"/></g></svg>`;
  }

  /* ================= actions ================= */
  function requestAi(op, extra = {}) {
    const draft = ui.composer.trim();
    const spec = {
      REPLY: { prompt: 'Draft a concise, helpful reply I could send next. Match the language of the conversation.', label: 'reply' },
      REWRITE: { prompt: `Rewrite this draft to be clear and polite, keeping its language and meaning:\n${draft}`, label: 'rewrite' },
      TRANSLATE: { prompt: draft, label: `in ${langName(extra.target)}` },
      SUMMARIZE: { prompt: 'Summarize this conversation: decisions, open questions and next steps.', label: 'summary' },
    }[op];
    ui.ai = { op, prompt: spec.prompt, target: extra.target || null, label: spec.label };
    ui.overlay = null;
    call('onRequestAi', op, spec.prompt, extra.target || null);
    render();
  }

  function onClick(e) {
    const el = e.target.closest('[data-act]'); if (!el) return;
    const a = el.dataset.act; const peer = el.dataset.peer;
    switch (a) {
      case 'tab': setTab(el.dataset.tab); break;
      case 'filter': ui.filter = el.dataset.f; render(); break;
      case 'back': if (ui.cloudChatId != null) closeCloudChat(); ui.chatPeerId = null; ui.ai = null; render(); break;
      case 'close': ui.overlay = null; render(); break;
      case 'open-chat': openChat(peer); break;
      case 'open-cloud': openCloudChat(el.dataset.cloud); break;
      case 'open-speak': ui.speak = true; render(); break;
      case 'close-speak': ui.speak = false; render(); break;
      case 'dismiss-reminder': ui.reminder = null; render(); break;
      case 'pick-peer': ui.overlay = { kind: 'peers' }; render(); break;
      case 'pick-language': ui.overlay = { kind: 'language' }; render(); break;
      case 'select-peer': call('onSelectPeer', peer); ui.overlay = null; render(); break;
      case 'speak-to': call('onSelectPeer', peer); ui.speak = true; render(); break;
      case 'set-language': call('onSelectLanguage', el.dataset.code); if (S) S.language = el.dataset.code; ui.overlay = null; render(); break;
      case 'start-call': { const id = ui.chatPeerId || (S && S.selectedPeer && S.selectedPeer.id); if (id) { ui.muted = false; ui.speaker = false; call('onStartCall', id); } break; }
      case 'call-accept': call('onAcceptCall', S.call.callId); break;
      case 'call-reject': call('onRejectCall', S.call.callId); break;
      case 'call-cancel': call('onCancelCall', S.call.callId); break;
      case 'call-end': call('onEndCall', S.call.callId); break;
      case 'call-mute': ui.muted = !ui.muted; call('onSetCallMuted', ui.muted); render(); break;
      case 'call-speaker': ui.speaker = !ui.speaker; call('onSetCallSpeakerphone', ui.speaker); render(); break;
      case 'scan': call('startDiscovery'); toast('Looking for people nearby…'); break;
      case 'scan-wifi': call('onDiscoverWifiDirect'); toast('Searching for Wi-Fi Direct devices…'); break;
      case 'connect': call('connectPeer', peer); toast('Connecting…'); break;
      case 'connect-wifi': call('onConnectWifiDirect', el.dataset.address); toast('Connecting over Wi-Fi Direct…'); break;
      case 'pair': call('sendPairRequest', peer); toast('Pair request sent'); break;
      case 'pair-review': ui.overlay = { kind: 'pair' }; render(); break;
      case 'pair-accept': call('acceptPair', S.incomingPairRequest.requestId); ui.overlay = null; render(); break;
      case 'pair-decline': call('declinePair', S.incomingPairRequest.requestId); ui.overlay = null; render(); break;
      case 'send': sendComposer(); break;
      case 'play-audio': { const m = findMsg(el.dataset.id); if (m) { for (const id of ui.playing) call('stopMessageAudio', id); ui.playing.clear(); ui.playing.add(m.id); call('playMessageAudio', m.id, m.text, String(m.language || 'EN')); render(); } break; }
      case 'stop-audio': call('stopMessageAudio', el.dataset.id); ui.playing.delete(el.dataset.id); render(); break;
      case 'open-context': ui.overlay = { kind: 'context' }; render(); break;
      case 'ai': requestAi(el.dataset.op); break;
      case 'ai-translate': ui.overlay = { kind: 'translate' }; render(); break;
      case 'ai-translate-to': requestAi('TRANSLATE', { target: el.dataset.code }); break;
      case 'ai-cancel': call('onCancelAiRequest'); break;
      case 'ai-retry': if (ui.ai) { call('onRequestAi', ui.ai.op, ui.ai.prompt, ui.ai.target); render(); } break;
      case 'ai-close': if (S && S.ai && S.ai.loading) call('onCancelAiRequest'); ui.ai = null; render(); break;
      case 'ai-use': ui.composer = S.ai.text; ui.aiSource = S.ai.text; ui.ai = null; render(); document.getElementById('composer')?.focus(); break;
      case 'ai-copy': navigator.clipboard?.writeText(S.ai.text).then(() => toast('Copied'), () => toast('Couldn’t copy')); break;
      case 'link-cloud': ui.overlay = { kind: 'link' }; render(); break;
      case 'link-to': {
        // linkCloudConversation(localConversationId, cloudConversationId): local id is conversations[].id.
        const conv = localConv(ui.chatPeerId); const cid = Number(el.dataset.cloud);
        if (conv && Number.isFinite(cid)) { call('linkCloudConversation', conv.id, cid); toast('Link requested'); }
        ui.overlay = null; render(); break;
      }
      case 'edit-name': ui.nameDraft = (S && S.identity && S.identity.name) || ''; render(); document.getElementById('name-input')?.focus(); break;
      case 'save-name': if (ui.nameDraft.trim()) { call('onUpdateDisplayName', ui.nameDraft.trim()); ui.nameDraft = null; toast('Name saved'); render(); } break;
      case 'tts-speed': call('onSetTtsSpeed', Number(el.dataset.v)); if (S) S.ttsSpeed = Number(el.dataset.v); render(); break;
      case 'toggle-sync': call('setCloudSyncEnabled', el.checked); break;
      case 'sync-now': call('syncCloudNow'); toast('Syncing…'); break;
      case 'logout': call('cloudLogout'); break;
      case 'ob-next': ui.onboarding = Math.min(OB_STEPS - 1, ui.onboarding + 1); render(); break;
      case 'ob-name': { const v = (ui.nameDraft != null ? ui.nameDraft : (S && S.identity && S.identity.name) || '').trim(); if (v) call('onUpdateDisplayName', v); ui.nameDraft = null; ui.onboarding++; render(); break; }
      case 'ob-finish': try { localStorage.setItem('it_onboarded_v2', 'true'); } catch { /* storage unavailable */ } ui.onboarding = null; ui.tab = 'chats'; render(); break;
    }
  }
  function findMsg(id) { return ((S && S.chatMessages) || []).find((m) => m.id === id); }
  function sendComposer() {
    const text = ui.composer.trim(); if (!text || !ui.chatPeerId) return;
    call('onSendMessage', ui.chatPeerId, text);
    ui.composer = ''; ui.aiSource = null; render(); scrollTranscript(true);
  }
  function onInput(e) {
    const k = e.target.dataset.input; if (!k) return;
    if (k === 'composer') { ui.composer = e.target.value; const has = !!ui.composer.trim(); const isSend = !!document.querySelector('.send-key'); if (has !== isSend) render(); else fitComposer(); }
    else if (k === 'search') { ui.search = e.target.value; const pos = e.target.selectionStart; render(); const el = document.getElementById('chat-search'); if (el) { el.focus(); el.setSelectionRange(pos, pos); } }
    else if (k === 'name') { ui.nameDraft = e.target.value; const btn = document.querySelector('[data-act="save-name"],[data-act="ob-name"]'); if (btn) btn.disabled = !ui.nameDraft.trim(); }
  }
  function onSubmit(e) {
    if (e.target.id !== 'cloud-form') return;
    e.preventDefault();
    const email = document.getElementById('cloud-email').value.trim(); const pw = document.getElementById('cloud-password').value;
    if (!email || !pw) { ui.authError = 'Enter your email and password.'; render(); return; }
    ui.authError = ''; ui.authBusy = true; render(); call('cloudLogin', email, pw);
  }
  function onKey(e) { if (e.target.id === 'composer' && e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); sendComposer(); } }

  /* ---------- push-to-talk: forward press/release only; native owns every state ---------- */
  let pttActive = false;
  function pttDown(e) {
    const btn = e.target.closest('#ptt, #ptt-inline'); if (!btn || btn.disabled) return;
    e.preventDefault(); pttActive = true; ui.pressing = true; btn.classList.add('pressing');
    if (ui.chatPeerId) call('onSelectPeer', ui.chatPeerId);
    call('onPttDown');
    if (navigator.vibrate) try { navigator.vibrate(12); } catch { /* not allowed */ }
  }
  function pttUp() { if (!pttActive) return; pttActive = false; ui.pressing = false; call('onPttUp'); render(); }

  /* ---------- layout ---------- */
  function fitComposer() { const t = document.getElementById('composer'); if (!t) return; t.style.height = 'auto'; t.style.height = Math.min(t.scrollHeight, 140) + 'px'; }
  function scrollTranscript(force) { requestAnimationFrame(() => { const t = document.getElementById('transcript'); if (t && (force || t.scrollHeight - t.scrollTop - t.clientHeight < 120)) t.scrollTop = t.scrollHeight; }); }
  function fitViewport() { const vv = window.visualViewport; if (!vv) return; document.documentElement.style.setProperty('--app-h', vv.height + 'px'); scrollTranscript(false); }

  /* ================= boot ================= */
  function boot() {
    document.addEventListener('click', onClick);
    document.addEventListener('input', onInput);
    document.addEventListener('change', (e) => { if (e.target.dataset.act === 'toggle-sync') onClick(e); });
    document.addEventListener('submit', onSubmit);
    document.addEventListener('keydown', onKey);
    document.addEventListener('pointerdown', pttDown, { passive: false });
    ['pointerup', 'pointercancel', 'touchend', 'touchcancel'].forEach((t) => window.addEventListener(t, pttUp, { capture: true }));
    document.addEventListener('contextmenu', (e) => { if (e.target.closest('#ptt, #ptt-inline')) e.preventDefault(); });
    if (window.visualViewport) { window.visualViewport.addEventListener('resize', fitViewport); fitViewport(); }
    let onboarded = false; try { onboarded = localStorage.getItem('it_onboarded_v2') === 'true'; } catch { /* storage unavailable */ }
    ui.onboarding = onboarded ? null : 0;
    const initial = call('getInitialState');
    if (initial) applyState(initial); else render();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot); else boot();
})();
