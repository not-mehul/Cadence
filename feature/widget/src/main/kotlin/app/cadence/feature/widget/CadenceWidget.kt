package app.cadence.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import app.cadence.core.data.repository.CycleAnnotationRepository
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.domain.cycles.CycleSnapshot
import app.cadence.core.domain.time.TodayProvider
import app.cadence.core.ui.quick.QuickAction
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val TAG = "CadenceWidget"

/** Draws every Cadence widget on the home screen from current data. */
@Singleton
class WidgetUpdater
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val periods: PeriodRepository,
        private val settings: SettingsRepository,
        private val annotations: CycleAnnotationRepository,
        private val today: TodayProvider,
    ) {
        private val manager = AppWidgetManager.getInstance(context)

        suspend fun updateAll() {
            val small = manager.idsOf<CadenceWidgetProvider>(context)
            val large = manager.idsOf<CadenceWidgetLargeProvider>(context)
            if (small.isEmpty() && large.isEmpty()) {
                WidgetClock.cancel(context)
                return
            }
            val date = today.today()
            val s = settings.settings.first()
            val snapshot =
                CycleSnapshot.compute(
                    periods.flowByDay.first(),
                    s,
                    date,
                    annotations.excludedStarts.first(),
                )
            if (small.isNotEmpty()) manager.updateAppWidget(small, views(WidgetContent.build(context, s, snapshot)))
            if (large.isNotEmpty()) {
                manager.updateAppWidget(
                    large,
                    largeViews(WidgetContent.large(context, s, snapshot)),
                )
            }
            WidgetClock.scheduleAfterMidnight(context, date)
        }

        fun views(text: WidgetText): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.widget_primary, text.primary)
                setTextViewText(R.id.widget_secondary, text.secondary)
                openApp()?.let { setOnClickPendingIntent(R.id.widget_root, it) }
            }

        fun largeViews(widget: LargeWidget): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_large).apply {
                setTextViewText(R.id.widget_primary, widget.primary)
                setTextViewText(R.id.widget_secondary, widget.secondary.orEmpty())
                setViewVisibility(R.id.widget_secondary, if (widget.secondary == null) View.GONE else View.VISIBLE)
                val ring = widget.ring
                setViewVisibility(R.id.widget_ring_frame, if (ring == null) View.GONE else View.VISIBLE)
                if (ring != null) {
                    val size = (RING_SIZE_DP * context.resources.displayMetrics.density).roundToInt()
                    setImageViewBitmap(R.id.widget_ring, WidgetRingRenderer.draw(context, ring, size))
                    setTextViewText(R.id.widget_ring_day, ring.day.toString())
                    setTextViewText(R.id.widget_ring_caption, widget.ringCaption.orEmpty())
                    setContentDescription(R.id.widget_ring_frame, widget.ringDescription)
                }
                openApp()?.let { setOnClickPendingIntent(R.id.widget_root, it) }
                setOnClickPendingIntent(
                    R.id.widget_log,
                    PendingIntent.getActivity(
                        context,
                        LOG_REQUEST,
                        QuickAction.LOG_TODAY.intent(context),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }

        private fun openApp(): PendingIntent? {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
            return PendingIntent.getActivity(
                context,
                0,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }

private const val RING_SIZE_DP = 96
private const val LOG_REQUEST = 1

private inline fun <reified T : AppWidgetProvider> AppWidgetManager.idsOf(context: Context): IntArray =
    getAppWidgetIds(ComponentName(context, T::class.java))

/** Keeps widgets in step with data and settings while the app is running. */
@Singleton
class WidgetSync
    @Inject
    constructor(
        private val updater: WidgetUpdater,
        private val periods: PeriodRepository,
        private val settings: SettingsRepository,
        private val annotations: CycleAnnotationRepository,
    ) {
        private val scope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.Default +
                    CoroutineExceptionHandler { _, e -> Log.w(TAG, "Widget update failed", e) },
            )
        private var started = false

        @OptIn(FlowPreview::class)
        @Synchronized
        fun start() {
            if (started) return
            started = true
            scope.launch {
                combine(periods.flowByDay, settings.settings, annotations.excludedStarts) { a, b, c -> Triple(a, b, c) }
                    .distinctUntilChanged()
                    .debounce(DEBOUNCE_MS)
                    .collect { updater.updateAll() }
            }
        }

        private companion object {
            const val DEBOUNCE_MS = 500L
        }
    }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun updater(): WidgetUpdater
}

/** The home screen asks for an update (when added, and about hourly); [WidgetClock] covers midnight. */
open class CadenceWidgetProvider : AppWidgetProvider() {
    /** The last widget of this size is gone; the midnight redraw stops once there are none of either. */
    override fun onDisabled(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        if (manager.idsOf<CadenceWidgetProvider>(context).isEmpty() &&
            manager.idsOf<CadenceWidgetLargeProvider>(context).isEmpty()
        ) {
            WidgetClock.cancel(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Null when onUpdate is called directly rather than from a broadcast (as in tests).
        val pending: PendingResult? = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                EntryPointAccessors
                    .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                    .updater()
                    .updateAll()
            } catch (e: Exception) {
                // Unreadable data and the like: leave the widget as it was rather than crash.
                Log.w(TAG, "Widget update failed", e)
            } finally {
                pending?.finish()
            }
        }
    }
}

/** The large widget: the same data, drawn by [WidgetUpdater.largeViews]. */
class CadenceWidgetLargeProvider : CadenceWidgetProvider()
