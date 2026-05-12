package com.mixer.xr18.app

import com.mixer.xr18.lib.data.repository.XR18RepositoryImpl
import com.mixer.xr18.lib.domain.model.MixerDevice
import com.mixer.xr18.lib.domain.usecase.DiscoverMixersUseCase
import com.mixer.xr18.lib.domain.usecase.QueryChannelStatesUseCase
import com.mixer.xr18.lib.domain.usecase.ObserveMixerStateUseCase
import com.mixer.xr18.lib.presentation.Xr18ViewModel
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch

object DiscoveryHelper {
    private var viewModel: Xr18ViewModel? = null
    
    @JvmStatic
    fun discoverSync(): List<MixerDevice> {
        val latch = CountDownLatch(1)
        var result: List<MixerDevice> = emptyList()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val repository = XR18RepositoryImpl(AppExecutors.scope)
                val discoverUseCase = DiscoverMixersUseCase(repository)
                val queryUseCase = QueryChannelStatesUseCase(repository)
                val observeUseCase = ObserveMixerStateUseCase(repository)
                
                viewModel = Xr18ViewModel(discoverUseCase, queryUseCase, observeUseCase)
                viewModel?.開始探索()
                
                repeat(30) {
                    delay(100)
                    val state = viewModel?.uiState?.value
                    when (state) {
                        is Xr18ViewModel.UiState.DevicesFound -> {
                            result = state.devices
                            return@repeat
                        }
                        is Xr18ViewModel.UiState.Error -> {
                            result = emptyList()
                            return@repeat
                        }
                        else -> { }
                    }
                }
            } catch (e: Exception) {
                result = emptyList()
            } finally {
                latch.countDown()
            }
        }
        
        latch.await()
        return result
    }
    
    @JvmStatic
    fun connectToDevice(device: MixerDevice) {
        viewModel?.連線至混音器(device)
    }
}