package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vrchatlegends.osccompanion.osc.VrcOsc
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.SignalCoral
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
import com.vrchatlegends.osccompanion.ui.theme.Warn
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InputScreen(viewModel: AppViewModel) {
    val buttonGroups = remember { VrcOsc.BUTTONS.groupBy { it.group } }

    ScreenScaffold(
        title = "Input",
        subtitle = "Drives VRChat's own controls. Buttons auto-release, axes snap back to zero.",
    ) {
        SectionCard(
            title = "Emergency release",
            subtitle = "Stops every held button and returns every axis to zero",
            trailing = { Icon(Icons.Filled.Emergency, contentDescription = null, tint = Warn) },
        ) {
            Button(
                onClick = { viewModel.osc.releaseAllInputs() },
                colors = ButtonDefaults.buttonColors(containerColor = Warn),
            ) {
                Icon(Icons.Filled.Emergency, contentDescription = null)
                Text("Release everything")
            }
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
    var pressed by remember { mutableStateOf(false) }
    val container by animateColorAsState(
        targetValue = if (pressed) SignalCoral.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = androidx.compose.animation.core.tween(100),
        label = "held input",
    )
    val border = if (pressed) SignalCoral else MaterialTheme.colorScheme.outline

    Surface(
        modifier = Modifier
            .heightIn(min = 52.dp)
            .semantics {
                role = Role.Button
                onClick(label = "Pulse $label") {
                    pulse()
                    true
                }
            }
            .pointerInput(label) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    hold()
                    tryAwaitRelease()
                    pressed = false
                    release()
                },
                onTap = { pulse() },
            )
        },
        shape = RoundedCornerShape(8.dp),
        color = container,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.TouchApp,
                contentDescription = null,
                tint = if (pressed) SignalCoral else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AxisRow(label: String, onValue: (Float) -> Unit) {
    var value by remember { mutableFloatStateOf(0f) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(150.dp))
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
        Surface(
            modifier = Modifier.width(62.dp),
            shape = RoundedCornerShape(6.dp),
            color = SignalCyan.copy(alpha = 0.11f),
        ) {
            Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                Text(
                    String.format(Locale.ROOT, "%.2f", value),
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalCyan,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
