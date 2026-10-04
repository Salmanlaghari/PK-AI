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
}
