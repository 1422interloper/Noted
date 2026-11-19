package io.github.interloper.noted.backup

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AutoBackupTest {

    private val target = MutableStateFlow<String?>(null)
    private val changes = MutableStateFlow(0)
    private val written = mutableListOf<Pair<String, String>>()
    private val errors = mutableListOf<Exception>()
    private var failWrites = false

    private fun autoBackup() = AutoBackup(
        target = target,
        changes = changes,
        export = { "backup#${changes.value}" },
        write = { folder, text ->
            if (failWrites) throw IOException("folder gone")
            written += folder to text
        },
        onError = { errors += it },
        debounceMillis = 3000,
    )

    @Test
    fun writesNothingWhileOff() = runTest {
        val job = autoBackup().start(backgroundScope)
        changes.value = 1
        advanceTimeBy(10_000)
        assertEquals(emptyList<Pair<String, String>>(), written)
        job.cancel()
    }

    @Test
    fun writesOnceAfterChangesSettle() = runTest {
        target.value = "content://folder"
        val job = autoBackup().start(backgroundScope)
        runCurrent()

        changes.value = 1
        advanceTimeBy(2000)
        changes.value = 2
        advanceTimeBy(2000)
        changes.value = 3
        assertEquals(emptyList<Pair<String, String>>(), written)

        advanceTimeBy(3001)
        assertEquals(listOf("content://folder" to "backup#3"), written)
        job.cancel()
    }

    @Test
    fun turningOn_writesCurrentData_andTurningOffStops() = runTest {
        val job = autoBackup().start(backgroundScope)
        advanceTimeBy(5000)
        assertEquals(0, written.size)

        target.value = "content://folder"
        advanceTimeBy(3001)
        assertEquals(1, written.size)

        target.value = null
        changes.value = 9
        advanceTimeBy(10_000)
        assertEquals(1, written.size)
        job.cancel()
    }

    @Test
    fun writeFailure_isReported_andLaterBackupsStillRun() = runTest {
        target.value = "content://folder"
        failWrites = true
        val job = autoBackup().start(backgroundScope)
        advanceTimeBy(3001)
        assertEquals(1, errors.size)
        assertEquals(0, written.size)

        failWrites = false
        changes.value = 1
        advanceTimeBy(3001)
        assertEquals(listOf("content://folder" to "backup#1"), written)
        job.cancel()
    }
}
