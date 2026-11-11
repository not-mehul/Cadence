package app.cadence.core.ui.expressive

import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/** A morph between normalised Material shapes, at [progress], as a path filling [size]. */
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
}
