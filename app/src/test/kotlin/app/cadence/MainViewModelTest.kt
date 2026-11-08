package app.cadence

import android.app.Application
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.security.AppLock
import app.cadence.core.data.security.DatabaseRecovery
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.security.SecuritySettings
import app.cadence.core.testing.FakeSettingsRepository
import app.cadence.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MainViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val appLock = AppLock()

    private fun vm(settings: SettingsRepository) = MainViewModel(settings, appLock, DatabaseRecovery(context))

    private fun MainViewModel.await(condition: (MainUiState) -> Boolean) =
        runBlocking { withTimeout(10_000) { uiState.first(condition) } }

    @Test
    fun `without app lock the app is never locked`() {
        val state =
            vm(
                FakeSettingsRepository(UserSettings(theme = ThemePreference.DARK, onboardingComplete = true)),
            ).uiState.value

        assertEquals(MainUiState.Ready(ThemePreference.DARK, true, SecuritySettings(), locked = false), state)
    }

    @Test
    fun `with app lock the app starts locked until unlocked`() {
        val settings =
            FakeSettingsRepository(
                UserSettings(onboardingComplete = true, security = SecuritySettings(lockEnabled = true)),
            )
        val vm = vm(settings)

        assertIs<MainUiState.Ready>(vm.uiState.value).also { assertEquals(true, it.locked) }
        vm.unlock()
        assertFalse(assertIs<MainUiState.Ready>(vm.uiState.value).locked)

        // Re-locks after being away when the timeout is "immediately".
        appLock.onBackground(nowMillis = 0)
        appLock.onForeground(nowMillis = 1, settings.settings.value.security)
        assertEquals(true, assertIs<MainUiState.Ready>(vm.uiState.value).locked)
    }

    @Test
    fun `unreadable data offers to start over, which erases it and asks for a restart`() {
        val keyFile = File(context.noBackupFilesDir, DatabaseRecovery.KEY_FILE_NAME).apply { writeText("old key") }
        val vm = vm(UnreadableSettings(SQLiteException("file is not a database")))

        vm.await { it == MainUiState.DataUnavailable }
        vm.startOver()

        vm.await { it == MainUiState.RestartRequired }
        assertFalse(keyFile.exists())
    }

    private class UnreadableSettings(
        private val error: Throwable,
    ) : SettingsRepository {
        override val settings: Flow<UserSettings> = flow { throw error }

        override suspend fun update(transform: (UserSettings) -> UserSettings) = throw error
    }
}
