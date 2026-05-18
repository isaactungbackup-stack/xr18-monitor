package com.mixer.wing.lib.data.osc;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for building WING OSC messages.
 */
public class WingMessageFactory {

    public static String buildSubscribeAll() {
        return "/S~";
    }

    public static List<String> buildChannelQuery(int channel) {
        List<String> addrs = new ArrayList<>();
        addrs.add(WingAddresses.chFader(channel));
        addrs.add(WingAddresses.chMute(channel));
        addrs.add(WingAddresses.chPan(channel));
        addrs.add(WingAddresses.chPreamp(channel));
        addrs.add(WingAddresses.chEqOn(channel));
        for (int band = 1; band <= 4; band++) {
            addrs.add(WingAddresses.chEqBandGain(channel, band));
        }
        return addrs;
    }

    /** Build the subscription request for /meters/1 (batch 1 = 40 values). */
    public static Object[] buildMeterSubscription(String path, int interval) {
        return new Object[]{ path, interval };
    }
}