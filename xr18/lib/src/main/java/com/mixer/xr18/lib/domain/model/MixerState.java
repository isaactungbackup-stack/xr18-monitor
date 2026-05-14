package com.mixer.xr18.lib.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates all channel states + discovery info.
 */
public class MixerState {
    public MixerDevice device;
    public List<ChannelState> channels;

    public MixerState() {
        this.channels = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) {
            this.channels.add(new ChannelState(i + 1));
        }
    }

    public MixerState(MixerDevice device, List<ChannelState> channels) {
        this.device = device;
        this.channels = channels;
    }
}