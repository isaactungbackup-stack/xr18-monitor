package com.mixer.xr18.lib.domain.usecase

import com.mixer.xr18.lib.domain.model.MixerDevice
import com.mixer.xr18.lib.domain.repository.MixerRepository

/**
 * Use case: discover XR18 mixers on the LAN.
 */
class DiscoverMixersUseCase(
    private val repository: MixerRepository
) {
    suspend operator fun invoke(timeoutMs: Long = 2000): List<MixerDevice> {
        return repository.discoverDevices(timeoutMs)
    }
}
