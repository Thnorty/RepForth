package com.repforth.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.repforth.core.designsystem.theme.Layout
import com.repforth.core.designsystem.theme.RepForthNumeric
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Stroke
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

/** One entry on a [TrendChart]. */
data class TrendPoint(val date: LocalDate, val value: Double)

/** Where each entry is drawn, and the value range the height spans. */
internal data class TrendLayout(val dots: List<Offset>, val minValue: Double, val maxValue: Double)

/**
 * Lays [points] out in a [width] × [height] box.
 *
 * Across by date, not by index, so a two-month gap looks like one. Up by value,
 * over the range padded by a tenth of its span, and by at least half a unit, so
 * a flat series is a line through the middle rather than a division by zero. A
 * single entry sits in the centre.
 */
internal fun trendLayout(points: List<TrendPoint>, width: Float, height: Float): TrendLayout {
    if (points.isEmpty()) return TrendLayout(emptyList(), 0.0, 0.0)
    val sorted = points.sortedBy { it.date }
    val low = sorted.minOf { it.value }
    val high = sorted.maxOf { it.value }
    val pad = max((high - low) * 0.1, 0.5)
    val min = low - pad
    val max = high + pad
    val days = ChronoUnit.DAYS.between(sorted.first().date, sorted.last().date).toFloat()

    val dots = sorted.map { point ->
        val x = if (days == 0f) width / 2f else ChronoUnit.DAYS.between(sorted.first().date, point.date) / days * width
        val y = ((max - point.value) / (max - min) * height).toFloat()
        Offset(x, y)
    }
    return TrendLayout(dots, min, max)
}

/**
 * A measurement over time: one line in the accent, a dot per entry.
 *
 * The figures — highest, lowest, latest — and the first and last dates are
 * ordinary text beside the canvas, so they follow the font scale. No animation:
 * a chart that draws itself in has nothing to say that a still one does not, and
 * there is then nothing for reduced motion to switch off.
 *
 * TalkBack reads [description], one sentence the caller builds, instead of the
 * pieces. Every screen that shows the chart lists the entries below it as well.
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    formatValue: (Double) -> String,
    formatDate: (LocalDate) -> String,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val sorted = points.sortedBy { it.date }
    val accent = MaterialTheme.colorScheme.primary
    val labelColour = MaterialTheme.colorScheme.onSurfaceVariant
    val lowest = sorted.minOf { it.value }
    val highest = sorted.maxOf { it.value }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Text(
            text = formatValue(sorted.last().value),
            style = RepForthNumeric.sm,
            modifier = Modifier.align(Alignment.End),
        )
        Row(modifier = Modifier.fillMaxWidth().height(Layout.trendChartHeight)) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(end = Space.s2),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatValue(highest), style = RepForthNumeric.xs, color = labelColour)
                Text(formatValue(lowest), style = RepForthNumeric.xs, color = labelColour)
            }
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val inset = DOT_RADIUS.toPx()
                val layout = trendLayout(sorted, size.width - inset * 2, size.height - inset * 2)
                val dots = layout.dots.map { it + Offset(inset, inset) }
                if (dots.size > 1) {
                    val path = Path().apply {
                        moveTo(dots.first().x, dots.first().y)
                        dots.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path,
                        color = accent,
                        style = DrawStroke(width = Stroke.thick.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                dots.forEach { drawCircle(accent, radius = inset, center = it) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDate(sorted.first().date), style = MaterialTheme.typography.labelMedium, color = labelColour)
            if (sorted.size > 1) {
                Text(formatDate(sorted.last().date), style = MaterialTheme.typography.labelMedium, color = labelColour)
            }
        }
    }
}

private val DOT_RADIUS = 4.dp
