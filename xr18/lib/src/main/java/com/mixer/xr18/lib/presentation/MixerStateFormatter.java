package com.mixer.xr18.lib.presentation;

import com.mixer.xr18.lib.domain.model.ChannelState;
import com.mixer.xr18.lib.domain.model.EqBands;
import com.mixer.xr18.lib.domain.model.MixerState;

/**
 * Converts raw [MixerState] to human-readable natural text.
 * Used for log output, status display, or debug UIs.
 */
public class MixerStateFormatter {
    private static final String TAG = "MixerFormatter";

    /**
     * Render the full mixer state as a natural-language block.
     */
    public static String format(MixerState state) {
        StringBuilder sb = new StringBuilder();

        sb.append("══════════════════════════════════════=\n");
        sb.append("  XR18 Mixer Status\n");
        sb.append("═══════════════════════════════════════\n");

        if (state.device != null) {
            sb.append("\n┌ Device Info ───────────────────────┐\n");
            sb.append("│ Name:        ").append(state.device.name).append("\n");
            sb.append("│ Model:       ").append(state.device.model).append("\n");
            sb.append("│ IP:          ").append(state.device.ipAddress).append("\n");
            sb.append("│ Firmware:    ").append(state.device.firmwareVersion).append("\n");
            sb.append("│ Port:        ").append(state.device.port).append("\n");
            sb.append("└──────────────────────────────────────┘\n");
        }

        sb.append("\n┌ Channel Status ─────────────────────┐\n");
        for (ChannelState ch : state.channels) {
            sb.append(formatChannel(ch)).append("\n");
        }
        sb.append("└──────────────────────────────────────┘\n");

        return sb.toString();
    }

    public static String formatChannel(ChannelState ch) {
        String num = String.format("%02d", ch.channelNumber);
        String fPct = ch.faderPercent();
        String fDb = ch.faderDbString();
        String mute = ch.muted ? "ON" : "OFF";
        String pan = ch.panString();

        String line1 = "  CH" + num + " Fader: " + fPct + " (" + fDb + "), Mute: " + mute + ", Pan: " + pan;

        String preampLine = String.format("Preamp: %.1f dB", ch.preampGain);
        String eqLine = ch.eqEnabled
            ? "EQ: ON [" + formatEqBands(ch.eqBands) + "]"
            : "EQ: OFF";

        if (ch.eqEnabled || ch.preampGain != 0f) {
            return line1 + "\n       " + preampLine + ", " + eqLine;
        }
        return line1;
    }

    private static String formatEqBands(EqBands bands) {
        return String.format("1:%.1fdB 2:%.1fdB 3:%.1fdB 4:%.1fdB",
            bands.band1, bands.band2, bands.band3, bands.band4);
    }

    /**
     * Short one-line summary of all channels.
     */
    public static String formatCompact(MixerState state) {
        StringBuilder sb = new StringBuilder();
        for (ChannelState ch : state.channels) {
            String n = String.format("%02d", ch.channelNumber);
            String m = ch.muted ? "M" : ".";
            String f = String.format("%.0f", ch.fader * 100);
            sb.append("[").append(n).append(":").append(f).append("%").append(m).append("]");
        }
        return sb.toString();
    }
}