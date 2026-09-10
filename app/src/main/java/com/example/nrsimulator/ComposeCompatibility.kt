package com.example.nrsimulator

import androidx.compose.material3.Slider as Material3Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Compatibility overload for the legacy Slider call order used by MainActivity.
 * Keeps the existing simulator UI/source logic unchanged while adapting it to
 * the current Material 3 Slider API, where valueRange is a named parameter.
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Material3Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        steps = steps,
        valueRange = valueRange
    )
}
