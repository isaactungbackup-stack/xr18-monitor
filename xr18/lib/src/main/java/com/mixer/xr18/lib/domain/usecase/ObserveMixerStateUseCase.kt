package com.mixer.xr18.lib.domain.usecase

import com.mixer.xr18.lib.domain.model.MixerState
import com.mixer.xr18.lib.domain.repository.MixerRepository

/**
 * Use case: observe mixer state changes as a continuous stream.
 */
class ObserveMixerStateUseCase(
    private val repository: MixerRepository
) {
    operator fun invoke(): kotlinx.coroutines.flow.StateFlow<MixerState> {
        return repository.mixerStateFlow()
    }
}
