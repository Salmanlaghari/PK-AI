package com.salmanlaghari.pkai.util

import android.content.Context
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val appContext: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"
        private val isInitialized = AtomicBoolean(false)

        fun initialize(context: Context) {
            if (isInitialized.getAndSet(true)) {
                Log.w(TAG, "CrashHandler already initialized, skipping")
                return
            }

            try {
                val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
                if (currentHandler is CrashHandler) {
                    Log.w(TAG, "Default handler is already CrashHandler, skipping")
                    return
                }

                val appContext = context.applicationContext ?: context
                Thread.setDefaultUncaughtExceptionHandler(
                    CrashHandler(appContext, currentHandler)
                )
                Log.i(TAG, "CrashHandler initialized successfully")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize CrashHandler", e)
            }
        }
    }

    private val isHandlingCrash = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        // Prevent recursive crashes
        if (!isHandlingCrash.compareAndSet(false, true)) {
            Log.e(TAG, "Recursive uncaught exception detected, delegating directly")
            forwardToDefaultHandler(thread, exception)
            return
        }

        try {
            val log = buildString {
                appendLine("Exception: ${exception.javaClass.simpleName}")
                appendLine("Message: ${exception.message}")
                appendLine("Thread: ${thread.name}")
                appendLine()
                appendLine("Stack Trace:")
                exception.stackTrace.forEach { element ->
                    appendLine("  at $element")
                }
                var currentCause: Throwable? = exception.cause
                var depth = 0
                while (currentCause != null && depth < 10) {
                    appendLine()
                    appendLine("Caused by: ${currentCause.javaClass.simpleName}")
                    appendLine("Message: ${currentCause.message}")
                    currentCause.stackTrace.forEach { element ->
                        appendLine("  at $element")
                    }
                    currentCause = currentCause.cause
                    depth++
                }
            }

            Log.e(TAG, "Uncaught exception captured:\n$log")

            // Synchronously commit crash log to disk before process termination
            CrashDiagnosticsManager.saveCrashLogDirect(appContext, log)
        } catch (t: Throwable) {
            Log.e(TAG, "Error while processing crash log", t)
        } finally {
            forwardToDefaultHandler(thread, exception)
        }
    }

    private fun forwardToDefaultHandler(thread: Thread, exception: Throwable) {
        if (defaultHandler != null && defaultHandler !is CrashHandler) {
            defaultHandler.uncaughtException(thread, exception)
        } else {
            // Terminate process cleanly if no default handler is present
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }
}
