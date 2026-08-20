package com.bingkil.tuktuk.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.bingkil.tuktuk.composition.CompositionBuilder
import com.bingkil.tuktuk.composition.VideoExporter
import com.bingkil.tuktuk.domain.ClipSelection
import com.bingkil.tuktuk.domain.RecordingResult
import com.bingkil.tuktuk.media.MediaInfo
import com.bingkil.tuktuk.storage.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class ExportPhase { Exporting, Completed, Failed, Cancelled }
private enum class SaveState { Idle, Saving, Saved, Failed }

@Composable
fun ExportScreen(
    mediaInfo: MediaInfo?,
    clip: ClipSelection?,
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
        if (mediaInfo != null && clip != null) {
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
        } else {
            // Ambient mode: the recording's own mic track already contains the room audio
            // (music + voice), so there's nothing to mix — just use the raw recording as-is.
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val recordedPath = recording.recordedVideoUri.path
                            ?: throw IllegalStateException("Recorded file could not be located.")
                        File(recordedPath).copyTo(outputFile, overwrite = true)
                    }
                    progress = 100
                    phase = ExportPhase.Completed
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Export failed"
                    phase = ExportPhase.Failed
                }
            }
        }
        onDispose { exporter.cancel() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (phase) {
            ExportPhase.Exporting -> {
                Text("Creating your video…", style = MaterialTheme.typography.headlineSmall)
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("$progress%")
                Text("Please keep the app open.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = {
                    phase = ExportPhase.Cancelled
                    exporter.cancel()
                }) { Text("Cancel") }
            }

            ExportPhase.Completed -> {
                Text("✓ Video Ready", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = { onExported(outputFile) }, modifier = Modifier.fillMaxWidth()) { Text("Play") }
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
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        when (saveState) {
                            SaveState.Idle -> "Save to Gallery"
                            SaveState.Saving -> "Saving..."
                            SaveState.Saved -> "Saved to Gallery ✓"
                            SaveState.Failed -> "Save failed, retry"
                        },
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                savedUri?.let { uri ->
                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) { Text("Share", modifier = Modifier.padding(vertical = 6.dp)) }
                }
                TextButton(onClick = onBack) { Text("Back") }
            }

            ExportPhase.Failed -> {
                Text("Export failed", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                Text(errorMessage ?: "", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onBack) { Text("Back") }
            }

            ExportPhase.Cancelled -> {
                Text("Export cancelled", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
    }
}
