package app.cadence.core.domain.contraception

import app.cadence.core.domain.model.Period
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** The scheduled hormonal methods Cadence can follow. */
enum class ContraceptiveMethod { PILL, PATCH, RING }

/** What the schedule asks for on a given day. */
enum class DoseAction {
    ACTIVE_PILL,

    /** A placebo pill during the break: keeps the daily habit, but missing one doesn't matter. */
    PLACEBO_PILL,

    /** Apply a new patch (the first of a pack, or the weekly change). */
    NEW_PATCH,
    REMOVE_PATCH,

    /** Insert a new ring (on continuous use, this replaces the old one). */
    NEW_RING,
    REMOVE_RING,
    ;

    /** Whether forgetting it matters, so it's worth a missed-dose prompt. */
    val required: Boolean
        get() = this != PLACEBO_PILL
}

/** What the user recorded for a scheduled dose. */
enum class DoseStatus {
    TAKEN,

    /** The user confirmed they missed it, so Cadence stops asking. */
    MISSED,
}

/**
 * A repeating pack: [activeDays] of pills, patches or a ring, then [breakDays] off (or on
 * placebo pills). With no break, use is continuous. Packs repeat forwards and backwards
 * from [packStart], so any day of any pack works as the anchor.
 */
data class ContraceptiveSchedule(
    val method: ContraceptiveMethod,
    /** Day 1 of a pack. */
    val packStart: LocalDate,
    val activeDays: Int,
    val breakDays: Int,
    /** Pills only: placebo pills are taken during the break. */
    val placebo: Boolean = false,
    /** When this layout was set up; doses before it are never reported as missed. */
    val since: LocalDate = packStart,
    val reminderEnabled: Boolean = false,
    val reminderTime: LocalTime = DEFAULT_REMINDER_TIME,
    /** Packs on hand, for refill reminders; null when the user doesn't track them. */
    val supply: PackSupply? = null,
) {
    init {
        require(activeDays in ACTIVE_DAYS_RANGE) { "activeDays must be in $ACTIVE_DAYS_RANGE, was $activeDays" }
        require(breakDays in BREAK_DAYS_RANGE) { "breakDays must be in $BREAK_DAYS_RANGE, was $breakDays" }
        require(method != ContraceptiveMethod.PATCH || activeDays % DAYS_PER_PATCH == 0) {
            "Patches are changed weekly, so activeDays must be whole weeks"
        }
        require(!placebo || (method == ContraceptiveMethod.PILL && breakDays > 0)) {
            "Only pill packs with a break can have placebo pills"
        }
    }

    val packLength: Int
        get() = activeDays + breakDays

    /** False on continuous use, when no withdrawal bleed is scheduled. */
    val hasBreak: Boolean
        get() = breakDays > 0

    /**
     * The first day with no pack left: the counted packs run from the start of the pack in use on
     * the day they were counted. Null when packs aren't tracked.
     */
    fun runsOutOn(): LocalDate? = supply?.let { s -> packStartFor(s.countedOn).plusDays(s.packs.toLong() * packLength) }

    /** Packs left on [date], counting the one in use; 0 once they've run out or aren't tracked. */
    fun packsLeftOn(date: LocalDate): Int {
        val runsOut = runsOutOn() ?: return 0
        if (!date.isBefore(runsOut)) return 0
        val days = ChronoUnit.DAYS.between(packStartFor(date), runsOut)
        return ((days + packLength - 1) / packLength).toInt()
    }

    /** 1-based day of the pack [date] falls in. */
    fun dayOfPack(date: LocalDate): Int =
        Math.floorMod(ChronoUnit.DAYS.between(packStart, date), packLength.toLong()).toInt() + 1

    /** Day 1 of the pack [date] falls in. */
    fun packStartFor(date: LocalDate): LocalDate = date.minusDays(dayOfPack(date) - 1L)

    fun inBreak(date: LocalDate): Boolean = dayOfPack(date) > activeDays

    fun actionOn(date: LocalDate): DoseAction? {
        val day = dayOfPack(date)
        val firstBreakDay = activeDays + 1
        return when (method) {
            ContraceptiveMethod.PILL -> {
                when {
                    day <= activeDays -> DoseAction.ACTIVE_PILL
                    placebo -> DoseAction.PLACEBO_PILL
                    else -> null
                }
            }

            ContraceptiveMethod.PATCH -> {
                when {
                    day <= activeDays && (day - 1) % DAYS_PER_PATCH == 0 -> DoseAction.NEW_PATCH
                    day == firstBreakDay -> DoseAction.REMOVE_PATCH
                    else -> null
                }
            }

            ContraceptiveMethod.RING -> {
                when (day) {
                    1 -> DoseAction.NEW_RING
                    firstBreakDay -> DoseAction.REMOVE_RING
                    else -> null
                }
            }
        }
    }

    /**
     * The breaks (when a withdrawal bleed is expected) of the next [count] packs, starting
     * with one still running on [from]; days before [from] are left out. Empty on continuous use.
     */
    fun upcomingBreaks(
        from: LocalDate,
        count: Int,
    ): List<Period> {
        if (!hasBreak) return emptyList()
        val firstPack = packStartFor(from)
        // The break ends its pack, so the pack containing [from] never has a break wholly in the past.
        return (0 until count).map { i ->
            val start = firstPack.plusDays(i.toLong() * packLength + activeDays)
            Period(maxOf(start, from), start.plusDays(breakDays - 1L))
        }
    }

    /** Where [date] sits in its pack. */
    fun packDay(date: LocalDate): PackDay {
        val day = dayOfPack(date)
        val inBreak = day > activeDays
        val packStart = packStartFor(date)
        val breakStart = packStart.plusDays(activeDays.toLong())
        return PackDay(
            dayOfPack = day,
            inBreak = inBreak,
            dayOfBreak = if (inBreak) day - activeDays else null,
            daysUntilBreak = if (hasBreak && !inBreak) activeDays - day + 1 else null,
            action = actionOn(date),
            currentOrNextBreak = if (hasBreak) Period(breakStart, breakStart.plusDays(breakDays - 1L)) else null,
        )
    }

    companion object {
        val ACTIVE_DAYS_RANGE = 1..365
        val BREAK_DAYS_RANGE = 0..7
        const val DAYS_PER_PATCH = 7
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

        /** The usual layout for each method: three weeks on, one week off. */
        fun typical(
            method: ContraceptiveMethod,
            packStart: LocalDate,
        ) = ContraceptiveSchedule(method, packStart, activeDays = 21, breakDays = 7)
    }
}

