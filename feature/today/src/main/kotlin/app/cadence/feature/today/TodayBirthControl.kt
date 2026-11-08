package app.cadence.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.contraception.PackDay
import app.cadence.core.domain.contraception.PendingDose
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.ui.components.CadenceCard
import app.cadence.core.ui.components.CycleRing
import app.cadence.core.ui.components.RingSegment
import app.cadence.core.ui.contraception.ContraceptionLabels
import app.cadence.core.ui.format.DateSkeletons
import app.cadence.core.ui.format.rememberDateFormatter
import app.cadence.core.ui.theme.CadenceTheme
import java.time.LocalDate

/** The pack as a ring: active days, then the break (when a bleed is expected). */
@Composable
internal fun PackRing(
    schedule: ContraceptiveSchedule,
    pack: PackDay,
    modifier: Modifier = Modifier,
) {
    val colors = CadenceTheme.cycleColors
    val segments =
        buildList {
            add(RingSegment(1, schedule.activeDays, colors.follicularPhase))
            if (schedule.hasBreak) add(RingSegment(schedule.activeDays + 1, schedule.packLength, colors.menstrualPhase))
        }
    CycleRing(
        cycleLength = schedule.packLength,
        today = pack.dayOfPack,
        segments = segments,
        contentDescription = stringResource(R.string.today_pack_ring_description, pack.dayOfPack, schedule.packLength),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(ContraceptionLabels.method(schedule.method)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(pack.dayOfPack.toString(), style = MaterialTheme.typography.displayLarge)
            Text(
                stringResource(R.string.today_pack_of, schedule.packLength),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pack.inBreak) {
                Text(
                    stringResource(R.string.today_pack_break_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = CadenceTheme.spacing.xs),
                )
            }
        }
    }
}

/** Under the headline on a schedule: when the next break (and so the next bleed) is. */
@Composable
internal fun PackSupportingText(pack: PackDay) {
    val dateFormat = rememberDateFormatter(DateSkeletons.DAY_MONTH_SHORT)
    val breakDays = pack.currentOrNextBreak
    val text =
        when {
            breakDays == null -> {
                stringResource(R.string.today_pack_continuous)
            }

            pack.inBreak -> {
                stringResource(R.string.today_pack_in_break)
            }

            else -> {
                stringResource(
                    R.string.today_pack_next_break,
                    dateFormat.format(breakDays.start),
                    dateFormat.format(breakDays.end),
                )
            }
        }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Today's pill, patch or ring, with a check-off. */
@Composable
internal fun DoseCard(
    birthControl: BirthControlToday,
    today: LocalDate,
    onRecord: (LocalDate, DoseStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = birthControl.schedule.actionOn(today)
    val dateFormat = rememberDateFormatter(DateSkeletons.WEEKDAY_DAY_MONTH)
    CadenceCard(modifier) {
        CardTitle(stringResource(R.string.today_dose_title))
        when {
            action == null -> {
                Text(stringResource(R.string.today_dose_nothing), style = MaterialTheme.typography.bodyMedium)
            }

            birthControl.todayStatus == DoseStatus.TAKEN -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.today_dose_done, stringResource(ContraceptionLabels.done(action))),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onRecord(today, null) }) { Text(stringResource(R.string.today_undo)) }
                }
            }

            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(ContraceptionLabels.task(action)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = { onRecord(today, DoseStatus.TAKEN) }) {
                        Text(stringResource(R.string.today_dose_mark_done))
                    }
                }
            }
        }
        birthControl.next?.takeIf { action == null || birthControl.todayStatus != null }?.let { next ->
            Text(
                stringResource(
                    R.string.today_dose_next,
                    stringResource(ContraceptionLabels.noun(next.action)),
                    dateFormat.format(next.date),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Doses in the last week that weren't checked off. */
@Composable
internal fun MissedDosesCard(
    missed: List<PendingDose>,
    onRecord: (LocalDate, DoseStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = rememberDateFormatter(DateSkeletons.WEEKDAY_DAY_MONTH)
    CadenceCard(modifier) {
        CardTitle(pluralStringResource(R.plurals.today_missed_title, missed.size, missed.size))
        missed.forEach { dose ->
            Column(Modifier.padding(top = CadenceTheme.spacing.sm)) {
                Text(
                    stringResource(
                        R.string.today_missed_item,
                        dateFormat.format(dose.date),
                        stringResource(ContraceptionLabels.noun(dose.action)),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.sm)) {
                    FilledTonalButton(onClick = { onRecord(dose.date, DoseStatus.TAKEN) }) {
                        Text(stringResource(R.string.today_missed_taken))
                    }
                    TextButton(onClick = { onRecord(dose.date, DoseStatus.MISSED) }) {
                        Text(stringResource(R.string.today_missed_missed))
                    }
                }
            }
        }
        Text(
            stringResource(R.string.today_missed_advice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CadenceTheme.spacing.sm),
        )
    }
}

/** Today's medications, ticked off when taken. */
@Composable
internal fun MedicationsCard(
    medications: List<MedicationToday>,
    onTakenChange: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    CadenceCard(modifier) {
        CardTitle(stringResource(R.string.today_medications_title))
        medications.forEach { (medication, taken) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = taken,
                            role = Role.Checkbox,
                            onValueChange = { onTakenChange(medication.id, it) },
                        ),
            ) {
                Checkbox(checked = taken, onCheckedChange = null)
                Column(Modifier.padding(start = CadenceTheme.spacing.sm)) {
                    Text(medication.name, style = MaterialTheme.typography.bodyLarge)
                    if (medication.use == MedicationUse.AS_NEEDED) {
                        Text(
                            stringResource(R.string.today_medications_as_needed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Birth control mode without a schedule: point to where one is set up. */
@Composable
internal fun SetUpBirthControlCard(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CadenceCard(modifier) {
        CardTitle(stringResource(R.string.today_bc_setup_title))
        Text(stringResource(R.string.today_bc_setup_body), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.today_bc_setup_action)) }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = CadenceTheme.spacing.xs).semantics { heading() },
    )
}
