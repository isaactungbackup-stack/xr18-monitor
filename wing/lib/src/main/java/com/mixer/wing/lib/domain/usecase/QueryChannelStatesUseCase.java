package com.mixer.wing.lib.domain.usecase;

import com.mixer.wing.lib.domain.model.MixerDevice;
import com.mixer.wing.lib.domain.model.MixerState;
import com.mixer.wing.lib.domain.repository.MixerRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Use case: query all channel states from a given mixer.
 */
public class QueryChannelStatesUseCase {
    private final MixerRepository repository;
    private final ExecutorService executor;

    public QueryChannelStatesUseCase(MixerRepository repository) {
        this.repository = repository;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void execute(MixerDevice device, MixerRepository.ChannelQueryCallback callback) {
        executor.submit(() -> {
            repository.queryChannelStates(device);
            long deadline = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < deadline) {
                // State updates come via MixerStateListener
            }
            callback.onChannelStatesReady(null);
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}