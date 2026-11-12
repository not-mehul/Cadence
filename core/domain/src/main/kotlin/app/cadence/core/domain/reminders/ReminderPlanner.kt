package app.cadence.core.domain.reminders

import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseAction
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.contraception.PackSupply
import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.cycles.CyclePrediction
import app.cadence.core.domain.cycles.CyclePredictor
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import java.time.LocalDate
import java.time.LocalDateTime

enum class ReminderKind {
    /** The period is expected in a few days. */
    UPCOMING_PERIOD,

    /** The period is past the end of its likely range. */
    LATE_PERIOD,

    /** Daily prompt to log symptoms and mood. */
    DAILY_LOG,

    /** Today's pill, patch or ring (Phase 6). */
    CONTRACEPTIVE,

    /** A daily medication; one per medication, told apart by [PlannedReminder.medicationId]. */
    MEDICATION,

    /** The packs on hand are about to run out. */
    REFILL,
}

/** The next time a reminder should fire, in local wall-clock time. */
data class PlannedReminder(
    val kind: ReminderKind,
    val at: LocalDateTime,
    /** For the upcoming-period reminder: days until the expected start. */
    val daysUntilPeriod: Int? = null,
    /** For the birth control reminder: what to do. */
    val action: DoseAction? = null,
    /** For a medication reminder: which medication. */
    val medicationId: Long? = null,
    /** For a refill reminder: the first day with no pack left. */
    val runsOutOn: LocalDate? = null,
) {
    /** The date the reminder is about (a dose is checked off against this day). */
    val date: LocalDate
        get() = at.toLocalDate()
}

/** Doses already recorded, so their reminders are skipped. */
data class RecordedDoses(
    val contraceptive: Map<LocalDate, DoseStatus> = emptyMap(),
    /** Medication ids taken on each day. */
    val medications: Map<LocalDate, Set<Long>> = emptyMap(),
)

/**
 * Works out the next occurrence of each enabled reminder. Only future times are returned;
 * a reminder whose moment has passed is simply not planned. The app re-plans whenever
 * data or settings change and after each reminder fires.
 */
object ReminderPlanner {
    fun plan(
        flowByDay: Map<LocalDate, MenstrualFlow>,
        settings: UserSettings,
        excludedCycleStarts: Set<LocalDate>,
        now: LocalDateTime,
        medications: List<Medication> = emptyList(),
        doses: RecordedDoses = RecordedDoses(),
    ): List<PlannedReminder> {
        val reminders = settings.reminders
        val today = now.toLocalDate()
        val schedule = settings.activeSchedule
        val history = CycleHistory.from(flowByDay, excludedCycleStarts)
        val prediction = CyclePredictor.predict(history, settings.cycleDefaults, today)
        val onPeriod = history.latestPeriodStartedBy(today)?.let { today in it } == true

        return buildList {
            // On a pill, patch or ring schedule, bleeds follow the pack, so period reminders don't apply.
            if (schedule == null) addAll(periodReminders(reminders, prediction, onPeriod, now))
            if (reminders.dailyEnabled) {
                val todayAt = today.atTime(reminders.dailyTime)
                add(PlannedReminder(ReminderKind.DAILY_LOG, if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)))
            }
            if (schedule != null && schedule.reminderEnabled) {
                contraceptiveReminder(schedule, doses.contraceptive, now)?.let(::add)
            }
            medications.filter { it.remindsDaily }.forEach { medication ->
                add(medicationReminder(medication, doses.medications, now))
            }
            schedule?.let { refillReminder(it, now) }?.let(::add)
        }
    }

    /** The next of the refill nudges before the packs run out, at the dose reminder time. */
    private fun refillReminder(
        schedule: ContraceptiveSchedule,
        now: LocalDateTime,
    ): PlannedReminder? {
        val runsOut = schedule.runsOutOn() ?: return null
        return PackSupply.REMIND_DAYS_BEFORE
            .map { runsOut.minusDays(it).atTime(schedule.reminderTime) }
            .sorted()
            .firstOrNull { it.isAfter(now) }
            ?.let { PlannedReminder(ReminderKind.REFILL, it, runsOutOn = runsOut) }
    }

    private fun periodReminders(
        reminders: ReminderSettings,
        prediction: CyclePrediction?,
        onPeriod: Boolean,
        now: LocalDateTime,
    ): List<PlannedReminder> =
        buildList {
            if (reminders.periodEnabled && prediction != null && !onPeriod && prediction.daysLate == 0) {
                val at =
                    prediction.expectedNextStart
                        .minusDays(
                            reminders.periodDaysBefore.toLong(),
                        ).atTime(reminders.periodTime)
                if (at.isAfter(now)) add(PlannedReminder(ReminderKind.UPCOMING_PERIOD, at, reminders.periodDaysBefore))
            }
            if (reminders.lateEnabled && prediction != null && !onPeriod) {
                // The first day the period counts as late: the day after its likely range ends.
                val at = prediction.latestNextStart.plusDays(1).atTime(reminders.periodTime)
                if (at.isAfter(now)) add(PlannedReminder(ReminderKind.LATE_PERIOD, at))
            }
        }

    /** The next scheduled dose not yet recorded whose reminder time is still ahead. */
    private fun contraceptiveReminder(
        schedule: ContraceptiveSchedule,
        recorded: Map<LocalDate, DoseStatus>,
        now: LocalDateTime,
    ): PlannedReminder? {
        val today = now.toLocalDate()
        // Two packs ahead is plenty: re-planning happens daily and after every dose.
        val date =
            (0L..2L * schedule.packLength)
                .map(today::plusDays)
                .firstOrNull { date ->
                    schedule.actionOn(date) != null &&
                        date !in recorded &&
                        date.atTime(schedule.reminderTime).isAfter(now)
                } ?: return null
        return PlannedReminder(
            ReminderKind.CONTRACEPTIVE,
            date.atTime(schedule.reminderTime),
            action = schedule.actionOn(date),
        )
    }

    private fun medicationReminder(
        medication: Medication,
        taken: Map<LocalDate, Set<Long>>,
        now: LocalDateTime,
    ): PlannedReminder {
        val time = medication.reminderTime!!
        val first = now.toLocalDate()
        val date =
            generateSequence(first) { it.plusDays(1) }.first { day ->
                day.atTime(time).isAfter(now) && medication.id !in taken[day].orEmpty()
            }
        return PlannedReminder(ReminderKind.MEDICATION, date.atTime(time), medicationId = medication.id)
    }
}
