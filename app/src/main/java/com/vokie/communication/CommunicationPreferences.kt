package com.vokie.communication

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.communicationDataStore: DataStore<Preferences> by preferencesDataStore(name = "vokie_communication")

class CommunicationPreferences(context: Context) {
    private val store = context.communicationDataStore

    val pushToTalkEnabled: Flow<Boolean> = store.data.map { it[PUSH_TO_TALK] ?: true }
    val alertsFullVolume: Flow<Boolean> = store.data.map { it[ALERTS_FULL_VOLUME] ?: true }

    suspend fun setPushToTalk(enabled: Boolean) = store.edit { it[PUSH_TO_TALK] = enabled }
    suspend fun setAlertsFullVolume(enabled: Boolean) = store.edit { it[ALERTS_FULL_VOLUME] = enabled }

    companion object {
        private val PUSH_TO_TALK = booleanPreferencesKey("push_to_talk")
        private val ALERTS_FULL_VOLUME = booleanPreferencesKey("alerts_full_volume")
    }
}
