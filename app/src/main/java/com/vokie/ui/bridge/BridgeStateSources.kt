package com.vokie.ui.bridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.vokie.data.local.CloudMessageEntity
import com.vokie.data.local.VokieDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/*
 * Read-only state sources for the WebView UI (docs/ANDROID_BRIDGE_STATE_CONTRACT.md §3).
 * They observe existing system services and Room tables. They never write, send, sync or schedule anything.
 */

/**
 * Internet status from the default network. "online" requires NET_CAPABILITY_VALIDATED:
 * a connected Wi-Fi or mobile network alone is reported as "connecting" (or "limited" behind a captive portal).
 * Nearby links (Bluetooth, Wi-Fi Direct groups) are not the default network and never count as internet.
 */
class NetworkStatusSource(context: Context) {
    data class Status(val state: String, val transport: String?, val metered: Boolean)

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _status = MutableStateFlow(NONE)
    val status: StateFlow<Status> = _status
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { _status.value = from(caps) }
        override fun onLost(network: Network) { _status.value = NONE }
        override fun onUnavailable() { _status.value = NONE }
    }

    fun start() {
        if (registered || cm == null) return
        _status.value = current()
        registered = runCatching { cm.registerDefaultNetworkCallback(callback) }.isSuccess
    }

    fun stop() {
        if (!registered || cm == null) return
        runCatching { cm.unregisterNetworkCallback(callback) }
        registered = false
    }

    private fun current(): Status {
        val network = cm?.activeNetwork ?: return NONE
        val caps = cm.getNetworkCapabilities(network) ?: return NONE
        return from(caps)
    }

    companion object {
        val NONE = Status("none", null, false)

        fun from(caps: NetworkCapabilities): Status {
            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
            val state = when {
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) -> "limited"
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "online"
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "connecting"
                else -> "none"
            }
            val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            return Status(state, transport, metered)
        }
    }
}

/**
 * Cloud conversation and per-conversation sync state, derived only from persistent Room records
 * scoped to the signed-in cloud account (cloud_user, cloud_id_mapping, cloud_conversations,
 * cloud_members, cloud_messages, messages).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudStateSource(private val db: VokieDatabase, scope: CoroutineScope) {
    data class ConversationSync(val cloudId: Long, val pending: Int, val failed: Int, val synced: Int)
    data class CloudConversationView(
        val cloudId: Long,
        val kind: String,
        val title: String?,
        val members: List<String>,
        val updatedAt: Long,
        val lastMessage: CloudMessageEntity?,
    )
    data class CloudChat(val cloudId: Long, val messages: List<CloudMessageEntity>)
    data class Snapshot(
        val accountId: Long?,
        /** Local conversation id -> link and sync counts, for the current account only. */
        val links: Map<String, ConversationSync>,
        /** Cloud conversations not linked to a local conversation for the current account. */
        val cloudOnly: List<CloudConversationView>,
        val chat: CloudChat?,
    )

    private val selected = MutableStateFlow<Long?>(null)

    /** Selects which cached cloud conversation's messages are exported. No network or writes. */
    fun openCloudConversation(cloudId: Long) { selected.value = cloudId }
    fun closeCloudConversation() { selected.value = null }

    val snapshot: StateFlow<Snapshot> = db.cloudUser().observe().flatMapLatest { user ->
        val accountId = user?.cloudUserId
        if (accountId == null) flowOf(EMPTY)
        else {
            val chatFlow = selected.flatMapLatest { id ->
                if (id == null) flowOf(null)
                else db.cloudMessages().observeRecent(id, 100).map { CloudChat(id, it.sortedBy { m -> m.createdAt }) }
            }
            combine(
                db.cloudIdMapping().observeConversationLinks(accountId),
                db.cloudIdMapping().observeMessageSyncCounts(accountId),
                db.cloudConversations().observeAll(),
                chatFlow,
            ) { links, counts, conversations, chat ->
                val byConversation = counts.filter { it.conversationId != null }.groupBy { it.conversationId!! }
                val linkMap = links.mapNotNull { link ->
                    val cloudId = link.cloudId ?: return@mapNotNull null
                    val c = byConversation[link.localId].orEmpty()
                    link.localId to ConversationSync(
                        cloudId = cloudId,
                        pending = c.filter { it.syncStatus == "pending" }.sumOf { it.count },
                        failed = c.filter { it.syncStatus == "error" }.sumOf { it.count },
                        synced = c.filter { it.syncStatus == "synced" }.sumOf { it.count },
                    )
                }.toMap()
                val linkedCloudIds = linkMap.values.map { it.cloudId }.toSet()
                val cloudOnly = conversations.filter { it.cloudId !in linkedCloudIds }.map { conv ->
                    CloudConversationView(
                        cloudId = conv.cloudId,
                        kind = conv.kind,
                        title = conv.title,
                        members = db.cloudMembers().getMembers(conv.cloudId).filter { it.userCloudId != accountId }.map { it.displayName },
                        updatedAt = conv.updatedAt,
                        lastMessage = conv.lastMessageCloudId?.let { db.cloudMessages().get(it) },
                    )
                }
                Snapshot(accountId, linkMap, cloudOnly, chat)
            }
        }
    }.flowOn(Dispatchers.IO).stateIn(scope, SharingStarted.Eagerly, EMPTY)

    companion object {
        val EMPTY = Snapshot(null, emptyMap(), emptyList(), null)
    }
}
