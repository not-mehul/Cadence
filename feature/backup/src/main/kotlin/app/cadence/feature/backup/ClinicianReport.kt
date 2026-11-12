package app.cadence.feature.backup

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.cadence.core.data.backup.BackupData
import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.cycles.CalendarDays
import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.cycles.CycleInsights
import app.cadence.core.domain.cycles.CyclePrediction
import app.cadence.core.domain.cycles.CyclePredictor
import app.cadence.core.domain.cycles.CycleStats
import app.cadence.core.domain.cycles.HealthFlag
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.patterns.PatternTiming
import app.cadence.core.domain.patterns.SymptomPattern
import app.cadence.core.domain.patterns.SymptomPatterns
import app.cadence.core.ui.log.LogLabels
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** How far back the clinician report looks. */
enum class ReportRange(
    /** Null: everything ever logged. */
    val months: Long?,
) {
    MONTHS_3(3),
    MONTHS_6(6),
    MONTHS_12(12),
    ALL(null),
    ;

    companion object {
        val DEFAULT = MONTHS_6
    }
}

/** What the clinician report shows; built purely from logged data so it can be tested. */
internal data class ReportContent(
    val generatedOn: LocalDate,
    /** The first day the report covers: the range's start, or the first day logged if later. */
    val firstLogged: LocalDate?,
    val goal: Goal,
    val stats: CycleStats,
    val prediction: CyclePrediction?,
    val cycles: List<ReportCycle>,
    /** Symptom id to number of days logged, most frequent first. */
    val topSymptoms: List<Pair<String, Int>>,
    val averagePainOnPeriodDays: Double?,
    val painDays: Int,
    val flags: List<HealthFlag>,
    /** The pill, patch or ring schedule being followed, if any. */
    val birthControl: ContraceptiveSchedule? = null,
    /** Doses recorded as missed in the report's period. */
    val missedDoses: Int = 0,
    val medications: List<ReportMedication> = emptyList(),
    val patterns: List<SymptomPattern> = emptyList(),
    val tagLabels: Map<Long, String> = emptyMap(),
) {
    data class ReportMedication(
        val name: String,
        val use: MedicationUse,
        val active: Boolean,
        /** Days taken in the report's period. */
        val daysTaken: Int,
    )

    data class ReportCycle(
        val start: LocalDate,
        val lengthDays: Int,
        val periodDays: Int,
        val excluded: Boolean,
    )

    companion object {
        /** Rows in the cycle table; "everything" can be years. */
        const val MAX_CYCLES = 24
        const val MAX_SYMPTOMS = 8

        /**
         * The report for [range] up to [today]. The range's start moves back to the start of a period
         * it falls in, so the first cycle is never cut in half. The prediction looks forward, so it
         * uses everything logged.
         */
        fun build(
            all: BackupData,
            today: LocalDate,
            range: ReportRange = ReportRange.DEFAULT,
        ): ReportContent {
            val fullHistory = CycleHistory.from(all.flowByDay(), all.excludedCycleStarts)
            val from =
                range.months?.let { months ->
                    val start = today.minusMonths(months)
                    CalendarDays.periodContaining(fullHistory, start)?.start ?: start
                }
            val data = if (from == null) all else all.since(from)
            val flowByDay = data.flowByDay()
            val history = CycleHistory.from(flowByDay, data.excludedCycleStarts)
            val periodLengths = history.periods.associate { it.start to it.length }
            val since = from ?: LocalDate.MIN
            val periodPain =
                data.entries.filter { e -> e.painLevel != null && history.isPeriodDay(e.date) }.map { it.painLevel!! }
            return ReportContent(
                generatedOn = today,
                firstLogged =
                    all.entries.minOfOrNull { it.date }?.let { first ->
                        from?.let { maxOf(it, first) }
                            ?: first
                    },
                goal = data.settings.goal,
                stats = CycleInsights.stats(history, today),
                // On a pill, patch or ring schedule, bleeds follow the pack rather than the cycle history.
                prediction =
                    if (data.settings.activeSchedule !=
                        null
                    ) {
                        null
                    } else {
                        CyclePredictor.predict(fullHistory, data.settings.cycleDefaults, today)
                    },
                cycles =
                    history.cycles
                        .takeLast(MAX_CYCLES)
                        .asReversed()
                        .map { ReportCycle(it.start, it.length, periodLengths.getValue(it.start), it.excluded) },
                topSymptoms =
                    data.entries
                        .filter { !it.date.isBefore(since) }
                        .flatMap { it.symptoms.keys }
                        .groupingBy { it }
                        .eachCount()
                        .entries
                        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                        .take(MAX_SYMPTOMS)
                        .map { it.key to it.value },
                averagePainOnPeriodDays = periodPain.takeIf { it.isNotEmpty() }?.average(),
                painDays = periodPain.size,
                flags =
                    CycleInsights.healthFlags(
                        history,
                        data.entries.mapNotNull { e -> e.painLevel?.let { e.date to it } }.toMap(),
                        data.settings.goal,
                        today,
                    ),
                birthControl = data.settings.activeSchedule,
                missedDoses =
                    data.contraceptiveDoses.count { (date, status) ->
                        status == DoseStatus.MISSED &&
                            !date.isAfter(today)
                    },
                medications =
                    data.medications.map { m ->
                        ReportMedication(
                            m.name,
                            m.use,
                            m.active,
                            data.medicationDoses.count { (date, ids) ->
                                m.id in ids && !date.isBefore(since) &&
                                    !date.isAfter(today)
                            },
                        )
                    },
                patterns = SymptomPatterns.find(history, data.entries.associate { it.date to it.patternItems() }),
                tagLabels = data.customTags.associate { it.id to it.label },
            )
        }
    }
}

