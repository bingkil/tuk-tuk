package com.musicvideocreator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.media.AudioFocusRequest
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.musicvideocreator.domain.ClipSelection
import com.musicvideocreator.media.AudioFocusManager
import com.musicvideocreator.media.MediaInfo
import kotlinx.coroutines.delay

private val DURATION_OPTIONS_MS = listOf(15_000L, 30_000L, 60_000L)

@Composable
fun ClipSelectionScreen(
    mediaInfo: MediaInfo,
    onBack: () -> Unit,
    onContinue: (ClipSelection) -> Unit
) {
    val context = LocalContext.current

    var clipDurationMs by remember {
        mutableStateOf(DURATION_OPTIONS_MS.filter { it <= mediaInfo.durationMs }.lastOrNull() ?: mediaInfo.durationMs)
    }
    val maxStartMs = (mediaInfo.durationMs - clipDurationMs).coerceAtLeast(0)
    var startMs by remember { mutableStateOf(0L) }
    var isPreviewing by remember { mutableStateOf(false) }
    var audioFocusRequest by remember { mutableStateOf<AudioFocusRequest?>(null) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaInfo.uri))
            prepare()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            player.release()
            AudioFocusManager.abandon(context, audioFocusRequest)
        }
    }

    LaunchedEffect(isPreviewing) {
        if (isPreviewing) {
            val focusRequest = AudioFocusManager.request(context) {
                player.pause()
                isPreviewing = false
            }
            if (focusRequest == null) {
                isPreviewing = false
                return@LaunchedEffect
            }
            audioFocusRequest = focusRequest
            player.seekTo(startMs)
            player.play()
            delay(clipDurationMs)
            player.pause()
            AudioFocusManager.abandon(context, audioFocusRequest)
            audioFocusRequest = null
            isPreviewing = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(mediaInfo.displayName, style = MaterialTheme.typography.titleMedium)
        Text("Duration: ${formatMs(mediaInfo.durationMs)}")

        Text("Clip length")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DURATION_OPTIONS_MS.filter { it <= mediaInfo.durationMs }.forEach { option ->
                FilterChip(
                    selected = clipDurationMs == option,
                    onClick = {
                        clipDurationMs = option
                        startMs = startMs.coerceAtMost((mediaInfo.durationMs - option).coerceAtLeast(0))
                    },
                    label = { Text("${option / 1000}s") }
                )
            }
        }

        Text("Start: ${formatMs(startMs)}")
        Slider(
            value = startMs.toFloat(),
            onValueChange = { newValue ->
                startMs = newValue.toLong()
                if (isPreviewing) player.seekTo(startMs) // let dragging scrub live playback, not just the next preview
            },
            valueRange = 0f..maxStartMs.toFloat().coerceAtLeast(0f),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { isPreviewing = true }, enabled = !isPreviewing) {
                Text(if (isPreviewing) "Playing…" else "▶ Preview")
            }
            if (isPreviewing) {
                Button(onClick = {
                    player.pause()
                    AudioFocusManager.abandon(context, audioFocusRequest)
                    audioFocusRequest = null
                    isPreviewing = false
                }) { Text("■ Stop") }
            }
            Button(onClick = { onContinue(ClipSelection(startMs, clipDurationMs)) }) {
                Text("Continue")
            }
        }

        TextButton(onClick = onBack) { Text("Choose different file") }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
