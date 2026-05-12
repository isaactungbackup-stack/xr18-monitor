package com.mixer.xr18.lib.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mixer.xr18.lib.domain.model.MixerDevice
import com.mixer.xr18.lib.domain.model.MixerState
import com.mixer.xr18.lib.domain.usecase.DiscoverMixersUseCase
import com.mixer.xr18.lib.domain.usecase.ObserveMixerStateUseCase
import com.mixer.xr18.lib.domain.usecase.QueryChannelStatesUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class Xr18ViewModel(
    private val discoverUseCase: DiscoverMixersUseCase,
    private val queryUseCase: QueryChannelStatesUseCase,
    private val observeUseCase: ObserveMixerStateUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val mixerState: StateFlow<MixerState> = observeUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, MixerState())

    sealed class UiState {
        data object Idle : UiState()
        data object Discovering : UiState()
        data class DevicesFound(val devices: List<MixerDevice>) : UiState()
        data object QueryingChannels : UiState()
        data object Connected : UiState()
        data class Error(val message: String) : UiState()
    }

    fun 開始探索() {
        viewModelScope.launch {
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
        viewModelScope.launch {
            _uiState.value = UiState.QueryingChannels
            try {
                queryUseCase(device)
                _uiState.value = UiState.Connected
            } catch (e: Exception) {
                _uiState.value = UiState.Error("連線失敗: ${e.message}")
            }
        }
    }
}
