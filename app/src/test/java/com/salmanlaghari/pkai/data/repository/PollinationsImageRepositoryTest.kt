package com.salmanlaghari.pkai.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PollinationsImageRepositoryTest {

    private lateinit var repo: PollinationsImageRepository

    @Before
    fun setUp() {
        repo = PollinationsImageRepository()
    }

    @Test
    fun `all native image requests fail closed until output moderation exists`() = runBlocking {
        listOf(
            "a cute cat on a rocket",
            "a nude portrait",
            ""
        ).forEach { prompt ->
            val result = repo.generateImage(prompt)
            assertTrue("Image generation must be blocked for: $prompt", result is ImageGenerationResult.Blocked)
            assertEquals(
                "AI image generation is temporarily unavailable while image-safety checks are added.",
                (result as ImageGenerationResult.Blocked).message
            )
        }
    }
}
