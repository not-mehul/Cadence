package app.cadence.core.ui.expressive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.ui.R

/**
 * A drop that fills up with the flow: a little for spotting, all the way for heavy. "None"
 * is a crossed-out drop. Decorative: always shown with the flow's name.
 */
@Composable
fun FlowIcon(
    flow: MenstrualFlow?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    if (flow == null) {
        Icon(painterResource(R.drawable.ms_format_color_reset), null, modifier.size(size), tint = tint)
        return
    }
    val level = fillLevel(flow)
    Box(modifier.size(size)) {
        Icon(painterResource(R.drawable.ms_humidity_low), null, Modifier.size(size), tint = tint)
        Icon(
            painterResource(R.drawable.ms_humidity_high),
            null,
            tint = tint,
            modifier =
                Modifier.size(size).drawWithContent {
                    // The drop spans roughly 9% to 92% of the icon's height.
                    val top = size.toPx() * (DROP_BOTTOM - level * (DROP_BOTTOM - DROP_TOP))
                    clipRect(top = top) { this@drawWithContent.drawContent() }
                },
        )
    }
}

internal fun fillLevel(flow: MenstrualFlow): Float =
    when (flow) {
        MenstrualFlow.SPOTTING -> 0.2f
        MenstrualFlow.LIGHT -> 0.42f
        MenstrualFlow.MEDIUM -> 0.68f
        MenstrualFlow.HEAVY -> 1f
    }

private const val DROP_TOP = 0.09f
private const val DROP_BOTTOM = 0.92f
