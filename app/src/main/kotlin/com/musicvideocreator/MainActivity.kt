package com.musicvideocreator

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.musicvideocreator.composition.CompositionSpike
import com.musicvideocreator.domain.ClipSelection
import com.musicvideocreator.domain.RecordingResult
import com.musicvideocreator.media.MediaInfo
import com.musicvideocreator.media.MediaInspector
import com.musicvideocreator.storage.MediaSourceRepository
import com.musicvideocreator.storage.SessionStorage
import com.musicvideocreator.ui.ClipSelectionScreen
import com.musicvideocreator.ui.ExportScreen
import com.musicvideocreator.ui.RecordingScreen
import java.io.File

private sealed interface Screen {
    data object Home : Screen
    data class SelectClip(val mediaInfo: MediaInfo) : Screen
    data class Record(val mediaInfo: MediaInfo, val clip: ClipSelection) : Screen
    data class Recorded(val mediaInfo: MediaInfo, val clip: ClipSelection, val result: RecordingResult) : Screen
    data class Export(val mediaInfo: MediaInfo, val clip: ClipSelection, val result: RecordingResult, val includeMic: Boolean) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionStorage.clearIntermediates(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                    var pickError by remember { mutableStateOf<String?>(null) }
                    val goHome = {
                        SessionStorage.clearIntermediates(this@MainActivity)
                        screen = Screen.Home
                    }

                    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) {
                            MediaSourceRepository.takePersistablePermission(this@MainActivity, uri)
                            val info = MediaInspector.inspect(this@MainActivity, uri)
                            when {
                                info == null -> pickError = "This audio cannot be used. Please select another file."
                                info.hasAudioTrack -> {
                                    pickError = null
                                    screen = Screen.SelectClip(info)
                                }
                                else -> pickError = "\"${info.displayName}\" has no usable audio. Choose a different file."
                            }
                        }
                    }

                    when (val current = screen) {
                        is Screen.Home -> HomeScreen(
                            pickError = pickError,
                            onSelectMedia = { pickMedia.launch(MediaSourceRepository.SUPPORTED_MIME_TYPES) }
                        )
                        is Screen.SelectClip -> ClipSelectionScreen(
                            mediaInfo = current.mediaInfo,
                            onBack = goHome,
                            onContinue = { clip -> screen = Screen.Record(current.mediaInfo, clip) }
                        )
                        is Screen.Record -> RecordingScreen(
                            mediaInfo = current.mediaInfo,
                            clip = current.clip,
                            onBack = goHome,
                            onRecorded = { result -> screen = Screen.Recorded(current.mediaInfo, current.clip, result) }
                        )
                        is Screen.Recorded -> RecordedScreen(
                            result = current.result,
                            onBack = goHome,
                            onContinue = { includeMic -> screen = Screen.Export(current.mediaInfo, current.clip, current.result, includeMic) }
                        )
                        is Screen.Export -> ExportScreen(
                            mediaInfo = current.mediaInfo,
                            clip = current.clip,
                            recording = current.result,
                            includeMic = current.includeMic,
                            onBack = goHome,
                            onExported = { file -> playFile(this@MainActivity, file) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(pickError: String?, onSelectMedia: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        var status by remember { mutableStateOf("Idle") }
        var outputFile by remember { mutableStateOf<File?>(null) }
        val context = LocalContext.current
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Music Video Creator")
            Button(onClick = onSelectMedia) {
                Text("Select Media")
            }
            pickError?.let { Text(it) }

            Text("Debug tools", style = MaterialTheme.typography.labelLarge)
            Button(onClick = {
                status = "Running composition spike..."
                outputFile = null
                CompositionSpike.run(context) { success, message ->
                    status = if (success) "Success: $message" else "Failed: $message"
                    outputFile = if (success) File(message.substringBefore('\n')) else null
                }
            }) {
                Text("Run Composition Spike")
            }
            Text(status)
            outputFile?.let { file ->
                Button(onClick = { playFile(context, file) }) {
                    Text("Play Result")
                }
            }
        }
    }
}

@Composable
private fun RecordedScreen(result: RecordingResult, onBack: () -> Unit, onContinue: (includeMic: Boolean) -> Unit) {
    val context = LocalContext.current
    var includeMic by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Recording complete")
        Text("File: ${result.recordedVideoUri}")
        Text("Measured sync offset: ${result.syncOffsetMs} ms")
        Button(onClick = { playUri(context, result.recordedVideoUri) }) {
            Text("Play Result")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = includeMic, onCheckedChange = { includeMic = it })
            Text(if (includeMic) "Include my voice (mixed with music)" else "Music only (lip-sync style)")
        }
        Button(onClick = { onContinue(includeMic) }) { Text("Create Video") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

private fun playFile(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "com.musicvideocreator.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Play with"))
}

// CameraX recordings are saved as file:// Uris, which need the FileProvider indirection to be shareable.
private fun playUri(context: android.content.Context, uri: android.net.Uri) {
    if (uri.scheme == "file") {
        uri.path?.let { playFile(context, File(it)) }
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Play with"))
}
