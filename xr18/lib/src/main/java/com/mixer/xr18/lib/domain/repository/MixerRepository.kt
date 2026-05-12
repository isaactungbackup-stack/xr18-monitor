package com.mixer.xr18.lib.domain.repository

import com.mixer.xr18.lib.domain.model.MixerDevice
import com.mixer.xr18.lib.domain.model.MixerState

/**
 * Repository interface for mixer discovery and state.
 * Defined in the domain layer so the app layer depends only on abstractions.
 */
interface MixerRepository {

    /**
     * Perform a LAN broadcast discovery and return all found devices.
     * This is a suspend function — runs on the calling coroutine context.
     *
     * @param timeoutMs time to wait for responses
     * @return list of discovered MixerDevice (empty if none found)
     */
    suspend fun discoverDevices(timeoutMs: Long = 2000): List<MixerDevice>

    /**
     * Query the current state of all 16 channels from the given device.
     * Results are emitted incrementally via the StateFlow.
     *
     * @param device the mixer to query
     */
    suspend fun queryChannelStates(device: MixerDevice)

    /**
     * A Flow of the latest MixerState, updated whenever a parameter changes.
     */
    fun mixerStateFlow(): kotlinx.coroutines.flow.StateFlow<MixerState>
}
