package app.cadence

import android.app.Application
import app.cadence.feature.reminders.ReminderChannels
import app.cadence.feature.reminders.ReminderSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CadenceApplication : Application() {
    @Inject
    lateinit var reminderSync: ReminderSync

    override fun onCreate() {
        super.onCreate()
        ReminderChannels.ensure(this)
        // Keeps reminder alarms in step with logged data and settings.
        reminderSync.start()
    }
}
