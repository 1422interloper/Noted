package io.github.interloper.noted.backup

import androidx.room.withTransaction
import io.github.interloper.noted.data.AppDatabase
import io.github.interloper.noted.data.Label
import io.github.interloper.noted.data.Note

data class RestoreResult(val notesInFile: Int, val notesAdded: Int, val notesSkipped: Int, val labelsAdded: Int)

class BackupManager(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun export(): String {
        val labels = db.labelDao().getAll()
        val notes = db.noteDao().getAllWithLabels()
        return BackupCodec.encode(
            BackupFile(
                exportedAt = clock(),
                labels = labels.map { BackupLabel(it.name, it.locked, it.folder) },
                notes = notes.map { n ->
                    BackupNote(
                        title = n.note.title,
                        content = n.note.content,
                        plainText = n.note.plainText,
                        pinned = n.note.pinned,
                        locked = n.note.locked,
                        binnedAt = n.note.binnedAt,
                        createdAt = n.note.createdAt,
                        editedAt = n.note.editedAt,
                        labels = n.labels.sortedBy { it.position }.map { it.name },
                    )
                },
            ),
        )
    }

    /**
     * Merges a backup into the current data; nothing existing is changed or removed.
     *
     * - Labels are matched by name. Missing ones are created after the existing labels; an existing label keeps its
     *   current locked/folder flags.
     * - A note is skipped when one with the same creation time, title and content already exists, so restoring the
     *   same file twice adds nothing.
     *
     * Runs in one transaction: a bad file leaves the data untouched.
     *
     * @throws BackupFormatException if [text] is not a readable Noted backup.
     */
    suspend fun restore(text: String): RestoreResult {
        val file = BackupCodec.decode(text)
        return db.withTransaction {
            val labelDao = db.labelDao()
            val noteDao = db.noteDao()

            val labelsByName = labelDao.getAll().associateBy { it.name }.toMutableMap()
            var labelsAdded = 0
            suspend fun labelFor(name: String, locked: Boolean = false, folder: Boolean = false): Label =
                labelsByName[name] ?: run {
                    val created = Label(name = name, locked = locked, folder = folder)
                    val id = labelDao.insert(created)
                    labelsAdded++
                    created.copy(id = id).also { labelsByName[name] = it }
                }
            file.labels.forEach { labelFor(it.name, it.locked, it.folder) }

            val existing = noteDao.getAllWithLabels()
                .mapTo(HashSet()) { Triple(it.note.createdAt, it.note.title, it.note.content) }
            var added = 0
            var skipped = 0
            for (n in file.notes) {
                if (!existing.add(Triple(n.createdAt, n.title, n.content))) {
                    skipped++
                    continue
                }
                val id = noteDao.insert(
                    Note(
                        title = n.title,
                        content = n.content,
                        plainText = n.plainText,
                        pinned = n.pinned,
                        locked = n.locked,
                        binnedAt = n.binnedAt,
                        createdAt = n.createdAt,
                        editedAt = n.editedAt,
                    ),
                )
                // A label named in a note but missing from the file's label list is created as a plain label.
                val labels = n.labels.distinct().map { labelFor(it) }
                // Local flags may differ from the backup's; keep the exclusivity rule by preferring the exclusive label.
                val exclusive = labels.firstOrNull { it.isExclusive }
                val assign = if (exclusive != null && labels.size > 1) listOf(exclusive) else labels
                labelDao.setLabelsForNote(id, assign.map { it.id })
                added++
            }
            RestoreResult(notesInFile = file.notes.size, notesAdded = added, notesSkipped = skipped, labelsAdded = labelsAdded)
        }
    }
}
