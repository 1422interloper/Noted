package io.github.interloper.noted.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private fun repo(name: String = "settings") = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("$name.preferences_pb") },
    )

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun defaults_whenNothingStored() = runBlocking {
        assertEquals(Settings(), repo().settings.first())
    }

    @Test
    fun values_roundTrip() = runBlocking {
        val repo = repo()
        repo.setThemeMode(ThemeMode.Dark)
        repo.setAmoled(true)
        repo.setDynamicColor(false)
        repo.setSeedColor(0xFF112233.toInt())
        repo.setFont(AppFont.Serif)
        repo.setViewMode(ViewMode.List)
        repo.setTitleOnly(true)
        repo.setPreviewLines(5)
        repo.setBinRetention(BinRetention.OneWeek)
        repo.setLastLabelId(42)
        repo.setPin("hash", "salt")

        val s = repo.settings.first()
        assertEquals(ThemeMode.Dark, s.themeMode)
        assertTrue(s.amoled)
        assertFalse(s.dynamicColor)
        assertEquals(0xFF112233.toInt(), s.seedColor)
        assertEquals(AppFont.Serif, s.font)
        assertEquals(ViewMode.List, s.viewMode)
        assertTrue(s.titleOnly)
        assertEquals(5, s.previewLines)
        assertEquals(BinRetention.OneWeek, s.binRetention)
        assertEquals(42L, s.lastLabelId)
        assertTrue(s.hasPin)

        repo.setLastLabelId(null)
        assertNull(repo.settings.first().lastLabelId)
    }

    @Test
    fun previewLines_areClampedToRange() = runBlocking {
        val repo = repo()
        repo.setPreviewLines(99)
        assertEquals(10, repo.settings.first().previewLines)
        repo.setPreviewLines(0)
        assertEquals(1, repo.settings.first().previewLines)
    }

    @Test
    fun clearingBackupFolder_turnsAutoBackupOff() = runBlocking {
        val repo = repo()
        repo.setBackupFolderUri("content://folder")
        repo.setAutoBackup(true)
        assertTrue(repo.settings.first().autoBackup)

        repo.setBackupFolderUri(null)
        val s = repo.settings.first()
        assertNull(s.backupFolderUri)
        assertFalse(s.autoBackup)
    }

    @Test
    fun binRetention_millis() {
        assertNull(BinRetention.Never.millis)
        assertEquals(86_400_000L, BinRetention.OneDay.millis)
        assertEquals(30L * 86_400_000L, BinRetention.OneMonth.millis)
    }

    @Test
    fun clearPin_forgetsPinAndFingerprintUnlock() = runBlocking {
        val repo = repo("clear-pin")
        repo.setPin("hash", "salt")
        repo.setBiometricUnlock(true)
        assertTrue(repo.settings.first().hasPin)

        repo.clearPin()

        val settings = repo.settings.first()
        assertFalse(settings.hasPin)
        assertNull(settings.pinHash)
        assertFalse(settings.biometricUnlock)
    }
}
