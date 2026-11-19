package io.github.interloper.noted.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Reads and writes backup text through the Storage Access Framework. */
object BackupFiles {

    const val MIME_TYPE = "application/json"

    suspend fun write(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        // "wt" truncates, so a shorter backup never leaves the tail of a longer one behind.
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** Creates or overwrites [AutoBackup.FILE_NAME] in the folder the user picked. */
    suspend fun writeToFolder(context: Context, folderUri: String, text: String) = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
            ?.takeIf { it.canWrite() }
            ?: throw IOException("Backup folder is not writable")
        val file = folder.findFile(AutoBackup.FILE_NAME)
            ?: folder.createFile(MIME_TYPE, AutoBackup.FILE_NAME)
            ?: throw IOException("Cannot create backup file")
        write(context, file.uri, text)
    }

    /** Display name of a picked folder, for the settings row. */
    fun folderName(context: Context, folderUri: String): String? =
        DocumentFile.fromTreeUri(context, Uri.parse(folderUri))?.name
}
