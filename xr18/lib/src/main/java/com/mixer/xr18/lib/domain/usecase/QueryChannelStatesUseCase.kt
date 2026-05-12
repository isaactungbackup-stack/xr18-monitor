package com.mixer.xr18.lib.domain.usecase

import com.mixer.xr18.lib.domain.model.ChannelState
import com.mixer.xr18.lib.domain.model.MixerDevice
import com.mixer.xr18.lib.domain.model.MixerState
import com.mixer.xr18.lib.domain.repository.MixerRepository
import kotlinx.coroutines.*

/**
 * Use case: query all channel states from a given mixer.
 */
class QueryChannelStatesUseCase(
    private val repository: MixerRepository
) {
    suspend operator fun invoke(device: MixerDevice): MixerState {
        // Start the query - this launches coroutines internally
        repository.queryChannelStates(device)
        
        // Wait up to 8 seconds for channel data to arrive
        val startMs = System.currentTimeMillis()
        while (System.currentTimeMillis() - startMs < 8000) {
            val state = repository.mixerStateFlow().value
            if (state.channels.isNotEmpty() && state.channels.any { it.fader != 0.707f }) {
                return state
            }
            delay(500)
        }
        
        return repository.mixerStateFlow().value
    }
}
