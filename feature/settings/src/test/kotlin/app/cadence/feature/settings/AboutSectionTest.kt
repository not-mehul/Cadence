package app.cadence.feature.settings

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.Signature
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.cadence.core.ui.theme.CadenceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class AboutSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the certificate fingerprint is the SHA-256 of the certificate, as apksigner prints it`() {
        // SHA-256 of the bytes "abc".
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            AppInfo.sha256Hex(Signature("616263")),
        )
    }

    @Test
    fun `about shows the version, where updates come from, and the disclaimer`() {
        val info =
            PackageInfo().apply {
                packageName = context.packageName
                versionName = "1.2.3"
            }
        shadowOf(context.packageManager).installPackage(info)

        compose.setContent { CadenceTheme { AboutSection() } }

        compose.onNodeWithText("Version 1.2.3").assertExists()
        compose.onNodeWithText("Cadence can't check for updates", substring = true).assertExists()
        compose.onNodeWithText("Open the releases page").assertExists()
        compose.onNodeWithText("not a medical device", substring = true).assertExists()
    }
}
