package io.github.interloper.noted.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteDaoTest : DaoTestBase() {

    @Test
    fun activeNotes_pinnedFirstThenNewestEdit() = runTest {
        insertNote("old", editedAt = 100)
        val pinned = insertNote("pinned", editedAt = 50)
        insertNote("new", editedAt = 200)
        notes.setPinned(listOf(pinned), true)

        assertEquals(listOf("pinned", "new", "old"), notes.observeActive().first().titles())
    }

    @Test
    fun bin_hidesFromActive_andRestoreKeepsPin() = runTest {
        val a = insertNote("a", editedAt = 100)
        insertNote("b", editedAt = 200)
        notes.setPinned(listOf(a), true)

        notes.moveToBin(listOf(a), 1000)
        assertEquals(listOf("b"), notes.observeActive().first().titles())
        assertEquals(listOf("a"), notes.observeBin().first().titles())

        notes.restore(listOf(a))
        val restored = notes.getById(a)!!
        assertFalse(restored.isBinned)
        assertTrue(restored.pinned)
        assertTrue(notes.observeBin().first().isEmpty())
    }

    @Test
    fun moveToBin_doesNotResetTimeOfAlreadyBinnedNote() = runTest {
        val a = insertNote("a")
        notes.moveToBin(listOf(a), 1000)
        notes.moveToBin(listOf(a), 2000)
        assertEquals(1000L, notes.getById(a)!!.binnedAt)
    }

    @Test
    fun deleteBinnedBefore_onlyRemovesOldBinnedNotes() = runTest {
        val oldBinned = insertNote("old binned")
        val newBinned = insertNote("new binned")
        val active = insertNote("active")
        notes.moveToBin(listOf(oldBinned), 1000)
        notes.moveToBin(listOf(newBinned), 5000)

        assertEquals(1, notes.deleteBinnedBefore(3000))
        assertNull(notes.getById(oldBinned))
        assertTrue(notes.getById(newBinned)!!.isBinned)
        assertFalse(notes.getById(active)!!.isBinned)
    }

    @Test
    fun clearBin_leavesActiveNotes() = runTest {
        val binned = insertNote("binned")
        val active = insertNote("active")
        notes.moveToBin(listOf(binned), 1000)

        assertEquals(1, notes.clearBin())
        assertNull(notes.getById(binned))
        assertEquals("active", notes.getById(active)!!.title)
    }

    @Test
    fun updateText_changesTextAndEditTimeOnly() = runTest {
        val id = insertNote("t", "body", editedAt = 100)
        notes.setPinned(listOf(id), true)

        notes.updateText(id, "t2", "<b>body2</b>", "body2", 900)

        val n = notes.getById(id)!!
        assertEquals("t2", n.title)
        assertEquals("<b>body2</b>", n.content)
        assertEquals("body2", n.plainText)
        assertEquals(900L, n.editedAt)
        assertEquals(100L, n.createdAt)
        assertTrue(n.pinned)
    }

    @Test
    fun lock_isPerNote_andSurvivesTextEdits_untilUnlockAll() = runTest {
        val a = insertNote("a")
        val b = insertNote("b")
        notes.setLocked(listOf(a), true)
        notes.updateText(a, "a2", "<p>x</p>", "x", 900)

        assertTrue(notes.getById(a)!!.locked)
        assertFalse(notes.getById(b)!!.locked)

        notes.setLocked(listOf(b), true)
        assertEquals(2, notes.unlockAll())
        assertFalse(notes.getById(a)!!.locked)
        assertFalse(notes.getById(b)!!.locked)
    }
}
