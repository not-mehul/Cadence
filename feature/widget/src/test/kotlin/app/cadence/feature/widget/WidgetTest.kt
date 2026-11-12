package app.cadence.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.cycles.CycleSnapshot
import app.cadence.core.domain.cycles.CycleTimeline
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.reminders.ReminderSettings
import app.cadence.core.domain.security.SecuritySettings
import app.cadence.core.testing.FakeCycleAnnotationRepository
import app.cadence.core.testing.FakePeriodRepository
import app.cadence.core.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rGB")
class WidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.of(2026, 3, 20)
    private val period = (1..5).associate { LocalDate.of(2026, 3, it) to MenstrualFlow.MEDIUM }
    private val tracking = UserSettings(cycleDefaults = CycleDefaults(28, 5), onboardingComplete = true)
    private val detailed = tracking.copy(reminders = ReminderSettings(discreet = false))

    private fun text(
        settings: UserSettings,
        flows: Map<LocalDate, MenstrualFlow> = period,
        day: LocalDate = today,
    ) = WidgetContent.build(context, settings, CycleSnapshot.compute(flows, settings, day), Locale.UK)

    @Test
    fun `discreet by default, only the day and the date`() {
        assertEquals(WidgetText("Day 20", "Friday 20 March"), text(tracking))
    }

    @Test
    fun `with discreet notifications off, the details`() {
        assertEquals(WidgetText("Period in 9 days", "Cycle day 20"), text(detailed))
        assertEquals(WidgetText("Period, day 2", "Cycle day 2"), text(detailed, day = LocalDate.of(2026, 3, 2)))
        assertEquals("Period expected today", text(detailed, day = LocalDate.of(2026, 3, 29)).primary)
        assertEquals("Period due any day", text(detailed, day = LocalDate.of(2026, 3, 30)).primary)
        assertEquals("Period 6 days late", text(detailed, day = LocalDate.of(2026, 4, 4)).primary)
    }

    @Test
    fun `on a pill schedule, the pack`() {
        val pill = ContraceptiveSchedule.typical(ContraceptiveMethod.PILL, LocalDate.of(2026, 3, 2))
        val onPill = detailed.copy(goal = Goal.BIRTH_CONTROL, contraception = pill)
        assertEquals(WidgetText("Break in 3 days", "Pack day 19"), text(onPill, flows = emptyMap()))
        assertEquals("Break starts tomorrow", text(onPill, flows = emptyMap(), day = LocalDate.of(2026, 3, 22)).primary)
        assertEquals("Break, day 2 of 7", text(onPill, flows = emptyMap(), day = LocalDate.of(2026, 3, 24)).primary)
        val continuous = onPill.copy(contraception = pill.copy(activeDays = 28, breakDays = 0))
        assertEquals("Pack day 19", text(continuous, flows = emptyMap()).primary)
        assertEquals("Day 19", text(onPill.copy(reminders = ReminderSettings()), flows = emptyMap()).primary)
    }

    @Test
    fun `nothing at all while app lock is on, and an invitation before there's data`() {
        assertEquals(
            WidgetText("Locked", "Open the app to see today"),
            text(detailed.copy(security = SecuritySettings(lockEnabled = true))),
        )
        assertEquals(WidgetText("Today", "Tap to log"), text(tracking, flows = emptyMap()))
        assertEquals(WidgetText("Today", "Tap to log"), text(tracking.copy(onboardingComplete = false)))
    }

    @Test
    fun `the updater draws every placed widget`() =
        runTest {
            val manager = shadowOf(AppWidgetManager.getInstance(context))
            val id = manager.createWidget(CadenceWidgetProvider::class.java, R.layout.widget_today)
            val updater =
                WidgetUpdater(
                    context,
                    FakePeriodRepository(period),
                    FakeSettingsRepository(detailed),
                    FakeCycleAnnotationRepository(),
                ) { today }

            updater.updateAll()

            val view = manager.getViewFor(id)
            assertEquals("Period in 9 days", view.findViewById<TextView>(R.id.widget_primary).text.toString())
            assertEquals(
                1,
                AppWidgetManager
                    .getInstance(
                        context,
                    ).getAppWidgetIds(ComponentName(context, CadenceWidgetProvider::class.java))
                    .size,
            )
            // The views can also be inflated fresh, as a launcher would.
            val fresh = updater.views(WidgetText("Day 3", "x")).apply(context, FrameLayout(context))
            assertEquals("Day 3", fresh.findViewById<TextView>(R.id.widget_primary).text.toString())
        }

    @Test
    fun `widgets are redrawn just after midnight, and the alarm stops with the last widget`() =
        runTest {
            val alarms = shadowOf(context.getSystemService(android.app.AlarmManager::class.java))
            val manager = shadowOf(AppWidgetManager.getInstance(context))
            val updater =
                WidgetUpdater(
                    context,
                    FakePeriodRepository(period),
                    FakeSettingsRepository(detailed),
                    FakeCycleAnnotationRepository(),
                ) { today }

            updater.updateAll()
            assertEquals(0, alarms.scheduledAlarms.size, "no widgets, no alarm")

            manager.createWidget(CadenceWidgetProvider::class.java, R.layout.widget_today)
            updater.updateAll()
            val midnight =
                today
                    .plusDays(1)
                    .atStartOfDay()
                    .plusSeconds(30)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            val alarm = alarms.scheduledAlarms.single()
            assertEquals(midnight, alarm.triggerAtMs)
            assertEquals(android.app.AlarmManager.RTC, alarm.getType(), "doesn't wake the phone")

            // The last small widget removed while another (here, still registered) remains: keep going.
            CadenceWidgetLargeProvider().onDisabled(context)
            assertEquals(1, alarms.scheduledAlarms.size)
        }

    @Test
    fun `the alarm stops once no widget of either size is left`() {
        val alarms = shadowOf(context.getSystemService(android.app.AlarmManager::class.java))
        WidgetClock.scheduleAfterMidnight(context, today)
        assertEquals(1, alarms.scheduledAlarms.size)
        CadenceWidgetProvider().onDisabled(context)
        assertEquals(0, alarms.scheduledAlarms.size)
    }

    private fun large(
        settings: UserSettings,
        flows: Map<LocalDate, MenstrualFlow> = period,
        day: LocalDate = today,
    ) = WidgetContent.large(context, settings, CycleSnapshot.compute(flows, settings, day), Locale.UK)

    @Test
    fun `the large widget, discreet, shows a plain progress ring and the date`() {
        assertEquals(
            LargeWidget(
                primary = "Friday 20 March",
                secondary = null,
                ring = WidgetRing(20, 28, listOf(WidgetArc(1, 20, ArcRole.PROGRESS))),
                ringCaption = "day",
                ringDescription = "Day 20",
            ),
            large(tracking),
        )
    }

    @Test
    fun `the large widget, detailed, shows the phases and stretches when late`() {
        val widget = large(detailed)
        assertEquals("Period in 9 days", widget.primary)
        assertEquals("Friday 20 March", widget.secondary)
        assertEquals("of 28", widget.ringCaption)
        assertEquals("Cycle day 20", widget.ringDescription)
        val ring = requireNotNull(widget.ring)
        assertEquals(20, ring.day)
        assertEquals(WidgetArc(1, 5, ArcRole.PERIOD), ring.arcs.first())
        assertEquals(ArcRole.LUTEAL, ring.arcs.last().role)
        assertEquals(28, ring.arcs.last().last)
        // Day 35: six days late, so the ring runs to 35.
        assertEquals(35, large(detailed, day = LocalDate.of(2026, 4, 4)).ring?.length)
    }

    @Test
    fun `the large widget on a pill shows the pack and its break`() {
        val pill = ContraceptiveSchedule.typical(ContraceptiveMethod.PILL, LocalDate.of(2026, 3, 2))
        val onPill = detailed.copy(goal = Goal.BIRTH_CONTROL, contraception = pill)
        assertEquals(
            WidgetRing(19, 28, listOf(WidgetArc(1, 21, ArcRole.ACTIVE), WidgetArc(22, 28, ArcRole.BREAK))),
            large(onPill, flows = emptyMap()).ring,
        )
        val continuous = onPill.copy(contraception = pill.copy(activeDays = 28, breakDays = 0))
        assertEquals(listOf(WidgetArc(1, 28, ArcRole.ACTIVE)), large(continuous, flows = emptyMap()).ring?.arcs)
    }

    @Test
    fun `the large widget shows no ring while locked or before there's data`() {
        val locked = large(detailed.copy(security = SecuritySettings(lockEnabled = true)))
        assertEquals(LargeWidget("Locked", "Open the app to see today", ring = null), locked)
        assertEquals(null, large(tracking, flows = emptyMap()).ring)
        assertEquals(null, large(tracking.copy(onboardingComplete = false)).ring)
    }

    @Test
    fun `phase arcs follow Today's ring`() {
        assertEquals(
            listOf(
                WidgetArc(1, 5, ArcRole.PERIOD),
                WidgetArc(6, 9, ArcRole.FOLLICULAR),
                WidgetArc(10, 15, ArcRole.FERTILE),
                WidgetArc(16, 28, ArcRole.LUTEAL),
            ),
            WidgetContent.phaseArcs(CycleTimeline(28, 5, 10..15)),
        )
        // A fertile window overlapping the period starts after it, and one running past the end is cut short.
        assertEquals(
            listOf(
                WidgetArc(1, 5, ArcRole.PERIOD),
                WidgetArc(6, 9, ArcRole.FERTILE),
                WidgetArc(10, 21, ArcRole.LUTEAL),
            ),
            WidgetContent.phaseArcs(CycleTimeline(21, 5, 3..9)),
        )
        assertEquals(
            listOf(
                WidgetArc(1, 5, ArcRole.PERIOD),
                WidgetArc(6, 19, ArcRole.FOLLICULAR),
                WidgetArc(20, 22, ArcRole.FERTILE),
            ),
            WidgetContent.phaseArcs(CycleTimeline(22, 5, 20..25)),
        )
        assertEquals(listOf(WidgetArc(1, 5, ArcRole.PERIOD)), WidgetContent.phaseArcs(CycleTimeline(28, 5, null)))
    }

    @Test
    fun `the ring is drawn around the edge, leaving the middle for the day`() {
        val ring = WidgetRing(3, 28, listOf(WidgetArc(1, 5, ArcRole.PERIOD), WidgetArc(6, 6, ArcRole.FERTILE)))
        val bitmap = WidgetRingRenderer.draw(context, ring, 200)
        assertEquals(200, bitmap.width)
        assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(100, 100)))
    }

    @Test
    fun `the updater draws large widgets too`() =
        runTest {
            val manager = shadowOf(AppWidgetManager.getInstance(context))
            val id = manager.createWidget(CadenceWidgetLargeProvider::class.java, R.layout.widget_large)
            val updater =
                WidgetUpdater(
                    context,
                    FakePeriodRepository(period),
                    FakeSettingsRepository(detailed),
                    FakeCycleAnnotationRepository(),
                ) { today }

            updater.updateAll()

            val view = manager.getViewFor(id)
            assertEquals("Period in 9 days", view.findViewById<TextView>(R.id.widget_primary).text.toString())
            assertEquals("20", view.findViewById<TextView>(R.id.widget_ring_day).text.toString())
            assertEquals("of 28", view.findViewById<TextView>(R.id.widget_ring_caption).text.toString())
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_ring_frame).visibility)

            val locked =
                updater
                    .largeViews(
                        LargeWidget("Locked", null, ring = null),
                    ).apply(context, FrameLayout(context))
            assertEquals(View.GONE, locked.findViewById<View>(R.id.widget_ring_frame).visibility)
            assertEquals(View.GONE, locked.findViewById<View>(R.id.widget_secondary).visibility)
        }
}
