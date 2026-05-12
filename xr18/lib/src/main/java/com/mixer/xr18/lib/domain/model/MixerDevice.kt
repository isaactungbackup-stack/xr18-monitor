package com.mixer.xr18.lib.domain.model

/**
 * Represents a discovered XR18 mixer on the network.
 */
data class MixerDevice(
    val ipAddress: String,
    val name: String,
    val model: String,
    val firmwareVersion: String,
    val port: Int = 10023
)
