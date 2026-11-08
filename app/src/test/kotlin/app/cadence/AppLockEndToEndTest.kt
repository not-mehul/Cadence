package app.cadence

import android.view.WindowManager
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.security.AppLock
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.security.SecuritySettings
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject
import kotlin.test.assertEquals

/** App lock and screenshot blocking through the real activity, view models and Room. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "en-rUS-w411dp-h891dp")
class AppLockEndToEndTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var appLock: AppLock

    @Before
    fun setUp() = hilt.inject()

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(TIMEOUT_MS) { shown(text) }

    private fun setSecurity(security: SecuritySettings) =
        runBlocking { settings.update { UserSettings(onboardingComplete = true, security = security) } }

    private val screenshotsBlocked
        get() = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun lockedAppShowsNothingButTheLockScreen() {
        setSecurity(SecuritySettings(lockEnabled = true))

        waitFor("Cadence is locked")
        // No tab, no cycle data: the app's content isn't composed at all while locked.
        assertEquals(false, shown("Today"))
        assertEquals(false, shown("Calendar"))
        assertEquals(true, screenshotsBlocked)

        // A successful fingerprint or screen-lock check.
        appLock.unlock()
        waitFor("Calendar")
        assertEquals(false, shown("Cadence is locked"))

        // Leaving the app re-locks it ("as soon as I leave" is the default).
        appLock.onBackground(nowMillis = 1_000)
        appLock.onForeground(nowMillis = 2_000, SecuritySettings(lockEnabled = true))
        waitFor("Cadence is locked")
        assertEquals(false, shown("Calendar"))
    }

    @Test
    fun aShortTripAwayDoesNotRelockWithATimeout() {
        val security = SecuritySettings(lockEnabled = true, lockTimeoutMinutes = 1)
        setSecurity(security)
        waitFor("Cadence is locked")
        appLock.unlock()
        waitFor("Calendar")

        appLock.onBackground(nowMillis = 0)
        appLock.onForeground(nowMillis = 30_000, security)
        compose.waitForIdle()
        assertEquals(true, shown("Calendar"))
    }

    @Test
    fun screenshotBlockingFollowsTheSetting() {
        setSecurity(SecuritySettings(blockScreenshots = false))
        waitFor("Calendar")
        compose.waitUntil(TIMEOUT_MS) { !screenshotsBlocked }

        setSecurity(SecuritySettings(blockScreenshots = true))
        compose.waitUntil(TIMEOUT_MS) { screenshotsBlocked }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
