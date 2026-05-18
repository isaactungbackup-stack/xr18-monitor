package com.mixer.wing.lib.domain.repository;

import com.mixer.wing.lib.domain.model.MixerDevice;
import com.mixer.wing.lib.domain.model.MixerState;

import java.util.List;

/**
 * Repository interface for WING mixer discovery and state.
 * Uses callback/listener pattern (no coroutines, no StateFlow).
 */
public interface MixerRepository {

    interface DiscoveryCallback {
        void onDevicesFound(List<MixerDevice> devices);
    }

    interface ChannelQueryCallback {
        void onChannelStatesReady(MixerState state);
    }

    interface MixerStateListener {
        void onMixerStateChanged(MixerState state);
    }

    interface OscMessageListener {
        void onOscMessage(String type, String message);
    }

    void discoverDevices(long timeoutMs, DiscoveryCallback callback);

    void queryChannelStates(MixerDevice device);

    void setMixerStateListener(MixerStateListener listener);

    void setOscMessageListener(OscMessageListener listener);

    void close();
}