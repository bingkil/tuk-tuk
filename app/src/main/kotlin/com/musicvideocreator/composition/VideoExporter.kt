package com.musicvideocreator.composition

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

/** Runs a Composition through Media3 Transformer with progress polling and cancellation (PRD Section 20). */
class VideoExporter(private val context: Context) {

    private var transformer: Transformer? = null
    private var pollHandler: Handler? = null
    private var currentOutputFile: File? = null

    fun export(
        composition: Composition,
        outputFile: File,
        onProgress: (Int) -> Unit,
        onCompleted: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (outputFile.exists()) outputFile.delete()
        currentOutputFile = outputFile

        val t = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    stopProgressPolling()
                    currentOutputFile = null
                    onCompleted()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    stopProgressPolling()
                    outputFile.delete()
                    currentOutputFile = null
                    onError(exportException.message ?: "Export failed")
                }
            })
            .build()
        transformer = t
        t.start(composition, outputFile.absolutePath)
        startProgressPolling(onProgress)
    }

    fun cancel() {
        transformer?.cancel()
        stopProgressPolling()
        currentOutputFile?.delete()
        currentOutputFile = null
    }

    private fun startProgressPolling(onProgress: (Int) -> Unit) {
        val progressHolder = ProgressHolder()
        val handler = Handler(Looper.getMainLooper())
        pollHandler = handler
        val runnable = object : Runnable {
            override fun run() {
                val state = transformer?.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress)
                }
                handler.postDelayed(this, 250)
            }
        }
        handler.post(runnable)
    }

    private fun stopProgressPolling() {
        pollHandler?.removeCallbacksAndMessages(null)
        pollHandler = null
    }
}
