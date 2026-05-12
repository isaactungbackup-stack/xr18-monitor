package com.mixer.xr18.lib.domain.usecase

import com.mixer.xr18.lib.domain.model.ChannelState
import com.mixer.xr18.lib.domain.model.MixerDevice
import com.mixer.xr18.lib.domain.model.MixerState
import com.mixer.xr18.lib.domain.repository.MixerRepository

/**
 * Use case: query all channel states from a given mixer.
 */
class QueryChannelStatesUseCase(
    private val repository: MixerRepository
) {
    suspend operator fun invoke(device: MixerDevice): MixerState {
        repository.queryChannelStates(device)
        return repository.mixerStateFlow().value
    }
}
