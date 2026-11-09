package app.cadence.feature.today

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.cadence.core.domain.cycles.Confidence
import app.cadence.core.domain.cycles.CyclePrediction
import app.cadence.core.domain.cycles.CycleSnapshot
import app.cadence.core.domain.cycles.CycleStatus
import app.cadence.core.domain.cycles.CycleTimeline
import app.cadence.core.domain.cycles.PredictionBasis
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.model.CyclePhase
import app.cadence.core.domain.model.Goal
import app.cadence.core.ui.components.CadenceCard
import app.cadence.core.ui.components.CycleRing
import app.cadence.core.ui.components.RingSegment
import app.cadence.core.ui.log.LogLabels
import app.cadence.core.ui.theme.CadenceTheme
import app.cadence.core.ui.theme.CycleColors

@Composable
internal fun headline(status: CycleStatus): String =
    when (status) {
        CycleStatus.NoData -> {
            stringResource(R.string.today_headline_no_data)
        }

        is CycleStatus.OnPeriod -> {
            stringResource(R.string.today_headline_on_period, status.dayOfPeriod)
        }

        is CycleStatus.BetweenPeriods -> {
            if (status.daysUntil == 0) {
                stringResource(R.string.today_headline_expected_today)
            } else {
                pluralStringResource(R.plurals.today_headline_period_in, status.daysUntil, status.daysUntil)
            }
        }

        is CycleStatus.Due -> {
            stringResource(R.string.today_headline_due)
        }

        is CycleStatus.Late -> {
            pluralStringResource(R.plurals.today_headline_late, status.daysLate, status.daysLate)
        }

        is CycleStatus.PackActive -> {
            status.daysUntilBreak?.let { pluralStringResource(R.plurals.today_headline_break_in, it, it) }
                ?: stringResource(R.string.today_headline_pack_day, status.dayOfPack)
        }

        is CycleStatus.PackBreak -> {
            stringResource(R.string.today_headline_break_day, status.dayOfBreak, status.breakDays)
        }
    }

@Composable
internal fun basisText(prediction: CyclePrediction): String =
    when (prediction.basis) {
        PredictionBasis.TYPICAL -> {
            stringResource(R.string.today_basis_typical)
        }

        PredictionBasis.HISTORY -> {
            val cycles =
                pluralStringResource(R.plurals.today_basis_history, prediction.cyclesUsed, prediction.cyclesUsed)
            stringResource(
                R.string.today_basis_with_confidence,
                cycles,
                stringResource(confidenceLabel(prediction.confidence)),
            )
        }
    }

@StringRes
internal fun confidenceLabel(confidence: Confidence): Int =
    when (confidence) {
        Confidence.HIGH -> R.string.today_confidence_high
        Confidence.MEDIUM -> R.string.today_confidence_medium
        Confidence.LOW -> R.string.today_confidence_low
    }

@StringRes
internal fun phaseName(phase: CyclePhase): Int =
    when (phase) {
        CyclePhase.MENSTRUAL -> R.string.phase_menstrual
        CyclePhase.FOLLICULAR -> R.string.phase_follicular
        CyclePhase.OVULATORY -> R.string.phase_ovulatory
        CyclePhase.LUTEAL -> R.string.phase_luteal
    }

/** Title and body for the card under the actions. */
internal fun phaseCopy(
    snapshot: CycleSnapshot,
    goal: Goal,
): Pair<Int, Int> {
    val phase = snapshot.phase
    return when {
        phase == CyclePhase.MENSTRUAL -> {
            R.string.phase_menstrual to R.string.phase_menstrual_body
        }

        phase == CyclePhase.FOLLICULAR -> {
            R.string.phase_follicular to R.string.phase_follicular_body
        }

        phase == CyclePhase.OVULATORY -> {
            R.string.phase_ovulatory to R.string.phase_ovulatory_body
        }

        phase == CyclePhase.LUTEAL -> {
            R.string.phase_luteal to R.string.phase_luteal_body
        }

        snapshot.status is CycleStatus.Late -> {
            R.string.phase_late to R.string.phase_late_body
        }

        snapshot.status is CycleStatus.Due -> {
            R.string.phase_due to R.string.phase_due_body
        }

        goal == Goal.BIRTH_CONTROL && snapshot.status != CycleStatus.NoData -> {
            R.string.phase_birth_control to R.string.phase_birth_control_body
        }

        else -> {
            R.string.phase_welcome to R.string.phase_welcome_body
        }
    }
}

