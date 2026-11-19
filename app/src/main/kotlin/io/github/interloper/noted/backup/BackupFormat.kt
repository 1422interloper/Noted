package io.github.interloper.noted.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The backup file: every note (binned ones included) and every label. The PIN and app settings are not included. */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long,
    val labels: List<BackupLabel> = emptyList(),
    val notes: List<BackupNote> = emptyList(),
) {
    companion object {
        const val FORMAT = "noted-backup"
        const val VERSION = 1
    }
}

@Serializable
data class BackupLabel(
    val name: String,
    val locked: Boolean = false,
    val folder: Boolean = false,
)

@Serializable
data class BackupNote(
    val title: String = "",
    val content: String = "",
    val plainText: String = "",
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val binnedAt: Long? = null,
    val createdAt: Long,
    val editedAt: Long,
    /** Label names; labels are matched by name on restore. */
    val labels: List<String> = emptyList(),
)

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCodec {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        // Lets an older app version read a backup written by a newer one that added fields.
        ignoreUnknownKeys = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** @throws BackupFormatException if the text is not a Noted backup this version can read. */
    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupFormatException("Not a Noted backup file", e)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("Not a Noted backup file", e)
        }
        if (file.format != BackupFile.FORMAT) throw BackupFormatException("Not a Noted backup file")
        if (file.version > BackupFile.VERSION) {
            throw BackupFormatException("Backup was made by a newer version of Noted")
        }
        return file
    }
}
