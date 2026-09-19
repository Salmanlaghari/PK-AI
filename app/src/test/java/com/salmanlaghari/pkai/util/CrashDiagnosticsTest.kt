package com.salmanlaghari.pkai.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class CrashDiagnosticsTest {

    class SimpleInMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class Editor(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private val toRemove = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) toRemove.add(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) target.clear()
                for (r in toRemove) target.remove(r)
                target.putAll(temp)
            }
        }
    }

    class TestContext : android.content.ContextWrapper(null) {
        private val prefs = SimpleInMemorySharedPreferences()

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

    @Test
    fun testCrashDiagnosticsManagerSaveAndClear() = runBlocking {
        val context = TestContext()
        val manager = CrashDiagnosticsManager(context)

        // Initial state should be null
        assertNull(manager.crashLog.first())
        assertNull(manager.crashTimestamp.first())

        // Save crash log
        val sampleLog = "Exception: NullPointerException\nMessage: test crash"
        manager.saveCrashLog(sampleLog)

        assertEquals(sampleLog, manager.crashLog.first())
        assertNotNull(manager.crashTimestamp.first())

        // Clear crash log
        manager.clearCrashLog()
        assertNull(manager.crashLog.first())
        assertNull(manager.crashTimestamp.first())
    }

    @Test
    fun testCrashHandlerInitializeAndHandle() {
        val forwarded = AtomicBoolean(false)
        Thread.setDefaultUncaughtExceptionHandler { _, _ ->
            forwarded.set(true)
        }

        val context = TestContext()
        CrashHandler.initialize(context)

        // Multiple calls to initialize should not crash or re-register
        CrashHandler.initialize(context)

        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull(handler)

        val testException = IllegalStateException("Test startup crash", RuntimeException("Root cause"))
        handler?.uncaughtException(Thread.currentThread(), testException)

        // Verify crash log was saved to shared preferences
        val prefs = context.getSharedPreferences(CrashDiagnosticsManager.PREFS_NAME, Context.MODE_PRIVATE)
        val savedLog = prefs.getString(CrashDiagnosticsManager.CRASH_LOG_KEY, null)
        assertNotNull(savedLog)
        assertTrue(savedLog!!.contains("IllegalStateException"))
        assertTrue(savedLog.contains("Root cause"))
        assertTrue("Exception must be forwarded to defaultHandler", forwarded.get())
    }
}
