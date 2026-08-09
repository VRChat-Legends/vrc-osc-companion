package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.vrchatlegends.osccompanion.osc.VrcOsc
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.Warn

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InputScreen(viewModel: AppViewModel) {
    val buttonGroups = remember { VrcOsc.BUTTONS.groupBy { it.group } }

    ScreenScaffold(
        title = "Input",
        subtitle = "Drives VRChat's own controls. Buttons auto-release, axes snap back to zero.",
    ) {
        SectionCard(
            title = "Safety",
            subtitle = "A latched input keeps moving you forever. This clears every axis and button.",
        ) {
            Button(
                onClick = { viewModel.osc.releaseAllInputs() },
                colors = ButtonDefaults.buttonColors(containerColor = Warn),
            ) { Text("Release all inputs") }
        }

        buttonGroups.forEach { (group, controls) ->
            SectionCard(title = group) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    controls.forEach { control ->
                        HoldButton(
                            label = control.label + if (control.vrOnly) "  (VR)" else "",
                            hold = { viewModel.osc.setButton(control.address, true) },
                            release = { viewModel.osc.setButton(control.address, false) },
                            pulse = { viewModel.osc.pulseButton(control.address) },
                        )
                    }
                }
                controls.mapNotNull { it.note }.distinct().forEach { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionCard(title = "Axes", subtitle = "Release the slider to send 0 and stop moving") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VrcOsc.AXES.forEach { axis -> AxisRow(axis.label) { viewModel.osc.setAxis(axis.address, it) } }
            }
        }
    }
}

/**
 * Press and hold sends 1 for as long as the trigger is down, a plain tap sends a short
 * 1-then-0 pulse. Both are needed: holding is right for movement, pulsing is right for
 * toggles like the quick menu.
 */
@Composable
private fun HoldButton(
    label: String,
    hold: () -> Unit,
    release: () -> Unit,
    pulse: () -> Unit,
) {
    OutlinedButton(
        // The gesture detector owns both paths, so the button's own click must be inert.
        onClick = {},
        modifier = Modifier.pointerInput(label) {
            detectTapGestures(
                onPress = {
                    hold()
                    tryAwaitRelease()
                    release()
                },
                onTap = { pulse() },
            )
        },
    ) { Text(label) }
}

@Composable
private fun AxisRow(label: String, onValue: (Float) -> Unit) {
    var value by remember { mutableStateOf(0f) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(220.dp))
        Slider(
            value = value,
            valueRange = -1f..1f,
            onValueChange = {
                value = it
                onValue(it)
            },
            onValueChangeFinished = {
                value = 0f
                onValue(0f)
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            String.format("%.2f", value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
