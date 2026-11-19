package io.github.interloper.noted.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert
    suspend fun insert(note: Note): Long

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): Note?

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getWithLabels(id: Long): NoteWithLabels?

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: Long): Flow<NoteWithLabels?>

    /** Notes outside the bin: pinned first, then most recently edited. */
    @Transaction
    @Query("SELECT * FROM notes WHERE binned_at IS NULL ORDER BY pinned DESC, edited_at DESC, id DESC")
    fun observeActive(): Flow<List<NoteWithLabels>>

    /** Binned notes, most recently binned first. */
    @Transaction
    @Query("SELECT * FROM notes WHERE binned_at IS NOT NULL ORDER BY binned_at DESC, id DESC")
    fun observeBin(): Flow<List<NoteWithLabels>>

    /** Every note, binned or not, oldest first; used for backups. */
    @Transaction
    @Query("SELECT * FROM notes ORDER BY created_at, id")
    suspend fun getAllWithLabels(): List<NoteWithLabels>

    /** Updates only the text columns, so an autosave never overwrites a concurrent pin or bin change. */
    @Query("UPDATE notes SET title = :title, content = :content, plain_text = :plainText, edited_at = :editedAt WHERE id = :id")
    suspend fun updateText(id: Long, title: String, content: String, plainText: String, editedAt: Long)

    @Query("UPDATE notes SET pinned = :pinned WHERE id IN (:ids)")
    suspend fun setPinned(ids: Collection<Long>, pinned: Boolean)

    @Query("UPDATE notes SET locked = :locked WHERE id IN (:ids)")
    suspend fun setLocked(ids: Collection<Long>, locked: Boolean)

    /** Removes the lock from every note; used when the PIN is removed. */
    @Query("UPDATE notes SET locked = 0 WHERE locked = 1")
    suspend fun unlockAll(): Int

    @Query("UPDATE notes SET binned_at = :now WHERE id IN (:ids) AND binned_at IS NULL")
    suspend fun moveToBin(ids: Collection<Long>, now: Long)

    @Query("UPDATE notes SET binned_at = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: Collection<Long>)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun delete(ids: Collection<Long>)

    @Query("DELETE FROM notes WHERE binned_at IS NOT NULL")
    suspend fun clearBin(): Int

    /** Permanently deletes notes binned before [cutoff]; backs the auto-clear-bin setting. */
    @Query("DELETE FROM notes WHERE binned_at IS NOT NULL AND binned_at < :cutoff")
    suspend fun deleteBinnedBefore(cutoff: Long): Int
}
