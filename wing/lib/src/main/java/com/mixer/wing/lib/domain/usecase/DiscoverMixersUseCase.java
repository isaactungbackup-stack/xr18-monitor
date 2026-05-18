package com.mixer.wing.lib.domain.usecase;

import com.mixer.wing.lib.domain.model.MixerDevice;
import com.mixer.wing.lib.domain.repository.MixerRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Use case: discover WING mixers on the LAN.
 */
public class DiscoverMixersUseCase {
    private final MixerRepository repository;
    private final ExecutorService executor;

    public DiscoverMixersUseCase(MixerRepository repository) {
        this.repository = repository;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void execute(long timeoutMs, MixerRepository.DiscoveryCallback callback) {
        executor.submit(() -> repository.discoverDevices(timeoutMs, callback));
    }

    public void shutdown() {
        executor.shutdown();
    }
}