package com.salmanlaghari.pkai.data.repository

import com.salmanlaghari.pkai.util.ContentSafetyFilter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLEncoder

class PollinationsImageRepositoryTest {

    private lateinit var repo: PollinationsImageRepository

    @Before
    fun setUp() {
        repo = PollinationsImageRepository()
    }

    @Test
    fun `buildImageUrl encodes prompt correctly`() {
        val prompt = "a cat on a rocket"
        val url = repo.buildImageUrl(prompt)
        val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
        assertTrue("URL should start with expected prefix", url.startsWith("https://image.pollinations.ai/prompt/$encodedPrompt?width=768&height=768&nologo=true&model=flux&enhance=true"))
    }

    @Test
    fun `buildImageUrl encodes special characters in prompt`() {
        val prompt = "sunset & mountains! #nature /water"
        val url = repo.buildImageUrl(prompt)
        val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
        assertTrue("URL should start with expected prefix", url.startsWith("https://image.pollinations.ai/prompt/$encodedPrompt?width=768&height=768&nologo=true&model=flux&enhance=true"))
    }

    @Test
    fun `buildImageUrl uses custom dimensions when provided`() {
        val url = repo.buildImageUrl("test", width = 512, height = 512)
        assertTrue("URL should start with expected prefix", url.startsWith("https://image.pollinations.ai/prompt/test?width=512&height=512&nologo=true&model=flux&enhance=true"))
    }

    @Test
    fun `buildImageUrl uses default dimensions`() {
        val url = repo.buildImageUrl("test")
        assertTrue("URL should start with expected prefix", url.startsWith("https://image.pollinations.ai/prompt/test?width=768&height=768&nologo=true&model=flux&enhance=true"))
    }

    // ── Play-policy compliance: safe=true + client-side filter ──

    @Test
    fun `buildImageUrl includes safe=true for server-side NSFW filtering`() {
        val url = repo.buildImageUrl("a cute cat")
        assertTrue("URL must contain safe=true", url.contains("safe=true"))
    }

    @Test
    fun `generateImage blocks explicit prompt before any network call`() = runBlocking {
        val result = repo.generateImage("a nude woman")
        assertTrue("Explicit prompt must be Blocked", result is ImageGenerationResult.Blocked)
        assertEquals(
            "This prompt isn't allowed. Please try something else.",
            (result as ImageGenerationResult.Blocked).message
        )
    }

    @Test
    fun `generateImage blocks obfuscated explicit prompts`() = runBlocking {
        listOf(
            "a n.u.d.e portrait",
            "n_u_d_e beach photo",
            "p o r n star",
            "s3xy dancer",
            "nak3d statue"
        ).forEach { prompt ->
            val result = repo.generateImage(prompt)
            assertTrue(
                "Obfuscated prompt must be Blocked: \"$prompt\"",
                result is ImageGenerationResult.Blocked
            )
        }
    }

    @Test
    fun `content filter does not flag innocent prompts`() {
        assertFalse(ContentSafetyFilter.isBlocked("a cute cat on a rocket"))
        assertFalse(ContentSafetyFilter.isBlocked("classic painting of mountains"))
        // Scunthorpe guard: innocent words containing blocked substrings pass
        assertFalse(ContentSafetyFilter.isBlocked("a classic car"))
        assertFalse(ContentSafetyFilter.isBlocked("shitake mushrooms"))
    }
}
