package com.salmanlaghari.pkai.ui.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TipsFragmentTest {
    @Test
    fun digestComparisonIsLocaleStableWithoutEmbeddingTheSecret() {
        assertEquals(TipsFragment.sha256("not-the-code"), TipsFragment.sha256(" NOT-THE-CODE "))
        assertEquals(64, TipsFragment.SUPER_CODE_SHA256.length)
        assertTrue(TipsFragment.SUPER_CODE_SHA256.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(TipsFragment.SUPER_CODE_SHA256, TipsFragment.sha256("not-the-code"))
    }
}
