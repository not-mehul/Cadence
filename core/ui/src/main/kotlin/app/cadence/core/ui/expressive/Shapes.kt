package app.cadence.core.ui.expressive

import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * A [Morph] between two Material shapes, frozen at [progress] (0 = start, 1 = end). Material's
 * shapes are normalised to a unit square, so the path is scaled to the layout size.
 */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(morph.scaledPath(progress, size))
}

/** [polygon] (a normalised Material shape) as a path filling [size]. */
fun RoundedPolygon.scaledPath(size: Size): Path = Morph(this, this).scaledPath(0f, size)

fun Morph.scaledPath(
    progress: Float,
    size: Size,
): Path {
    val path = toPath(progress, Path())
    path.transform(Matrix().apply { scale(size.width, size.height) })
    return path
}

/** Shapes with a job in Cadence, so screens stay consistent. */
object CadenceShapeSet {
    /** Behind card header icons. */
    val header: RoundedPolygon get() = MaterialShapes.Cookie9Sided

    /** The selected calendar day grows from a circle into this. */
    val selectedDay: RoundedPolygon get() = MaterialShapes.Cookie12Sided

    /** Today on the calendar. */
    val today: RoundedPolygon get() = MaterialShapes.Cookie4Sided

    /** Ovulation estimate on the calendar: a sun, so it differs from the fertile days by shape. */
    val ovulation: RoundedPolygon get() = MaterialShapes.Sunny

    val circle: RoundedPolygon get() = MaterialShapes.Circle
}
