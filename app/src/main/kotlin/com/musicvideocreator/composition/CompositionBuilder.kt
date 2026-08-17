package com.musicvideocreator.composition

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.musicvideocreator.domain.ClipSelection

/**
 * Assembles the final Composition per PRD Section 16: recorded video + its own mic audio,
 * mixed with the selected music clip window, with the later-starting track's lead-in trimmed
 * from the earlier one so both align (PRD 36.2).
 */
object CompositionBuilder {

    fun build(
        recordedVideoUri: Uri,
        musicUri: Uri,
        clip: ClipSelection,
        syncOffsetMs: Long,
        includeMic: Boolean = true,
        micVolume: Float = AudioMixProcessor.DEFAULT_MIC_VOLUME,
        musicVolume: Float = AudioMixProcessor.DEFAULT_MUSIC_VOLUME
    ): Composition {
        // Positive offset: music started after video, so trim video's lead-in. Negative: trim music's lead-in.
        val videoLeadInMs = if (syncOffsetMs > 0) syncOffsetMs else 0L
        val musicLeadInMs = if (syncOffsetMs < 0) -syncOffsetMs else 0L

        val videoItemBuilder = EditedMediaItem.Builder(
            MediaItem.Builder()
                .setUri(recordedVideoUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(videoLeadInMs)
                        .build()
                )
                .build()
        )
        // TikTok lip-sync style: drop the mic entirely so the export uses only the clean music track.
        val videoItem = if (includeMic) {
            videoItemBuilder.setEffects(Effects(listOf(AudioMixProcessor.gainProcessor(micVolume)), emptyList())).build()
        } else {
            videoItemBuilder.setRemoveAudio(true).build()
        }

        val musicItem = EditedMediaItem.Builder(
            MediaItem.Builder()
                .setUri(musicUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs + musicLeadInMs)
                        .setEndPositionMs(clip.startMs + clip.durationMs)
                        .build()
                )
                .build()
        )
            .setRemoveVideo(true) // in case the music source is an MP4, its visual track must not appear (PRD Section 16)
            .setEffects(Effects(listOf(AudioMixProcessor.gainProcessor(musicVolume)), emptyList()))
            .build()

        return Composition.Builder(
            EditedMediaItemSequence(videoItem),
            EditedMediaItemSequence(musicItem)
        ).build()
    }
}
