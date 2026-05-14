package com.mixer.xr18.lib.data.osc;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for building XR18 OSC messages.
 * No external dependencies.
 */
public class XR18MessageFactory {

    public static String buildXRemote() {
        return "/xremote";
    }

    public static List<String> buildChannelQuery(int channel) {
        List<String> addrs = new ArrayList<>();
        String ch = channel < 10 ? ("0" + channel) : String.valueOf(channel);
        addrs.add("/ch/" + ch + "/mix/fader");
        addrs.add("/ch/" + ch + "/mix/on");
        addrs.add("/ch/" + ch + "/mix/pan");
        addrs.add("/ch/" + ch + "/eq/on");
        addrs.add("/ch/" + ch + "/eq/1/g");
        addrs.add("/ch/" + ch + "/eq/2/g");
        addrs.add("/ch/" + ch + "/eq/3/g");
        addrs.add("/ch/" + ch + "/eq/4/g");
        addrs.add("/headamp/" + channel + "/gain");
        return addrs;
    }

    public static String buildSubscribeRequest(String param) {
        return "/" + param + "/subscribe";
    }
}