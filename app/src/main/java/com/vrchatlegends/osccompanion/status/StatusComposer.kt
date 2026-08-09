package com.vrchatlegends.osccompanion.status

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import com.vrchatlegends.osccompanion.data.AppSettings
import com.vrchatlegends.osccompanion.data.StatusLine
import com.vrchatlegends.osccompanion.osc.clipChatbox
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceSnapshot(
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
)

object DeviceStats {

    /**
     * Headset battery. Quest controller batteries are only exposed through the Meta XR
     * SDK inside an immersive app, so a 2D panel app cannot read them.
     */
    fun read(context: Context): DeviceSnapshot {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return DeviceSnapshot()

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else null
        return DeviceSnapshot(
            batteryPercent = percent,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }

    fun uptime(): String {
        val seconds = SystemClock.elapsedRealtime() / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}

/**
 * Builds the auto-chatbox line, MagicChatbox style.
 *
 * Everything is assembled then clipped to VRChat's 144 character budget, so a long
 * custom prefix squeezes the modules rather than getting the whole message rejected.
 */
class StatusComposer {

    private val clockFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private var rotationIndex = 0

    data class Input(
        val settings: AppSettings,
        val device: DeviceSnapshot,
        val heartRate: Int?,
        val vrclDisplayName: String?,
        val rotationLines: List<StatusLine>,
    )

    fun compose(input: Input): String {
        val parts = mutableListOf<String>()

        input.settings.statusPrefix.trim().takeIf { it.isNotEmpty() }?.let { parts += it }

        nextRotationLine(input.rotationLines)?.let { parts += it }

        if (input.settings.statusShowClock) {
            parts += clockFormat.format(Date())
        }

        if (input.settings.statusShowBattery) {
            input.device.batteryPercent?.let { pct ->
                parts += if (input.device.charging) "$pct% (charging)" else "$pct%"
            }
        }

        if (input.settings.statusShowHeartRate) {
            input.heartRate?.takeIf { it > 0 }?.let { parts += "$it BPM" }
        }

        if (input.settings.statusShowVrcl) {
            input.vrclDisplayName?.takeIf { it.isNotBlank() }?.let { parts += it }
        }

        return clipChatbox(parts.joinToString(SEPARATOR))
    }

    private fun nextRotationLine(lines: List<StatusLine>): String? {
        val enabled = lines.filter { it.enabled && it.text.isNotBlank() }
        if (enabled.isEmpty()) return null
        val line = enabled[rotationIndex % enabled.size]
        rotationIndex = (rotationIndex + 1) % enabled.size
        return line.text
    }

    companion object {
        const val SEPARATOR = "  |  "
    }
}

/**
 * Splits a long string into chatbox-sized windows so it can scroll instead of being cut.
 * Braille or wide glyphs count as one character each, same as VRChat's own counter.
 */
fun marqueeFrames(text: String, window: Int = 40, pad: String = "   "): List<String> {
    if (text.length <= window) return listOf(text)
    val loop = text + pad
    return loop.indices.map { start ->
        val end = start + window
        if (end <= loop.length) loop.substring(start, end)
        else loop.substring(start) + loop.substring(0, end - loop.length)
    }
}
