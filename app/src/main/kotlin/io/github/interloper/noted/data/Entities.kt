package io.github.interloper.noted.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "notes", indices = [Index("binned_at")])
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Plain text. */
    val title: String = "",
    /** Rich-text body, stored as HTML. */
    val content: String = "",
    /** [content] with markup stripped; used for search and list previews. */
    @ColumnInfo(name = "plain_text") val plainText: String = "",
    val pinned: Boolean = false,
    /**
     * Epoch millis when the note was moved to the bin, or null if it is not binned. The pinned flag is left alone
     * while a note is binned, so restoring brings the pin back.
     */
    @ColumnInfo(name = "binned_at") val binnedAt: Long? = null,
    /**
     * Locked with the PIN: the list shows only the title, and opening the note, unlocking it, binning it or deleting
     * it needs the PIN or fingerprint.
     */
    @ColumnInfo(defaultValue = "0") val locked: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0,
    @ColumnInfo(name = "edited_at") val editedAt: Long = 0,
) {
    val isBinned: Boolean get() = binnedAt != null
}

@Entity(tableName = "labels", indices = [Index(value = ["name"], unique = true)])
data class Label(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Notes with a locked label are hidden until the user unlocks with PIN or biometrics. */
    val locked: Boolean = false,
    /** Notes with a folder label are only shown while that label is selected. */
    val folder: Boolean = false,
    /** Sort order in the label strip and the labels screen; lower comes first. */
    val position: Int = 0,
) {
    /** Locked and folder labels are exclusive: a note carrying one cannot carry any other label. */
    val isExclusive: Boolean get() = locked || folder
}

/** Join row: one label assigned to one note. Rows disappear with either side. */
@Entity(
    tableName = "note_labels",
    primaryKeys = ["note_id", "label_id"],
    foreignKeys = [
        ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Label::class, parentColumns = ["id"], childColumns = ["label_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("label_id")],
)
data class NoteLabel(
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "label_id") val labelId: Long,
)

data class NoteWithLabels(
    @Embedded val note: Note,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(value = NoteLabel::class, parentColumn = "note_id", entityColumn = "label_id"),
    )
    val labels: List<Label>,
) {
    val hasLockedLabel: Boolean get() = labels.any { it.locked }
    /** True if the note's text may only be shown after unlocking, through its own lock or a locked label. */
    val isProtected: Boolean get() = note.locked || hasLockedLabel
    val hasFolderLabel: Boolean get() = labels.any { it.folder }
    fun hasLabel(labelId: Long): Boolean = labels.any { it.id == labelId }
}
