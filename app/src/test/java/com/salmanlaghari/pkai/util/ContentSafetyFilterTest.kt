package com.salmanlaghari.pkai.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Play-policy compliance tests for the client-side NSFW prompt filter.
 */
class ContentSafetyFilterTest {

    @Test
    fun `plain explicit prompts are blocked`() {
        listOf(
            "a nude woman",
            "naked man on a beach",
            "porn star portrait",
            "sexy lingerie model",
            "erotic painting",
            "a blowjob scene",
            "topless dancer"
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
            "s.e.x.y dancer"
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
            "8oob closeup"
        ).forEach { prompt ->
            assertTrue("Should block leetspeak: \"$prompt\"", ContentSafetyFilter.isBlocked(prompt))
        }
    }

    @Test
    fun `uppercase and mixed case prompts are blocked`() {
        assertTrue(ContentSafetyFilter.isBlocked("A NUDE PORTRAIT"))
        assertTrue(ContentSafetyFilter.isBlocked("NaKeD statue"))
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
        // "ass" in "classic", "cum" in "document", "tit" in "title",
        // "anal" in "analysis", "shit" in "shitake" must NOT block.
        listOf(
            "a classic car",
            "an important document",
            "the title of the book",
            "data analysis chart",
            "shitake mushrooms",
            "cucumber salad",
            "breaststroke swimmer"
        ).forEach { prompt ->
            assertFalse("False positive on: \"$prompt\"", ContentSafetyFilter.isBlocked(prompt))
        }
    }

    @Test
    fun `blockedReason names the matched term`() {
        val reason = ContentSafetyFilter.blockedReason("a nude portrait")
        assertNotNull(reason)
        assertTrue("Reason should name the term: $reason", reason!!.contains("nude"))
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
