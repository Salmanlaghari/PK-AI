package com.salmanlaghari.pkai.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownImageParserTest {

    @Test
    fun `strips base64 image markdown from visible text`() {
        val content = "Here is the picture:\n![alt text](data:image/png;base64,iVBORw0KGgoAAAANS) done."
        val parsed = MarkdownImageParser.parse(content)
        assertEquals(1, parsed.images.size)
        assertTrue(parsed.images[0].source.startsWith("data:image/"))
        assertEquals("Here is the picture:\ndone.", parsed.text)
    }

    @Test
    fun `strips remote image markdown`() {
        val content = "![cat](https://example.com/cat.png)"
        val parsed = MarkdownImageParser.parse(content)
        assertEquals(1, parsed.images.size)
        assertEquals("https://example.com/cat.png", parsed.images[0].source)
        assertEquals("", parsed.text.trim())
    }

    @Test
    fun `ignores non image markdown links`() {
        val content = "See [docs](https://example.com) for more."
        val parsed = MarkdownImageParser.parse(content)
        assertEquals(0, parsed.images.size)
        assertEquals(content, parsed.text)
    }

    @Test
    fun `handles multiple images`() {
        val content = "![a](data:image/png;base64,AAA)![b](https://x.com/y.jpg)"
        val parsed = MarkdownImageParser.parse(content)
        assertEquals(2, parsed.images.size)
    }
}
