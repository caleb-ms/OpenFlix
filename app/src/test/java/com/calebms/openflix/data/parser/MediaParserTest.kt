package com.calebms.openflix.data.parser

import org.junit.Assert.*
import org.junit.Test

class MediaParserTest {

    @Test
    fun parseTvShowEpisode_returnsCorrectSeasonAndEpisode() {
        val parsed = MediaParser.parse("The.Expanse.S01E01.1080p.mkv")
        assertEquals("The Expanse", parsed.cleanTitle)
        assertTrue(parsed.isTvShow)
        assertEquals(1, parsed.seasonNumber)
        assertEquals(1, parsed.episodeNumber)
        assertFalse(parsed.isSample)
    }

    @Test
    fun parseSampleEpisode_detectsSampleFlag() {
        val parsed = MediaParser.parse("The.Expanse-S01E01-sample.mp4")
        assertTrue(parsed.isSample)
        assertTrue(parsed.isTvShow)
        assertEquals(1, parsed.seasonNumber)
        assertEquals(1, parsed.episodeNumber)
    }

    @Test
    fun parseStandaloneSampleFile_detectsSampleFlag() {
        val parsed = MediaParser.parse("sample.mp4")
        assertTrue(parsed.isSample)
    }

    @Test
    fun parseMovie_returnsCleanTitleAndYear() {
        val parsed = MediaParser.parse("Inception.2010.1080p.Bluray.mkv")
        assertEquals("Inception", parsed.cleanTitle)
        assertFalse(parsed.isTvShow)
        assertEquals(2010, parsed.releaseYear)
        assertFalse(parsed.isSample)
    }
}
