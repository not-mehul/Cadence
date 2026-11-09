package app.cadence.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import app.cadence.core.data.repository.CycleAnnotationRepository
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.domain.cycles.CycleSnapshot
import app.cadence.core.domain.time.TodayProvider
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

        private fun widgetIds(): IntArray =
            manager.getAppWidgetIds(ComponentName(context, CadenceWidgetProvider::class.java))

        suspend fun updateAll() {
            val ids = widgetIds()
            if (ids.isEmpty()) return
            val s = settings.settings.first()
            val snapshot =
                CycleSnapshot.compute(
                    periods.flowByDay.first(),
                    s,
                    today.today(),
                    annotations.excludedStarts.first(),
                )
            manager.updateAppWidget(ids, views(WidgetContent.build(context, s, snapshot)))
        }

        fun views(text: WidgetText): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.widget_primary, text.primary)
                setTextViewText(R.id.widget_secondary, text.secondary)
                openApp()?.let { setOnClickPendingIntent(R.id.widget_root, it) }
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

/** The home screen asks for an update (when added, and hourly, for the change of day). */
class CadenceWidgetProvider : AppWidgetProvider() {
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
