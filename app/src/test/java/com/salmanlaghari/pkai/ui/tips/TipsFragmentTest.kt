package com.salmanlaghari.pkai.ui.tips

import org.junit.Assert.assertEquals
import org.junit.Test

class TipsFragmentTest {
    @Test
    fun superCodeDigestMatchesTheDistributedCode() {
        assertEquals(
            TipsFragment.SUPER_CODE_SHA256,
            TipsFragment.sha256("PKAI-SUPER-18")
        )
    }
}
