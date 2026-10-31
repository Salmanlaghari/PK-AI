package com.salmanlaghari.pkai.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseRegistryTest {

    @Test
    fun `picker contains only stable safe emoji indices`() {
        val indices = PoseRegistry.allStickers().toList()
        assertEquals(28, indices.size)
        assertEquals((216..243).toList(), indices)
        assertTrue(indices.all(PoseRegistry::isSafeSticker))
    }

    @Test
    fun `all mood rotations remain within the safe emoji allowlist`() {
        assertTrue(PoseRegistry.moodStickers.values.flatten().all(PoseRegistry::isSafeSticker))
        Mood.entries.forEach { mood ->
            repeat(12) { rotation ->
                assertTrue(PoseRegistry.isSafeSticker(PoseRegistry.stickerForMood(mood, rotation)))
            }
        }
    }

    @Test
    fun `retired character indices normalize to a safe default`() {
        assertEquals(PoseRegistry.defaultSticker, PoseRegistry.sanitizeSticker(0))
        assertEquals(PoseRegistry.defaultSticker, PoseRegistry.sanitizeSticker(215))
        assertEquals(216, PoseRegistry.sanitizeSticker(216))
    }
}
