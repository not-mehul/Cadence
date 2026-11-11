package app.cadence.feature.calendar

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cadence.core.domain.cycles.DayMark
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.ui.components.CadenceCard
import app.cadence.core.ui.components.IconDisc
import app.cadence.core.ui.expressive.Buddy
import app.cadence.core.ui.expressive.BuddyFace
import app.cadence.core.ui.log.LogIcons
import app.cadence.core.ui.log.LogLabels
import app.cadence.core.ui.log.LoggedChips
import app.cadence.core.ui.theme.CadenceTheme
import java.time.LocalDate
import app.cadence.core.ui.R as UiR

/**
 * The selected day: where it sits in the cycle, what's logged, and what can be done with it.
 * Switching days cross-fades with a little pop, so it's clear the card follows the tap.
 */
@Composable
internal fun DayDetails(
    state: CalendarUiState.Ready,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state.selected,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f))
                .togetherWith(fadeOut(motion.fastEffectsSpec()))
        },
        label = "day details",
        modifier = modifier.fillMaxWidth(),
    ) { date ->
        // While the new day's log loads, the old entry belongs to another date: show nothing yet.
        val entry = state.selectedEntry?.takeIf { it.date == date }
        DayDetailsCard(date, entry, state, actions)
    }
}

@Composable
private fun DayDetailsCard(
    date: LocalDate,
    entry: DayEntry?,
    state: CalendarUiState.Ready,
    actions: CalendarActions,
) {
    val spacing = CadenceTheme.spacing
    val mark = state.markOn(date)
    CadenceCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            val (icon, tint, container) = markBadge(mark)
            IconDisc(painterResource(icon), tint, container, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    dayHeading(date),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                StatusLine(date, mark, state)
            }
        }

        if (entry != null) {
            if (entry.isEmpty) {
                EmptyDay(future = !state.canOpenLog(date))
            } else {
                LoggedItems(entry, state.tagLabels, Modifier.padding(top = spacing.md))
            }
        }

        if (state.canOpenLog(date)) DayActions(date, entry, state, actions)
    }
}

/** The icon and colours that sum up a day at a glance. */
@Composable
private fun markBadge(mark: DayMark): Triple<Int, Color, Color> {
    val c = CadenceTheme.cycleColors
    val scheme = MaterialTheme.colorScheme
    return when (mark) {
        DayMark.LOGGED_PERIOD -> Triple(UiR.drawable.ms_water_drop_fill1, c.onPeriod, c.period)
        DayMark.PREDICTED_PERIOD -> Triple(UiR.drawable.ms_water_drop, c.predictedPeriod, c.predictedPeriodContainer)
        DayMark.OVULATION -> Triple(UiR.drawable.ic_phase_ovulatory, c.onFertileWindow, c.fertileWindow)
        DayMark.FERTILE -> Triple(UiR.drawable.ic_phase_follicular, c.onFertileWindow, c.fertileWindow)
        DayMark.NONE -> Triple(UiR.drawable.ms_today, scheme.primary, scheme.primaryContainer)
    }
}

/** "Today · Period · Cycle day 2", as words: the colours above are never the only cue. */
@Composable
private fun StatusLine(
    date: LocalDate,
    mark: DayMark,
    state: CalendarUiState.Ready,
) {
    val parts =
        buildList {
            if (date == state.today) add(stringResource(R.string.calendar_legend_today))
            when (mark) {
                DayMark.LOGGED_PERIOD -> add(stringResource(R.string.calendar_legend_period))
                DayMark.PREDICTED_PERIOD -> add(stringResource(R.string.calendar_legend_predicted))
                DayMark.FERTILE -> add(stringResource(R.string.calendar_legend_fertile))
                DayMark.OVULATION -> add(stringResource(R.string.calendar_legend_ovulation))
                DayMark.NONE -> Unit
            }
            state.cycleDay(date)?.let {
                add(
                    stringResource(
                        if (it.estimated) R.string.calendar_cycle_day_estimate else R.string.calendar_cycle_day,
                        it.day,
                    ),
                )
            }
            state.packDay(date)?.let { add(stringResource(R.string.calendar_pack_day, it)) }
        }
    if (parts.isNotEmpty()) {
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyDay(future: Boolean) {
    val c = CadenceTheme.cycleColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.md),
        modifier = Modifier.padding(top = CadenceTheme.spacing.md),
    ) {
        Buddy(
            body = c.neutralTint,
            face = MaterialTheme.colorScheme.onSurface,
            expression = if (future) BuddyFace.SLEEPY else BuddyFace.HAPPY,
            size = 52.dp,
        )
        Text(
            stringResource(if (future) R.string.calendar_future_day else R.string.calendar_nothing_logged),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Everything logged, as icon chips, and the note in full. */
@Composable
private fun LoggedItems(
    entry: DayEntry,
    tagLabels: Map<Long, String>,
    modifier: Modifier = Modifier,
) {
    val spacing = CadenceTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        LoggedChips(LogLabels.summary(entry, tagLabels, includeNote = false))
        entry.note?.takeIf { it.isNotBlank() }?.let { note ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Icon(
                    painterResource(LogIcons.category(LogCategory.NOTES)),
                    contentDescription = stringResource(LogLabels.category(LogCategory.NOTES)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(note, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Add or edit the day's log, and the period actions that make sense for it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayActions(
    date: LocalDate,
    entry: DayEntry?,
    state: CalendarUiState.Ready,
    actions: CalendarActions,
) {
    val spacing = CadenceTheme.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = spacing.lg),
    ) {
        val hasLog = entry != null && !entry.isEmpty
        FilledTonalButton(onClick = { actions.onOpenLog(date) }, shapes = ButtonDefaults.shapes()) {
            ButtonIcon(if (hasLog) UiR.drawable.ms_edit else UiR.drawable.ms_add)
            Text(stringResource(if (hasLog) R.string.calendar_edit_details else R.string.calendar_add_details))
        }
        if (state.canStartPeriodOn(date)) {
            val c = CadenceTheme.cycleColors
            Button(
                onClick = { actions.onStartPeriod(date) },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(containerColor = c.period, contentColor = c.onPeriod),
            ) {
                ButtonIcon(UiR.drawable.ms_water_drop)
                Text(stringResource(R.string.calendar_period_started))
            }
        }
        if (state.canEndPeriodOn(date)) {
            OutlinedButton(onClick = { actions.onEndPeriod(date) }, shapes = ButtonDefaults.shapes()) {
                ButtonIcon(UiR.drawable.ms_check)
                Text(stringResource(R.string.calendar_period_ended))
            }
        }
        if (state.isLogged(date)) {
            TextButton(onClick = { actions.onRemovePeriodDay(date) }, shapes = ButtonDefaults.shapes()) {
                ButtonIcon(UiR.drawable.ms_close)
                Text(stringResource(R.string.calendar_not_period))
            }
        }
    }
}

@Composable
private fun ButtonIcon(
    @DrawableRes icon: Int,
) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
    )
}
