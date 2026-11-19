package io.github.interloper.noted.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.interloper.noted.data.AppDatabase
import io.github.interloper.noted.data.Label
import io.github.interloper.noted.data.Note
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {

    private val databases = mutableListOf<AppDatabase>()

    private fun newDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { databases += it }

    @After
    fun tearDown() = databases.forEach { it.close() }

    private suspend fun seed(db: AppDatabase) {
        val work = db.labelDao().insert(Label(name = "work"))
        val home = db.labelDao().insert(Label(name = "home"))
        val secret = db.labelDao().insert(Label(name = "secret", locked = true))
        val a = db.noteDao().insert(Note(title = "A", content = "<p>a</p>", plainText = "a", pinned = true, createdAt = 10, editedAt = 11))
        val b = db.noteDao().insert(Note(title = "B", content = "<p>b</p>", plainText = "b", locked = true, createdAt = 20, editedAt = 21))
        val c = db.noteDao().insert(Note(title = "C", content = "<p>c</p>", plainText = "c", binnedAt = 99, createdAt = 30, editedAt = 31))
        db.labelDao().setLabelsForNote(a, listOf(work, home))
        db.labelDao().setLabelsForNote(b, listOf(secret))
        db.labelDao().reorder(listOf(secret, home, work))
        check(c > 0)
    }

    @Test
    fun exportThenRestoreIntoEmptyApp_reproducesNotesLabelsAndAssignments() = runTest {
        val source = newDb().also { seed(it) }
        val text = BackupManager(source) { 1234 }.export()

        val target = newDb()
        val result = BackupManager(target).restore(text)

        assertEquals(RestoreResult(notesInFile = 3, notesAdded = 3, notesSkipped = 0, labelsAdded = 3), result)
        assertEquals(listOf("secret", "home", "work"), target.labelDao().getAll().map { it.name })
        assertTrue(target.labelDao().findByName("secret")!!.locked)

        val notes = target.noteDao().getAllWithLabels().associateBy { it.note.title }
        assertEquals(setOf("A", "B", "C"), notes.keys)
        val a = notes.getValue("A")
        assertTrue(a.note.pinned)
        assertFalse(a.note.locked)
        assertTrue(notes.getValue("B").note.locked)
        assertEquals(10L, a.note.createdAt)
        assertEquals(11L, a.note.editedAt)
        assertEquals("<p>a</p>", a.note.content)
        assertEquals(setOf("work", "home"), a.labels.map { it.name }.toSet())
        assertEquals(listOf("secret"), notes.getValue("B").labels.map { it.name })
        assertEquals(99L, notes.getValue("C").note.binnedAt)
    }

    @Test
    fun restoringTwice_addsNothingTheSecondTime() = runTest {
        val source = newDb().also { seed(it) }
        val text = BackupManager(source).export()

        val second = BackupManager(source).restore(text)

        assertEquals(RestoreResult(notesInFile = 3, notesAdded = 0, notesSkipped = 3, labelsAdded = 0), second)
        assertEquals(3, source.noteDao().getAllWithLabels().size)
        assertEquals(3, source.labelDao().getAll().size)
    }

    @Test
    fun restore_keepsExistingNotes_andAddsEditedCopyAsNewNote() = runTest {
        val source = newDb().also { seed(it) }
        val text = BackupManager(source).export()

        val target = newDb()
        target.noteDao().insert(Note(title = "Mine", createdAt = 1, editedAt = 1))
        // Same creation time and title as backup note A, but different content: both versions are kept.
        target.noteDao().insert(Note(title = "A", content = "<p>changed</p>", createdAt = 10, editedAt = 50))

        val result = BackupManager(target).restore(text)

        assertEquals(3, result.notesAdded)
        assertEquals(listOf("Mine", "A", "A", "B", "C"), target.noteDao().getAllWithLabels().map { it.note.title })
    }

    @Test
    fun restore_respectsLocalLabelFlags_andKeepsExclusivityRule() = runTest {
        val target = newDb()
        // Locally "work" is a folder label; the backup has it as a plain label combined with "home".
        target.labelDao().insert(Label(name = "work", folder = true))
        val text = BackupCodec.encode(
            BackupFile(
                exportedAt = 1,
                labels = listOf(BackupLabel("work"), BackupLabel("home")),
                notes = listOf(BackupNote(title = "n", createdAt = 1, editedAt = 1, labels = listOf("home", "work"))),
            ),
        )

        val result = BackupManager(target).restore(text)

        assertEquals(1, result.labelsAdded)
        assertTrue(target.labelDao().findByName("work")!!.folder)
        assertEquals(listOf("work"), target.noteDao().getAllWithLabels().single().labels.map { it.name })
    }

    @Test
    fun restore_createsLabelsThatOnlyAppearOnNotes() = runTest {
        val target = newDb()
        val text = BackupCodec.encode(
            BackupFile(
                exportedAt = 1,
                notes = listOf(BackupNote(title = "n", createdAt = 1, editedAt = 1, labels = listOf("orphan"))),
            ),
        )

        BackupManager(target).restore(text)

        assertFalse(target.labelDao().findByName("orphan")!!.isExclusive)
        assertEquals(listOf("orphan"), target.noteDao().getAllWithLabels().single().labels.map { it.name })
    }

    @Test
    fun restore_ofBadFile_changesNothing() = runTest {
        val target = newDb().also { seed(it) }
        try {
            BackupManager(target).restore("""[{"noteID":"1_note"}]""")
            fail("expected BackupFormatException")
        } catch (_: BackupFormatException) {
        }
        assertEquals(3, target.noteDao().getAllWithLabels().size)
    }
}
