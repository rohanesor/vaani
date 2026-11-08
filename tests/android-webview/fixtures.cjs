/*
 * Fixture snapshots with the exact key set produced by ITantraNativeBridge.buildStateJson().
 * Values are illustrative test data, not product data — the shipped UI contains none of it.
 */
// Relative to the run time so "5 min ago" labels stay meaningful in screenshots.
const NOW = Math.floor(Date.now() / 60000) * 60000;
const min = 60000;

const people = {
  meera: { id: 'peer-meera-7f2a', name: 'Meera Iyer', initial: 'M' },
  arjun: { id: 'peer-arjun-19bc', name: 'Arjun Reddy', initial: 'A' },
  farah: { id: 'peer-farah-0d41', name: 'Farah Khan', initial: 'F' },
  dev: { id: 'peer-dev-6e03', name: 'Dev Patil', initial: 'D' },
};

function base(over = {}) {
  return {
    identity: { peerId: 'self-rose-a1', name: 'Rose', initial: 'R' },
    cloud: { signedIn: true, displayName: 'Rose', email: 'rose@example.invalid', syncEnabled: true, syncState: 'Synced', configured: true, lastSyncedAt: NOW - 6 * min },
    network: { state: 'online', transport: 'wifi', metered: false },
    nearby: { discovered: 1, connected: 2, trusted: 3, pairingIncoming: 0, pairingOutgoing: 0 },
    outgoingPairRequests: [],
    ai: { loading: false, error: null, text: null, actionsRequireConfirmation: true },
    aiConfigured: true,
    speech: { sttState: 'READY', ttsInstalled: ['EN', 'HI', 'TA', 'TE', 'KN', 'BN', 'GU', 'MR'] },
    messageAudio: {},
    language: 'EN',
    ttsSpeed: 1.0,
    call: { callId: null, state: 'IDLE', direction: null, peerId: null, peerName: null, peerInitial: null, failureReason: null },
    stripDot: 'green',
    stripText: '2 active nearby · Wi-Fi preferred, Bluetooth fallback',
    selectedPeer: { ...people.meera, transport: 'wifi', connected: true },
    pttState: 'idle',
    pttLabel: 'Hold to speak',
    activePeers: [
      { ...people.meera, transport: 'wifi', connected: true },
      { ...people.arjun, transport: 'bluetooth', connected: true },
    ],
    discoveredPeers: [{ ...people.dev, transport: 'Bluetooth', connected: false }],
    trustedPeers: [
      { ...people.meera, status: 'TRUSTED' },
      { ...people.arjun, status: 'TRUSTED' },
      { ...people.farah, status: 'TRUSTED' },
    ],
    wifiDirectPeers: [{ address: '9a:3c:51:0e:22:7b', name: 'Pixel 7 (Dev)', available: true }],
    incomingPairRequest: null,
    conversations: [
      { id: 'conv-meera', peerId: people.meera.id, ...pick(people.meera), lastMessage: 'मैं गेट नंबर दो पर हूँ, नीली जैकेट में।', time: NOW - 3 * min, unreadCount: 2, origin: 'nearby', sync: null },
      { id: 'conv-arjun', peerId: people.arjun.id, ...pick(people.arjun), lastMessage: 'Water stall moved next to the medical tent.', time: NOW - 52 * min, unreadCount: 0, origin: 'cloud-linked', sync: { cloudId: 41, pending: 0, failed: 0, synced: 18 } },
      { id: 'conv-farah', peerId: people.farah.id, ...pick(people.farah), lastMessage: 'নিরাপদে পৌঁছেছি, ধন্যবাদ!', time: NOW - 26 * 60 * min, unreadCount: 1, origin: 'nearby', sync: null },
    ],
    cloudConversations: [
      { cloudId: 52, kind: 'group', title: 'Relief camp volunteers', members: ['Kavya Reddy', 'Arjun Mehta', 'Meena Sundaram'], updatedAt: NOW - 20 * min,
        lastMessage: { body: 'Water tanker arrives at 4 PM near gate three.', senderName: 'Kavya Reddy', kind: 'text', createdAt: NOW - 20 * min } },
      { cloudId: 57, kind: 'direct', title: null, members: ['Priya Nair'], updatedAt: NOW - 5 * 60 * min,
        lastMessage: { body: 'நாளை சந்திப்போம்', senderName: 'Priya Nair', kind: 'text', createdAt: NOW - 5 * 60 * min } },
    ],
    cloudChat: null,
    chatMessages: chatMeera(),
    ...over,
  };
}
function pick(p) { return { name: p.name, initial: p.initial }; }

