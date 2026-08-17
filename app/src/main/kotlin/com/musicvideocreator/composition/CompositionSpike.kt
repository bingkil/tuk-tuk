package com.musicvideocreator.composition

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.musicvideocreator.media.ToneWavGenerator
import java.io.File

/**
 * PRD 36.1 spike: prove Media3 Transformer can mix two independent audio sources
 * (here, two synthetic tones standing in for "music" and "microphone") at distinct
 * volumes into one output file. No video track yet - that's a separate follow-up.
 */
object CompositionSpike {

    private const val TAG = "CompositionSpike"

    fun run(context: Context, onResult: (success: Boolean, message: String) -> Unit) {
        val musicWav = ToneWavGenerator.generate(context, "spike_music.wav", frequencyHz = 440.0, durationSec = 5.0)
        val micWav = ToneWavGenerator.generate(context, "spike_mic.wav", frequencyHz = 880.0, durationSec = 5.0)

        val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(musicWav.toUri()))
            .setEffects(Effects(listOf(gainProcessor(0.3f)), emptyList()))
            .build()

        val micItem = EditedMediaItem.Builder(MediaItem.fromUri(micWav.toUri()))
            .setEffects(Effects(listOf(gainProcessor(1.0f)), emptyList()))
            .build()

        val composition = Composition.Builder(
            EditedMediaItemSequence(musicItem),
            EditedMediaItemSequence(micItem)
        ).build()

        val outputFile = File(context.getExternalFilesDir(null), "spike_output.mp4")
        if (outputFile.exists()) outputFile.delete()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    analyzeAndReport(outputFile, onResult)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    Log.e(TAG, "Export failed", exportException)
                    onResult(false, exportException.message ?: "Unknown export error")
                }
            })
            .build()

        transformer.start(composition, outputFile.absolutePath)
    }

    /** Decodes the exported file and reports 440Hz/880Hz energies, so mixing is verified by measurement, not by ear. */
    private fun analyzeAndReport(outputFile: File, onResult: (success: Boolean, message: String) -> Unit) {
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            val message = try {
                val energies = OutputAudioAnalyzer.analyze(outputFile, listOf(440.0, 880.0))
                val musicEnergy = energies.getValue(440.0)
                val micEnergy = energies.getValue(880.0)
                val verdict = if (musicEnergy > 0.01 && micEnergy > 0.01) {
                    "Both tones present (mixing OK)"
                } else {
                    "MIXING FAILED: only one tone detected"
                }
                "${outputFile.absolutePath}\n440Hz energy=%.4f, 880Hz energy=%.4f\n%s".format(musicEnergy, micEnergy, verdict)
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed", e)
                "${outputFile.absolutePath}\n(analysis failed: ${e.message})"
            }
            mainHandler.post { onResult(true, message) }
        }.start()
    }

    private fun gainProcessor(volume: Float): ChannelMixingAudioProcessor =
        ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(volume))
        }
}
