package com.bettermifitness.sync.health

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persisted Google Health OAuth tokens (access + refresh + expiry).
 * Kept separate from Mi Account credentials.
 */
class GoogleHealthTokenStore(
    private val dataStore: DataStore<Preferences>,
) {
    val accessToken: Flow<String?> = dataStore.data.map { it[ACCESS_TOKEN_KEY] }
    val refreshToken: Flow<String?> = dataStore.data.map { it[REFRESH_TOKEN_KEY] }
    val expiresAtEpochSec: Flow<Long> = dataStore.data.map { it[EXPIRES_AT_KEY] ?: 0L }

    suspend fun hasToken(): Boolean = refreshToken.first() != null

    suspend fun save(accessToken: String, refreshToken: String?, expiresInSec: Long) {
        val now = kotlin.time.Clock.System.now().epochSeconds
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            if (refreshToken != null) prefs[REFRESH_TOKEN_KEY] = refreshToken
            prefs[EXPIRES_AT_KEY] = now + expiresInSec
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN_KEY)
            prefs.remove(REFRESH_TOKEN_KEY)
            prefs.remove(EXPIRES_AT_KEY)
        }
    }

    private companion object {
        val ACCESS_TOKEN_KEY = stringPreferencesKey("google_health_access_token")
        val REFRESH_TOKEN_KEY = stringPreferencesKey("google_health_refresh_token")
        val EXPIRES_AT_KEY = longPreferencesKey("google_health_expires_at")
    }
}
