package com.vokie.tts

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ttsPreferences by preferencesDataStore(name = "tts_settings")

class TtsPreferences(private val context: Context) {
    val speed: Flow<Float> = context.ttsPreferences.data.map { values ->
        values[SPEED]?.takeIf { it in MIN_TTS_SPEED..MAX_TTS_SPEED } ?: DEFAULT_TTS_SPEED
    }

    suspend fun setSpeed(speed: Float) {
        context.ttsPreferences.edit { it[SPEED] = validateTtsSpeed(speed) }
    }

    private companion object { val SPEED = floatPreferencesKey("speed") }
}
