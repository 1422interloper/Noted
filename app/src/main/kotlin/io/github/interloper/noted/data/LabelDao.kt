package io.github.interloper.noted.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {

    @Insert
    suspend fun insertRaw(label: Label): Long

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM labels")
    suspend fun nextPosition(): Int

    /** Appends the label after all existing ones. Throws SQLiteConstraintException if the name is taken. */
    @Transaction
    suspend fun insert(label: Label): Long = insertRaw(label.copy(position = nextPosition()))

    @Query("SELECT * FROM labels ORDER BY position, id")
    fun observeAll(): Flow<List<Label>>

    @Query("SELECT * FROM labels ORDER BY position, id")
    suspend fun getAll(): List<Label>

    @Query("SELECT * FROM labels WHERE name = :name")
    suspend fun findByName(name: String): Label?

    /** Throws SQLiteConstraintException if the name is taken. */
    @Query("UPDATE labels SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE labels SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    /** Persists a drag-reorder: labels take the position of their index in [orderedIds]. */
    @Transaction
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setPosition(id, index) }
    }

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun delete(id: Long)

    /** "Delete all labels" leaves locked labels alone; those need the PIN to remove. */
    @Query("DELETE FROM labels WHERE locked = 0")
    suspend fun deleteAllUnlocked(): Int

    /** Turns every locked label into an ordinary one; used when the PIN is removed. */
    @Query("UPDATE labels SET locked = 0 WHERE locked = 1")
    suspend fun unlockAll(): Int

    @Query("SELECT * FROM labels WHERE id IN (:ids)")
    suspend fun getByIds(ids: Collection<Long>): List<Label>

    @Query("DELETE FROM note_labels WHERE note_id = :noteId")
    suspend fun clearNoteLabels(noteId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteLabels(rows: List<NoteLabel>)

    /**
     * Replaces the note's labels. Throws IllegalArgumentException if the set breaks the exclusivity rule (a locked or
     * folder label must be the note's only label).
     */
    @Transaction
    suspend fun setLabelsForNote(noteId: Long, labelIds: Collection<Long>) {
        val ids = labelIds.toSet()
        if (ids.size > 1) {
            getByIds(ids).firstOrNull { it.isExclusive }?.let {
                throw IllegalArgumentException("Label '${it.name}' cannot be combined with others")
            }
        }
        clearNoteLabels(noteId)
        insertNoteLabels(ids.map { NoteLabel(noteId, it) })
    }
}
