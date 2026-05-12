package com.mixer.xr18.lib.presentation

import com.mixer.xr18.lib.domain.model.MixerDevice
import com.mixer.xr18.lib.domain.model.MixerState
import com.mixer.xr18.lib.domain.usecase.DiscoverMixersUseCase
import com.mixer.xr18.lib.domain.usecase.ObserveMixerStateUseCase
import com.mixer.xr18.lib.domain.usecase.QueryChannelStatesUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * ViewModel replacement without Android lifecycle dependency.
 */
class Xr18ViewModel(
    private val discoverUseCase: DiscoverMixersUseCase,
    private val queryUseCase: QueryChannelStatesUseCase,
    private val observeUseCase: ObserveMixerStateUseCase
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    sealed class UiState {
        data object Idle : UiState()
        data object Discovering : UiState()
        data class DevicesFound(val devices: List<MixerDevice>) : UiState()
        data object QueryingChannels : UiState()
        data object Connected : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _mixerState = MutableStateFlow(MixerState())
    val mixerState: StateFlow<MixerState> = _mixerState.asStateFlow()

    fun 開始探索() {
        scope.launch {
            _uiState.value = UiState.Discovering
            try {
                val devices = discoverUseCase()
                _uiState.value = UiState.DevicesFound(devices)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("探索失敗: ${e.message}")
            }
        }
    }

    fun 連線至混音器(device: MixerDevice) {
        scope.launch {
            _uiState.value = UiState.QueryingChannels
            try {
                queryUseCase(device)
                _uiState.value = UiState.Connected
            } catch (e: Exception) {
                _uiState.value = UiState.Error("連線失敗: ${e.message}")
            }
        }
    }

    fun cancel() {
        scope.cancel()
    }
}
