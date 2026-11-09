package app.cadence

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import app.cadence.feature.settings.AppIcon
import app.cadence.feature.settings.AppIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** The launcher shows exactly one Cadence icon, and Settings can swap it for the discreet one. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppIconTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val pm = context.packageManager

    private fun component(icon: AppIcon) = android.content.ComponentName(context.packageName, icon.alias)

    /** Whether the alias is enabled, counting its manifest default. */
    private fun enabled(icon: AppIcon): Boolean =
        when (pm.getComponentEnabledSetting(component(icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> pm.getActivityInfo(component(icon), PackageManager.MATCH_DISABLED_COMPONENTS).enabled
        }

    private fun launcherEntries(): List<String> = AppIcon.entries.filter(::enabled).map { it.alias }

    @Test
    fun `one launcher icon, the standard one, by default`() {
        assertEquals(listOf(AppIcon.STANDARD.alias), launcherEntries())
        assertEquals(AppIcon.STANDARD, AppIcons.current(context))
    }

    @Test
    fun `switching to the discreet icon and back`() {
        AppIcons.set(context, AppIcon.DISCREET)
        assertEquals(listOf(AppIcon.DISCREET.alias), launcherEntries())
        assertEquals(AppIcon.DISCREET, AppIcons.current(context))

        AppIcons.set(context, AppIcon.STANDARD)
        assertEquals(listOf(AppIcon.STANDARD.alias), launcherEntries())
    }

    @Test
    fun `the activity itself isn't exported, only the launcher entries are`() {
        val info =
            context.packageManager.getActivityInfo(
                android.content.ComponentName(context, MainActivity::class.java),
                0,
            )
        assertEquals(false, info.exported)
    }
}
