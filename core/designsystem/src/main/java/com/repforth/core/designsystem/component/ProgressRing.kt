package com.repforth.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.repforth.core.designsystem.theme.RepForthTheme

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
 * **This draws exactly the progress it is given, and animates nothing.** That
 * is deliberate, and it is the second attempt at this ring.
 *
 * The first version animated between the values it was handed, which is the
 * obvious thing to do and is wrong for a timer. Every Compose animation is
 * scaled by the system's animator duration setting — *0.5* on the phone this
 * was found on, and *0* for anyone who has turned animations off — so a tween
 * written to span the gap between two countdown updates finishes early and
 * leaves the ring standing still for the remainder of it. Widening the tween
 * cannot fix that; the scale applies to whatever it is widened to. The result
 * was a ring that moved in two visible jerks a second at 400ms and still moved
 * in two visible jerks a second at 500ms.
 *
 * So the caller owns the motion, because only the caller knows the timeline it
 * is moving along. A countdown feeds this a value recomputed every frame from
 * its own deadline and gets a continuous sweep with no animation involved; a
 * ring showing something static feeds it a static value. Reduced motion is the
 * caller's decision for the same reason — see `rememberRestSweep` in the
 * session screen, which steps once per second instead.
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
    content: @Composable () -> Unit,
) {
    val swept = progress.coerceIn(0f, 1f)

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
