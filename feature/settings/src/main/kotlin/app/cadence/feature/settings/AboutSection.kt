package app.cadence.feature.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.core.net.toUri
import app.cadence.core.ui.components.SectionHeader
import app.cadence.core.ui.theme.CadenceTheme
import java.security.MessageDigest

/**
 * Version, signing certificate and where updates come from. Cadence
 * has no internet access, so it can't check for updates itself.
 */
@Composable
internal fun AboutSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info = remember(context) { AppInfo.read(context) }
    val gutter = CadenceTheme.spacing.screenGutter
    SettingsSection(stringResource(R.string.settings_section_about), modifier) {
        Note(stringResource(R.string.settings_version, info.version))
        Note(stringResource(R.string.settings_credit))
        Note(stringResource(R.string.settings_updates))
        val releasesUrl = stringResource(R.string.settings_releases_url)
        TextButton(onClick = {
            context.openUrl(releasesUrl)
        }, modifier = Modifier.padding(horizontal = CadenceTheme.spacing.sm)) {
            Text(stringResource(R.string.settings_updates_open))
        }
        info.certificateSha256?.let { fingerprint ->
            Note(stringResource(R.string.settings_certificate))
            Text(
                fingerprint,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = gutter),
            )
        }
        Note(stringResource(R.string.settings_disclaimer))
        Note(stringResource(R.string.settings_font_credit))
    }
}

internal data class AppInfo(
    val version: String,
    /** Lower-case hex, the way apksigner and the release notes print it. */
    val certificateSha256: String?,
) {
    companion object {
        fun read(context: Context): AppInfo {
            val pm = context.packageManager
            val version = pm.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            return AppInfo(version, signingCertificate(context)?.let(::sha256Hex))
        }

        // Only reads Cadence's own certificate to display it; nothing is trusted on the strength of it.
        @SuppressLint("PackageManagerGetSignatures")
        @Suppress("DEPRECATION")
        private fun signingCertificate(context: Context): Signature? {
            val pm = context.packageManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm
                    .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo
                    ?.apkContentsSigners
                    ?.firstOrNull()
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
            }
        }

        fun sha256Hex(certificate: Signature): String =
            MessageDigest
                .getInstance(
                    "SHA-256",
                ).digest(certificate.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser; the address is shown on the button's screen anyway.
    }
}
