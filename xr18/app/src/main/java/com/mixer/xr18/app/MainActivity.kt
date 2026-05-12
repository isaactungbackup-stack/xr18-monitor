package com.mixer.xr18.app

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mixer.xr18.lib.data.repository.XR18RepositoryImpl
import com.mixer.xr18.lib.domain.usecase.DiscoverMixersUseCase
import com.mixer.xr18.lib.domain.usecase.ObserveMixerStateUseCase
import com.mixer.xr18.lib.domain.usecase.QueryChannelStatesUseCase
import com.mixer.xr18.lib.presentation.MixerStateFormatter
import com.mixer.xr18.lib.presentation.Xr18ViewModel
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: Xr18ViewModel
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        textView = TextView(this).apply {
            text = "XR18 Test App\nSearching for mixer..."
            textSize = 10f
            setPadding(16, 16, 16, 16)
        }
        
        val scrollView = ScrollView(this).apply {
            addView(textView)
        }
        setContentView(scrollView)

        val repository = XR18RepositoryImpl(scope)
        val discoverUseCase = DiscoverMixersUseCase(repository)
        val queryUseCase = QueryChannelStatesUseCase(repository)
        val observeUseCase = ObserveMixerStateUseCase(repository)
        viewModel = Xr18ViewModel(discoverUseCase, queryUseCase, observeUseCase)

        scope.launch {
            viewModel.mixerState.collect { state ->
                if (state.device != null) {
                    textView.text = MixerStateFormatter.format(state)
                }
            }
        }

        scope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is Xr18ViewModel.UiState.Idle -> {
                        textView.append("\n[Idle]")
                    }
                    is Xr18ViewModel.UiState.Discovering -> {
                        textView.append("\n[Searching...]")
                    }
                    is Xr18ViewModel.UiState.DevicesFound -> {
                        val info = state.devices.joinToString("\n") { "${it.name} (${it.ipAddress})" }
                        textView.append("\n[Found ${state.devices.size} device(s)]\n$info")
                        if (state.devices.isNotEmpty()) {
                            viewModel.連線至混音器(state.devices.first())
                        }
                    }
                    is Xr18ViewModel.UiState.QueryingChannels -> {
                        textView.append("\n[Connecting...]")
                    }
                    is Xr18ViewModel.UiState.Connected -> {
                        textView.append("\n[Connected]")
                    }
                    is Xr18ViewModel.UiState.Error -> {
                        textView.append("\n[Error] ${state.message}")
                    }
                }
            }
        }

        viewModel.開始探索()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.cancel()
        scope.cancel()
    }
}