function chatMeera() {
  const me = 'self-rose-a1'; const m = people.meera.id;
  return [
    { id: 'm1', text: 'Hi Meera — are you near the north entrance?', senderId: me, isOutgoing: true, time: NOW - 14 * min, status: 'DELIVERED', type: 'TEXT', language: 'EN', urgent: false, semanticLabel: null, translatedText: null, translatedLanguage: null },
    { id: 'm2', text: 'हाँ, मैं गेट नंबर दो पर हूँ, नीली जैकेट में।', senderId: m, isOutgoing: false, time: NOW - 12 * min, status: 'DELIVERED', type: 'VOICE_TEXT', language: 'HI', urgent: false, semanticLabel: null, translatedText: 'Yes, I’m at gate number two, in a blue jacket.', translatedLanguage: 'EN' },
    { id: 'm3', text: 'भीड़ बढ़ रही है, जल्दी आना।', senderId: m, isOutgoing: false, time: NOW - 11 * min, status: 'DELIVERED', type: 'TEXT', language: 'HI', urgent: true, semanticLabel: 'Request', translatedText: 'The crowd is growing, come quickly.', translatedLanguage: 'EN' },
    { id: 'm4', text: 'On my way. Five minutes.', senderId: me, isOutgoing: true, time: NOW - 4 * min, status: 'RECEIVED_BY_PEER', type: 'VOICE_TEXT', language: 'EN', urgent: false, semanticLabel: null, translatedText: null, translatedLanguage: null },
    { id: 'm5', text: 'Can you hold a spot near the water stall?', senderId: me, isOutgoing: true, time: NOW - 3 * min, status: 'RETRYING', type: 'TEXT', language: 'EN', urgent: false, semanticLabel: null, translatedText: null, translatedLanguage: null },
  ];
}

const empty = () => base({
  stripDot: 'grey', stripText: 'No peers active · Offline', selectedPeer: null, activePeers: [], discoveredPeers: [], trustedPeers: [],
  wifiDirectPeers: [], conversations: [], chatMessages: [], cloudConversations: [],
  nearby: { discovered: 0, connected: 0, trusted: 0, pairingIncoming: 0, pairingOutgoing: 0 },
});

function cloudChat52() {
  return { cloudId: 52, messages: [
    { cloudId: 901, senderName: 'Kavya Reddy', isMine: false, kind: 'text', body: 'Kitchen team: 120 meals ready by noon.', sourceLanguage: 'en', createdAt: NOW - 95 * min, deleted: false },
    { cloudId: 902, senderName: 'Rose', isMine: true, kind: 'text', body: 'Great — I’ll bring the second batch of water cans.', sourceLanguage: 'en', createdAt: NOW - 80 * min, deleted: false },
    { cloudId: 903, senderName: 'Meena Sundaram', isMine: false, kind: 'voice', body: 'மருத்துவ கூடாரம் இப்போது திறந்துள்ளது.', sourceLanguage: 'ta', createdAt: NOW - 40 * min, deleted: false },
    { cloudId: 904, senderName: 'Kavya Reddy', isMine: false, kind: 'text', body: 'Water tanker arrives at 4 PM near gate three.', sourceLanguage: 'en', createdAt: NOW - 20 * min, deleted: false },
  ] };
}

// Internet off, nearby still connected; linked conversation has uploads waiting in sync_queue/mappings.
const offlineNearby = () => {
  const f = base({ network: { state: 'none', transport: null, metered: false }, cloud: { ...base().cloud, syncState: 'WaitingForConnection', lastSyncedAt: NOW - 3 * 60 * min } });
  f.conversations[1] = { ...f.conversations[1], sync: { cloudId: 41, pending: 3, failed: 0, synced: 18 } };
  return f;
};
const wifiNotValidated = () => base({ network: { state: 'connecting', transport: 'wifi', metered: false } });
const captivePortal = () => base({ network: { state: 'limited', transport: 'wifi', metered: false } });
const signedOut = () => {
  const f = base({ cloud: { signedIn: false, displayName: null, email: null, syncEnabled: false, syncState: 'Idle', configured: true, lastSyncedAt: null }, cloudConversations: [] });
  f.conversations = f.conversations.map((c) => ({ ...c, origin: 'nearby', sync: null }));
  return f;
};
const unconfigured = () => ({ ...signedOut(), cloud: { ...signedOut().cloud, configured: false }, aiConfigured: false });

module.exports = { NOW, people, base, empty, chatMeera, cloudChat52, offlineNearby, wifiNotValidated, captivePortal, signedOut, unconfigured };
