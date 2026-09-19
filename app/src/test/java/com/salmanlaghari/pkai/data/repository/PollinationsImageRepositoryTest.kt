package com.salmanlaghari.pkai.data.repository

import org.junit.Assert.assertEquals
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
}
