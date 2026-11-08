package app.cadence.core.domain.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecuritySettingsTest {
    @Test
    fun `by default there is no lock and screenshots are blocked`() {
        val s = SecuritySettings()
        assertFalse(s.lockEnabled)
        assertTrue(s.blockScreenshots)
        assertEquals(listOf(0, 1, 5), SecuritySettings.LOCK_TIMEOUT_OPTIONS)
    }

    @Test
    fun `never relocks when the lock is off`() {
        assertFalse(SecuritySettings(lockEnabled = false).shouldRelock(Long.MAX_VALUE))
    }

    @Test
    fun `immediate lock relocks after any time away`() {
        assertTrue(SecuritySettings(lockEnabled = true, lockTimeoutMinutes = 0).shouldRelock(0))
    }

    @Test
    fun `timed lock waits for the timeout`() {
        val s = SecuritySettings(lockEnabled = true, lockTimeoutMinutes = 5)
        assertFalse(s.shouldRelock(5 * 60_000L - 1))
        assertTrue(s.shouldRelock(5 * 60_000L))
    }

    @Test
    fun `only offered timeouts are accepted`() {
        assertThrows<IllegalArgumentException> { SecuritySettings(lockTimeoutMinutes = 3) }
    }
}
