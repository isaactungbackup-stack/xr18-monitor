package com.mixer.wing.lib.presentation;

import com.mixer.wing.lib.domain.model.MixerDevice;
import com.mixer.wing.lib.domain.model.MixerState;
import com.mixer.wing.lib.domain.repository.MixerRepository;
import com.mixer.wing.lib.domain.usecase.DiscoverMixersUseCase;
import com.mixer.wing.lib.domain.usecase.ObserveMixerStateUseCase;
import com.mixer.wing.lib.domain.usecase.QueryChannelStatesUseCase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for WING mixer UI state management.
 * Mirrors Xr18ViewModel adapted for WING.
 */
public class WingViewModel {
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

    public WingViewModel(
            DiscoverMixersUseCase discoverUseCase,
            QueryChannelStatesUseCase queryUseCase,
            ObserveMixerStateUseCase observeUseCase,
            MixerRepository repository
    ) {
        this.discoverUseCase = discoverUseCase;
        this.queryUseCase = queryUseCase;
        this.observeUseCase = observeUseCase;
        this.repository = repository;

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