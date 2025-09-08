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
        private val KEY_EMAIL = stringPreferencesKey("email")
        private val KEY_PASSWORD = stringPreferencesKey("password")
    }

    val emailFlow: Flow<String> = context.dataStore.data.map { it[KEY_EMAIL] ?: "" }
    val passwordFlow: Flow<String> = context.dataStore.data.map { it[KEY_PASSWORD] ?: "" }

    suspend fun save(email: String, password: String) {
        context.dataStore.edit {
            it[KEY_EMAIL] = email
            it[KEY_PASSWORD] = password
        }
    }

    suspend fun clear() {
        context.dataStore.edit {
            it.remove(KEY_EMAIL)
            it.remove(KEY_PASSWORD)
        }
    }
}
