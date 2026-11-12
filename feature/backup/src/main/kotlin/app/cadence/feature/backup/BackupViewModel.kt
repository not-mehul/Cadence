package app.cadence.feature.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cadence.core.data.backup.BackupCodec
import app.cadence.core.data.backup.BackupData
import app.cadence.core.data.backup.BackupException
import app.cadence.core.data.backup.BackupRepository
import app.cadence.core.data.backup.CsvExport
import app.cadence.core.data.backup.ImportMode
import app.cadence.core.domain.time.TodayProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** Reads and writes the files the user picked; separate so tests don't need a content provider. */
interface FileAccess {
    fun openOutput(uri: Uri): OutputStream?

    fun openInput(uri: Uri): InputStream?
}

sealed interface BackupMessage {
    data object ExportDone : BackupMessage

    data object CsvDone : BackupMessage

    data object ReportDone : BackupMessage

    data class ImportDone(
        val days: Int,
    ) : BackupMessage

    data object WriteFailed : BackupMessage

    data object ReadFailed : BackupMessage

    /** The backup was fine but saving it into the database failed; nothing was changed. */
    data object RestoreFailed : BackupMessage

    data class ImportFailed(
        val reason: BackupException.Reason,
    ) : BackupMessage
}

/** An import in progress: either waiting for a password, or for the user to choose how to restore. */
sealed interface PendingImport {
    class NeedsPassword(
        val bytes: ByteArray,
        val wrongPassword: Boolean = false,
    ) : PendingImport

    data class ChooseMode(
        val data: BackupData,
    ) : PendingImport
}

data class BackupUiState(
    val busy: Boolean = false,
    val message: BackupMessage? = null,
    val pendingImport: PendingImport? = null,
)

@HiltViewModel
class BackupViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val backups: BackupRepository,
        private val files: FileAccess,
        private val clock: Clock,
        private val today: TodayProvider,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(BackupUiState())
        val state: StateFlow<BackupUiState> = mutableState.asStateFlow()

        /** During onboarding there's nothing to merge with, so imports always replace. */
        var alwaysReplace = false

        fun exportBackup(
            uri: Uri,
            password: CharArray?,
        ) = write(uri, BackupMessage.ExportDone) { out ->
            out.write(BackupCodec.encode(backups.snapshot(), Instant.now(clock), password))
            password?.fill(' ')
        }

        fun exportCsv(uri: Uri) =
            write(
                uri,
                BackupMessage.CsvDone,
            ) { out -> out.write(CsvExport.write(backups.snapshot()).toByteArray()) }

        fun exportReport(
            uri: Uri,
            range: ReportRange = ReportRange.DEFAULT,
        ) = write(uri, BackupMessage.ReportDone) { out ->
            ReportPdfWriter(context).write(ReportContent.build(backups.snapshot(), today.today(), range), out)
        }

        fun startImport(uri: Uri) =
            work {
                val bytes =
                    try {
                        files.openInput(uri)?.use { it.readBytes() } ?: throw IOException("No input")
                    } catch (e: IOException) {
                        mutableState.update { it.copy(message = BackupMessage.ReadFailed) }
                        return@work
                    } catch (e: SecurityException) {
                        // The provider revoked access to the file.
                        mutableState.update { it.copy(message = BackupMessage.ReadFailed) }
                        return@work
                    }
                if (BackupCodec.isEncrypted(bytes)) {
                    mutableState.update { it.copy(pendingImport = PendingImport.NeedsPassword(bytes)) }
                } else {
                    decode(bytes, null)
                }
            }

        fun submitPassword(password: CharArray) {
            val pending = state.value.pendingImport as? PendingImport.NeedsPassword ?: return
            work { decode(pending.bytes, password) }
        }

        fun confirmImport(mode: ImportMode) {
            val pending = state.value.pendingImport as? PendingImport.ChooseMode ?: return
            work {
                val message =
                    try {
                        backups.restore(pending.data, mode)
                        BackupMessage.ImportDone(pending.data.entries.size)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: RuntimeException) {
                        // The restore runs in one transaction, so a failure leaves the old data untouched.
                        BackupMessage.RestoreFailed
                    }
                mutableState.update { it.copy(pendingImport = null, message = message) }
            }
        }

        fun cancelImport() = mutableState.update { it.copy(pendingImport = null) }

        fun dismissMessage() = mutableState.update { it.copy(message = null) }

        private suspend fun decode(
            bytes: ByteArray,
            password: CharArray?,
        ) {
            val data =
                try {
                    withContext(Dispatchers.Default) { BackupCodec.decode(bytes, password) }
                } catch (e: BackupException) {
                    mutableState.update {
                        if (e.reason == BackupException.Reason.WRONG_PASSWORD) {
                            it.copy(pendingImport = PendingImport.NeedsPassword(bytes, wrongPassword = true))
                        } else {
                            it.copy(pendingImport = null, message = BackupMessage.ImportFailed(e.reason))
                        }
                    }
                    return
                } finally {
                    password?.fill(' ')
                }
            mutableState.update { it.copy(pendingImport = PendingImport.ChooseMode(data)) }
            if (alwaysReplace) confirmImport(ImportMode.REPLACE)
        }

        private fun write(
            uri: Uri,
            done: BackupMessage,
            block: suspend (OutputStream) -> Unit,
        ) = work {
            val message =
                try {
                    val out = files.openOutput(uri) ?: throw IOException("No output")
                    out.use { withContext(Dispatchers.Default) { block(it) } }
                    done
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    BackupMessage.WriteFailed
                } catch (e: RuntimeException) {
                    // E.g. a provider that rejects the write, or PdfDocument failing; never crash the app.
                    BackupMessage.WriteFailed
                }
            mutableState.update { it.copy(message = message) }
        }

        private fun work(block: suspend () -> Unit) {
            mutableState.update { it.copy(busy = true) }
            viewModelScope.launch {
                try {
                    block()
                } finally {
                    mutableState.update { it.copy(busy = false) }
                }
            }
        }
    }
