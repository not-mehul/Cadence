package app.cadence.core.data.security

import app.cadence.core.domain.security.SecuritySettings
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockTest {
    private val immediate = SecuritySettings(lockEnabled = true, lockTimeoutMinutes = 0)
    private val fiveMinutes = SecuritySettings(lockEnabled = true, lockTimeoutMinutes = 5)

    @Test
    fun `starts locked, and content is hidden only when the lock is on`() {
        val lock = AppLock()
        assertTrue(lock.locked.value)
        assertTrue(lock.mustHide(immediate))
        assertFalse(lock.mustHide(SecuritySettings()))
    }

    @Test
    fun `unlocking shows content until the app is backgrounded`() {
        val lock = AppLock()
        lock.unlock()
        assertFalse(lock.mustHide(immediate))

        lock.onBackground(1_000)
        lock.onForeground(1_500, immediate)
        assertTrue(lock.mustHide(immediate))
    }

    @Test
    fun `a timed lock waits`() {
        val lock = AppLock()
        lock.unlock()
        lock.onBackground(0)
        lock.onForeground(4 * 60_000, fiveMinutes)
        assertFalse(lock.locked.value)

        lock.onBackground(10_000_000)
        lock.onForeground(10_000_000 + 5 * 60_000, fiveMinutes)
        assertTrue(lock.locked.value)
    }

    @Test
    fun `coming to the foreground without having left does nothing`() {
        val lock = AppLock()
        lock.unlock()
        lock.onForeground(99_999_999, immediate)
        assertFalse(lock.locked.value)
    }

    @Test
    fun `unreadable data is told apart from other errors`() {
        assertTrue(DatabaseRecovery.isUnreadableData(KeyUnavailableException("gone")))
        assertTrue(
            DatabaseRecovery.isUnreadableData(
                IllegalStateException("wrap", android.database.SQLException("not a database")),
            ),
        )
        assertFalse(DatabaseRecovery.isUnreadableData(IOException("disk")))
    }
}
