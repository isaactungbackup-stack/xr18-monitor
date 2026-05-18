package com.mixer.wing.lib.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates all channel states + discovery info.
 * WING supports 48 channels.
 */
public class MixerState {
    public MixerDevice device;
    public List<ChannelState> channels;
    public float lrMeterLeft;
    public float lrMeterRight;

    public MixerState() {
        this.channels = new ArrayList<>(48);
        for (int i = 0; i < 48; i++) {
            this.channels.add(new ChannelState(i + 1));
        }
        this.lrMeterLeft = 0f;
        this.lrMeterRight = 0f;
    }

    public MixerState(MixerDevice device, List<ChannelState> channels) {
        this.device = device;
        this.channels = channels;
        this.lrMeterLeft = 0f;
        this.lrMeterRight = 0f;
    }

    public ChannelState[] getChannels() {
        return channels.toArray(new ChannelState[0]);
    }
}