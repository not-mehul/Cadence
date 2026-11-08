package app.cadence.core.domain.security

/** App lock and screen privacy (docs/PLAN.md §2.7). */
data class SecuritySettings(
    /** Ask for fingerprint, face or the phone's screen lock to open Cadence. */
    val lockEnabled: Boolean = false,
    /** How long Cadence may sit in the background before it locks again. */
    val lockTimeoutMinutes: Int = 0,
    /** Block screenshots, screen recording and the preview in recent apps. On by default. */
    val blockScreenshots: Boolean = true,
) {
    init {
        require(
            lockTimeoutMinutes in LOCK_TIMEOUT_OPTIONS,
        ) { "lockTimeoutMinutes must be one of $LOCK_TIMEOUT_OPTIONS" }
    }

    /** Whether, after [backgroundMillis] in the background, the app should ask to unlock again. */
    fun shouldRelock(backgroundMillis: Long): Boolean =
        lockEnabled && backgroundMillis >= lockTimeoutMinutes * MILLIS_PER_MINUTE

    companion object {
        /** Immediately, after 1 minute, after 5 minutes. */
        val LOCK_TIMEOUT_OPTIONS = listOf(0, 1, 5)
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
