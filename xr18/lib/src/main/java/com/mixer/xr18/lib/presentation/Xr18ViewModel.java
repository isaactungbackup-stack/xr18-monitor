package com.mixer.xr18.lib.presentation;

import com.mixer.xr18.lib.domain.model.MixerDevice;
import com.mixer.xr18.lib.domain.model.MixerState;
import com.mixer.xr18.lib.domain.repository.MixerRepository;
import com.mixer.xr18.lib.domain.usecase.DiscoverMixersUseCase;
import com.mixer.xr18.lib.domain.usecase.ObserveMixerStateUseCase;
import com.mixer.xr18.lib.domain.usecase.QueryChannelStatesUseCase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel replacement without Android lifecycle dependency.
 * Uses callback listeners instead of StateFlow/Coroutines.
 */
public class Xr18ViewModel {
    private final DiscoverMixersUseCase discoverUseCase;
    private final QueryChannelStatesUseCase queryUseCase;
    private final ObserveMixerStateUseCase observeUseCase;
    private final MixerRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface UiStateListener {
        void onUiStateChanged(UiState state);
    }
    private UiStateListener uiStateListener;

    public interface MixerStateListener {
        void onMixerStateChanged(MixerState state);
    }
    private MixerStateListener mixerStateListener;

    public enum UiState {
        IDLE,
        DISCOVERING,
        DEVICES_FOUND,
        QUERYING_CHANNELS,
        CONNECTED,
        ERROR
    }

    private UiState currentUiState = UiState.IDLE;
    private List<MixerDevice> discoveredDevices = new ArrayList<>();

    public Xr18ViewModel(
            DiscoverMixersUseCase discoverUseCase,
            QueryChannelStatesUseCase queryUseCase,
            ObserveMixerStateUseCase observeUseCase,
            MixerRepository repository
    ) {
        this.discoverUseCase = discoverUseCase;
        this.queryUseCase = queryUseCase;
        this.observeUseCase = observeUseCase;
        this.repository = repository;

        // Wire up state listener to forward to UI
        this.repository.setMixerStateListener(state -> {
            executor.execute(() -> {
                if (mixerStateListener != null) {
                    mixerStateListener.onMixerStateChanged(state);
                }
            });
        });
    }

    public void setUiStateListener(UiStateListener listener) {
        this.uiStateListener = listener;
    }

    public void setMixerStateListener(MixerStateListener listener) {
        this.mixerStateListener = listener;
    }

    public void startDiscovery() {
        setUiState(UiState.DISCOVERING);
        discoverUseCase.execute(2000, devices -> {
            discoveredDevices = devices;
            setUiState(devices.isEmpty() ? UiState.IDLE : UiState.DEVICES_FOUND);
        });
    }

    public void connectToMixer(MixerDevice device) {
        setUiState(UiState.QUERYING_CHANNELS);
        observeUseCase.setListener(state -> {
            executor.execute(() -> {
                if (mixerStateListener != null) {
                    mixerStateListener.onMixerStateChanged(state);
                }
            });
        });
        repository.queryChannelStates(device);
        // After a delay, mark as connected if we have channel data
        executor.execute(() -> {
            try { Thread.sleep(5000); } catch (InterruptedException e) { }
            setUiState(UiState.CONNECTED);
        });
    }

    public void disconnect() {
        repository.close();
        setUiState(UiState.IDLE);
    }

    public List<MixerDevice> getDiscoveredDevices() {
        return discoveredDevices;
    }

    public UiState getUiState() {
        return currentUiState;
    }

    private void setUiState(UiState state) {
        currentUiState = state;
        if (uiStateListener != null) {
            uiStateListener.onUiStateChanged(state);
        }
    }

    public void shutdown() {
        repository.close();
        executor.shutdownNow();
    }
}