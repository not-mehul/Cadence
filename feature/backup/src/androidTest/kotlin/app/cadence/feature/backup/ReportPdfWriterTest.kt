package app.cadence.feature.backup

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cadence.core.data.backup.BackupData
import app.cadence.core.domain.cycles.HealthFlag
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.Severity
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** PdfDocument is native, so the report is drawn on a real device (Robolectric can't). */
@RunWith(AndroidJUnit4::class)
class ReportPdfWriterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.of(2026, 3, 20)

    private fun content(): ReportContent {
        val start = LocalDate.of(2025, 4, 1)
        val entries =
            (0 until 12).flatMap { cycle ->
                (0L until 5L).map { day ->
                    DayEntry(
                        start.plusDays(cycle * 29L + day),
                        flow = MenstrualFlow.MEDIUM,
                        symptoms = mapOf("cramps" to Severity.MODERATE),
                        painLevel = 5,
                    )
                }
            }
        return ReportContent.build(BackupData(UserSettings(onboardingComplete = true), entries, emptySet()), today)
    }

    private fun render(content: ReportContent): File {
        val file = File(context.cacheDir, "report-test.pdf")
        file.outputStream().use { ReportPdfWriter(context, Locale.US).write(content, it) }
        return file
    }

    private fun pageCount(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                // Every page must render, not just exist.
                repeat(renderer.pageCount) { index ->
                    renderer.openPage(index).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        assertEquals(595, page.width)
                        assertEquals(842, page.height)
                        bitmap.recycle()
                    }
                }
                renderer.pageCount
            }
        }

    @Test
    fun writesAnA4Report() {
        val file = render(content())

        assertEquals("%PDF-", file.readBytes().decodeToString(0, 5))
        assertTrue(pageCount(file) >= 1)
    }

    @Test
    fun longReportsFlowOntoMorePages() {
        val base = content()
        val long =
            base.copy(
                cycles = List(80) { base.cycles.first() },
                flags =
                    listOf(
                        HealthFlag.ShortCycles(21),
                        HealthFlag.LongCycles(40),
                        HealthFlag.IrregularCycles(12),
                        HealthFlag.LongPeriod(9, today.minusDays(40)),
                        HealthFlag.NoRecentPeriod(95),
                        HealthFlag.SeverePain(3),
                    ),
            )

        assertTrue(pageCount(render(long)) > 1)
    }

    @Test
    fun anEmptyLogStillWritesAReport() {
        val empty =
            ReportContent.build(
                BackupData(UserSettings(onboardingComplete = true), emptyList(), emptySet()),
                today,
            )

        assertEquals(1, pageCount(render(empty)))
    }
}
