package app.cadence.feature.today

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.contraception.PackDay
import app.cadence.core.domain.contraception.PendingDose
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.ui.components.CadenceCard
import app.cadence.core.ui.components.CardHeader
import app.cadence.core.ui.components.CycleRing
import app.cadence.core.ui.components.RingSegment
import app.cadence.core.ui.contraception.ContraceptionLabels
import app.cadence.core.ui.format.DateSkeletons
import app.cadence.core.ui.format.formatHeading
import app.cadence.core.ui.format.rememberDateFormatter
import app.cadence.core.ui.theme.CadenceTheme
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import app.cadence.core.ui.R as UiR

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
            add(RingSegment(1, schedule.activeDays, colors.follicularRing))
            if (schedule.hasBreak) add(RingSegment(schedule.activeDays + 1, schedule.packLength, colors.menstrualRing))
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
                stringResource(R.string.today_pack_day_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(pack.dayOfPack.toString(), style = MaterialTheme.typography.displayLarge)
            Text(
                stringResource(R.string.today_pack_of, schedule.packLength),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    onUpdatePacks: () -> Unit = {},
) {
    val action = birthControl.schedule.actionOn(today)
    val dateFormat = rememberDateFormatter(DateSkeletons.WEEKDAY_DAY_MONTH)
    CadenceCard(modifier) {
        CardTitle(stringResource(R.string.today_dose_title), UiR.drawable.ms_pill)
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
                    TextButton(onClick = { onRecord(today, null) }, shapes = ButtonDefaults.shapes()) {
                        ButtonIcon(UiR.drawable.ms_undo)
                        Text(stringResource(R.string.today_undo))
                    }
                }
            }

            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(ContraceptionLabels.task(action)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        onClick = { onRecord(today, DoseStatus.TAKEN) },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        ButtonIcon(UiR.drawable.ms_check)
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
        SupplyNote(birthControl.schedule, today, onUpdatePacks)
    }
}

/** When the counted packs are about to run out (or have), a nudge to pick up more and update the count. */
@Composable
private fun SupplyNote(
    schedule: ContraceptiveSchedule,
    today: LocalDate,
    onUpdatePacks: () -> Unit,
) {
    val runsOut = schedule.runsOutOn() ?: return
    val days = ChronoUnit.DAYS.between(today, runsOut).toInt()
    if (days > SUPPLY_WARNING_DAYS) return
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(top = CadenceTheme.spacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.sm),
            modifier = Modifier.padding(start = CadenceTheme.spacing.md, end = CadenceTheme.spacing.xs),
        ) {
            Icon(
                painterResource(UiR.drawable.ms_medication),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                if (days > 0) {
                    pluralStringResource(R.plurals.today_supply_runs_out, days, days)
                } else {
                    stringResource(R.string.today_supply_ran_out)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = CadenceTheme.spacing.sm),
            )
            TextButton(onClick = onUpdatePacks) { Text(stringResource(R.string.today_supply_update)) }
        }
    }
}

/** The note appears this many days before the packs run out. */
private const val SUPPLY_WARNING_DAYS = 7

/** Doses in the last week that weren't checked off. */
@Composable
internal fun MissedDosesCard(
    missed: List<PendingDose>,
    onRecord: (LocalDate, DoseStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = rememberDateFormatter(DateSkeletons.WEEKDAY_DAY_MONTH)
    CadenceCard(modifier) {
        CardTitle(
            pluralStringResource(R.plurals.today_missed_title, missed.size, missed.size),
            UiR.drawable.ms_schedule,
        )
        missed.forEach { dose ->
            Column(Modifier.padding(top = CadenceTheme.spacing.sm)) {
                Text(
                    stringResource(
                        R.string.today_missed_item,
                        dateFormat.formatHeading(dose.date),
                        stringResource(ContraceptionLabels.noun(dose.action)),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.sm)) {
                    FilledTonalButton(
                        onClick = { onRecord(dose.date, DoseStatus.TAKEN) },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        ButtonIcon(UiR.drawable.ms_check)
                        Text(stringResource(R.string.today_missed_taken))
                    }
                    TextButton(onClick = { onRecord(dose.date, DoseStatus.MISSED) }, shapes = ButtonDefaults.shapes()) {
                        ButtonIcon(UiR.drawable.ms_close)
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
        CardTitle(stringResource(R.string.today_medications_title), UiR.drawable.ms_pill)
        medications.forEach { (medication, taken) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = CadenceTheme.spacing.minTouchTarget)
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
        CardTitle(stringResource(R.string.today_bc_setup_title), UiR.drawable.ms_pill)
        Text(stringResource(R.string.today_bc_setup_body), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onOpenSettings, shapes = ButtonDefaults.shapes()) {
            ButtonIcon(UiR.drawable.ms_settings)
            Text(stringResource(R.string.today_bc_setup_action))
        }
    }
}

@Composable
private fun CardTitle(
    text: String,
    @DrawableRes icon: Int,
) {
    CardHeader(
        text,
        icon = painterResource(icon),
        accent = MaterialTheme.colorScheme.secondary,
        accentContainer = MaterialTheme.colorScheme.secondaryContainer,
    )
}

/** A decorative icon before a button's label. */
@Composable
internal fun ButtonIcon(
    @DrawableRes icon: Int,
) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
    )
}
