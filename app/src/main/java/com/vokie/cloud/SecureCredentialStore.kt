package com.vokie.cloud

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keystore-backed storage for the active cloud bearer token. */
class SecureCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("vaani_secure_credentials", Context.MODE_PRIVATE)
    private val keyAlias = "vaani.cloud.token.v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
            keyAlias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    fun saveToken(token: String) {
        require(token.isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(KEY_TOKEN, payload).apply()
    }

    fun token(): String? = runCatching {
        val parts = prefs.getString(KEY_TOKEN, null)?.split('.') ?: return null
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        }.doFinal(ciphertext).toString(StandardCharsets.UTF_8)
    }.getOrNull()

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object { private const val KEY_TOKEN = "cloud_bearer_token" }
}

/** Explicit cloud-sync opt-in; local nearby/offline messaging does not depend on it. */
class CloudSyncPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("vaani_cloud_sync", Context.MODE_PRIVATE)
    private val _enabled = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: kotlinx.coroutines.flow.StateFlow<Boolean> = _enabled
    val sessionGeneration: String
        get() = prefs.getString(KEY_GENERATION, "") ?: ""

    fun rotateSession() {
        prefs.edit().putString(KEY_GENERATION, java.util.UUID.randomUUID().toString()).apply()
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_GENERATION = "session_generation"
    }
}
