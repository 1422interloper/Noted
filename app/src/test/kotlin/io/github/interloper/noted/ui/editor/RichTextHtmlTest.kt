package io.github.interloper.noted.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.mohamedrejeb.richeditor.model.HeadingStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Notes are stored as the rich-text library's HTML, so every format the toolbar offers has to survive being written
 * out and read back. These pin that behaviour against library upgrades.
 */
@RunWith(RobolectricTestRunner::class)
class RichTextHtmlTest {

    private fun stateOf(html: String) = RichTextState().apply { setHtml(html) }

    private fun reloaded(state: RichTextState) = stateOf(state.toHtml())

    private fun RichTextState.select(start: Int, end: Int = start) {
        selection = TextRange(start, end)
    }

    @Test
    fun plainParagraphs_roundTripUnchanged() {
        val html = "<p>one two</p><p>three</p>"
        val state = stateOf(html)
        assertEquals(html, state.toHtml())
        assertEquals("one two\nthree", state.toText())
    }

    @Test
    fun emptyEditor_hasNoText() {
        assertEquals("", RichTextState().toText())
        assertEquals("", stateOf("").toText())
        assertEquals(RichTextState().toHtml(), stateOf("").toHtml())
    }

    @Test
    fun characterFormats_surviveReload() {
        val state = stateOf("<p>one two</p>")
        state.select(0, 3)
        state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
        state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
        state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
        state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
        state.addSpanStyle(SpanStyle(color = Color(0xFFE53935)))
        state.addSpanStyle(SpanStyle(background = Color(0x66FFEB3B)))

        val again = reloaded(state)
        again.select(1, 2)
        val span = again.currentSpanStyle
        assertEquals(FontWeight.Bold, span.fontWeight)
        assertEquals(FontStyle.Italic, span.fontStyle)
        assertTrue(span.textDecoration!!.contains(TextDecoration.Underline))
        assertTrue(span.textDecoration!!.contains(TextDecoration.LineThrough))
        assertEquals(Color(0xFFE53935), span.color)
        // The highlight keeps its transparency.
        assertEquals(0.4f, span.background.alpha, 0.01f)
        assertEquals("one two", again.toText())
        assertEquals(state.toHtml(), again.toHtml())

        again.select(5, 6)
        assertNull(again.currentSpanStyle.fontWeight)
    }

    @Test
    fun removingColor_restoresDefault() {
        val state = stateOf("<p>one</p>")
        state.select(0, 3)
        state.addSpanStyle(SpanStyle(color = Color(0xFFE53935)))
        state.removeSpanStyle(SpanStyle(color = state.currentSpanStyle.color))
        assertEquals("<p>one</p>", state.toHtml())
    }

    @Test
    fun headings_surviveReload() {
        for (heading in listOf(HeadingStyle.H1, HeadingStyle.H2, HeadingStyle.H3, HeadingStyle.H4)) {
            val state = stateOf("<p>title</p><p>body</p>")
            state.select(2)
            state.setHeadingStyle(heading)
            val again = reloaded(state)
            again.select(2)
            assertEquals(heading, again.currentHeadingStyle)
            again.select(8)
            assertEquals(HeadingStyle.Normal, again.currentHeadingStyle)
        }
    }

    @Test
    fun alignment_survivesReload_asLeftCenterRight() {
        for (align in listOf(TextAlign.Left, TextAlign.Center, TextAlign.Right)) {
            val state = stateOf("<p>x</p>")
            state.addParagraphStyle(ParagraphStyle(textAlign = align))
            assertEquals(align, reloaded(state).currentParagraphStyle.textAlign)
        }
    }

    @Test
    fun lists_surviveReload() {
        val state = stateOf("<p>one</p><p>two</p>")
        state.select(1)
        state.toggleUnorderedList()
        state.select(state.annotatedString.text.length)
        state.toggleOrderedList()

        val again = reloaded(state)
        again.select(1)
        assertTrue(again.isUnorderedList)
        again.select(again.annotatedString.text.length)
        assertTrue(again.isOrderedList)
        assertEquals(state.toHtml(), again.toHtml())
    }

    @Test
    fun linkAndCode_surviveReload() {
        val state = stateOf("<p>see site now</p>")
        state.select(4, 8)
        state.addLinkToSelection("https://example.org")
        state.select(9, 12)
        state.toggleCodeSpan()

        val again = reloaded(state)
        again.select(5, 6)
        assertTrue(again.isLink)
        assertEquals("https://example.org", again.selectedLinkUrl)
        again.select(10, 11)
        assertTrue(again.isCodeSpan)
        again.select(1, 2)
        assertFalse(again.isLink)
        assertEquals("see site now", again.toText())
    }

    @Test
    fun undoHistory_startsEmptyAfterLoading() {
        val state = stateOf("<p>abc</p>")
        state.history.clear()
        assertFalse(state.history.canUndo)
        state.select(0, 3)
        state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
        assertTrue(state.history.canUndo)
        state.history.undo()
        assertEquals("<p>abc</p>", state.toHtml())
    }

    @Test
    fun normaliseUrl_addsHttpsOnlyWhenNoScheme() {
        assertEquals("https://example.org", normaliseUrl(" example.org "))
        assertEquals("http://example.org", normaliseUrl("http://example.org"))
        assertEquals("mailto:a@example.org", normaliseUrl("mailto:a@example.org"))
        assertNull(normaliseUrl("   "))
    }
}
