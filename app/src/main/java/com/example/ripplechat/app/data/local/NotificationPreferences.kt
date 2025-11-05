package com.example.ripplechat.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NotificationPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val MUTED_USERS = stringSetPreferencesKey("muted_users")
    }

    val mutedUsersFlow: Flow<Set<String>> =
        dataStore.data.map { it[MUTED_USERS] ?: emptySet() }

    suspend fun addMutedUser(userId: String) {
        dataStore.edit { prefs ->
            val current = prefs[MUTED_USERS] ?: emptySet()
            prefs[MUTED_USERS] = current + userId
        }
    }

    suspend fun removeMutedUser(userId: String) {
        dataStore.edit { prefs ->
            val current = prefs[MUTED_USERS] ?: emptySet()
            prefs[MUTED_USERS] = current - userId
        }
    }
}