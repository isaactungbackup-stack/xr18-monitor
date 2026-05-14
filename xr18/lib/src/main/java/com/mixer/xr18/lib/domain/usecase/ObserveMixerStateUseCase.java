package com.mixer.xr18.lib.domain.usecase;

import com.mixer.xr18.lib.domain.model.MixerState;
import com.mixer.xr18.lib.domain.repository.MixerRepository;

/**
 * Use case: observe mixer state changes as a continuous stream.
 * The caller registers a MixerStateListener via the repository to receive updates.
 */
public class ObserveMixerStateUseCase {
    private final MixerRepository repository;

    public ObserveMixerStateUseCase(MixerRepository repository) {
        this.repository = repository;
    }

    public void setListener(MixerRepository.MixerStateListener listener) {
        repository.setMixerStateListener(listener);
    }
}