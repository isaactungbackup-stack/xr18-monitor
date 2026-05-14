package com.mixer.xr18.lib.data.osc;

import java.util.regex.Pattern;

/**
 * Parser for XR18 OSC messages.
 * No external dependencies.
 */
public class XR18MessageParser {
    private static final Pattern CH_PATTERN = Pattern.compile("/ch/(\\d+)/");
    private static final Pattern EQ_PATTERN = Pattern.compile("/eq/(\\d+)/");

    public static Integer parseChannelNumber(String address) {
        java.util.regex.Matcher m = CH_PATTERN.matcher(address);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    public static Integer parseEqBand(String address) {
        java.util.regex.Matcher m = EQ_PATTERN.matcher(address);
        if (m.find()) {
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
}