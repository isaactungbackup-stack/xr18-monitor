package com.mixer.xr18.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mixer.xr18.lib.data.repository.XR18RepositoryImpl
import com.mixer.xr18.lib.domain.model.ChannelState
import com.mixer.xr18.lib.domain.model.EqBands
import com.mixer.xr18.lib.domain.model.MixerDevice
import com.mixer.xr18.lib.domain.model.MixerState
import com.mixer.xr18.lib.presentation.MixerStateFormatter
import com.mixer.xr18.lib.presentation.Xr18ViewModel
import com.mixer.xr18.lib.domain.usecase.DiscoverMixersUseCase
import com.mixer.xr18.lib.domain.usecase.ObserveMixerStateUseCase
import com.mixer.xr18.lib.domain.usecase.QueryChannelStatesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var viewModel: Xr18ViewModel
    private lateinit var statusText: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private val statusLog = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            statusText = TextView(this).apply {
                text = "XR18 Test App V1.0004\n\nStarting..."
                textSize = 11f
                setPadding(24, 24, 24, 24)
            }
            
            setContentView(ScrollView(this).apply { addView(statusText) })
            
            appendStatus("App started OK")
            initializeComponents()
            
        } catch (e: Exception) {
            setContentView(TextView(this).apply {
                text = "FATAL ERROR in onCreate:\n${e.message}"
                setPadding(32, 32, 32, 32)
            })
        }
    }
    
    private fun initializeComponents() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                appendStatus("Creating repository...")
                val scope = this@MainActivity.lifecycleScope
                val repository = XR18RepositoryImpl(scope)
                appendStatus("Creating use cases...")
                val discoverUseCase = DiscoverMixersUseCase(repository)
                val queryUseCase = QueryChannelStatesUseCase(repository)
                val observeUseCase = ObserveMixerStateUseCase(repository)
                appendStatus("Creating ViewModel...")
                viewModel = Xr18ViewModel(discoverUseCase, queryUseCase, observeUseCase)
                appendStatus("ViewModel ready")
                
                launch { observeUiState() }
                launch { observeMixerState() }
                
                appendStatus("Starting discovery...")
                viewModel.開始探索()
                
            } catch (e: Exception) {
                appendStatus("Init error: ${e.message}")
                showDemoData()
            }
        }
    }
    
    private suspend fun observeUiState() = withContext(Dispatchers.Main) {
        try {
            viewModel.uiState.collect { state ->
                val msg: String = when (state) {
                    is Xr18ViewModel.UiState.Idle -> "Ready"
                    is Xr18ViewModel.UiState.Discovering -> "Searching for XR18..."
                    is Xr18ViewModel.UiState.DevicesFound -> {
                        if (state.devices.isEmpty()) {
                            showDemoData()
                            "No devices found"
                        } else {
                            val names = state.devices.joinToString(", ") { d -> d.name }
                            "Found ${state.devices.size}: $names"
                        }
                    }
                    is Xr18ViewModel.UiState.QueryingChannels -> "Querying channels..."
                    is Xr18ViewModel.UiState.Connected -> "Connected!"
                    is Xr18ViewModel.UiState.Error -> "Error: ${state.message}"
                }
                appendStatus(msg)
            }
        } catch (e: Exception) {
            appendStatus("UI state error: ${e.message}")
        }
    }
    
    private suspend fun observeMixerState() = withContext(Dispatchers.Main) {
        try {
            viewModel.mixerState.collect { state ->
                if (state.device != null) {
                    val formatted = MixerStateFormatter.format(state)
                    mainHandler.post {
                        statusText.text = "XR18 Mixer V1.0004\n\n$formatted"
                    }
                }
            }
        } catch (e: Exception) {
            appendStatus("State error: ${e.message}")
        }
    }
    
    private fun showDemoData() {
        val demoState = MixerState(
            device = MixerDevice(
                ipAddress = "192.168.1.100",
                name = "Demo-XR18",
                model = "XR18",
                firmwareVersion = "1.2.3"
            ),
            channels = (1..16).map { ch ->
                ChannelState(
                    channelNumber = ch,
                    fader = 0.4f + (ch % 5) * 0.1f,
                    faderDb = ChannelState.faderToDb(0.4f + (ch % 5) * 0.1f),
                    muted = ch % 4 == 0,
                    pan = 0.5f,
                    preampGain = (ch % 6) * 3f,
                    eqEnabled = ch % 3 != 0,
                    eqBands = EqBands(
                        band1 = (ch % 7 - 3) * 1.5f,
                        band2 = (ch % 5 - 2) * 1f,
                        band3 = (ch % 6 - 3) * 2f,
                        band4 = (ch % 4 - 2) * 0.5f
                    )
                )
            }
        )
        val formatted = MixerStateFormatter.format(demoState)
        mainHandler.post {
            statusText.text = "XR18 V1.0004 (Demo)\n\n$formatted"
        }
    }
    
    private fun appendStatus(msg: String) {
        mainHandler.post {
            statusLog.add(msg)
            if (statusLog.size > 50) statusLog.removeAt(0)
            statusText.text = "XR18 Test V1.0004\n\n" + statusLog.takeLast(30).joinToString("\n")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::viewModel.isInitialized) {
            try { viewModel.cancel() } catch (e: Exception) { }
        }
    }
}
