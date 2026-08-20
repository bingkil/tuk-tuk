package com.bingkil.tuktuk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bingkil.tuktuk.domain.ClipSelection
import com.bingkil.tuktuk.domain.RecordingResult
import com.bingkil.tuktuk.media.MediaInfo

private sealed interface RecordingSubScreen {
    data object Recording : RecordingSubScreen
    data object MusicGallery : RecordingSubScreen
    data class SelectClip(val mediaInfo: MediaInfo) : RecordingSubScreen
}

/**
 * Owns the "+" tab's sub-flow: record page <-> choose music <-> trim clip, so choosing music
 * is reached from the recording page itself instead of being a prerequisite screen.
 */
@Composable
fun RecordingFlowScreen(
    onBack: () -> Unit,
    onRecorded: (mediaInfo: MediaInfo?, clip: ClipSelection?, result: RecordingResult) -> Unit
) {
    var mediaInfo by remember { mutableStateOf<MediaInfo?>(null) }
    var clip by remember { mutableStateOf<ClipSelection?>(null) }
    var subScreen by remember { mutableStateOf<RecordingSubScreen>(RecordingSubScreen.Recording) }

    when (val current = subScreen) {
        is RecordingSubScreen.Recording -> RecordingScreen(
            mediaInfo = mediaInfo,
            clip = clip,
            onBack = onBack,
            onChooseMusic = { subScreen = RecordingSubScreen.MusicGallery },
            onRecorded = { result -> onRecorded(mediaInfo, clip, result) }
        )

        is RecordingSubScreen.MusicGallery -> MusicGalleryScreen(
            onBack = { subScreen = RecordingSubScreen.Recording },
            onMusicChosen = { info -> subScreen = RecordingSubScreen.SelectClip(info) }
        )

        is RecordingSubScreen.SelectClip -> ClipSelectionScreen(
            mediaInfo = current.mediaInfo,
            onBack = { subScreen = RecordingSubScreen.MusicGallery },
            onContinue = { selectedClip ->
                mediaInfo = current.mediaInfo
                clip = selectedClip
                subScreen = RecordingSubScreen.Recording
            }
        )
    }
}
