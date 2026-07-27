package com.salmanlaghari.pkai.ui.profile

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.salmanlaghari.pkai.data.local.datastore.UserSession
import com.salmanlaghari.pkai.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: ProfileViewModel

    private val observers = mutableListOf<Pair<androidx.lifecycle.LiveData<*>, Observer<*>>>()

    class FakeAuthRepository : AuthRepository {
        private val sessionFlow = MutableStateFlow(
            UserSession(
                isLoggedIn = true,
                isGuest = false,
                userId = "google_user123",
                displayName = "Salman Laghari",
                email = "salman@pkai.com",
                profileImageUrl = "photo_url"
            )
        )

        override fun getSession(): Flow<UserSession> = sessionFlow

        override suspend fun loginWithGoogle(
            idToken: String,
            displayName: String?,
            email: String?,
            photoUrl: String?
        ): Result<UserSession> {
            return Result.success(sessionFlow.value)
        }

        override suspend fun loginAsGuest(): Result<UserSession> {
            return Result.success(sessionFlow.value)
        }

        override suspend fun logout(): Result<Unit> {
            sessionFlow.value = UserSession(
                isLoggedIn = false,
                isGuest = false,
                userId = null,
                displayName = null,
                email = null,
                profileImageUrl = null
            )
            return Result.success(Unit)
        }
    }

    private fun <T> androidx.lifecycle.LiveData<T>.observeForeverAndTrack(observer: Observer<T>) {
        this.observeForever(observer)
        @Suppress("UNCHECKED_CAST")
        observers.add(this to (observer as Observer<*>))
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        viewModel = ProfileViewModel(fakeRepository)
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
    fun `userSession flow initially exposes correct session details`() {
        // Given
        val liveSession = viewModel.userSession
        val observer = Observer<UserSession> {}
        liveSession.observeForeverAndTrack(observer)

        // Wait for LiveData/Flow to settle
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val sessionValue = liveSession.value
        assert(sessionValue != null)
        assertEquals(true, sessionValue?.isLoggedIn)
        assertEquals(false, sessionValue?.isGuest)
        assertEquals("Salman Laghari", sessionValue?.displayName)
        assertEquals("salman@pkai.com", sessionValue?.email)
    }

    @Test
    fun `logout clears the session successfully`() {
        // Given
        val liveSession = viewModel.userSession
        val observer = Observer<UserSession> {}
        liveSession.observeForeverAndTrack(observer)

        // When
        var logoutCompleted = false
        viewModel.logout {
            logoutCompleted = true
        }

        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assert(logoutCompleted)
        val sessionValue = liveSession.value
        assertEquals(false, sessionValue?.isLoggedIn)
        assertEquals(null, sessionValue?.userId)
    }
}
