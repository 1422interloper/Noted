package io.github.interloper.noted.domain

import io.github.interloper.noted.data.Label
import io.github.interloper.noted.data.Note
import io.github.interloper.noted.data.NoteWithLabels
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteFilterTest {

    private val work = Label(id = 1, name = "work")
    private val secret = Label(id = 2, name = "secret", locked = true)
    private val archive = Label(id = 3, name = "archive", folder = true)

    private fun note(title: String, body: String = "", vararg labels: Label, binned: Boolean = false) =
        NoteWithLabels(Note(title = title, plainText = body, binnedAt = if (binned) 1L else null), labels.toList())

    private val plain = note("Plain", "milk")
    private val worked = note("Worked", "Milk shake", work)
    private val locked = note("Locked", "milk diary", secret)
    private val foldered = note("Foldered", "old milk", archive)
    private val active = listOf(plain, worked, locked, foldered)

    private val binnedPlain = note("Binned", "milk", binned = true)
    private val binnedLocked = note("Binned locked", "milk", secret, binned = true)
    private val bin = listOf(binnedPlain, binnedLocked)

    private fun titles(filter: NoteFilter, query: String = "", unlocked: Boolean = false) =
        visibleNotes(active, bin, filter, query, unlocked).map { it.note.title }

    @Test
    fun all_hidesLockedAndFolderNotes_evenWhenUnlocked() {
        assertEquals(listOf("Plain", "Worked"), titles(NoteFilter.All))
        assertEquals(listOf("Plain", "Worked"), titles(NoteFilter.All, unlocked = true))
    }

    @Test
    fun byLabel_showsOnlyThatLabel() {
        assertEquals(listOf("Worked"), titles(NoteFilter.ByLabel(work.id)))
        assertEquals(listOf("Foldered"), titles(NoteFilter.ByLabel(archive.id)))
    }

    @Test
    fun lockedLabel_isEmptyUntilUnlocked() {
        assertEquals(emptyList<String>(), titles(NoteFilter.ByLabel(secret.id)))
        assertEquals(listOf("Locked"), titles(NoteFilter.ByLabel(secret.id), unlocked = true))
    }

    @Test
    fun bin_hidesLockedNotesUntilUnlocked() {
        assertEquals(listOf("Binned"), titles(NoteFilter.Bin))
        assertEquals(listOf("Binned", "Binned locked"), titles(NoteFilter.Bin, unlocked = true))
    }

    @Test
    fun search_ignoresCaseAndSelectedLabel_includesFolders_excludesLocked() {
        assertEquals(listOf("Plain", "Worked", "Foldered"), titles(NoteFilter.All, "MILK"))
        assertEquals(listOf("Plain", "Worked", "Foldered"), titles(NoteFilter.ByLabel(work.id), "milk"))
        assertEquals(listOf("Plain", "Worked", "Locked", "Foldered"), titles(NoteFilter.All, "milk", unlocked = true))
    }

    @Test
    fun search_matchesTitle_andTrimsQuery() {
        assertEquals(listOf("Worked"), titles(NoteFilter.All, "  worked "))
    }

    @Test
    fun search_inBin_onlySearchesBin() {
        assertEquals(listOf("Binned"), titles(NoteFilter.Bin, "milk"))
        assertEquals(emptyList<String>(), titles(NoteFilter.Bin, "shake"))
    }

    @Test
    fun lockedNote_isListed_butItsTextIsOnlySearchableOnceUnlocked() {
        val sealed = NoteWithLabels(Note(title = "Diary", plainText = "secret milk", locked = true), emptyList())
        val notes = listOf(plain, sealed)
        fun found(query: String, unlocked: Boolean) =
            visibleNotes(notes, emptyList(), NoteFilter.All, query, unlocked).map { it.note.title }

        assertEquals(listOf("Plain", "Diary"), found("", unlocked = false))
        assertEquals(listOf("Plain"), found("milk", unlocked = false))
        assertEquals(listOf("Diary"), found("diary", unlocked = false))
        assertEquals(listOf("Plain", "Diary"), found("milk", unlocked = true))
    }
}
