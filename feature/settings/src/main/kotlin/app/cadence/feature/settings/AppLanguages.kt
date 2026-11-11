package app.cadence.feature.settings

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.util.Locale
import app.cadence.core.ui.R as UiR

/**
 * The languages Cadence is translated into, and the per-app language choice.
 *
 * Android 13 and later remember a language for each app; on older versions the app follows the phone's
 * language, so there is nothing to choose here.
 */
object AppLanguages {
    /** Language tags, matching the `values-<tag>` resource folders (English is the unqualified default). */
    val TAGS: List<String> = listOf("en", "ru")

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val canChooseInApp: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** The chosen language tag, or null when the app follows the phone. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun current(context: Context): String? {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        if (locales.isEmpty) return null
        return TAGS.firstOrNull { it == locales[0].language }
    }

    /** Applies [tag] (null follows the phone again). The system recreates the activity in the new language. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun set(
        context: Context,
        tag: String?,
    ) {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    /** A language's name in that language ("English", "Русский"), so it can be found from any language. */
    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }
}

/** The "Language" row: a chooser on Android 13+, otherwise a shortcut to the phone's language settings. */
@Composable
internal fun LanguageRow() {
    val context = LocalContext.current
    if (!AppLanguages.canChooseInApp) {
        SettingRow(
            icon = UiR.drawable.ms_translate,
            title = stringResource(R.string.settings_language),
            value = stringResource(R.string.settings_language_follows_phone),
            onClick = { context.openSettings(Intent(Settings.ACTION_LOCALE_SETTINGS)) },
        )
        return
    }
    LanguageChooserRow(context)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LanguageChooserRow(context: Context) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val current = AppLanguages.current(context)
    val label: @Composable (String?) -> String = { tag ->
        tag?.let(AppLanguages::displayName) ?: stringResource(R.string.settings_language_system)
    }
    SettingRow(
        icon = UiR.drawable.ms_translate,
        title = stringResource(R.string.settings_language),
        value = label(current),
        onClick = { choosing = true },
    )
    if (choosing) {
        ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = listOf<String?>(null) + AppLanguages.TAGS,
            selected = current,
            label = label,
            detail = { null },
            onSelect = {
                choosing = false
                AppLanguages.set(context, it)
            },
            onDismiss = { choosing = false },
        )
    }
}
