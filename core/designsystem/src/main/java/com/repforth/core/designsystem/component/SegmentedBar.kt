package com.repforth.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.repforth.core.designsystem.theme.Dur
import com.repforth.core.designsystem.theme.Ease
import com.repforth.core.designsystem.theme.Radius
import com.repforth.core.designsystem.theme.RepForthNumeric
import com.repforth.core.designsystem.theme.RepForthTheme
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.rfTween

/**
 * A row of segments, some of them filled.
 *
 * Ported from `.rf-bar--segmented` in `design-system/css/feedback.css`: 8px
 * tall, fully rounded, 3px between segments, the current one at reduced
 * opacity.
 *
 * Segmented rather than continuous on purpose. A week of training is four
 * things out of five, not 80% — a continuous bar invites reading a percentage
 * off it, and the number that matters is countable. §3 asks how many days a
 * week someone trains, so this is the shape that answers the question they were
 * asked.
 */
@Composable
fun RfSegmentedBar(
    label: String,
    value: String,
    filled: Int,
    total: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Column(
        // One node, one sentence. Read segment by segment this is eight
        // unlabelled boxes; the caller supplies the sentence a screen reader
        // should say instead.
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s3),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(text = value, style = RepForthNumeric.xs)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            repeat(total.coerceAtLeast(1)) { index ->
                Segment(on = index < filled, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Segment(on: Boolean, modifier: Modifier = Modifier) {
    // The fill moves rather than appearing, which is the only motion on this
    // screen and the reason `rfTween` exists -- reduced motion collapses it to
    // an instant swap without this composable knowing.
    val color by animateColorAsState(
        targetValue = if (on) {
            MaterialTheme.colorScheme.primary
        } else {
            RepForthTheme.colors.track
        },
        animationSpec = rfTween(durationMillis = Dur.long, easing = Ease.standard),
        label = "segment_fill",
    )

    Row(
        modifier = modifier
            .height(SEGMENT_HEIGHT)
            .background(color = color, shape = RoundedCornerShape(Radius.chip))
            .padding(0.dp),
    ) {}
}

/** `height:8px` and `gap:3px` in the CSS. */
private val SEGMENT_HEIGHT = 8.dp
private val SEGMENT_GAP = 3.dp
