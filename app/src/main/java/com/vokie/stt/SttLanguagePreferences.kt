package com.vokie.stt

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

private val Context.sttPreferences by preferencesDataStore(name = "stt_settings")

/**
 * Retained as a construction dependency for compatibility. Recognition is no longer configurable:
 * the persisted UserLanguageProfile input language is authoritative for every transcription.
 */
class SttLanguagePreferences(@Suppress("unused") private val context: Context)
