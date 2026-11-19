package io.github.interloper.noted.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/** Single entry point to stored notes and labels. */
class NotesRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val notes = db.noteDao()
    private val labels = db.labelDao()

    // ---- Notes ----

    fun observeActiveNotes(): Flow<List<NoteWithLabels>> = notes.observeActive()

    fun observeBin(): Flow<List<NoteWithLabels>> = notes.observeBin()

    fun observeNote(id: Long): Flow<NoteWithLabels?> = notes.observeById(id)

    suspend fun getNote(id: Long): NoteWithLabels? = notes.getWithLabels(id)

    /** Inserts a new note stamped with the current time and returns its id. */
    suspend fun createNote(title: String, content: String, plainText: String): Long {
        val now = clock()
        return notes.insert(Note(title = title, content = content, plainText = plainText, createdAt = now, editedAt = now))
    }

    suspend fun saveNoteText(id: Long, title: String, content: String, plainText: String) =
        notes.updateText(id, title, content, plainText, clock())

    suspend fun setPinned(ids: Collection<Long>, pinned: Boolean) = notes.setPinned(ids, pinned)

    suspend fun setLocked(ids: Collection<Long>, locked: Boolean) = notes.setLocked(ids, locked)

    /** Removes every note lock and makes every locked label ordinary, so nothing stays sealed without a PIN. */
    suspend fun removeAllLocks() = db.withTransaction {
        notes.unlockAll()
        labels.unlockAll()
    }

    suspend fun moveToBin(ids: Collection<Long>) = notes.moveToBin(ids, clock())

    suspend fun restoreFromBin(ids: Collection<Long>) = notes.restore(ids)

    suspend fun deletePermanently(ids: Collection<Long>) = notes.delete(ids)

    suspend fun clearBin(): Int = notes.clearBin()

    /** Permanently deletes notes that have been in the bin longer than [maxAgeMillis]. */
    suspend fun purgeBinOlderThan(maxAgeMillis: Long): Int = notes.deleteBinnedBefore(clock() - maxAgeMillis)

    // ---- Labels ----

    fun observeLabels(): Flow<List<Label>> = labels.observeAll()

    /** Creates a label and returns its id, or null if the name is already in use. */
    suspend fun createLabel(name: String, locked: Boolean, folder: Boolean): Long? {
        if (labels.findByName(name) != null) return null
        return labels.insert(Label(name = name, locked = locked, folder = folder))
    }

    /** Renames a label; returns false if another label already has that name. */
    suspend fun renameLabel(id: Long, name: String): Boolean {
        val existing = labels.findByName(name)
        if (existing != null && existing.id != id) return false
        labels.rename(id, name)
        return true
    }

    suspend fun reorderLabels(orderedIds: List<Long>) = labels.reorder(orderedIds)

    suspend fun deleteLabel(id: Long) = labels.delete(id)

    suspend fun deleteAllUnlockedLabels(): Int = labels.deleteAllUnlocked()

    suspend fun setLabelsForNote(noteId: Long, labelIds: Collection<Long>) = labels.setLabelsForNote(noteId, labelIds)
}
