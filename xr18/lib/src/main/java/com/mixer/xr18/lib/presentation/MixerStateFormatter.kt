package com.mixer.xr18.lib.presentation

import android.util.Log
import com.mixer.xr18.lib.domain.model.ChannelState
import com.mixer.xr18.lib.domain.model.MixerState

/**
 * Converts raw [MixerState] to human-readable natural text.
 * Used for log output, status display, or debug UIs.
 */
object MixerStateFormatter {

    private const val TAG = "MixerFormatter"

    /**
     * Render the full mixer state as a natural-language block.
     * Shows device info + all 16 channel statuses.
     */
    fun format(state: MixerState): String {
        val sb = StringBuilder()

        // Header
        sb.appendLine("═══════════════════════════════════════")
        sb.appendLine("  XR18 Mixer Status")
        sb.appendLine("═══════════════════════════════════════")

        state.device?.let { dev ->
            sb.appendLine("")
            sb.appendLine("┌ Device Info ───────────────────────┐")
            sb.appendLine("│ Name:        ${dev.name}")
            sb.appendLine("│ Model:       ${dev.model}")
            sb.appendLine("│ IP:          ${dev.ipAddress}")
            sb.appendLine("│ Firmware:    ${dev.firmwareVersion}")
            sb.appendLine("│ Port:        ${dev.port}")
            sb.appendLine("└──────────────────────────────────────┘")
        }

        sb.appendLine("")
        sb.appendLine("┌ Channel Status ─────────────────────┐")

        state.channels.forEach { ch ->
            sb.appendLine(formatChannel(ch))
        }

        sb.appendLine("└──────────────────────────────────────┘")
        return sb.toString()
    }

    /**
     * Format a single channel as a readable one-liner.
     *
     * Example output:
     *   CH01 Fader: 60.6% (-8.2 dB), Mute: OFF, Pan: C
     *   CH02 Fader: 75.0% (-3.2 dB), Mute: ON,  Pan: L40
     */
    fun formatChannel(ch: ChannelState): String {
        val num  = "%02d".format(ch.channelNumber)
        val fPct = ch.faderPercent()
        val fDb  = ch.faderDbString()
        val mute = if (ch.muted) "ON"  else "OFF"
        val pan  = ch.panString()

        val faderLine = "Fader: $fPct ($fDb)"
        val muteLine  = "Mute: $mute"
        val panLine   = "Pan: $pan"

        val line1 = "  CH$num $faderLine, $muteLine, $panLine"

        // Preamp + EQ detail on second line (only if non-zero or active)
        val preampLine = "Preamp: ${"%.1f dB".format(ch.preampGain)}"
        val eqLine = if (ch.eqEnabled) {
            "EQ: ON [${formatEqBands(ch.eqBands)}]"
        } else {
            "EQ: OFF"
        }

        val line2 = if (ch.eqEnabled || ch.preampGain != 0f) {
            "       $preampLine, $eqLine"
        } else {
            ""
        }

        return if (line2.isNotEmpty()) "$line1\n$line2" else line1
    }

    private fun formatEqBands(bands: com.mixer.xr18.lib.domain.model.EqBands): String {
        return listOf(bands.band1, bands.band2, bands.band3, bands.band4)
            .mapIndexed { i, v -> "${i + 1}:${"%.1f".format(v)}dB" }
            .joinToString(" ")
    }

    /**
     * Short one-line summary of all channels — useful for a compact log line.
     */
    fun formatCompact(state: MixerState): String {
        val parts = state.channels.map { ch ->
            val n  = "%02d".format(ch.channelNumber)
            val m  = if (ch.muted) "M" else "."
            val f  = "%.0f".format(ch.fader * 100)
            "[$n:$f%$m]"
        }
        return parts.joinToString(" ")
    }

    /**
     * Write formatted state to android.util.Log at INFO level.
     */
    fun log(state: MixerState) {
        Log.i(TAG, format(state))
    }
}
