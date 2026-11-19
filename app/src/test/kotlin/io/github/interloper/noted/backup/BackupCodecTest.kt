package io.github.interloper.noted.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val sample = BackupFile(
        exportedAt = 123,
        labels = listOf(BackupLabel("work"), BackupLabel("secret", locked = true)),
        notes = listOf(
            BackupNote(
                title = "Title \"quoted\"",
                content = "<p>Body <b>bold</b> &amp; émoji 🎉</p>",
                plainText = "Body bold & émoji 🎉",
                pinned = true,
                binnedAt = 55,
                createdAt = 1,
                editedAt = 2,
                labels = listOf("work"),
            ),
        ),
    )

    @Test
    fun roundTrip_preservesEverything() {
        assertEquals(sample, BackupCodec.decode(BackupCodec.encode(sample)))
    }

    @Test
    fun encode_writesFormatMarkerAndVersion() {
        val text = BackupCodec.encode(sample)
        assertTrue(text.contains("\"format\": \"noted-backup\""))
        assertTrue(text.contains("\"version\": 1"))
    }

    @Test
    fun decode_toleratesUnknownFieldsAndMissingOptionalOnes() {
        val text = """
            {"format":"noted-backup","version":1,"exportedAt":5,"futureField":true,
             "notes":[{"title":"t","createdAt":1,"editedAt":2,"mystery":[1,2]}]}
        """.trimIndent()
        val file = BackupCodec.decode(text)
        assertEquals(1, file.notes.size)
        assertEquals("t", file.notes[0].title)
        assertEquals(emptyList<String>(), file.notes[0].labels)
        assertEquals(emptyList<BackupLabel>(), file.labels)
    }

    @Test
    fun decode_rejectsOtherJsonAndGarbage() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("[]") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("{}") }
        // A NotesMaster export is a bare array of notes; it is not accepted.
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode("""[{"noteID":"1_note","title":"a","content":"b"}]""")
        }
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode("""{"format":"something-else","version":1,"exportedAt":1}""")
        }
    }

    @Test
    fun decode_rejectsNewerVersion() {
        val e = assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode("""{"format":"noted-backup","version":2,"exportedAt":1}""")
        }
        assertTrue(e.message!!.contains("newer"))
    }
}