private fun BackupData.flowByDay() = entries.mapNotNull { e -> e.flow?.let { e.date to it } }.toMap()

/** Only what was logged from [from] on. */
private fun BackupData.since(from: LocalDate) =
    copy(
        entries = entries.filter { !it.date.isBefore(from) },
        excludedCycleStarts = excludedCycleStarts.filterTo(mutableSetOf()) { !it.isBefore(from) },
        contraceptiveDoses = contraceptiveDoses.filterKeys { !it.isBefore(from) },
        medicationDoses = medicationDoses.filterKeys { !it.isBefore(from) },
    )

/** Draws [ReportContent] as an A4 PDF with Android's built-in PdfDocument (no third-party library). */
internal class ReportPdfWriter(
    private val context: Context,
    private val locale: Locale = Locale.getDefault(),
) {
    private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val title = textPaint(20f, bold = true)
    private val heading = textPaint(13f, bold = true)
    private val body = textPaint(10.5f)
    private val small = textPaint(9f).apply { color = 0xFF555555.toInt() }
    private val rule = Paint().apply { color = 0xFFCCCCCC.toInt() }

    private lateinit var document: PdfDocument
    private lateinit var page: PdfDocument.Page
    private var pageNumber = 0
    private var y = 0f

    fun write(
        content: ReportContent,
        out: OutputStream,
    ) {
        document = PdfDocument()
        try {
            newPage()
            draw(content)
            finishPage()
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private fun draw(c: ReportContent) {
        paragraph(s(R.string.report_title), title)
        paragraph(s(R.string.report_generated, dateFormat.format(c.generatedOn)), small)
        c.firstLogged?.let {
            paragraph(s(R.string.report_covers, dateFormat.format(it), dateFormat.format(c.generatedOn)), small)
        }
        y += 12

        section(s(R.string.report_summary))
        val stats = c.stats
        row(
            s(R.string.report_mode),
            s(
                if (c.goal ==
                    Goal.TRACK
                ) {
                    R.string.report_mode_track
                } else {
                    R.string.report_mode_birth_control
                },
            ),
        )
        c.birthControl?.let { bc ->
            row(s(R.string.report_birth_control), birthControlText(bc))
            row(s(R.string.report_missed_doses), c.missedDoses.toString())
        }
        row(s(R.string.report_cycles_recorded), stats.cyclesCounted.toString())
        row(s(R.string.report_average_cycle), stats.averageCycleLength?.let { days(it) } ?: s(R.string.report_none))
        val shortest = stats.shortestCycle
        val longest = stats.longestCycle
        // A range like "28-28 days" adds nothing, so it's left out when every cycle was the same length.
        if (shortest != null && longest != null && shortest != longest) {
            row(s(R.string.report_cycle_range), q(R.plurals.report_range, longest, shortest, longest))
        }
        row(s(R.string.report_average_period), stats.averagePeriodLength?.let { days(it) } ?: s(R.string.report_none))
        c.prediction?.let { row(s(R.string.report_next_period), dateFormat.format(it.expectedNextStart)) }

        section(s(R.string.report_cycles))
        if (c.cycles.isEmpty()) {
            paragraph(s(R.string.report_none), body)
        } else {
            tableRow(
                listOf(
                    s(R.string.report_col_start),
                    s(R.string.report_col_cycle),
                    s(R.string.report_col_period),
                    s(R.string.report_col_note),
                ),
                heading,
            )
            c.cycles.forEach { cycle ->
                tableRow(
                    listOf(
                        dateFormat.format(cycle.start),
                        q(R.plurals.report_days, cycle.lengthDays, cycle.lengthDays),
                        q(R.plurals.report_days, cycle.periodDays, cycle.periodDays),
                        if (cycle.excluded) s(R.string.report_excluded) else "",
                    ),
                    body,
                )
            }
        }

        section(s(R.string.report_symptoms))
        if (c.topSymptoms.isEmpty()) {
            paragraph(s(R.string.report_none), body)
        } else {
            c.topSymptoms.forEach { (id, count) ->
                row(
                    LogLabels.symptomRes(id)?.let { context.getString(it) } ?: id,
                    q(R.plurals.report_symptom_days, count, count),
                )
            }
        }

        section(s(R.string.report_pain))
        paragraph(
            c.averagePainOnPeriodDays?.let {
                q(
                    R.plurals.report_pain_average,
                    c.painDays,
                    String.format(locale, "%.1f", it),
                    c.painDays,
                )
            }
                ?: s(R.string.report_pain_none),
            body,
        )

        if (c.medications.isNotEmpty()) {
            section(s(R.string.report_medications))
            c.medications.forEach { m ->
                val use =
                    s(
                        if (m.use ==
                            MedicationUse.DAILY
                        ) {
                            R.string.report_medication_daily
                        } else {
                            R.string.report_medication_as_needed
                        },
                    )
                val stopped = if (m.active) "" else " " + s(R.string.report_medication_stopped)
                row(m.name + stopped, use + " · " + q(R.plurals.report_medication_days, m.daysTaken, m.daysTaken))
            }
        }

        if (c.patterns.isNotEmpty()) {
            section(s(R.string.report_patterns))
            c.patterns.forEach { p ->
                row(itemLabel(p.itemId, c.tagLabels), patternText(p))
            }
        }

        section(s(R.string.report_flags))
        if (c.flags.isEmpty()) paragraph(s(R.string.report_flags_none), body)
        c.flags.forEach { paragraph("• " + flagText(it), body) }

        y += 16
        paragraph(s(R.string.report_footer), small)
    }

    private fun birthControlText(bc: ContraceptiveSchedule): String {
        val method =
            s(
                when (bc.method) {
                    ContraceptiveMethod.PILL -> R.string.report_bc_pill
                    ContraceptiveMethod.PATCH -> R.string.report_bc_patch
                    ContraceptiveMethod.RING -> R.string.report_bc_ring
                },
            )
        return when {
            !bc.hasBreak -> s(R.string.report_bc_continuous, method, bc.activeDays)
            bc.placebo -> s(R.string.report_bc_placebo, method, bc.activeDays, bc.breakDays)
            else -> s(R.string.report_bc_break, method, bc.activeDays, bc.breakDays)
        }
    }

    private fun itemLabel(
        id: String,
        tagLabels: Map<Long, String>,
    ): String =
        CustomTag.fromItemId(id)?.let { tagLabels[it] } ?: LogLabels.itemRes(id)?.let { context.getString(it) } ?: id

    private fun patternText(p: SymptomPattern): String {
        val day = p.day ?: 0
        val timing =
            when (p.timing) {
                PatternTiming.DURING_PERIOD -> s(R.string.report_pattern_during)
                PatternTiming.BEFORE_PERIOD -> q(R.plurals.report_pattern_before, day, day)
                PatternTiming.AROUND_CYCLE_DAY -> s(R.string.report_pattern_cycle_day, day)
            }
        return timing + " · " + q(R.plurals.report_pattern_cycles, p.outOf, p.cycles, p.outOf)
    }

    private fun flagText(flag: HealthFlag): String =
        when (flag) {
            is HealthFlag.ShortCycles -> {
                q(R.plurals.report_flag_short, flag.averageDays, flag.averageDays)
            }

            is HealthFlag.LongCycles -> {
                q(R.plurals.report_flag_long, flag.averageDays, flag.averageDays)
            }

            is HealthFlag.IrregularCycles -> {
                q(R.plurals.report_flag_irregular, flag.variationDays, flag.variationDays)
            }

            is HealthFlag.LongPeriod -> {
                q(
                    R.plurals.report_flag_long_period,
                    flag.lengthDays,
                    flag.lengthDays,
                    dateFormat.format(flag.start),
                )
            }

            is HealthFlag.NoRecentPeriod -> {
                q(R.plurals.report_flag_no_period, flag.daysSinceLastPeriod, flag.daysSinceLastPeriod)
            }

            is HealthFlag.SeverePain -> {
                q(R.plurals.report_flag_pain, flag.cycles, flag.cycles)
            }
        }

    private fun days(value: Double) = s(R.string.report_days_decimal, String.format(locale, "%.1f", value))

    private fun s(
        id: Int,
        vararg args: Any,
    ): String = context.getString(id, *args)

    private fun q(
        id: Int,
        count: Int,
        vararg args: Any,
    ): String = context.resources.getQuantityString(id, count, *args)

    // --- Layout --------------------------------------------------------------------------

    private fun section(text: String) {
        y += 14
        ensureSpace(40f)
        paragraph(text, heading)
        page.canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule)
        y += 6
    }

    private fun row(
        label: String,
        value: String,
    ) = tableRow(listOf(label, value), body, columns = listOf(0f, 0.55f))

    private fun tableRow(
        cells: List<String>,
        paint: TextPaint,
        columns: List<Float> = listOf(0f, 0.32f, 0.54f, 0.76f),
    ) {
        val lineHeight = paint.fontSpacing + 3
        ensureSpace(lineHeight)
        val width = PAGE_WIDTH - 2 * MARGIN
        cells.forEachIndexed { i, text ->
            page.canvas.drawText(text, MARGIN + width * columns[i], y - paint.ascent(), paint)
        }
        y += lineHeight
    }

    private fun paragraph(
        text: String,
        paint: TextPaint,
    ) {
        val layout =
            StaticLayout.Builder
                .obtain(text, 0, text.length, paint, (PAGE_WIDTH - 2 * MARGIN).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
        ensureSpace(layout.height.toFloat())
        page.canvas.save()
        page.canvas.translate(MARGIN, y)
        layout.draw(page.canvas)
        page.canvas.restore()
        y += layout.height + 4
    }

    private fun ensureSpace(height: Float) {
        if (y + height > PAGE_HEIGHT - MARGIN - FOOTER) {
            finishPage()
            newPage()
        }
    }

    private fun newPage() {
        pageNumber++
        page =
            document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageNumber).create(),
            )
        y = MARGIN
    }

    private fun finishPage() {
        page.canvas.drawText(s(R.string.report_page, pageNumber), MARGIN, PAGE_HEIGHT - MARGIN / 2, small)
        document.finishPage(page)
    }

    private fun textPaint(
        size: Float,
        bold: Boolean = false,
    ) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        color = 0xFF222222.toInt()
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private companion object {
        // A4 in PostScript points.
        const val PAGE_WIDTH = 595f
        const val PAGE_HEIGHT = 842f
        const val MARGIN = 48f
        const val FOOTER = 16f
    }
}
