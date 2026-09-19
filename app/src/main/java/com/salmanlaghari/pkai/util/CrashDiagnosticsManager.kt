package com.salmanlaghari.pkai.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashDiagnosticsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val PREFS_NAME = "crash_diagnostics"
        const val CRASH_LOG_KEY = "crash_log"
        const val CRASH_TIMESTAMP_KEY = "crash_timestamp"
        private const val TAG = "CrashDiagnosticsManager"

        fun saveCrashLogDirect(context: Context, log: String) {
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(CRASH_LOG_KEY, log)
                    .putString(CRASH_TIMESTAMP_KEY, timestamp)
                    .commit()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save crash log directly", e)
            }
        }
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _crashLogFlow = MutableStateFlow<String?>(null)
    val crashLog: Flow<String?> = _crashLogFlow.asStateFlow()

    private val _crashTimestampFlow = MutableStateFlow<String?>(null)
    val crashTimestamp: Flow<String?> = _crashTimestampFlow.asStateFlow()

    init {
        loadLogs()
    }

    private fun loadLogs() {
        try {
            _crashLogFlow.value = prefs.getString(CRASH_LOG_KEY, null)
            _crashTimestampFlow.value = prefs.getString(CRASH_TIMESTAMP_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load crash logs from preferences", e)
        }
    }

    suspend fun saveCrashLog(log: String) {
        try {
            saveCrashLogDirect(context, log)
            loadLogs()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log", e)
        }
    }

    suspend fun clearCrashLog() {
        try {
            prefs.edit()
                .remove(CRASH_LOG_KEY)
                .remove(CRASH_TIMESTAMP_KEY)
                .commit()
            _crashLogFlow.value = null
            _crashTimestampFlow.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear crash log", e)
        }
    }
}
