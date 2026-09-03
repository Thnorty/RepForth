package com.repforth.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.repforth.core.designsystem.theme.Dur
import com.repforth.core.designsystem.theme.Ease
import com.repforth.core.designsystem.theme.RepForthTheme
import com.repforth.core.designsystem.theme.rfTween

/** Which of the ring's three meanings this one carries. `.rf-ring--*` in the CSS. */
enum class RingTone { Accent, Rest, Done }

/**
 * A circular progress ring with something written in the middle.
 *
 * Ported from `design-system/components/feedback/ProgressRing.jsx` and
 * `.rf-ring` in `design-system/css/feedback.css`. The geometry there is
 * parametric rather than fixed, and is kept that way here:
 *
 *   stroke = max(6, size * 0.06)      radius = (size - stroke) / 2
 *
 * so a ring stays the same object at any diameter instead of becoming a
 * different one with a hand-picked stroke.
 *
 * It starts at twelve o'clock — the CSS rotates the whole `svg` by -90°, and
 * the arc here starts at -90° for the same reason. A countdown that began at
 * three o'clock would be a clock nobody has ever seen.
 *
 * **The sweep is the one continuous motion the design system allows.** Its
 * rules reserve rotation for an active timer ring specifically, and this is
 * that ring. It still goes through [rfTween], so reduced motion snaps between
 * values rather than sliding — the countdown remains completely readable, which
 * is the point of the setting.
 *
 * The ring draws no semantics of its own. Whatever is written in the middle is
 * already the thing a screen reader should read, and a `contentDescription`
 * here would either duplicate it or shadow it.
 */
@Composable
fun RfProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    tone: RingTone = RingTone.Accent,
    stepMillis: Int = Dur.long,
    content: @Composable () -> Unit,
) {
    val target = progress.coerceIn(0f, 1f)
    val swept by animateFloatAsState(
        targetValue = target,
        // Linear, across exactly the interval between updates, so consecutive
        // steps join into one continuous sweep.
        //
        // The first version eased over 400ms while the rest countdown updated
        // every 500ms, so the ring accelerated, stopped, waited, and did it
        // again -- twice a second. A countdown ring either ticks once per
        // second like a clock hand or moves continuously; moving in two eased
        // jerks per second reads as neither.
        animationSpec = rfTween(durationMillis = stepMillis, easing = Ease.linear),
        label = "ring_progress",
    )

    val track = RepForthTheme.colors.track
    val fill: Color = when (tone) {
        RingTone.Accent -> MaterialTheme.colorScheme.primary
        RingTone.Rest -> MaterialTheme.colorScheme.tertiary
        RingTone.Done -> MaterialTheme.colorScheme.secondary
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = maxOf(MIN_STROKE.toPx(), this.size.minDimension * STROKE_RATIO)
            val inset = strokePx / 2f
            val diameter = this.size.minDimension - strokePx
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)

            drawArc(
                color = track,
                startAngle = START_ANGLE,
                sweepAngle = FULL_TURN,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(diameter, diameter),
                style = stroke,
            )
            drawArc(
                color = fill,
                startAngle = START_ANGLE,
                sweepAngle = FULL_TURN * swept,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(diameter, diameter),
                style = stroke,
            )
        }
        content()
    }
}

/** `Math.max(6, ...)` and `size * 0.06` in the JSX. */
private val MIN_STROKE = 6.dp
private const val STROKE_RATIO = 0.06f

/** Twelve o'clock, and a whole turn. */
private const val START_ANGLE = -90f
private const val FULL_TURN = 360f
