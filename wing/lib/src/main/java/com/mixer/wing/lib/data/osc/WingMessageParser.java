package com.mixer.wing.lib.data.osc;

import java.util.regex.Pattern;

/**
 * Parser for WING OSC messages.
 */
public class WingMessageParser {
    private static final Pattern CH_PATTERN = Pattern.compile("/ch/(\\d+)/");
    private static final Pattern CH_FADER_PATTERN = Pattern.compile("^/ch/(\\d+)/fdr$");
    private static final Pattern CH_MUTE_PATTERN = Pattern.compile("^/ch/(\\d+)/mute$");
    private static final Pattern CH_PAN_PATTERN = Pattern.compile("^/ch/(\\d+)/pan$");

    public static Integer parseChannelNumber(String address) {
        java.util.regex.Matcher m = CH_PATTERN.matcher(address);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    public static Integer parseChFaderChannel(String address) {
        java.util.regex.Matcher m = CH_FADER_PATTERN.matcher(address);
        if (m.matches()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    public static Integer parseChMuteChannel(String address) {
        java.util.regex.Matcher m = CH_MUTE_PATTERN.matcher(address);
        if (m.matches()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    public static Float getFloat(OSCMessage msg) {
        if (msg.args.length > 0 && msg.args[0] instanceof Float) {
            return (Float) msg.args[0];
        }
        if (msg.args.length > 0 && msg.args[0] instanceof Number) {
            return ((Number) msg.args[0]).floatValue();
        }
        return null;
    }

    public static Integer getInt(OSCMessage msg) {
        if (msg.args.length > 0 && msg.args[0] instanceof Integer) {
            return (Integer) msg.args[0];
        }
        if (msg.args.length > 0 && msg.args[0] instanceof Number) {
            return ((Number) msg.args[0]).intValue();
        }
        return null;
    }

    /**
     * Parse little-endian meter blob from WING /meters/1.
     * Blob format: [4-byte BE size][little-endian 16-bit signed values...]
     * WING uses little-endian for meter values (unlike XR18 which uses big-endian).
     *
     * @param blob raw blob bytes (starting at offset 0 of blob)
     * @param blobDataLen number of data bytes in blob (excluding 4-byte size header)
     * @return array of short meter values (little-endian decoded)
     */
    public static short[] parseMeterBlobLittleEndian(byte[] blob, int blobDataLen) {
        int numMeters = blobDataLen / 2;
        short[] values = new short[numMeters];
        for (int i = 0; i < numMeters; i++) {
            int b0 = blob[i * 2] & 0xFF;
            int b1 = blob[i * 2 + 1] & 0xFF;
            int unsignedVal = (b1 << 8) | b0;  // LITTLE-endian
            values[i] = (short) (unsignedVal >= 32768 ? unsignedVal - 65536 : unsignedVal);
        }
        return values;
    }
}