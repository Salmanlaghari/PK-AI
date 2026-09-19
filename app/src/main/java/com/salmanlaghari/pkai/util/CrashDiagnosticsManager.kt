package com.salmanlaghari.pkai.util

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.crashDataStore by preferencesDataStore(name = "crash_diagnostics")

@Singleton
class CrashDiagnosticsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val CRASH_LOG_KEY = stringPreferencesKey("crash_log")
        private val CRASH_TIMESTAMP_KEY = stringPreferencesKey("crash_timestamp")
        private const val TAG = "CrashDiagnosticsManager"
        private val EMPTY_PREFERENCES = Preferences.emptyPreferences()
    }

    val crashLog: Flow<String?> = context.crashDataStore.data
        .catch { e ->
            Log.e(TAG, "Error reading crash log from DataStore", e)
            emit(EMPTY_PREFERENCES)
        }
        .map { preferences ->
            preferences[CRASH_LOG_KEY]
        }

    val crashTimestamp: Flow<String?> = context.crashDataStore.data
        .catch { e ->
            Log.e(TAG, "Error reading crash timestamp from DataStore", e)
            emit(EMPTY_PREFERENCES)
        }
        .map { preferences ->
            preferences[CRASH_TIMESTAMP_KEY]
        }

    suspend fun saveCrashLog(log: String) {
        try {
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            context.crashDataStore.edit { preferences ->
                preferences[CRASH_LOG_KEY] = log
                preferences[CRASH_TIMESTAMP_KEY] = timestamp
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log", e)
        }
    }

    suspend fun clearCrashLog() {
        try {
            context.crashDataStore.edit { preferences ->
                preferences.remove(CRASH_LOG_KEY)
                preferences.remove(CRASH_TIMESTAMP_KEY)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear crash log", e)
        }
    }
}
