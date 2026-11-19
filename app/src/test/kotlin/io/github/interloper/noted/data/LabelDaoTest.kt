package io.github.interloper.noted.data

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LabelDaoTest : DaoTestBase() {

    private suspend fun labelNames() = labels.getAll().map { it.name }

    private suspend inline fun <reified E : Throwable> assertFails(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is E) return
            throw e
        }
        fail("Expected ${E::class.simpleName}")
    }

    @Test
    fun insert_appendsInOrder_andReorderPersists() = runTest {
        val a = insertLabel("a")
        val b = insertLabel("b")
        val c = insertLabel("c")
        assertEquals(listOf("a", "b", "c"), labelNames())

        labels.reorder(listOf(c, a, b))
        assertEquals(listOf("c", "a", "b"), labelNames())

        insertLabel("d")
        assertEquals(listOf("c", "a", "b", "d"), labelNames())
    }

    @Test
    fun duplicateName_isRejected() = runTest {
        insertLabel("a")
        val b = insertLabel("b")
        assertFails<SQLiteConstraintException> { insertLabel("a") }
        assertFails<SQLiteConstraintException> { labels.rename(b, "a") }
    }

    @Test
    fun rename_keepsNoteAssignments() = runTest {
        val note = insertNote("n")
        val label = insertLabel("before")
        labels.setLabelsForNote(note, listOf(label))

        labels.rename(label, "after")

        assertEquals(listOf("after"), notes.getWithLabels(note)!!.labels.map { it.name })
    }

    @Test
    fun setLabelsForNote_replacesPreviousSet() = runTest {
        val note = insertNote("n")
        val a = insertLabel("a")
        val b = insertLabel("b")
        val c = insertLabel("c")

        labels.setLabelsForNote(note, listOf(a, b))
        labels.setLabelsForNote(note, listOf(b, c))

        assertEquals(setOf(b, c), notes.getWithLabels(note)!!.labels.map { it.id }.toSet())

        labels.setLabelsForNote(note, emptyList())
        assertTrue(notes.getWithLabels(note)!!.labels.isEmpty())
    }

    @Test
    fun exclusiveLabels_cannotBeCombined_andFailedSetChangesNothing() = runTest {
        val note = insertNote("n")
        val plain = insertLabel("plain")
        val locked = insertLabel("locked", locked = true)
        val folder = insertLabel("folder", folder = true)
        labels.setLabelsForNote(note, listOf(plain))

        assertFails<IllegalArgumentException> { labels.setLabelsForNote(note, listOf(plain, locked)) }
        assertFails<IllegalArgumentException> { labels.setLabelsForNote(note, listOf(plain, folder)) }
        assertEquals(listOf(plain), notes.getWithLabels(note)!!.labels.map { it.id })

        labels.setLabelsForNote(note, listOf(locked))
        val loaded = notes.getWithLabels(note)!!
        assertTrue(loaded.hasLockedLabel)
        assertEquals(1, loaded.labels.size)
    }

    @Test
    fun deletingLabelOrNote_removesAssignments() = runTest {
        val note = insertNote("n")
        val a = insertLabel("a")
        val b = insertLabel("b")
        labels.setLabelsForNote(note, listOf(a, b))

        labels.delete(a)
        assertEquals(listOf(b), notes.getWithLabels(note)!!.labels.map { it.id })

        notes.delete(listOf(note))
        assertNotNull(labels.findByName("b"))
        val other = insertNote("other")
        assertTrue(notes.getWithLabels(other)!!.labels.isEmpty())
    }

    @Test
    fun unlockAll_makesLockedLabelsOrdinary_andKeepsTheirNotes() = runTest {
        val secret = insertLabel("secret", locked = true)
        val folder = insertLabel("folder", folder = true)
        val note = insertNote("n")
        labels.setLabelsForNote(note, listOf(secret))

        assertEquals(1, labels.unlockAll())

        val all = labels.getAll().associateBy { it.id }
        assertFalse(all.getValue(secret).locked)
        assertTrue(all.getValue(folder).folder)
        assertEquals(listOf("secret"), notes.getWithLabels(note)!!.labels.map { it.name })
    }

    @Test
    fun deleteAllUnlocked_keepsLockedLabels() = runTest {
        insertLabel("plain")
        insertLabel("folder", folder = true)
        insertLabel("secret", locked = true)

        assertEquals(2, labels.deleteAllUnlocked())
        assertEquals(listOf("secret"), labelNames())
    }
}
