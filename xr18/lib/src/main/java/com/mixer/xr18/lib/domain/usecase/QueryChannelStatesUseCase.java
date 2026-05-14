package com.mixer.xr18.lib.domain.usecase;

import com.mixer.xr18.lib.domain.model.MixerDevice;
import com.mixer.xr18.lib.domain.model.MixerState;
import com.mixer.xr18.lib.domain.repository.MixerRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
            // Wait up to 8 seconds for channel data to arrive
            long deadline = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < deadline) {
                // State updates come through the MixerStateListener
                // The caller should have set up a listener before calling this
            }
            // Return whatever we have so far via callback
            callback.onChannelStatesReady(null);
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}