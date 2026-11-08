package app.cadence.core.data.backup

import kotlinx.serialization.Serializable

/**
 * The backup file format (docs/PLAN.md Phase 5). Plain values only, so the file is readable
 * and stable across app versions. Bump [VERSION] for incompatible changes and keep reading
 * older versions.
 */
@Serializable
data class BackupDocument(
    val format: String = FORMAT,
    val version: Int = VERSION,
    /** ISO-8601 instant. */
    val exportedAt: String,
    val settings: BackupSettings,
    val days: List<BackupDay>,
    /** ISO dates: first days of cycles excluded from predictions. */
    val excludedCycleStarts: List<String> = emptyList(),
) {
    companion object {
        const val FORMAT = "cadence-backup"
        const val VERSION = 1
    }
}

@Serializable
data class BackupSettings(
    val goal: String,
    val cycleLength: Int,
    val periodLength: Int,
    val lutealPhaseLength: Int,
    val theme: String,
    val hiddenCategories: List<String> = emptyList(),
    val periodReminderEnabled: Boolean = false,
    val periodReminderDaysBefore: Int = 2,
    /** "HH:mm" */
    val periodReminderTime: String = "09:00",
    val lateReminderEnabled: Boolean = false,
    val dailyReminderEnabled: Boolean = false,
    val dailyReminderTime: String = "20:00",
    val discreetNotifications: Boolean = true,
)

@Serializable
data class BackupDay(
    /** ISO date. */
    val date: String,
    val flow: String? = null,
    /** Symptom id to severity name. */
    val symptoms: Map<String, String> = emptyMap(),
    val moods: List<String> = emptyList(),
    val painLevel: Int? = null,
    val energy: Int? = null,
    val sleep: Int? = null,
    val stress: Int? = null,
    val sex: String? = null,
    val note: String? = null,
)

/** A backup file couldn't be read. [reason] says why, for a helpful message. */
class BackupException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(reason.name, cause) {
    enum class Reason {
        /** Not a Cadence backup at all. */
        NOT_A_BACKUP,

        /** Made by a newer version of Cadence. */
        NEWER_VERSION,

        /** Encrypted; a password is needed. */
        PASSWORD_REQUIRED,

        WRONG_PASSWORD,

        /** A Cadence backup, but its contents are damaged or invalid. */
        INVALID,
    }
}
