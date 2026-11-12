package app.cadence.core.ui.quick

import android.content.Context
import android.content.Intent

/**
 * One-tap logging from outside the app: launcher shortcuts, the Quick Settings tile and the
 * large widget open Cadence with one of these. It runs once the app is unlocked, on Today.
 */
enum class QuickAction {
    /** Opens today's log. */
    LOG_TODAY,

    /** "Period started" today, as on Today (with undo). */
    PERIOD_STARTED,

    /** Today's pill, patch or ring done. */
    DOSE_TAKEN,
    ;

    /** Opens the app with this action. The app's activity isn't exported; only Cadence itself (and the shortcuts it publishes) can start it. */
    fun intent(context: Context): Intent =
        Intent(Intent.ACTION_VIEW)
            .setClassName(context, MAIN_ACTIVITY)
            .putExtra(EXTRA, name)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )

    companion object {
        const val EXTRA = "app.cadence.extra.QUICK_ACTION"
        private const val MAIN_ACTIVITY = "app.cadence.MainActivity"

        fun from(intent: Intent?): QuickAction? =
            intent?.getStringExtra(EXTRA)?.let { n ->
                entries.firstOrNull {
                    it.name ==
                        n
                }
            }
    }
}
