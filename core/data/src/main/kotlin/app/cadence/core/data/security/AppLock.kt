package app.cadence.core.data.security

import app.cadence.core.domain.security.SecuritySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the app is locked. Starts locked, so content is never shown
 * before the user has unlocked; whether that matters depends on [SecuritySettings.lockEnabled].
 * The app calls [onBackground] and [onForeground] from the process lifecycle.
 */
@Singleton
class AppLock
    @Inject
    constructor() {
        private val mutableLocked = MutableStateFlow(true)
        val locked: StateFlow<Boolean> = mutableLocked.asStateFlow()

        private var backgroundedAt: Long? = null

        fun unlock() {
            mutableLocked.value = false
        }

        /** [nowMillis] from a monotonic clock (SystemClock.elapsedRealtime). */
        fun onBackground(nowMillis: Long) {
            backgroundedAt = nowMillis
        }

        fun onForeground(
            nowMillis: Long,
            security: SecuritySettings,
        ) {
            val since = backgroundedAt ?: return
            backgroundedAt = null
            if (security.shouldRelock(nowMillis - since)) mutableLocked.value = true
        }

        /** Whether content must be hidden right now. */
        fun mustHide(security: SecuritySettings): Boolean = security.lockEnabled && locked.value
    }
