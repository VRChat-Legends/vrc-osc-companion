package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.osc.VrcOsc
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.Bad
import com.vrchatlegends.osccompanion.ui.theme.Good
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
import com.vrchatlegends.osccompanion.ui.theme.Warn
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScaleScreen(viewModel: AppViewModel) {
    val eyeHeight by viewModel.osc.eyeHeight.collectAsStateWithLifecycle()
    val minHeight by viewModel.osc.eyeHeightMin.collectAsStateWithLifecycle()
    val maxHeight by viewModel.osc.eyeHeightMax.collectAsStateWithLifecycle()
    val allowed by viewModel.osc.scalingAllowed.collectAsStateWithLifecycle()

    var draft by remember { mutableFloatStateOf(eyeHeight) }
    LaunchedEffect(eyeHeight) { draft = eyeHeight }

    ScreenScaffold(
        title = "Avatar scale",
        subtitle = "Adjust your VRChat eye height in metres",
    ) {
        SectionCard(
            title = "Eye height",
            subtitle = if (allowed) {
                "World allows scaling. Menu range is ${fmt(minHeight)} to ${fmt(maxHeight)} m."
            } else {
                "This world blocks scaling, so writes are ignored."
            },
            trailing = { ScalingStatus(allowed) },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScaleValue(
                    label = "CURRENT",
                    value = eyeHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                ScaleValue(
                    label = "TARGET",
                    value = draft,
                    color = if (allowed) SignalCyan else Bad,
                    modifier = Modifier.weight(1f),
                )
            }

            LinearProgressIndicator(
                progress = ((draft - VrcOsc.EYE_HEIGHT_SUPPORTED_MIN) / (3f - VrcOsc.EYE_HEIGHT_SUPPORTED_MIN))
                    .coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                color = if (allowed) SignalCyan else Bad,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Slider(
                value = draft,
                valueRange = VrcOsc.EYE_HEIGHT_SUPPORTED_MIN..3f,
                enabled = allowed,
                onValueChange = { draft = it },
                onValueChangeFinished = {
                    viewModel.osc.setEyeHeight(draft)
                    viewModel.updateSettings { setLastEyeHeight(draft) }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                if (allowed) "Release the slider to apply" else "Scaling is unavailable in this world",
                style = MaterialTheme.typography.labelSmall,
                color = if (allowed) MaterialTheme.colorScheme.onSurfaceVariant else Bad,
            )

            if (draft < VrcOsc.EYE_HEIGHT_SUPPORTED_MIN || draft > VrcOsc.EYE_HEIGHT_SUPPORTED_MAX) {
                Text(
                    "Outside VRChat's supported 0.1 - 100 m range. It will work but VRChat shows a HUD warning.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Warn,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        draft = (draft - 0.05f).coerceAtLeast(0.01f)
                        viewModel.osc.setEyeHeight(draft)
                    },
                    enabled = allowed,
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease eye height by 5 centimetres")
                }
                Surface(
                    modifier = Modifier.width(112.dp),
                    shape = RoundedCornerShape(7.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        "5 cm steps",
                        modifier = Modifier.padding(vertical = 9.dp),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                IconButton(
                    onClick = {
                        draft += 0.05f
                        viewModel.osc.setEyeHeight(draft)
                    },
                    enabled = allowed,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase eye height by 5 centimetres")
                }
            }
        }

        SectionCard(title = "Presets", subtitle = "Pick a starting point, then fine tune above") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { (label, height) ->
                    FilterChip(
                        selected = abs(draft - height) < 0.025f,
                        onClick = {
                            draft = height
                            viewModel.osc.setEyeHeight(height)
                            viewModel.updateSettings { setLastEyeHeight(height) }
                        },
                        enabled = allowed,
                        label = { Text("$label  ${fmt(height)} m") },
                        leadingIcon = if (abs(draft - height) < 0.025f) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            { Icon(Icons.Filled.Straighten, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScaleValue(
    label: String,
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${fmt(value)} m",
                style = MaterialTheme.typography.headlineSmall,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ScalingStatus(allowed: Boolean) {
    val color = if (allowed) Good else Bad
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Text(
                if (allowed) "ALLOWED" else "BLOCKED",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val PRESETS = listOf(
    "Tiny" to 0.3f,
    "Small" to 0.9f,
    "Short" to 1.4f,
    "Default" to 1.7f,
    "Tall" to 2.0f,
    "Giant" to 3.0f,
)

private fun fmt(value: Float) = String.format(Locale.ROOT, "%.2f", value)
