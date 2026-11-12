package app.cadence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.cadence.core.ui.quick.QuickAction

/**
 * A Quick Settings tile that opens today's log. Its label, "Log today", doesn't name the app,
 * so it's as discreet as the discreet icon. On a locked phone it asks to unlock first.
 */
class QuickLogTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        // A button, not a toggle: always shown as off.
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::openLog) else openLog()
    }

    // The Intent overload is the only one before Android 14.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openLog() {
        val intent = QuickAction.LOG_TODAY.intent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
