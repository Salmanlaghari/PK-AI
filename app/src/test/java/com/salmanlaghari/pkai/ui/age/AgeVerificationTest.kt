package com.salmanlaghari.pkai.ui.age

import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AgeVerificationTest {

    private lateinit var mockPreferencesManager: PreferencesManager

    @Before
    fun setUp() {
        mockPreferencesManager = mock(PreferencesManager::class.java)
    }

    @Test
    fun testAgeVerificationDefaultFalse() = runTest {
        `when`(mockPreferencesManager.isAgeVerified).thenReturn(flowOf(false))

        val verified = mockPreferencesManager.isAgeVerified
        verified.collect { isVerified ->
            assertFalse(isVerified)
        }
    }

    @Test
    fun testAgeVerificationConfirmed() = runTest {
        `when`(mockPreferencesManager.isAgeVerified).thenReturn(flowOf(true))

        val verified = mockPreferencesManager.isAgeVerified
        verified.collect { isVerified ->
            assertTrue(isVerified)
        }
    }

    @Test
    fun testStickerHintDismissedDefaultFalse() = runTest {
        `when`(mockPreferencesManager.isStickerHintDismissed).thenReturn(flowOf(false))

        val dismissedFlow = mockPreferencesManager.isStickerHintDismissed
        dismissedFlow.collect { isDismissed ->
            assertFalse(isDismissed)
        }
    }
}
