package com.vokie.data

import android.content.Context
import android.content.SharedPreferences
import com.vokie.domain.model.LanguagePreference
import com.vokie.domain.model.UserIdentity
import com.vokie.domain.model.VokieLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

interface SettingsRepository {
    val identity: StateFlow<UserIdentity>
    val languagePreference: StateFlow<LanguagePreference>
    val pushToTalk: StateFlow<Boolean>
    val alertsFullVolume: StateFlow<Boolean>
    suspend fun updateDisplayName(name: String)
    suspend fun updateLanguages(speak: VokieLanguage, understand: VokieLanguage)
    suspend fun setPushToTalk(enabled: Boolean)
    suspend fun setAlertsFullVolume(enabled: Boolean)
}

class SharedPreferencesSettingsRepository(
    context: Context,
) : SettingsRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("vokie_user_settings", Context.MODE_PRIVATE)

    private val _identity: MutableStateFlow<UserIdentity>
    override val identity: StateFlow<UserIdentity>

    private val _languagePreference: MutableStateFlow<LanguagePreference>
    override val languagePreference: StateFlow<LanguagePreference>

    private val _pushToTalk = MutableStateFlow(prefs.getBoolean("push_to_talk", true))
    override val pushToTalk: StateFlow<Boolean> = _pushToTalk.asStateFlow()

    private val _alertsFullVolume = MutableStateFlow(prefs.getBoolean("alerts_full_volume", true))
    override val alertsFullVolume: StateFlow<Boolean> = _alertsFullVolume.asStateFlow()

    init {
        val deviceId = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
        val defaultName = android.os.Build.MODEL.trim().ifBlank { "Android device" }
        val storedName = prefs.getString("display_name", null)
        // Migrate the old prototype seed, but preserve a user-selected name.
        val name = storedName?.takeUnless { it.equals("Meera", ignoreCase = true) } ?: defaultName
        if (storedName == null || storedName.equals("Meera", ignoreCase = true)) {
            prefs.edit().putString("display_name", name).apply()
        }
        _identity = MutableStateFlow(UserIdentity(deviceId, name, name.firstOrNull()?.uppercase() ?: "A"))
        identity = _identity.asStateFlow()

        val speak = prefs.getString("speak_lang", "HI")?.let { VokieLanguage.fromCode(it) } ?: VokieLanguage.HI
        val understand = prefs.getString("understand_lang", "EN")?.let { VokieLanguage.fromCode(it) } ?: VokieLanguage.EN
        _languagePreference = MutableStateFlow(LanguagePreference(speak, understand))
        languagePreference = _languagePreference.asStateFlow()
    }

    override suspend fun updateDisplayName(name: String) {
        val trimmed = name.trim().ifBlank { android.os.Build.MODEL.trim().ifBlank { "Android device" } }
        prefs.edit().putString("display_name", trimmed).apply()
        _identity.value = _identity.value.copy(
            displayName = trimmed,
            avatarInitial = trimmed.firstOrNull()?.uppercase() ?: "M",
        )
    }

    override suspend fun updateLanguages(speak: VokieLanguage, understand: VokieLanguage) {
        prefs.edit()
            .putString("speak_lang", speak.code)
            .putString("understand_lang", understand.code)
            .apply()
        _languagePreference.value = LanguagePreference(speak, understand)
    }

    override suspend fun setPushToTalk(enabled: Boolean) {
        prefs.edit().putBoolean("push_to_talk", enabled).apply()
        _pushToTalk.value = enabled
    }

    override suspend fun setAlertsFullVolume(enabled: Boolean) {
        prefs.edit().putBoolean("alerts_full_volume", enabled).apply()
        _alertsFullVolume.value = enabled
    }
}
