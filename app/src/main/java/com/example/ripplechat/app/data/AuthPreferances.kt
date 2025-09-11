package com.example.ripplechat.app.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("auth_prefs")

class AuthPreferences(private val context: Context) {
    companion object {
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_PASSWORD = stringPreferencesKey("password")
    }

    suspend fun save(username: String, password: String) {
        context.dataStore.edit {
            it[KEY_USERNAME] = username
            it[KEY_PASSWORD] = password
        }
    }
    val credentialsFlow: Flow<Pair<String?, String?>> = context.dataStore.data.map {
        it[KEY_USERNAME] to it[KEY_PASSWORD]
    }

    suspend fun clear() {
        context.dataStore.edit {
            it.remove(KEY_USERNAME)
            it.remove(KEY_PASSWORD)
        }
    }
}
