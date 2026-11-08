package app.cadence.core.ui.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Asks for the phone's own fingerprint, face or screen lock. Using the device credential
 * (rather than a separate app PIN) means nothing extra to remember and works on every
 * supported Android version.
 */
object DeviceAuth {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** False when the phone has no screen lock set up, so the app lock can't be offered. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        onSuccess: () -> Unit,
        onFailure: () -> Unit = {},
    ) {
        val prompt =
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) = onFailure()
                },
            )
        prompt.authenticate(
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(title)
                .apply { subtitle?.let(::setSubtitle) }
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }
}
