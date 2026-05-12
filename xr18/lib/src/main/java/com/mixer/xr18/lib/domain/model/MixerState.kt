package com.mixer.xr18.lib.domain.model

/**
 * Aggregates all channel states + discovery info.
 */
data class MixerState(
    val device: MixerDevice? = null,
    val channels: List<ChannelState> = List(16) { ChannelState(channelNumber = it + 1) }
)
