package com.repforth.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.repforth.core.designsystem.theme.Space
import com.repforth.core.designsystem.theme.Target
import kotlin.math.roundToInt

/*
 * The two controls that ask "which one?" and "how much?".
 *
 * Both were written first inside a feature and then wanted by two more. Settings
 * picks a goal, Coach overrides it for one generation, and onboarding asks it in
 * the first place; the same is true of session length. Three copies of a chip
 * row is three sets of paddings and three answers to what happens when the label
 * is Turkish.
 */

/**
 * One choice from a few, as chips that wrap.
 *
 * Chips rather than a segmented row on purpose. A `SingleChoiceSegmentedButtonRow`
 * divides its width equally between the options, so four goals on a phone leaves
 * about 80dp each and "General fitness" breaks onto a second line inside its own
 * pill — which is what Settings shipped until it was seen on a device. Equal
 * fixed shares of a fixed width is the one layout that cannot respond to its
 * text growing, and this app has to survive Turkish and 200% font scale.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> RfChoiceChips(
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        label?.let {
            Text(text = it, style = MaterialTheme.typography.bodyLarge)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
            verticalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    enabled = enabled,
                    label = { Text(labelOf(option)) },
                    // Target.min, not Target.icon: this is a thing a
                    // finger has to land on, and 40dp is under the
                    // stated minimum. Material chips are 32dp and get
                    // no automatic touch expansion.
                    modifier = Modifier.heightIn(min = Target.min),
                )
            }
        }
    }
}

/**
 * A whole number on a slider, with the value written above it.
 *
 * The number is the answer, so it is stated in words rather than left to be read
 * off the track position — a slider alone tells you roughly, and "roughly 45
 * minutes" is not a thing anyone means.
 *
 * [step] is in the value's own units. `Slider` counts the gaps between stops
 * rather than the stops, which is the off-by-one this hides.
 */
@Composable
fun RfValueSlider(
    value: Int,
    range: IntRange,
    label: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    enabled: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                onValueChange(raw.toStepValue(range, step))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step) - 1,
            enabled = enabled,
            modifier = Modifier
                .heightIn(min = Target.min)
                // The label is drawn above as a sibling `Text`, which is not
                // this control's name and is not announced with it -- so
                // without this a screen reader read out a bare percentage and
                // left the listener to guess what was half full. Every slider
                // in the app comes through here, so every one was unnamed.
                .semantics { contentDescription = label },
        )
    }
}

/**
 * Snaps a slider's continuous position onto the nearest legal stop.
 *
 * Rounding, not truncating, and internal rather than private so the guard that
 * came with this code from onboarding can still reach it. That guard exists
 * because day six of seven could not be selected on a device: `toInt()` made
 * the whole band that should read six read five instead. See
 * `ValueSliderConversionTest`.
 */
internal fun Float.toStepValue(range: IntRange, step: Int): Int {
    val steps = ((this - range.first) / step).roundToInt()
    return (range.first + steps * step).coerceIn(range.first, range.last)
}
