package com.mixer.xr18.lib.domain.repository;

import com.mixer.xr18.lib.domain.model.MixerDevice;
import com.mixer.xr18.lib.domain.model.MixerState;

import java.util.List;

/**
 * Repository interface for mixer discovery and state.
 * Uses callback/listener instead of Kotlin coroutines or StateFlow.
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

    /**
     * Perform a LAN broadcast discovery and return all found devices.
     * @param timeoutMs time to wait for responses
     * @param callback called with the list of discovered devices
     */
    void discoverDevices(long timeoutMs, DiscoveryCallback callback);

    /**
     * Begin querying channel states from the given device.
     * Results are delivered via MixerStateListener.
     * @param device the mixer to query
     */
    void queryChannelStates(MixerDevice device);

    /**
     * Register a listener for ongoing mixer state updates.
     * @param listener called whenever mixer state changes
     */
    void setMixerStateListener(MixerStateListener listener);

    /**
     * Register a listener for raw OSC message logging.
     * @param listener called for each incoming/outgoing OSC message
     */
    void setOscMessageListener(OscMessageListener listener);

    /**
     * Stop all ongoing operations and release resources.
     */
    void close();
}