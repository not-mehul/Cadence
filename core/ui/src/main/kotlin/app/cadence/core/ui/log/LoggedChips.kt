package app.cadence.core.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.cadence.core.ui.expressive.FlowIcon
import app.cadence.core.ui.theme.CadenceTheme

/**
 * What's logged for a day as small icon chips: flow first (peach, with a drop filled to its
 * level), then symptoms, moods, tags, ratings and sex. Icons are decorative; every chip has text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoggedChips(
    items: List<LogSummaryItem>,
    modifier: Modifier = Modifier,
) {
    val spacing = CadenceTheme.spacing
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items.forEach { item -> LoggedChip(item) }
    }
}

@Composable
private fun LoggedChip(item: LogSummaryItem) {
    val cycle = CadenceTheme.cycleColors
    val scheme = MaterialTheme.colorScheme
    val isFlow = item.flow != null
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isFlow) cycle.period else scheme.secondaryContainer,
        contentColor = if (isFlow) cycle.onPeriod else scheme.onSecondaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            val flow = item.flow
            if (flow != null) {
                FlowIcon(flow, size = 18.dp, tint = cycle.onPeriod)
            } else {
                Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(item.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
