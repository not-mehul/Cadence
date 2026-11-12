package app.cadence.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.ColorRes
import androidx.core.graphics.createBitmap
import kotlin.math.cos
import kotlin.math.sin

/** What a stretch of the large widget's ring stands for. */
enum class ArcRole(
    @param:ColorRes val color: Int,
) {
    PERIOD(R.color.widget_ring_period),
    FOLLICULAR(R.color.widget_ring_follicular),
    FERTILE(R.color.widget_ring_fertile),
    LUTEAL(R.color.widget_ring_luteal),

    /** Days on the pill, patch or ring. */
    ACTIVE(R.color.widget_ring_active),

    /** The pack's break. */
    BREAK(R.color.widget_ring_period),

    /** Discreet: just how far along, with no phases. */
    PROGRESS(R.color.widget_ring_progress),
}

/** Days [first] to [last] (1-based, inclusive). */
data class WidgetArc(
    val first: Int,
    val last: Int,
    val role: ArcRole,
)

/** The large widget's ring: [day] of [length] days, with coloured [arcs] (uncovered days show the track). */
data class WidgetRing(
    val day: Int,
    val length: Int,
    val arcs: List<WidgetArc>,
)

/** Draws a [WidgetRing] to a bitmap, since a home-screen widget can't run Compose. */
internal object WidgetRingRenderer {
    private const val STROKE = 0.11f
    private const val GAP_DEGREES = 3f
    private const val START_DEGREES = -90f
    private const val FULL_CIRCLE = 360f
    private const val MARKER = 0.085f
    private const val MARKER_HALO = 0.04f

    fun draw(
        context: Context,
        ring: WidgetRing,
        sizePx: Int,
    ): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val size = sizePx.toFloat()
        val stroke = size * STROKE
        val haloWidth = size * MARKER_HALO
        // Today's marker is the widest thing on the ring, so it sets the margin.
        val inset = size * MARKER + haloWidth
        val bounds = RectF(inset, inset, size - inset, size - inset)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
        val step = FULL_CIRCLE / ring.length
        // Round caps reach past the arc's ends; trim them so neighbouring arcs keep a clean gap.
        val capDegrees = Math.toDegrees((stroke / 2 / (bounds.width() / 2)).toDouble()).toFloat()
        val trim = GAP_DEGREES / 2 + capDegrees

        paint.color = context.getColor(R.color.widget_ring_track)
        canvas.drawArc(bounds, 0f, FULL_CIRCLE, false, paint)
        ring.arcs.forEach { arc ->
            val sweep = (arc.last - arc.first + 1) * step - 2 * trim
            paint.color = context.getColor(arc.role.color)
            if (sweep > 0) {
                canvas.drawArc(bounds, START_DEGREES + (arc.first - 1) * step + trim, sweep, false, paint)
            } else {
                // Too short for an arc: a dot in the middle of its days.
                drawDot(canvas, bounds, START_DEGREES + (arc.first - 1 + arc.last) / 2f * step, stroke / 2, paint)
            }
        }

        // Today: a dot in the middle of the day, ringed in the widget's background.
        val angle = START_DEGREES + (ring.day.coerceAtMost(ring.length) - 0.5f) * step
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = context.getColor(R.color.widget_background)
        drawDot(canvas, bounds, angle, size * MARKER + haloWidth, fill)
        fill.color = context.getColor(R.color.widget_text)
        drawDot(canvas, bounds, angle, size * MARKER, fill)
        return bitmap
    }

    private fun drawDot(
        canvas: Canvas,
        bounds: RectF,
        degrees: Float,
        radius: Float,
        paint: Paint,
    ) {
        val radians = Math.toRadians(degrees.toDouble())
        val r = bounds.width() / 2
        val x = bounds.centerX() + r * cos(radians).toFloat()
        val y = bounds.centerY() + r * sin(radians).toFloat()
        val dotPaint = Paint(paint).apply { style = Paint.Style.FILL }
        canvas.drawCircle(x, y, radius, dotPaint)
    }
}
