package app.cadence.feature.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.cadence.core.ui.theme.CadenceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class LanguageSettingTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val localeManager = context.getSystemService(LocaleManager::class.java)

    @Test
    fun `languages are named in their own language`() {
        assertEquals("English", AppLanguages.displayName("en"))
        assertEquals("Русский", AppLanguages.displayName("ru"))
    }

    @Test
    fun `the app follows the phone until a language is chosen, and can go back`() {
        assertNull(AppLanguages.current(context))

        AppLanguages.set(context, "ru")
        assertEquals("ru", localeManager.applicationLocales.toLanguageTags())
        assertEquals("ru", AppLanguages.current(context))

        AppLanguages.set(context, null)
        assertNull(AppLanguages.current(context))
    }

    @Test
    fun `choosing Russian in settings applies it to the app`() {
        compose.setContent { CadenceTheme { LanguageRow() } }

        compose.onNodeWithText("Same as your phone").assertExists()
        compose.onNodeWithText("Language").performClick()
        compose.onNodeWithText("Русский").performClick()

        assertEquals("ru", localeManager.applicationLocales.toLanguageTags())
        compose.onNodeWithText("English").assertDoesNotExist()
    }

    @Test
    fun `the current language is the selected choice`() {
        AppLanguages.set(context, "ru")
        compose.setContent { CadenceTheme { LanguageRow() } }

        compose.onNodeWithText("Language").performClick()
        compose.onNode(hasTextExactly("Русский") and isSelectable()).assertIsSelected()
        compose.onNode(hasTextExactly("English") and isSelectable()).assertIsNotSelected()
    }

    @Test
    fun `before Android 13 the row opens the phone's language settings`() {
        val sdk = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.S)
        try {
            compose.setContent { CadenceTheme { LanguageRow() } }
            compose.onNodeWithText("Follows your phone's language", substring = true).assertExists()
            compose.onNodeWithText("Language").performClick()
            compose.waitForIdle()
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
        }
        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(Settings.ACTION_LOCALE_SETTINGS, started.action)
    }
}
