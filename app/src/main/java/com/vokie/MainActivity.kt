package com.vokie

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.vokie.ui.bridge.ITantraNativeBridge
import com.vokie.ui.communication.CommunicationViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var webView: WebView? = null
    private lateinit var bridge: ITantraNativeBridge
    private var networkStatus: com.vokie.ui.bridge.NetworkStatusSource? = null
    private var cloudState: com.vokie.ui.bridge.CloudStateSource? = null
    private val vm: CommunicationViewModel by viewModels()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] ?: false
        if (!micGranted) {
            vm.reportError("Microphone permission required for offline speech.")
        }
        vm.startDiscovery()
        vm.discoverWifiDirect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)

        // VAANI UI (additive, read-only state sources): validated internet status and account-scoped cloud state.
        networkStatus = com.vokie.ui.bridge.NetworkStatusSource(applicationContext).also { it.start() }
        cloudState = com.vokie.ui.bridge.CloudStateSource((application as VokieApplication).database, lifecycleScope)
        bridge = ITantraNativeBridge(vm, { webView }, lifecycleScope, networkStatus, cloudState)

        val wv = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                useWideViewPort = true
                loadWithOverviewMode = true
            }
            // VAANI: match tokens.css --color-bg for the current light/dark theme (values / values-night).
            setBackgroundColor(getColor(R.color.vaani_window_bg))
            addJavascriptInterface(bridge, "ITantraNative")
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                    Log.d("ITantraWeb", "${cm?.message()} -- L${cm?.lineNumber()} [${cm?.sourceId()}]")
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("ITantraWeb", "Page finished: $url")
                    bridge.pushStateUpdate()
                }
            }
            loadUrl("file:///android_asset/www/index.html")
        }
        this.webView = wv
        setContentView(wv)

        checkPermissions()
        vm.initializeStt()
        vm.startListening()
        vm.startDiscovery()
        handleNotificationIntent(intent)

        // Observe StateFlows from ViewModel
        lifecycleScope.launch {
            vm.speakUiState.collect { bridge.pushStateUpdate() }
        }
        lifecycleScope.launch {
            vm.peersUiState.collect { bridge.pushStateUpdate() }
        }
        lifecycleScope.launch {
            vm.pairingUiState.collect { bridge.pushStateUpdate() }
        }
        lifecycleScope.launch {
            vm.messagesUiState.collect { bridge.pushStateUpdate() }
        }
        lifecycleScope.launch {
            vm.chatUiState.collect { bridge.pushStateUpdate() }
        }
        lifecycleScope.launch {
            vm.callUiState.collect { bridge.pushStateUpdate() }
        }
        lifecycleScope.launch {
            vm.wifiDirectPeers.collect { bridge.pushStateUpdate() }
        }
        lifecycleScope.launch {
            vm.aiAssistant.collect { bridge.pushStateUpdate() }
        }
        // VAANI UI (additive): push state when settings, cloud and speech status change, so the
        // WebView never has to guess. These only trigger re-renders; they change no behaviour.
        lifecycleScope.launch { vm.settingsRepository.identity.collect { bridge.pushStateUpdate() } }
        lifecycleScope.launch { vm.settingsRepository.languagePreference.collect { bridge.pushStateUpdate() } }
        lifecycleScope.launch { vm.ttsSpeed.collect { bridge.pushStateUpdate() } }
        lifecycleScope.launch { vm.cloudUser.collect { bridge.pushStateUpdate() } }
        lifecycleScope.launch { vm.cloudSyncEnabled.collect { bridge.pushStateUpdate() } }
        lifecycleScope.launch { vm.cloudSyncState.collect { bridge.pushStateUpdate() } }
        lifecycleScope.launch { vm.messageTtsStates.collect { bridge.pushStateUpdate() } }
        lifecycleScope.launch { vm.installedTtsLanguages.collect { bridge.pushStateUpdate() } }
        lifecycleScope.launch { vm.sttStatus.map { it.state }.distinctUntilChanged().collect { bridge.pushStateUpdate() } }
        lifecycleScope.launch { networkStatus?.status?.collect { bridge.pushStateUpdate() } }
        lifecycleScope.launch { cloudState?.snapshot?.collect { bridge.pushStateUpdate() } }
    }

    override fun onDestroy() {
        networkStatus?.stop()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // uiMode is in configChanges, so the activity is not recreated on a light/dark switch.
        webView?.setBackgroundColor(getColor(R.color.vaani_window_bg))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val peerId = intent?.getStringExtra(com.vokie.notification.AppNotificationManager.EXTRA_PEER_ID) ?: return
        vm.selectPeer(peerId)
        vm.markConversationAsRead(peerId)
        bridge.navigateToChat(peerId)
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onBackPressed() {
        webView?.evaluateJavascript(
            """
            (function() {
                if (typeof window.handleAndroidBack === 'function') {
                    return window.handleAndroidBack();
                }
                if (typeof currentScreen !== 'undefined' && currentScreen !== 'screen-speak' && currentScreen !== 'screen-a1') {
                    navTo('screen-speak', 'back');
                    return true;
                }
                return false;
            })()
            """.trimIndent()
        ) { result ->
            if (result == "false" || result == null) {
                super.onBackPressed()
            }
        }
    }
}
