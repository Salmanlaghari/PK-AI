package com.salmanlaghari.pkai.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongSearchHelperTest {
    @Test
    fun explicitSongRequestsRemainSupported() {
        assertEquals("kesariya", SongSearchHelper.extractSongQuery("play kesariya"))
        assertEquals("kesariya", SongSearchHelper.extractSongQuery("kesariya play karo"))
        assertEquals("tum hi ho", SongSearchHelper.extractSongQuery("song: tum hi ho"))
        assertEquals("game of thrones", SongSearchHelper.extractSongQuery("play game of thrones"))
        assertEquals("video killed the radiohead", SongSearchHelper.extractSongQuery("play video killed the radiohead"))
    }

    @Test
    fun ordinaryMediaAndLinkRequestsDoNotHijackChat() {
        assertNull(SongSearchHelper.extractSongQuery("play store se link bhejo"))
        assertNull(SongSearchHelper.extractSongQuery("play video games"))
        assertNull(SongSearchHelper.extractSongQuery("download game play karo"))
        assertNull(SongSearchHelper.extractSongQuery("song: game download"))
        assertNull(SongSearchHelper.extractSongQuery("play online game song sunao"))
    }

    @Test
    fun searchPrefixRequiresRealTitleText() {
        // Questions that merely end in "song" must not hijack chat.
        assertNull(SongSearchHelper.extractSongQuery("find out the meaning of this song"))
        assertNull(SongSearchHelper.extractSongQuery("find the meaning of this song"))
        assertNull(SongSearchHelper.extractSongQuery("search for a song"))
        assertNull(SongSearchHelper.extractSongQuery("search best song"))
        assertNull(SongSearchHelper.extractSongQuery("search my best song"))
        // Real titles still match, single- or multi-word.
        assertEquals("kesariya", SongSearchHelper.extractSongQuery("search kesariya song"))
        assertEquals("tum hi ho", SongSearchHelper.extractSongQuery("search tum hi ho song"))
        assertEquals(
            "hum dil de chuke sanam",
            SongSearchHelper.extractSongQuery("search hum dil de chuke sanam song")
        )
        // Optional karo/kar/karein tail on the prefix form.
        assertEquals(
            "hum dil de chuke sanam",
            SongSearchHelper.extractSongQuery("search hum dil de chuke sanam song karo")
        )
        assertEquals("kesariya", SongSearchHelper.extractSongQuery("find kesariya song karein"))
    }

    @Test
    fun searchSuffixRejectsServiceNamesAndSupportsTalash() {
        // Service-name questions get answered with chat, not a song card.
        assertNull(SongSearchHelper.extractSongQuery("youtube song search"))
        assertNull(SongSearchHelper.extractSongQuery("spotify song find"))
        assertNull(SongSearchHelper.extractSongQuery("youtube song talash karo"))
        // Suffix forms keep working, talash included.
        assertEquals("kesariya", SongSearchHelper.extractSongQuery("kesariya song search karo"))
        assertEquals("kesariya", SongSearchHelper.extractSongQuery("kesariya song talash karo"))
        assertEquals("kesariya", SongSearchHelper.extractSongQuery("kesariya song talash"))
    }

    @Test
    fun searchPrefixRejectsServiceNames() {
        // The service-name guard covers the prefix form too, not just suffix.
        assertNull(SongSearchHelper.extractSongQuery("search youtube song"))
        assertNull(SongSearchHelper.extractSongQuery("find spotify song karo"))
        // Real titles still match on the prefix form.
        assertEquals("kesariya", SongSearchHelper.extractSongQuery("search kesariya song"))
    }

    @Test
    fun packSongAttachmentStripsPipeDelimiter() {
        val song = SongSearchHelper.SongResult(
            title = "A|||B",
            artist = "X|Y",
            artworkUrl = "https://a.example/art.jpg",
            audioUrl = "https://a.example/song.mp3",
            pageUrl = "https://a.example/song/"
        )
        assertEquals(
            "AB|||XY|||https://a.example/art.jpg|||https://a.example/song/",
            SongSearchHelper.packSongAttachment(song)
        )
    }
}
