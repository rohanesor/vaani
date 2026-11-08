/*
 * Test double for the Android bridge (window.ITantraNative). Injected by the browser harness only — never shipped.
 * It records every call and returns the fixture from getInitialState(). It deliberately has NO behaviour:
 * state changes in tests are pushed explicitly with window.onAppStateUpdate(fixture), just as native does.
 */
(function () {
  // Exactly the @JavascriptInterface methods in app/src/main/java/com/vokie/ui/bridge/ITantraNativeBridge.kt.
  const METHODS = [
    'onPttDown', 'onPttUp', 'onSendMessage', 'onRequestAi', 'onCancelAiRequest', 'onSelectPeer', 'startDiscovery', 'stopDiscovery',
    'connectPeer', 'onDiscoverWifiDirect', 'onConnectWifiDirect', 'sendPairRequest', 'acceptPair', 'declinePair', 'onStartCall',
    'onAcceptCall', 'onRejectCall', 'onCancelCall', 'onEndCall', 'onSetCallMuted', 'onSetCallSpeakerphone', 'playMessageAudio',
    'stopMessageAudio', 'onUpdateDisplayName', 'onSelectLanguage', 'onSetTtsSpeed', 'onMarkAsRead', 'onDismissNotification',
    'cloudLogin', 'cloudLogout', 'setCloudSyncEnabled', 'syncCloudNow', 'linkCloudConversation', 'getInitialState',
    'onOpenCloudConversation', 'onCloseCloudConversation',
  ];
  window.__bridgeMethods = METHODS;
  window.__calls = [];
  const native = {};
  METHODS.forEach((m) => {
    native[m] = function (...args) {
      // Mirror the real interface: passwords are recorded redacted.
      window.__calls.push({ m, args: m === 'cloudLogin' ? [args[0], '<redacted>'] : args });
      if (m === 'getInitialState') return JSON.stringify(window.__fixture || null);
      return undefined;
    };
  });
  if (!window.__noBridge) window.ITantraNative = native;
})();
