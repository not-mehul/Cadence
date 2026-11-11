package app.cadence.core.ui.expressive

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * False when the user turned animations off (Settings → Accessibility → Remove animations,
 * or the developer animator scale) and in previews. Decorative motion is skipped then.
 *
 * Every decorative animation in Cadence is finite (a bounce in, a couple of blinks), never a
 * loop, so screens settle and tests can wait for them.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    return remember(context, inspecting) {
        !inspecting &&
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
