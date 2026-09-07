package com.salmanlaghari.pkai.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeBlockParserTest {

    @Test
    fun testLanguageMapping() {
        assertEquals("PYTHON3_8", CodeBlockParser.mapToHackerEarthLanguage("python"))
        assertEquals("PYTHON3_8", CodeBlockParser.mapToHackerEarthLanguage("py"))
        assertEquals("JAVA17", CodeBlockParser.mapToHackerEarthLanguage("java"))
        assertEquals("CPP17", CodeBlockParser.mapToHackerEarthLanguage("cpp"))
        assertEquals("CPP17", CodeBlockParser.mapToHackerEarthLanguage("c++"))
        assertEquals("JAVASCRIPT_NODE", CodeBlockParser.mapToHackerEarthLanguage("js"))
        assertEquals("KOTLIN", CodeBlockParser.mapToHackerEarthLanguage("kotlin"))
        assertEquals("GO", CodeBlockParser.mapToHackerEarthLanguage("go"))
        assertEquals("RUST", CodeBlockParser.mapToHackerEarthLanguage("rust"))
        assertNull(CodeBlockParser.mapToHackerEarthLanguage("html"))
        assertNull(CodeBlockParser.mapToHackerEarthLanguage("css"))
        assertNull(CodeBlockParser.mapToHackerEarthLanguage("json"))
    }

    @Test
    fun testParseSegmentsWithCodeBlock() {
        val markdown = """
            Here is a Python example:
            ```python
            print("Hello World")
            ```
            End of explanation.
        """.trimIndent()

        val segments = CodeBlockParser.parseSegments(markdown)
        assertEquals(3, segments.size)

        assertTrue(segments[0] is MessageSegment.Text)
        assertEquals("Here is a Python example:", (segments[0] as MessageSegment.Text).content)

        assertTrue(segments[1] is MessageSegment.CodeBlock)
        val codeBlock = segments[1] as MessageSegment.CodeBlock
        assertEquals("python", codeBlock.rawLanguage)
        assertEquals("PYTHON3_8", codeBlock.hackerEarthLang)
        assertEquals("print(\"Hello World\")", codeBlock.code)

        assertTrue(segments[2] is MessageSegment.Text)
        assertEquals("End of explanation.", (segments[2] as MessageSegment.Text).content)
    }
}
