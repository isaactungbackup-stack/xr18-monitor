package com.mixer.xr18.lib.domain.model

/**
 * EQ band gain values for a single channel.
 */
data class EqBands(
    val band1: Float = 0f,
    val band2: Float = 0f,
    val band3: Float = 0f,
    val band4: Float = 0f
)

/**
 * Complete state for one mixer input channel (CH01-CH16).
 */
data class ChannelState(
    val channelNumber: Int,          // 1-16
    val fader: Float = 0.707f,       // 0.0–1.0 (linear)
    val faderDb: Float = 0f,         // dB equivalent
    val muted: Boolean = false,
    val pan: Float = 0.5f,          // 0.0 (L) – 0.5 (C) – 1.0 (R)
    val preampGain: Float = 0f,     // dB
    val eqEnabled: Boolean = false,
    val eqBands: EqBands = EqBands()
) {
    companion object {
        const val PAN_LEFT = 0f
        const val PAN_CENTER = 0.5f
        const val PAN_RIGHT = 1f

        fun panToString(pan: Float): String = when {
            pan <= 0f          -> "L100"
            pan < 0.25f        -> "L$((100 * (1 - pan * 4)).toInt())"
            pan < 0.5f         -> "L$((50 * (0.5f - pan) * 4).toInt())"
            pan == 0.5f        -> "C"
            pan < 0.75f        -> "R$((50 * (pan - 0.5f) * 4).toInt())"
            pan < 1f           -> "R$((100 * (pan - 0.25f * 4)).toInt())"
            else               -> "R100"
        }

        /**
         * Convert linear fader (0–1) to dB.
         * 1.0 = +10 dB, 0.707 ≈ 0 dB, 0.0 = –∞ dB
         */
        fun faderToDb(linear: Float): Float {
            if (linear <= 0f) return Float.NEGATIVE_INFINITY
            return 20f * kotlin.math.log10(linear) + 10f
        }
    }

    fun faderPercent(): String = "%.1f%%".format(fader * 100)
    fun faderDbString(): String {
        val db = faderDb
        return if (db <= -90f) "–∞ dB" else "%+.1f dB".format(db)
    }
    fun panString(): String = panToString(pan)
}
