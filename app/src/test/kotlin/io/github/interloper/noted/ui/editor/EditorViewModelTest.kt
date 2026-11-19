package io.github.interloper.noted.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.github.interloper.noted.data.DaoTestBase
import io.github.interloper.noted.data.NotesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

/**
 * Runs against a real in-memory database on real threads; saves are awaited through the database's own flows. A
 * single-threaded dispatcher stands in for the main thread, and the test drives the ViewModel on it, as the UI would.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest : DaoTestBase() {

    private var now = 1_000L
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val store = ViewModelStore()
    private lateinit var repository: NotesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(mainThread)
        repository = NotesRepository(db) { now }
    }

    @After
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
        mainThread.close()
    }

    private fun open(noteId: Long?, labelId: Long? = null): EditorViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EditorViewModel(repository, appScope, noteId, labelId, saveDelayMillis = 0) as T
        }
        val vm = onMain { ViewModelProvider(store, factory)[EditorViewModel::class.java] }
        runBlocking { withTimeout(5_000) { vm.load.first { it != EditorLoad.Loading } } }
        return vm
    }

    private fun <T> onMain(block: () -> T): T = runBlocking(mainThread) { block() }

    /** Stands in for the screen filling its editor from the loaded note. */
    private fun EditorViewModel.seed() = onMain {
        val ready = load.value as EditorLoad.Ready
        onEditorInitialised(ready.content, ready.content.replace(Regex("<[^>]+>"), ""))
    }

    private fun EditorViewModel.type(title: String? = null, html: String? = null, text: String = "") = onMain {
        title?.let { onTitleChanged(it) }
        html?.let { onBodyChanged(it, text) }
    }

    private fun close() = onMain { store.clear() }

    private fun awaitNotes(count: Int) = runBlocking {
        withTimeout(5_000) { notes.observeActive().first { it.size == count } }
    }

    @Test
    fun newNote_isCreatedOnFirstEdit_withTheListLabel() = runBlocking {
        val label = insertLabel("work")
        val vm = open(noteId = null, labelId = label)
        vm.seed()
        assertTrue(notes.observeActive().first().isEmpty())

        // Two edits in quick succession: the second lands while the first is still being written.
        vm.type(title = " Shopping ")
        vm.type(html = "<p>milk</p>", text = "milk")

        val saved = withTimeout(5_000) {
            notes.observeActive().first { it.size == 1 && it[0].note.plainText == "milk" && it[0].labels.isNotEmpty() }
        }.single()
        assertEquals("Shopping", saved.note.title)
        assertEquals("<p>milk</p>", saved.note.content)
        assertEquals(listOf("work"), saved.labels.map { it.name })
        assertEquals(saved.note.id, vm.noteId.value)
    }

    @Test
    fun newNote_leftBlank_isNeverStored() = runBlocking {
        val vm = open(noteId = null)
        vm.seed()
        vm.type(title = "   ", html = "<p> </p>", text = " ")
        close()

        // Give the closing save a chance to run before checking that it wrote nothing.
        insertNote("marker")
        assertEquals(listOf("marker"), awaitNotes(1).map { it.note.title })
        assertNull(vm.noteId.value)
    }

    @Test
    fun openingWithoutEditing_doesNotTouchTheNote() = runBlocking {
        val id = insertNote("kept", "text", editedAt = 500)
        val vm = open(id)
        // The editor reports its own rendering of the stored HTML, which becomes the baseline.
        onMain { vm.onEditorInitialised("<p>text</p><p></p>", "text") }
        vm.type(html = "<p>text</p><p></p>", text = "text")
        onMain { vm.flush() }
        close()

        insertNote("marker")
        awaitNotes(2)
        val note = notes.getById(id)!!
        assertEquals(500L, note.editedAt)
        assertEquals("<p>text</p>", note.content)
    }

    @Test
    fun editsBeforeTheEditorIsFilled_areIgnored() = runBlocking {
        val id = insertNote("kept", "text", editedAt = 500)
        val vm = open(id)
        vm.type(html = "<p></p>")
        onMain { vm.flush() }
        close()

        insertNote("marker")
        awaitNotes(2)
        assertEquals("text", notes.getById(id)!!.plainText)
    }

    @Test
    fun editingExistingNote_savesTextAndEditTime() = runBlocking {
        val id = insertNote("old", "text", editedAt = 500)
        val vm = open(id)
        vm.seed()
        now = 2_000

        vm.type(html = "<p>changed</p>", text = "changed")

        val saved = withTimeout(5_000) { notes.observeById(id).first { it?.note?.plainText == "changed" } }!!
        assertEquals(2_000L, saved.note.editedAt)
        assertEquals("old", saved.note.title)
        assertEquals("<p>changed</p>", vm.bodyHtml)
    }

    @Test
    fun noteEmptiedThenClosed_isDeleted() = runBlocking {
        val id = insertNote("gone", "text")
        val vm = open(id)
        vm.seed()
        vm.type(title = "", html = "<p></p>")
        close()

        withTimeout(5_000) { notes.observeById(id).first { it == null } }
        assertNull(notes.getById(id))
    }

    @Test
    fun binnedNote_isReadOnly() = runBlocking {
        val id = insertNote("binned", "text", editedAt = 500)
        notes.moveToBin(listOf(id), 600)
        val vm = open(id)
        assertTrue((vm.load.value as EditorLoad.Ready).binned)
        vm.seed()

        vm.type(title = "", html = "<p></p>")
        close()

        insertNote("marker")
        awaitNotes(1)
        val note = notes.getById(id)!!
        assertEquals("binned", note.title)
        assertEquals("text", note.plainText)
    }

    @Test
    fun rapidEdits_neverDuplicateANewNote_andTheLastOneWins() = runBlocking {
        val vm = open(noteId = null)
        vm.seed()
        repeat(50) { i -> vm.type(html = "<p>v$i</p>", text = "v$i") }

        withTimeout(5_000) { notes.observeActive().first { it.isNotEmpty() && it.all { n -> n.note.plainText == "v49" } } }
        assertEquals(1, notes.observeActive().first().size)
    }

    @Test
    fun newNote_pinAndLabelsChosenBeforeFirstSave_areAppliedWhenItIsCreated() = runBlocking {
        val listLabel = insertLabel("from list")
        val chosen = insertLabel("chosen")
        val deleted = insertLabel("deleted meanwhile")
        val vm = open(noteId = null, labelId = listLabel)
        vm.seed()

        onMain {
            vm.togglePin()
            vm.setLocked(true)
            vm.setLabels(setOf(chosen, deleted))
        }
        assertTrue(withTimeout(5_000) { vm.pinned.first { it } })
        assertEquals(setOf(chosen, deleted), withTimeout(5_000) { vm.labelIds.first { it.size == 2 } })
        // Nothing is stored for a note with no text, whatever its pin and labels.
        assertTrue(notes.observeActive().first().isEmpty())

        labels.delete(deleted)
        vm.type(html = "<p>text</p>", text = "text")

        val saved = withTimeout(5_000) { notes.observeActive().first { it.size == 1 && it[0].labels.isNotEmpty() } }.single()
        assertTrue(saved.note.pinned)
        assertTrue(withTimeout(5_000) { notes.observeById(saved.note.id).first { it?.note?.locked == true } } != null)
        assertEquals(listOf("chosen"), saved.labels.map { it.name })
    }

    @Test
    fun newEmptyNote_movedToBin_binsNothing() = runBlocking {
        val vm = open(noteId = null)
        vm.seed()
        var result: Long? = -1
        onMain { vm.moveToBin { result = it } }
        withTimeout(5_000) { while (result == -1L) kotlinx.coroutines.delay(10) }
        assertNull(result)
        assertTrue(notes.observeBin().first().isEmpty())
    }

    @Test
    fun missingNote_reportsMissing() {
        val vm = open(noteId = 42)
        assertEquals(EditorLoad.Missing, vm.load.value)
    }

    @Test
    fun moveToBin_savesPendingEditsFirst() = runBlocking {
        val id = insertNote("note", "text")
        val vm = open(id)
        vm.seed()
        vm.type(html = "<p>last words</p>", text = "last words")
        var done = false
        onMain { vm.moveToBin { done = true } }

        val binned = withTimeout(5_000) { notes.observeBin().first { it.size == 1 } }.single()
        assertEquals("last words", binned.note.plainText)
        withTimeout(5_000) { while (!done) kotlinx.coroutines.delay(10) }
        assertFalse(notes.observeBin().first().isEmpty())
    }
}
