package app.cadence.core.data.backup

import kotlinx.serialization.Serializable

/**
 * The backup file format. Plain values only, so the file is readable
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
    // Version 2. Version 1 files simply have none of these.
    val contraceptiveDoses: List<BackupDose> = emptyList(),
    val medications: List<BackupMedication> = emptyList(),
    val medicationDoses: List<BackupMedicationDose> = emptyList(),
    val customTags: List<BackupTag> = emptyList(),
) {
    companion object {
        const val FORMAT = "cadence-backup"

        /** 2: birth control schedule and doses, medications, custom tags. */
        const val VERSION = 2
    }
}

@Serializable
data class BackupContraception(
    val method: String,
    /** ISO date. */
    val packStart: String,
    val activeDays: Int,
    val breakDays: Int,
    val placebo: Boolean = false,
    /** ISO date. */
    val since: String,
    val reminderEnabled: Boolean = false,
    /** "HH:mm" */
    val reminderTime: String = "09:00",
    /** Packs on hand, for refill reminders (added without a format change: older files just lack it). */
    val supplyPacks: Int? = null,
    /** ISO date the packs were counted. */
    val supplyCountedOn: String? = null,
)

@Serializable
data class BackupDose(
    /** ISO date. */
    val date: String,
    val status: String,
)

@Serializable
data class BackupMedication(
    val id: Long,
    val name: String,
    val use: String,
    /** "HH:mm", daily medications only. */
    val reminderTime: String? = null,
    val active: Boolean = true,
)

@Serializable
data class BackupMedicationDose(
    /** ISO date. */
    val date: String,
    val medicationId: Long,
)

@Serializable
data class BackupTag(
    val id: Long,
    val label: String,
)

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
    /** Version 2. Kept even in tracking mode, like in the app. */
    val contraception: BackupContraception? = null,
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
    /** Version 2: custom tag ids (see [BackupDocument.customTags]). */
    val tags: List<Long> = emptyList(),
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
