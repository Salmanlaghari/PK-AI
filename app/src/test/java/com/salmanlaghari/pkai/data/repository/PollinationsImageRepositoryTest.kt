package com.salmanlaghari.pkai.data.repository

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.URLEncoder

class PollinationsImageRepositoryTest {

    private lateinit var repo: PollinationsImageRepository

    @Before
    fun setUp() {
        repo = PollinationsImageRepository(OkHttpClient.Builder().build())
    }

    @Test
    fun `buildImageUrl encodes prompt correctly`() {
        val prompt = "a cat on a rocket"
        val url = repo.buildImageUrl(prompt)
        val expected = "https://image.pollinations.ai/prompt/" +
                URLEncoder.encode(prompt, "UTF-8") +
                "?width=768&height=768&nologo=true&model=flux&enhance=true"
        assertEquals(expected, url)
    }

    @Test
    fun `buildImageUrl encodes special characters in prompt`() {
        val prompt = "sunset & mountains! #nature /water"
        val url = repo.buildImageUrl(prompt)
        val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
        assertEquals("https://image.pollinations.ai/prompt/$encodedPrompt?width=768&height=768&nologo=true&model=flux&enhance=true", url)
    }

    @Test
    fun `buildImageUrl uses custom dimensions when provided`() {
        val url = repo.buildImageUrl("test", width = 512, height = 512)
        assertEquals("https://image.pollinations.ai/prompt/test?width=512&height=512&nologo=true&model=flux&enhance=true", url)
    }

    @Test
    fun `buildImageUrl uses default dimensions`() {
        val url = repo.buildImageUrl("test")
        assertEquals("https://image.pollinations.ai/prompt/test?width=768&height=768&nologo=true&model=flux&enhance=true", url)
    }
}
