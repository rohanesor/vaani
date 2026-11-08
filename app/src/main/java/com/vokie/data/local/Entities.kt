package com.vokie.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val receiverId: String?,
    val timestamp: Long,
    val text: String,
    val language: String,
    val messageType: String,
    val deliveryState: String,
    val transport: String?,
    val hopCount: Int,
    val retryCount: Int,
    val requiresAck: Boolean,
    val lastError: String?,
    val sequenceNumber: Long = 0,
    val ttlMs: Long = 300000,
    val priority: Int = 0,
    val checksum: Long = 0,
    val nextRetryAt: Long? = null,
    /** Base64 of the bounded N5 wire hint; null for legacy/text-only messages. */
    val semanticMetadata: String? = null,
    /** Persisted translation for receiver display; null for sender's own messages. */
    val translatedText: String? = null,
    /** Target language code of the translation; null if not translated. */
    val translatedLanguage: String? = null,
    /** Stable local conversation UUID; never replaced by a cloud conversation ID. */
    val conversationId: String? = null,
)

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val id: String,
    val deviceAddress: String,
    val deviceName: String,
    val protocolVersion: Int,
    val lastSeen: Long,
    val connectionState: String,
    val transport: String,
    val isTrusted: Boolean,
    val sourceLanguage: String? = null,
    val targetLanguage: String? = null,
    val priority: Int = 0,
)

@Entity(tableName = "transport_events")
data class TransportEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val transport: String,
    val eventType: String,
    val peerId: String?,
    val messageId: String?,
    val detail: String?,
    val latencyMs: Long?,
)

@Entity(tableName = "emergency_alerts")
data class EmergencyAlertEntity(@PrimaryKey val id: String, val severity: String, val title: String, val body: String, val timestamp: Long, val source: String, val status: String)
@Entity(tableName = "received_packets", primaryKeys = ["sourceDeviceId", "messageId", "sequenceNumber"])
data class ReceivedPacketEntity(val sourceDeviceId: String, val messageId: String, val sequenceNumber: Long, val receivedAt: Long, val expiresAt: Long)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(@PrimaryKey val id: Int = 1, val language: String, val walkieTalkieMode: Boolean, val darkMode: Boolean, val haptics: Boolean)
