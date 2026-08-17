package com.musicvideocreator.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFocusRequest
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.musicvideocreator.camera.CameraController
import com.musicvideocreator.domain.ClipSelection
import com.musicvideocreator.domain.RecordingResult
import com.musicvideocreator.media.AudioFocusManager
import com.musicvideocreator.media.MediaInfo
import kotlinx.coroutines.delay
import java.io.File

private enum class RecordingPhase { Idle, Countdown, Recording, Finished, Interrupted }

@Composable
fun RecordingScreen(
    mediaInfo: MediaInfo,
    clip: ClipSelection,
    onBack: () -> Unit,
    onRecorded: (RecordingResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val requestPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        hasPermissions = results.values.all { it }
    }
    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            requestPermissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    if (!hasPermissions) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Camera and microphone permissions are required to record.")
            TextButton(onClick = onBack) { Text("Back") }
        }
        return
    }

    var useFrontCamera by remember { mutableStateOf(true) }
    var phase by remember { mutableStateOf(RecordingPhase.Idle) }
    var countdownValue by remember { mutableStateOf(3) }
    var statusText by remember { mutableStateOf("") }
    var isCameraReady by remember { mutableStateOf(false) }
    var audioFocusRequest by remember { mutableStateOf<AudioFocusRequest?>(null) }

    val cameraController = remember { CameraController(context) }
    val previewView = remember { PreviewView(context) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaInfo.uri))
            prepare()
            seekTo(clip.startMs)
        }
    }

    fun abortRecording(reason: String) {
        if (phase == RecordingPhase.Recording) {
            cameraController.stopRecording()
        }
        player.pause()
        AudioFocusManager.abandon(context, audioFocusRequest)
        audioFocusRequest = null
        statusText = reason
        phase = RecordingPhase.Interrupted
    }

    DisposableEffect(useFrontCamera) {
        isCameraReady = false
        cameraController.bind(lifecycleOwner, previewView, useFrontCamera) { isCameraReady = true }
        onDispose { cameraController.unbind() }
    }
    DisposableEffect(Unit) {
        onDispose {
            player.release()
            AudioFocusManager.abandon(context, audioFocusRequest)
        }
    }

    // Fail safe rather than silently producing a corrupted recording if the app leaves the foreground mid-take (PRD Section 23).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP &&
                (phase == RecordingPhase.Countdown || phase == RecordingPhase.Recording)
            ) {
                abortRecording("Recording stopped: app was backgrounded.")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(phase) {
        if (phase == RecordingPhase.Countdown) {
            countdownValue = 3
            while (countdownValue > 0) {
                delay(1000)
                countdownValue--
            }
            phase = RecordingPhase.Recording
        }
    }

    LaunchedEffect(phase) {
        if (phase == RecordingPhase.Recording) {
            val focusRequest = AudioFocusManager.request(context) {
                abortRecording("Recording stopped: audio focus was lost (e.g. a call or another app).")
            }
            if (focusRequest == null) {
                abortRecording("Could not start recording: audio focus unavailable.")
                return@LaunchedEffect
            }
            audioFocusRequest = focusRequest

            var recordingStartNs = 0L
            var musicStartNs = 0L
            val outputFile = File(context.getExternalFilesDir(null), "recording_${System.currentTimeMillis()}.mp4")
            val executor = ContextCompat.getMainExecutor(context)

            val playerListener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying && musicStartNs == 0L) {
                        musicStartNs = SystemClock.elapsedRealtimeNanos()
                    }
                }
            }
            player.addListener(playerListener)

            cameraController.startRecording(outputFile, executor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        recordingStartNs = SystemClock.elapsedRealtimeNanos()
                        player.play()
                    }
                    is VideoRecordEvent.Finalize -> {
                        player.removeListener(playerListener)
                        AudioFocusManager.abandon(context, audioFocusRequest)
                        audioFocusRequest = null
                        val syncOffsetMs = (musicStartNs - recordingStartNs) / 1_000_000
                        statusText = "Recorded. Sync offset: ${syncOffsetMs}ms"
                        phase = RecordingPhase.Finished
                        onRecorded(
                            RecordingResult(
                                recordedVideoUri = event.outputResults.outputUri,
                                recordingStartNs = recordingStartNs,
                                musicStartNs = musicStartNs,
                                syncOffsetMs = syncOffsetMs
                            )
                        )
                    }
                    else -> Unit
                }
            }

            delay(clip.durationMs)
            cameraController.stopRecording()
            player.pause()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { useFrontCamera = !useFrontCamera }, enabled = phase == RecordingPhase.Idle) {
                Text("↻ Switch Camera")
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("♫ ${mediaInfo.displayName}", color = Color.White)

                when (phase) {
                    RecordingPhase.Idle -> Button(
                        onClick = { phase = RecordingPhase.Countdown },
                        enabled = isCameraReady
                    ) { Text("Record") }

                    RecordingPhase.Countdown -> Text(
                        countdownValue.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White
                    )

                    RecordingPhase.Recording -> Button(onClick = {
                        cameraController.stopRecording()
                        player.pause()
                    }) { Text("■ Stop") }

                    RecordingPhase.Finished -> Text(statusText, color = Color.White)

                    RecordingPhase.Interrupted -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(statusText, color = Color.White)
                        Button(onClick = onBack) { Text("Back") }
                    }
                }

                if (phase != RecordingPhase.Finished && phase != RecordingPhase.Interrupted) {
                    TextButton(onClick = {
                        if (phase == RecordingPhase.Recording) {
                            cameraController.stopRecording()
                            player.pause()
                            AudioFocusManager.abandon(context, audioFocusRequest)
                            audioFocusRequest = null
                        }
                        onBack()
                    }) { Text("Cancel") }
                }
            }
        }
    }
}
