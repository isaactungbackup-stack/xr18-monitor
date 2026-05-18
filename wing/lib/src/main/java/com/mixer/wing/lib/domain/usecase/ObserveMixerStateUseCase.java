package com.mixer.wing.lib.domain.usecase;

import com.mixer.wing.lib.domain.model.MixerState;
import com.mixer.wing.lib.domain.repository.MixerRepository;

/**
 * Use case: observe mixer state changes as a continuous stream.
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