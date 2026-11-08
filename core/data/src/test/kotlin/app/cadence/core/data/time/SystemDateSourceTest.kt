package app.cadence.core.data.time

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SystemDateSourceTest {
    @Test
    fun `emits today, then the new date when the day changes`() =
        runTest {
            var today = LocalDate.of(2026, 3, 1)
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val source = SystemDateSource(context) { today }

            val dates = async { source.today.take(2).toList() }
            runCurrent()
            today = LocalDate.of(2026, 3, 2)
            context.sendBroadcast(Intent(Intent.ACTION_DATE_CHANGED))
            shadowOf(Looper.getMainLooper()).idle()
            runCurrent()

            assertEquals(listOf(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2)), dates.await())
        }
}
