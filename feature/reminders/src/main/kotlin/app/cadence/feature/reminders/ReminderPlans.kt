package app.cadence.feature.reminders

import app.cadence.core.data.repository.ContraceptionRepository
import app.cadence.core.data.repository.CycleAnnotationRepository
import app.cadence.core.data.repository.MedicationRepository
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.reminders.PlannedReminder
import app.cadence.core.domain.reminders.RecordedDoses
import app.cadence.core.domain.reminders.ReminderPlanner
import app.cadence.core.domain.reminders.ReminderSettings
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject

/** Everything reminders are planned from, read once. */
class ReminderSnapshot(
    val planned: List<PlannedReminder>,
    val medications: List<Medication>,
    val settings: ReminderSettings,
)

/** Reads the current data and plans reminders from it (see [ReminderPlanner]). */
class ReminderPlans
    @Inject
    constructor(
        private val periods: PeriodRepository,
        private val settings: SettingsRepository,
        private val annotations: CycleAnnotationRepository,
        private val contraception: ContraceptionRepository,
        private val medications: MedicationRepository,
    ) {
        suspend fun at(now: LocalDateTime): ReminderSnapshot {
            val userSettings = settings.settings.first()
            val meds = medications.medications.first()
            val planned =
                ReminderPlanner.plan(
                    flowByDay = periods.flowByDay.first(),
                    settings = userSettings,
                    excludedCycleStarts = annotations.excludedStarts.first(),
                    now = now,
                    medications = meds,
                    doses = RecordedDoses(contraception.doses.first(), medications.doses.first()),
                )
            return ReminderSnapshot(planned, meds, userSettings.reminders)
        }
    }
