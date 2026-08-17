package com.musicvideocreator.composition

import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix

/** Volume levels for the final mix (PRD Section 17). */
object AudioMixProcessor {
    const val DEFAULT_MIC_VOLUME = 1.0f
    const val DEFAULT_MUSIC_VOLUME = 0.7f

    /** Scales all channels of a track by [volume], supporting both mono and stereo sources. */
    fun gainProcessor(volume: Float): ChannelMixingAudioProcessor =
        ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(volume))
            putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(volume))
        }
}
