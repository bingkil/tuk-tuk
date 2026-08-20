package com.bingkil.tuktuk.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFocusRequest
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.bingkil.tuktuk.camera.CameraController
import com.bingkil.tuktuk.domain.ClipSelection
import com.bingkil.tuktuk.domain.RecordingResult
import com.bingkil.tuktuk.media.AudioFocusManager
import com.bingkil.tuktuk.media.MediaInfo
import kotlinx.coroutines.delay
import java.io.File

private enum class RecordingPhase { Idle, Countdown, Recording, Finished, Interrupted }

@Composable
fun RecordingScreen(
    mediaInfo: MediaInfo?,
    clip: ClipSelection?,
    onBack: () -> Unit,
    onChooseMusic: () -> Unit,
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
    val player = remember { ExoPlayer.Builder(context).build() }
    LaunchedEffect(mediaInfo?.uri, clip) {
        val info = mediaInfo
        val selectedClip = clip
        if (info != null && selectedClip != null) {
            player.setMediaItem(MediaItem.fromUri(info.uri))
            player.prepare()
            player.seekTo(selectedClip.startMs)
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
            val safeClip = clip
            var recordingStartNs = 0L
            var musicStartNs = 0L
            val outputFile = File(context.getExternalFilesDir(null), "recording_${System.currentTimeMillis()}.mp4")
            val executor = ContextCompat.getMainExecutor(context)

            // Ambient mode (no music picked): don't touch ExoPlayer or audio focus at all, so
            // whatever's already playing nearby (e.g. Spotify/YouTube in split-screen) keeps
            // playing undisturbed and gets picked up naturally by the mic.
            val playerListener = if (safeClip != null) {
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying && musicStartNs == 0L) {
                            musicStartNs = SystemClock.elapsedRealtimeNanos()
                        }
                    }
                }.also { player.addListener(it) }
            } else null

            if (safeClip != null) {
                val focusRequest = AudioFocusManager.request(context) {
                    abortRecording("Recording stopped: audio focus was lost (e.g. a call or another app).")
                }
                if (focusRequest == null) {
                    abortRecording("Could not start recording: audio focus unavailable.")
                    return@LaunchedEffect
                }
                audioFocusRequest = focusRequest
            }

            cameraController.startRecording(outputFile, executor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        recordingStartNs = SystemClock.elapsedRealtimeNanos()
                        if (safeClip != null) player.play()
                    }
                    is VideoRecordEvent.Finalize -> {
                        playerListener?.let { player.removeListener(it) }
                        AudioFocusManager.abandon(context, audioFocusRequest)
                        audioFocusRequest = null
                        val syncOffsetMs = if (safeClip != null) (musicStartNs - recordingStartNs) / 1_000_000 else 0L
                        statusText = if (safeClip != null) "Recorded. Sync offset: ${syncOffsetMs}ms" else "Recorded."
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

            if (safeClip != null) {
                // Synced mode: auto-stop once the chosen clip duration elapses.
                delay(safeClip.durationMs)
                cameraController.stopRecording()
                player.pause()
            }
            // Ambient mode: no auto-stop — recording continues until the user taps stop.
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

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        mediaInfo?.let { "♫ ${it.displayName}" }
                            ?: "No music selected — tap Choose Music, or just hit record to capture whatever's playing nearby (e.g. Spotify/YouTube)",
                        color = Color.White
                    )

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when (phase) {
                            RecordingPhase.Idle -> RecordButton(
                                recording = false,
                                enabled = isCameraReady,
                                onClick = { phase = RecordingPhase.Countdown }
                            )

                        RecordingPhase.Countdown -> Text(
                            countdownValue.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White
                        )

                        RecordingPhase.Recording -> RecordButton(
                            recording = true,
                            enabled = true,
                            onClick = {
                                cameraController.stopRecording()
                                player.pause()
                            }
                        )

                        RecordingPhase.Finished -> Text(statusText, color = Color.White)

                        RecordingPhase.Interrupted -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(statusText, color = Color.White)
                            Button(onClick = onBack) { Text("Back") }
                        }
                    }
                }

                if (phase == RecordingPhase.Idle) {
                    Button(onClick = onChooseMusic) {
                        Text(if (mediaInfo == null) "Choose Music" else "Change Music")
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

@Composable
private fun RecordButton(recording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(if (enabled) Color(0xFFE0304A) else Color.Gray)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (recording) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
            )
        }
    }
}