/** Where a day sits in its pack, for the Today screen. */
data class PackDay(
    val dayOfPack: Int,
    val inBreak: Boolean,
    /** 1-based day of the break, during a break. */
    val dayOfBreak: Int?,
    /** Days until the break starts; null during a break or on continuous use. */
    val daysUntilBreak: Int?,
    val action: DoseAction?,
    /** This pack's break (which may be under way); null on continuous use. */
    val currentOrNextBreak: Period?,
)

/** A scheduled dose with nothing recorded for it. */
data class PendingDose(
    val date: LocalDate,
    val action: DoseAction,
)

object MissedDoses {
    /** How far back Cadence asks about doses that weren't checked off. */
    const val LOOKBACK_DAYS = 7L

    /**
     * Required doses in the last week (not counting today) with nothing recorded, oldest
     * first. Never earlier than when the schedule was set up.
     */
    fun find(
        schedule: ContraceptiveSchedule,
        doses: Map<LocalDate, DoseStatus>,
        today: LocalDate,
    ): List<PendingDose> =
        (LOOKBACK_DAYS downTo 1L)
            .map { today.minusDays(it) }
            .filterNot { it.isBefore(schedule.since) || it in doses }
            .mapNotNull { date -> schedule.actionOn(date)?.takeIf { it.required }?.let { PendingDose(date, it) } }
}

/**
 * How many packs (of pills, patches or rings) the user has, counting the one in use, as of
 * [countedOn]. Updated when they pick up more.
 */
data class PackSupply(
    val packs: Int,
    val countedOn: LocalDate,
) {
    init {
        require(packs in PACKS_RANGE) { "packs must be in $PACKS_RANGE, was $packs" }
    }

    companion object {
        val PACKS_RANGE = 1..24

        /** Reminders come this many days before the last pack runs out, at the dose reminder time. */
        val REMIND_DAYS_BEFORE = listOf(5L, 1L)
    }
}