/**
 * A little more about a phase, and where it comes from (docs/PLAN.md §2.10: short, factual,
 * sourced). Keyed by the card's title; null when there's nothing more to say.
 */
internal fun phaseDetails(title: Int): Pair<Int, Int>? =
    when (title) {
        R.string.phase_menstrual -> R.string.phase_menstrual_more to R.string.phase_source_periods
        R.string.phase_follicular -> R.string.phase_follicular_more to R.string.phase_source_fertility
        R.string.phase_ovulatory -> R.string.phase_ovulatory_more to R.string.phase_source_fertility
        R.string.phase_luteal -> R.string.phase_luteal_more to R.string.phase_source_pms
        R.string.phase_birth_control -> R.string.phase_birth_control_more to R.string.phase_source_contraception
        else -> null
    }

/** Ring segments for each phase, clamped so they never overlap. */
internal fun ringSegments(
    timeline: CycleTimeline,
    colors: CycleColors,
): List<RingSegment> {
    val periodEnd = timeline.periodEndDay.coerceAtMost(timeline.cycleLength)
    val segments = mutableListOf(RingSegment(1, periodEnd, colors.menstrualPhase))
    val fertile = timeline.fertileWindowDays ?: return segments
    val fertileStart = maxOf(fertile.first, periodEnd + 1)
    val fertileEnd = minOf(fertile.last, timeline.cycleLength)
    if (fertileStart > periodEnd + 1) segments += RingSegment(periodEnd + 1, fertileStart - 1, colors.follicularPhase)
    if (fertileEnd >= fertileStart) segments += RingSegment(fertileStart, fertileEnd, colors.ovulatoryPhase)
    val lutealStart = maxOf(fertileEnd, periodEnd) + 1
    if (lutealStart <=
        timeline.cycleLength
    ) {
        segments += RingSegment(lutealStart, timeline.cycleLength, colors.lutealPhase)
    }
    return segments
}

@Composable
internal fun TodayRing(
    snapshot: CycleSnapshot,
    modifier: Modifier = Modifier,
) {
    val timeline = snapshot.timeline ?: return
    val cycleDay = snapshot.cycleDay ?: return
    val phase = snapshot.phase
    val description =
        if (phase != null) {
            stringResource(
                R.string.today_ring_description_phase,
                cycleDay,
                timeline.cycleLength,
                stringResource(phaseName(phase)),
            )
        } else {
            stringResource(R.string.today_ring_description, cycleDay, timeline.cycleLength)
        }
    CycleRing(
        cycleLength = timeline.cycleLength,
        today = cycleDay,
        segments = ringSegments(timeline, CadenceTheme.cycleColors),
        contentDescription = description,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.today_ring_day_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(cycleDay.toString(), style = MaterialTheme.typography.displayLarge)
            Text(
                stringResource(R.string.today_ring_of_about, timeline.cycleLength),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (phase != null) {
                Text(
                    stringResource(phaseName(phase)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = CadenceTheme.spacing.xs),
                )
            }
        }
    }
}

@Composable
internal fun EndPeriodDialog(
    canEndYesterday: Boolean,
    onEnd: (endedYesterday: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.today_end_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.sm)) {
                OutlinedButton(onClick = { onEnd(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.today_end_today))
                }
                if (canEndYesterday) {
                    OutlinedButton(onClick = { onEnd(true) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.today_end_yesterday))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.today_cancel)) } },
    )
}

/** Summary of today's log as chips; tapping opens the log sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayLogCard(
    entry: DayEntry,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    tagLabels: Map<Long, String> = emptyMap(),
) {
    val items = LogLabels.summary(entry, tagLabels)
    CadenceCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.today_log_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onOpen) {
                Text(stringResource(if (items.isEmpty()) R.string.today_log_add else R.string.today_log_edit))
            }
        }
        if (items.isEmpty()) {
            Text(stringResource(R.string.today_log_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.xs),
            ) {
                items.forEach { label ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            modifier =
                                Modifier.padding(
                                    horizontal = CadenceTheme.spacing.md,
                                    vertical = CadenceTheme.spacing.xs,
                                ),
                        )
                    }
                }
            }
        }
    }
}
