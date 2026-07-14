package com.salmanlaghari.pkai.ui.login

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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: LoginViewModel

    class FakeAuthRepository : AuthRepository {
        private val sessionFlow = MutableStateFlow(
            UserSession(
                isLoggedIn = false,
                isGuest = false,
                userId = null,
                displayName = null,
                email = null,
                profileImageUrl = null
            )
        )

        override fun getSession(): Flow<UserSession> = sessionFlow

        override suspend fun loginWithGoogle(
            idToken: String,
            displayName: String?,
            email: String?,
            photoUrl: String?
        ): Result<UserSession> {
            return if (idToken == "error_token") {
                Result.failure(Exception("Google Sign-In failed"))
            } else {
                Result.success(
                    UserSession(
                        isLoggedIn = true,
                        isGuest = false,
                        userId = "google_$idToken",
                        displayName = displayName,
                        email = email,
                        profileImageUrl = photoUrl
                    )
                )
            }
        }

        override suspend fun loginAsGuest(): Result<UserSession> {
            return Result.success(
                UserSession(
                    isLoggedIn = true,
                    isGuest = true,
                    userId = "guest_user",
                    displayName = "Guest User",
                    email = "",
                    profileImageUrl = ""
                )
            )
        }

        override suspend fun logout(): Result<Unit> {
            return Result.success(Unit)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        viewModel = LoginViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loginAsGuest transitions uiState to Success`() {
        // When
        viewModel.loginAsGuest()

        // Wait for coroutines
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assert(state is LoginUiState.Success)
        val successState = state as LoginUiState.Success
        assertEquals(true, successState.session.isGuest)
        assertEquals("guest_user", successState.session.userId)
    }

    @Test
    fun `loginWithGoogle with valid token transitions uiState to Success`() {
        // When
        viewModel.loginWithGoogle("valid_token", "John Doe", "john@example.com", "photo_url")

        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assert(state is LoginUiState.Success)
        val successState = state as LoginUiState.Success
        assertEquals(false, successState.session.isGuest)
        assertEquals("google_valid_token", successState.session.userId)
        assertEquals("John Doe", successState.session.displayName)
    }

    @Test
    fun `loginWithGoogle with error token transitions uiState to Error`() {
        // When
        viewModel.loginWithGoogle("error_token", null, null, null)

        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assert(state is LoginUiState.Error)
        val errorState = state as LoginUiState.Error
        assertEquals("Google Sign-In failed", errorState.message)
    }

    @Test
    fun `resetState transitions uiState back to Idle`() {
        // Given
        viewModel.loginWithGoogle("error_token", null, null, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assert(viewModel.uiState.value is LoginUiState.Error)

        // When
        viewModel.resetState()

        // Then
        assertEquals(LoginUiState.Idle, viewModel.uiState.value)
    }
}
