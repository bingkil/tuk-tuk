package com.musicvideocreator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.musicvideocreator.composition.CompositionBuilder
import com.musicvideocreator.composition.VideoExporter
import com.musicvideocreator.domain.ClipSelection
import com.musicvideocreator.domain.RecordingResult
import com.musicvideocreator.media.MediaInfo
import com.musicvideocreator.storage.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class ExportPhase { Exporting, Completed, Failed, Cancelled }
private enum class SaveState { Idle, Saving, Saved, Failed }

@Composable
fun ExportScreen(
    mediaInfo: MediaInfo,
    clip: ClipSelection,
    recording: RecordingResult,
    includeMic: Boolean,
    onBack: () -> Unit,
    onExported: (File) -> Unit
) {
    val context = LocalContext.current
    val exporter = remember { VideoExporter(context) }
    val coroutineScope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(ExportPhase.Exporting) }
    var progress by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf("") }
    var saveState by remember { mutableStateOf(SaveState.Idle) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    val outputFile = remember { File(context.getExternalFilesDir(null), "export_${System.currentTimeMillis()}.mp4") }

    DisposableEffect(Unit) {
        val composition = CompositionBuilder.build(
            recordedVideoUri = recording.recordedVideoUri,
            musicUri = mediaInfo.uri,
            clip = clip,
            syncOffsetMs = recording.syncOffsetMs,
            includeMic = includeMic
        )
        exporter.export(
            composition = composition,
            outputFile = outputFile,
            onProgress = { progress = it },
            onCompleted = { phase = ExportPhase.Completed },
            onError = { message ->
                errorMessage = message
                phase = ExportPhase.Failed
            }
        )
        onDispose { exporter.cancel() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (phase) {
            ExportPhase.Exporting -> {
                Text("Creating video...")
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("$progress%")
                Text("Please keep the app open.")
                TextButton(onClick = {
                    phase = ExportPhase.Cancelled
                    exporter.cancel()
                }) { Text("Cancel") }
            }

            ExportPhase.Completed -> {
                Text("✓ Video Ready")
                Button(onClick = { onExported(outputFile) }) { Text("Play") }
                Button(
                    enabled = saveState != SaveState.Saving,
                    onClick = {
                        saveState = SaveState.Saving
                        coroutineScope.launch {
                            val uri = withContext(Dispatchers.IO) {
                                MediaStoreRepository.saveVideoToGallery(context, outputFile)
                            }
                            savedUri = uri
                            saveState = if (uri != null) SaveState.Saved else SaveState.Failed
                        }
                    }
                ) {
                    Text(
                        when (saveState) {
                            SaveState.Idle -> "Save to Gallery"
                            SaveState.Saving -> "Saving..."
                            SaveState.Saved -> "Saved to Gallery ✓"
                            SaveState.Failed -> "Save failed, retry"
                        }
                    )
                }
                savedUri?.let { uri ->
                    Button(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                    }) { Text("Share") }
                }
                TextButton(onClick = onBack) { Text("Back") }
            }

            ExportPhase.Failed -> {
                Text("Export failed: $errorMessage")
                TextButton(onClick = onBack) { Text("Back") }
            }

            ExportPhase.Cancelled -> {
                Text("Export cancelled")
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
    }
}
