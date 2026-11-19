package io.github.interloper.noted.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
abstract class DaoTestBase {

    protected lateinit var db: AppDatabase
    protected lateinit var notes: NoteDao
    protected lateinit var labels: LabelDao

    @Before
    fun openDb() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        notes = db.noteDao()
        labels = db.labelDao()
    }

    @After
    fun closeDb() = db.close()

    protected suspend fun insertNote(title: String, plainText: String = "", editedAt: Long = 0): Long =
        notes.insert(
            Note(title = title, content = "<p>$plainText</p>", plainText = plainText, createdAt = editedAt, editedAt = editedAt),
        )

    protected suspend fun insertLabel(name: String, locked: Boolean = false, folder: Boolean = false): Long =
        labels.insert(Label(name = name, locked = locked, folder = folder))

    protected fun List<NoteWithLabels>.titles(): List<String> = map { it.note.title }
}
