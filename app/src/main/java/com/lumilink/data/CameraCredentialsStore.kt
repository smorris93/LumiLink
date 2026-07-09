package com.lumilink.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lumilink.model.CameraCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Top-level DataStore delegate. Must be a top-level property (one per file per name); the
// `by` keyword installs a lazy, process-wide singleton bound to this Context extension.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "camera")

/**
 * Persists the last camera the user connected to, so later launches connect without re-entering
 * the SSID/password.
 *
 * Kotlin/Android note: DataStore is the modern replacement for SharedPreferences. Reads are a
 * `Flow` (a stream of values over time) and writes are `suspend` functions (run on a background
 * dispatcher, never blocking the main thread).
 */
class CameraCredentialsStore(context: Context) {

    private val appContext = context.applicationContext

    private val ssidKey = stringPreferencesKey("ssid")
    private val passphraseKey = stringPreferencesKey("passphrase")

    /** Emits the saved credentials, or `null` if none saved yet; re-emits when they change. */
    val credentials: Flow<CameraCredentials?> = appContext.dataStore.data.map { prefs ->
        val ssid = prefs[ssidKey]
        val passphrase = prefs[passphraseKey]
        if (ssid.isNullOrEmpty() || passphrase == null) {
            null
        } else {
            CameraCredentials(ssid, passphrase)
        }
    }

    /** Saves (overwrites) the camera credentials. */
    suspend fun save(credentials: CameraCredentials) {
        appContext.dataStore.edit { prefs ->
            prefs[ssidKey] = credentials.ssid
            prefs[passphraseKey] = credentials.passphrase
        }
    }
}
