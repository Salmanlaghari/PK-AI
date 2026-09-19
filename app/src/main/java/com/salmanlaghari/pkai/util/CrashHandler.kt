package com.salmanlaghari.pkai.util

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashHandler private constructor(
    private val crashDiagnosticsManager: CrashDiagnosticsManager?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"
        private var defaultHandler: Thread.UncaughtExceptionHandler? = null
        private var isInitialized = false

        fun initialize(crashDiagnosticsManager: CrashDiagnosticsManager?) {
            if (isInitialized) {
                Log.w(TAG, "CrashHandler already initialized, skipping")
                return
            }
            try {
                defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
                Thread.setDefaultUncaughtExceptionHandler(
                    CrashHandler(crashDiagnosticsManager)
                )
                isInitialized = true
                Log.i(TAG, "CrashHandler initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CrashHandler", e)
            }
        }
    }

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        val log = buildString {
            appendLine("Exception: ${exception.javaClass.simpleName}")
            appendLine("Message: ${exception.message}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine("Stack Trace:")
            exception.stackTrace.forEach { element ->
                appendLine("  at $element")
            }
            if (exception.cause != null) {
                appendLine()
                appendLine("Caused by: ${exception.cause?.javaClass?.simpleName}")
                appendLine("Message: ${exception.cause?.message}")
                exception.cause?.stackTrace?.forEach { element ->
                    appendLine("  at $element")
                }
            }
        }

        Log.e(TAG, "Uncaught exception captured:\n$log")

        try {
            if (crashDiagnosticsManager != null) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        crashDiagnosticsManager.saveCrashLog(log)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save crash log", e)
                    }
                }
            } else {
                Log.w(TAG, "CrashDiagnosticsManager is null, skipping crash log save")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in crash handler", e)
        }

        defaultHandler?.uncaughtException(thread, exception)
    }
}
