package app.cadence.feature.backup

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.cadence.core.data.backup.BackupCodec
import app.cadence.core.data.backup.BackupData
import app.cadence.core.data.backup.BackupException
import app.cadence.core.data.backup.ImportMode
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.time.TodayProvider
import app.cadence.core.testing.FakeBackupRepository
import app.cadence.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class BackupViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 3, 20)
    private val clock = Clock.fixed(Instant.parse("2026-03-20T12:00:00Z"), ZoneOffset.UTC)
    private val data =
        BackupData(
            UserSettings(goal = Goal.BIRTH_CONTROL, onboardingComplete = true),
            listOf(
                DayEntry(LocalDate.of(2026, 3, 1), flow = MenstrualFlow.HEAVY, note = "Rough day, \"very\" tired"),
                DayEntry(LocalDate.of(2026, 3, 2), flow = MenstrualFlow.MEDIUM, painLevel = 4),
            ),
            setOf(LocalDate.of(2026, 1, 1)),
        )
    private val backups = FakeBackupRepository(data)
    private val files = FakeFiles()
    private val target = Uri.parse("content://test/out")
    private val source = Uri.parse("content://test/in")

    private val vm by lazy {
        BackupViewModel(ApplicationProvider.getApplicationContext(), backups, files, clock, TodayProvider { today })
    }

    private fun await(condition: (BackupUiState) -> Boolean): BackupUiState =
        runBlocking { withTimeout(30_000) { vm.state.first { !it.busy && condition(it) } } }

    @Test
    fun `exports a backup that restores to the same data`() {
        vm.exportBackup(target, password = null)

        assertEquals(BackupMessage.ExportDone, await { it.message != null }.message)
        val written = files.written.getValue(target)
        assertFalse(BackupCodec.isEncrypted(written))
        assertEquals(data, BackupCodec.decode(written, null))
    }

    @Test
    fun `a password encrypts the backup and is wiped afterwards`() {
        val password = "correct horse".toCharArray()
        vm.exportBackup(target, password)

        assertEquals(BackupMessage.ExportDone, await { it.message != null }.message)
        val written = files.written.getValue(target)
        assertTrue(BackupCodec.isEncrypted(written))
        assertEquals(data, BackupCodec.decode(written, "correct horse".toCharArray()))
        assertTrue(password.all { it == ' ' })
    }

    @Test
    fun `exports a spreadsheet`() {
        vm.exportCsv(target)

        assertEquals(BackupMessage.CsvDone, await { it.message != null }.message)
        val csv = files.written.getValue(target).decodeToString()
        assertTrue(csv.startsWith("date,flow,"))
        assertTrue(csv.contains("\"Rough day, \"\"very\"\" tired\""))
    }

    @Test
    fun `a file that can't be opened for writing is reported`() {
        files.failWrites = true
        vm.exportCsv(target)

        assertEquals(BackupMessage.WriteFailed, await { it.message != null }.message)
        vm.dismissMessage()
        assertEquals(null, vm.state.value.message)
    }

    @Test
    fun `an unexpected error while writing is reported instead of crashing`() {
        files.throwOnWrite = true
        vm.exportCsv(target)

        assertEquals(BackupMessage.WriteFailed, await { it.message != null }.message)
    }

    @Test
    fun `a failed restore is reported and clears the pending import`() {
        files.contents[source] = BackupCodec.encode(data, Instant.now(clock), null)
        backups.failure = IllegalStateException("disk full")
        vm.startImport(source)
        await { it.pendingImport != null }

        vm.confirmImport(ImportMode.REPLACE)
        assertEquals(BackupMessage.RestoreFailed, await { it.message != null }.message)
        assertEquals(null, vm.state.value.pendingImport)
    }

    @Test
    fun `importing asks how to restore, then restores`() {
        files.contents[source] = BackupCodec.encode(data, Instant.now(clock), null)
        vm.startImport(source)

        val pending = assertIs<PendingImport.ChooseMode>(await { it.pendingImport != null }.pendingImport)
        assertEquals(data, pending.data)

        vm.confirmImport(ImportMode.MERGE)
        assertEquals(BackupMessage.ImportDone(2), await { it.message != null }.message)
        assertEquals(listOf(data to ImportMode.MERGE), backups.restored)
        assertEquals(null, vm.state.value.pendingImport)
    }

    @Test
    fun `cancelling an import restores nothing`() {
        files.contents[source] = BackupCodec.encode(data, Instant.now(clock), null)
        vm.startImport(source)
        await { it.pendingImport != null }

        vm.cancelImport()
        assertEquals(null, vm.state.value.pendingImport)
        assertTrue(backups.restored.isEmpty())
    }

    @Test
    fun `during onboarding an import replaces without asking`() {
        files.contents[source] = BackupCodec.encode(data, Instant.now(clock), null)
        vm.alwaysReplace = true
        vm.startImport(source)

        assertEquals(BackupMessage.ImportDone(2), await { it.message != null }.message)
        assertEquals(listOf(data to ImportMode.REPLACE), backups.restored)
    }

    @Test
    fun `an encrypted backup asks for the password until it is right`() {
        files.contents[source] =
            BackupCodec.encode(data, Instant.now(clock), "secret pass".toCharArray(), iterations = 1_000)
        vm.startImport(source)

        val first = assertIs<PendingImport.NeedsPassword>(await { it.pendingImport != null }.pendingImport)
        assertFalse(first.wrongPassword)

        vm.submitPassword("wrong pass".toCharArray())
        await { (it.pendingImport as? PendingImport.NeedsPassword)?.wrongPassword == true }

        vm.submitPassword("secret pass".toCharArray())
        val chosen =
            assertIs<PendingImport.ChooseMode>(await { it.pendingImport is PendingImport.ChooseMode }.pendingImport)
        assertEquals(data, chosen.data)
    }

    @Test
    fun `a file that isn't a backup is rejected`() {
        files.contents[source] = "{\"hello\":\"world\"}".toByteArray()
        vm.startImport(source)

        val message = assertIs<BackupMessage.ImportFailed>(await { it.message != null }.message)
        assertEquals(BackupException.Reason.NOT_A_BACKUP, message.reason)
        assertTrue(backups.restored.isEmpty())
    }

    @Test
    fun `a file that can't be read is reported`() {
        vm.startImport(source)

        assertEquals(BackupMessage.ReadFailed, await { it.message != null }.message)
    }

    @Test
    fun `password and mode actions are ignored when nothing is pending`() {
        vm.submitPassword("x".toCharArray())
        vm.confirmImport(ImportMode.REPLACE)

        assertTrue(backups.restored.isEmpty())
        assertEquals(BackupUiState(), vm.state.value)
    }

    private class FakeFiles : FileAccess {
        val written = mutableMapOf<Uri, ByteArray>()
        val contents = mutableMapOf<Uri, ByteArray>()
        var failWrites = false
        var throwOnWrite = false

        override fun openOutput(uri: Uri): OutputStream? =
            if (failWrites) {
                null
            } else {
                object : ByteArrayOutputStream() {
                    override fun write(
                        b: ByteArray,
                        off: Int,
                        len: Int,
                    ) {
                        check(!throwOnWrite) { "Provider rejected the write" }
                        super.write(b, off, len)
                    }

                    override fun close() {
                        written[uri] = toByteArray()
                    }
                }
            }

        override fun openInput(uri: Uri): InputStream? = contents[uri]?.let(::ByteArrayInputStream)
    }
}
