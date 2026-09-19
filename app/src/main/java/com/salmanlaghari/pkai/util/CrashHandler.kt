package com.salmanlaghari.pkai.util

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashHandler @Inject constructor(
    private val crashDiagnosticsManager: CrashDiagnosticsManager
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"
        private var defaultHandler: Thread.UncaughtExceptionHandler? = null

        fun initialize(crashDiagnosticsManager: CrashDiagnosticsManager) {
            defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(crashDiagnosticsManager)
            )
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

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            crashDiagnosticsManager.saveCrashLog(log)
        }

        defaultHandler?.uncaughtException(thread, exception)
    }
}
