package app.cadence.feature.reminders

import android.util.Log
import app.cadence.core.data.repository.ContraceptionRepository
import app.cadence.core.data.repository.CycleAnnotationRepository
import app.cadence.core.data.repository.MedicationRepository
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps alarms in step with the data: whenever periods, settings or excluded cycles change,
 * reminders are re-planned (e.g. logging a period early moves the next reminder).
 */
@Singleton
class ReminderSync
    @Inject
    constructor(
        private val scheduler: ReminderScheduler,
        private val periods: PeriodRepository,
        private val settings: SettingsRepository,
        private val annotations: CycleAnnotationRepository,
        private val contraception: ContraceptionRepository,
        private val medications: MedicationRepository,
    ) {
        private val scope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.Default +
                    CoroutineExceptionHandler { _, e -> Log.w("CadenceReminders", "Reminder sync failed", e) },
            )
        private var started = false

        @OptIn(FlowPreview::class)
        @Synchronized
        fun start() {
            if (started) return
            started = true
            scope.launch {
                combine(
                    periods.flowByDay,
                    settings.settings,
                    annotations.excludedStarts,
                    contraception.doses,
                    combine(medications.medications, medications.doses, ::Pair),
                ) { a, b, c, d, e -> listOf(a, b, c, d, e) }
                    .distinctUntilChanged()
                    .debounce(DEBOUNCE_MS)
                    .collect { scheduler.reschedule() }
            }
        }

        private companion object {
            const val DEBOUNCE_MS = 500L
        }
    }
