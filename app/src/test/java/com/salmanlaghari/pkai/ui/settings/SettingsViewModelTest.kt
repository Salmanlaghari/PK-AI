package com.salmanlaghari.pkai.ui.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockPreferencesManager: PreferencesManager
    private lateinit var viewModel: SettingsViewModel

    private val observers = mutableListOf<Pair<androidx.lifecycle.LiveData<*>, Observer<*>>>()

    private val isDarkModeFlow = MutableStateFlow(true)
    private val appLanguageFlow = MutableStateFlow("en")
    private val notificationsEnabledFlow = MutableStateFlow(true)

    private fun <T> androidx.lifecycle.LiveData<T>.observeForeverAndTrack(observer: Observer<T>) {
        this.observeForever(observer)
        @Suppress("UNCHECKED_CAST")
        observers.add(this to (observer as Observer<*>))
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockPreferencesManager = Mockito.mock(PreferencesManager::class.java)
        Mockito.`when`(mockPreferencesManager.isDarkMode).thenReturn(isDarkModeFlow)
        Mockito.`when`(mockPreferencesManager.appLanguage).thenReturn(appLanguageFlow)
        Mockito.`when`(mockPreferencesManager.isNotificationsEnabled).thenReturn(notificationsEnabledFlow)

        viewModel = SettingsViewModel(mockPreferencesManager)
    }

    @After
    fun tearDown() {
        observers.forEach { (liveData, observer) ->
            @Suppress("UNCHECKED_CAST")
            (liveData as androidx.lifecycle.LiveData<Any>).removeObserver(observer as Observer<Any>)
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `settingsViewModel exposes correct initial settings`() {
        // Given
        val modeObs = Observer<Boolean> {}
        val langObs = Observer<String> {}
        val notifObs = Observer<Boolean> {}

        viewModel.isDarkMode.observeForeverAndTrack(modeObs)
        viewModel.appLanguage.observeForeverAndTrack(langObs)
        viewModel.isNotificationsEnabled.observeForeverAndTrack(notifObs)

        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertEquals(true, viewModel.isDarkMode.value)
        assertEquals("en", viewModel.appLanguage.value)
        assertEquals(true, viewModel.isNotificationsEnabled.value)
    }

    @Test
    fun `setDarkMode updates value correctly`() = runTest(testDispatcher) {
        // Given
        val modeObs = Observer<Boolean> {}
        viewModel.isDarkMode.observeForeverAndTrack(modeObs)

        // When
        viewModel.setDarkMode(false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        Mockito.verify(mockPreferencesManager).setDarkMode(false)
    }

    @Test
    fun `setAppLanguage updates value correctly`() = runTest(testDispatcher) {
        // Given
        val langObs = Observer<String> {}
        viewModel.appLanguage.observeForeverAndTrack(langObs)

        // When
        viewModel.setAppLanguage("ur")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        Mockito.verify(mockPreferencesManager).setAppLanguage("ur")
    }

    @Test
    fun `setNotificationsEnabled updates value correctly`() = runTest(testDispatcher) {
        // Given
        val notifObs = Observer<Boolean> {}
        viewModel.isNotificationsEnabled.observeForeverAndTrack(notifObs)

        // When
        viewModel.setNotificationsEnabled(false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        Mockito.verify(mockPreferencesManager).setNotificationsEnabled(false)
    }
}
