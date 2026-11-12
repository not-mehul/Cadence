package app.cadence.core.domain.medication

import java.time.LocalTime

enum class MedicationUse {
    /** Taken every day, like a supplement; can have a daily reminder. */
    DAILY,

    /** Taken when needed, like a painkiller; logged on the days it's taken. */
    AS_NEEDED,
}

/** A medication the user tracks besides their birth control. */
data class Medication(
    val id: Long,
    val name: String,
    val use: MedicationUse,
    /** Daily medications only: when to remind, or null for no reminder. */
    val reminderTime: LocalTime? = null,
    /** Stopped medications are kept for their history but hidden from Today and reminders. */
    val active: Boolean = true,
) {
    init {
        require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH) { "name must be 1 to $MAX_NAME_LENGTH characters" }
        require(reminderTime == null || use == MedicationUse.DAILY) { "Only daily medications have reminders" }
    }

    val remindsDaily: Boolean
        get() = active && reminderTime != null

    companion object {
        const val MAX_NAME_LENGTH = 40
    }
}
