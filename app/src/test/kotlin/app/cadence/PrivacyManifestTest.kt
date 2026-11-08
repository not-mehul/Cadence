package app.cadence

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Device-only guarantees from docs/PLAN.md §2.7, checked against the merged manifest. */
@RunWith(RobolectricTestRunner::class)
class PrivacyManifestTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `app requests no network permissions`() {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions.orEmpty().toList()

        assertFalse("android.permission.INTERNET" in permissions, "requested: $permissions")
        assertFalse("android.permission.ACCESS_NETWORK_STATE" in permissions, "requested: $permissions")
    }

    @Test
    fun `android backup is disabled`() {
        val flags = context.applicationInfo.flags

        assertEquals(0, flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
