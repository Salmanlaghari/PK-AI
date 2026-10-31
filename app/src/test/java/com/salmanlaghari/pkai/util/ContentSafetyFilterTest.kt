package com.salmanlaghari.pkai.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Best-effort lexical safety coverage; not a substitute for semantic moderation. */
class ContentSafetyFilterTest {

    @Test
    fun `plain sexual and profane prompts are blocked`() {
        listOf(
            "a nude woman",
            "naked man on a beach",
            "porn star portrait",
            "sexy lingerie model",
            "erotic painting",
            "a blowjob scene",
            "topless dancer",
            "what the fuck",
            "madarchod",
            "nangi tasveer"
        ).forEach { prompt ->
            assertTrue("Should block: \"$prompt\"", ContentSafetyFilter.isBlocked(prompt))
        }
    }

    @Test
    fun `separator-obfuscated prompts are blocked`() {
        listOf(
            "a n.u.d.e portrait",
            "n_u_d_e beach photo",
            "n u d e statue",
            "p-o-r-n video still",
            "s.e.x.y dancer",
            "f.u.c.k this"
        ).forEach { prompt ->
            assertTrue("Should block obfuscated: \"$prompt\"", ContentSafetyFilter.isBlocked(prompt))
        }
    }

    @Test
    fun `leetspeak-obfuscated prompts are blocked`() {
        listOf(
            "n4k3d swimmer",
            "s3xy model",
            "nud3 beach",
            "p0rn magazine",
            "7opless dancer",
            "8oob closeup",
            "f@ggot"
        ).forEach { prompt ->
            assertTrue("Should block leetspeak: \"$prompt\"", ContentSafetyFilter.isBlocked(prompt))
        }
    }

    @Test
    fun `uppercase and mixed case prompts are blocked`() {
        assertTrue(ContentSafetyFilter.isBlocked("A NUDE PORTRAIT"))
        assertTrue(ContentSafetyFilter.isBlocked("NaKeD statue"))
        assertTrue(ContentSafetyFilter.isBlocked("FUCK THIS"))
    }

    @Test
    fun `innocent prompts pass`() {
        listOf(
            "a cute cat on a rocket",
            "sunset over mountains",
            "a robot playing chess",
            "family picnic in the park"
        ).forEach { prompt ->
            assertFalse("Should pass: \"$prompt\"", ContentSafetyFilter.isBlocked(prompt))
            assertNull(ContentSafetyFilter.blockedReason(prompt))
        }
    }

    @Test
    fun `scunthorpe guard avoids false positives on innocent words`() {
        listOf(
            "a classic car",
            "an important document",
            "the title of the book",
            "data analysis chart",
            "shitake mushrooms",
            "cucumber salad",
            "breaststroke swimmer",
            "hello there"
        ).forEach { prompt ->
            assertFalse("False positive on: \"$prompt\"", ContentSafetyFilter.isBlocked(prompt))
        }
    }

    @Test
    fun `unsafe generated output is replaced before display or speech`() {
        assertEquals(
            ContentSafetyFilter.BLOCKED_OUTPUT_MESSAGE,
            ContentSafetyFilter.filterAiOutput("Here is a nude portrait.")
        )
        assertEquals("A quiet blue ocean.", ContentSafetyFilter.filterAiOutput("A quiet blue ocean."))
    }

    @Test
    fun `blockedReason names only the matched term`() {
        val reason = ContentSafetyFilter.blockedReason("a nude portrait")
        assertNotNull(reason)
        assertTrue(reason!!.contains("nude"))
        assertFalse(reason.contains("portrait"))
    }

    @Test
    fun `matchedTerm is null for clean prompts`() {
        assertNull(ContentSafetyFilter.matchedTerm("a puppy in a garden"))
    }

    @Test
    fun `empty and blank prompts are not blocked`() {
        assertFalse(ContentSafetyFilter.isBlocked(""))
        assertFalse(ContentSafetyFilter.isBlocked("   "))
    }
}
