package com.mixer.xr18.lib.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates all channel states + discovery info.
 */
public class MixerState {
    public MixerDevice device;
    public List<ChannelState> channels;
    public float lrMeterLeft;   // LR main meter left  (0.0-1.0)
    public float lrMeterRight;  // LR main meter right (0.0-1.0)

    public MixerState() {
        this.channels = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) {
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
}