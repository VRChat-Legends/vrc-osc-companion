package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.osc.VrcOsc
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.LabelledValue
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.Warn

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScaleScreen(viewModel: AppViewModel) {
    val eyeHeight by viewModel.osc.eyeHeight.collectAsStateWithLifecycle()
    val minHeight by viewModel.osc.eyeHeightMin.collectAsStateWithLifecycle()
    val maxHeight by viewModel.osc.eyeHeightMax.collectAsStateWithLifecycle()
    val allowed by viewModel.osc.scalingAllowed.collectAsStateWithLifecycle()

    var draft by remember { mutableStateOf(eyeHeight) }
    LaunchedEffect(eyeHeight) { draft = eyeHeight }

    ScreenScaffold(
        title = "Avatar scale",
        subtitle = "Writes /avatar/eyeheight in metres. This endpoint is writable even though the " +
            "ScaleFactor and EyeHeightAsMeters avatar parameters are read-only.",
    ) {
        SectionCard(
            title = "Eye height",
            subtitle = if (allowed) {
                "World allows scaling. Menu range is ${fmt(minHeight)} to ${fmt(maxHeight)} m."
            } else {
                "This world blocks scaling, so writes are ignored."
            },
        ) {
            LabelledValue("Current", "${fmt(eyeHeight)} m")
            LabelledValue("Target", "${fmt(draft)} m")

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

            if (draft < VrcOsc.EYE_HEIGHT_SUPPORTED_MIN || draft > VrcOsc.EYE_HEIGHT_SUPPORTED_MAX) {
                Text(
                    "Outside VRChat's supported 0.1 - 100 m range. It will work but VRChat shows a HUD warning.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Warn,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    draft = (draft - 0.05f).coerceAtLeast(0.01f)
                    viewModel.osc.setEyeHeight(draft)
                }, enabled = allowed) { Text("-5 cm") }
                OutlinedButton(onClick = {
                    draft += 0.05f
                    viewModel.osc.setEyeHeight(draft)
                }, enabled = allowed) { Text("+5 cm") }
            }
        }

        SectionCard(title = "Presets") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { (label, height) ->
                    AssistChip(
                        onClick = {
                            draft = height
                            viewModel.osc.setEyeHeight(height)
                            viewModel.updateSettings { setLastEyeHeight(height) }
                        },
                        enabled = allowed,
                        label = { Text("$label  ${fmt(height)}m") },
                    )
                }
            }
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

private fun fmt(value: Float) = String.format("%.2f", value)
