package app.cadence.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.cadence.core.domain.model.CyclePhase
import app.cadence.core.ui.R
import app.cadence.core.ui.theme.CadenceTheme

/**
 * How a phase looks: its colour, the soft wash behind content, and its glyph. Colour is
 * never the only cue; the phase is always named in text as well.
 */
data class PhaseStyle(
    val color: Color,
    val tint: Color,
    @param:DrawableRes val icon: Int,
)

object PhaseStyles {
    /** [phase] null: no phase applies (birth control, or the period is due or late). */
    @Composable
    @ReadOnlyComposable
    fun of(phase: CyclePhase?): PhaseStyle {
        val c = CadenceTheme.cycleColors
        return when (phase) {
            CyclePhase.MENSTRUAL -> PhaseStyle(c.menstrualPhase, c.menstrualTint, R.drawable.ic_phase_menstrual)
            CyclePhase.FOLLICULAR -> PhaseStyle(c.follicularPhase, c.follicularTint, R.drawable.ic_phase_follicular)
            CyclePhase.OVULATORY -> PhaseStyle(c.ovulatoryPhase, c.ovulatoryTint, R.drawable.ic_phase_ovulatory)
            CyclePhase.LUTEAL -> PhaseStyle(c.lutealPhase, c.lutealTint, R.drawable.ic_phase_luteal)
            null -> neutral()
        }
    }

    /** For states without a phase. */
    @Composable
    @ReadOnlyComposable
    fun neutral(
        @DrawableRes icon: Int = R.drawable.ic_phase_luteal,
    ): PhaseStyle = PhaseStyle(MaterialTheme.colorScheme.primary, CadenceTheme.cycleColors.neutralTint, icon)

    /** On a pill, patch or ring: the active days and the break, in the ring's colours. */
    @Composable
    @ReadOnlyComposable
    fun pack(inBreak: Boolean): PhaseStyle {
        val c = CadenceTheme.cycleColors
        return if (inBreak) {
            PhaseStyle(c.menstrualPhase, c.menstrualTint, R.drawable.ic_pill)
        } else {
            PhaseStyle(c.follicularPhase, c.follicularTint, R.drawable.ic_pill)
        }
    }
}

/**
 * A pill-shaped label with the phase's glyph. Decorative: wherever it's shown, the same
 * words are already in a heading or description, so screen readers skip it.
 */
@Composable
fun PhaseChip(
    label: String,
    style: PhaseStyle,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f),
        contentColor = style.color,
        modifier = modifier.clearAndSetSemantics {},
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CadenceTheme.spacing.xs),
            modifier = Modifier.padding(horizontal = CadenceTheme.spacing.md, vertical = 6.dp),
        ) {
            Icon(painterResource(style.icon), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
