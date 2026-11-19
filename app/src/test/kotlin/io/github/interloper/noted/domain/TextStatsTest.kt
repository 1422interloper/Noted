package io.github.interloper.noted.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TextStatsTest {

    @Test
    fun emptyAndBlankText_haveNoWords() {
        assertEquals(TextStats(0, 0), textStats(""))
        assertEquals(TextStats(0, 3), textStats("   "))
    }

    @Test
    fun countsWordsAcrossLinesAndRepeatedSpaces() {
        assertEquals(TextStats(words = 4, characters = 18), textStats("one  two\nthree\n\nfour "))
    }
}
