package app.cadence

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.security.AppLock
import app.cadence.core.domain.security.SecuritySettings
import app.cadence.feature.healthconnect.HealthConnectSync
import app.cadence.feature.reminders.ReminderChannels
import app.cadence.feature.reminders.ReminderSync
import app.cadence.feature.widget.WidgetSync
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltAndroidApp
class CadenceApplication : Application() {
    @Inject
    lateinit var reminderSync: ReminderSync

    @Inject
    lateinit var appLock: AppLock

    @Inject
    lateinit var widgetSync: WidgetSync

    @Inject
    lateinit var healthConnect: HealthConnectSync

    @Inject
    lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Latest security settings, for re-locking when the app comes back to the foreground. */
    @Volatile
    private var security = SecuritySettings(lockEnabled = true)

    override fun onCreate() {
        super.onCreate()
        ReminderChannels.ensure(this)
        // Keeps reminder alarms in step with logged data and settings.
        reminderSync.start()
        // Keeps any home-screen widgets up to date while the app runs.
        widgetSync.start()
        // Mirrors period days to Health Connect, only if the user turned that on (granted access).
        healthConnect.start()
        settings.settings
            .onEach { security = it.security }
            .catch { /* Unreadable data is handled on screen; keep the safe default. */ }
            .launchIn(scope)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) = appLock.onBackground(SystemClock.elapsedRealtime())

                override fun onStart(owner: LifecycleOwner) =
                    appLock.onForeground(SystemClock.elapsedRealtime(), security)
            },
        )
    }
}
