package com.bingkil.tuktuk.domain

import android.net.Uri

/** Output of the camera+mic recording phase, plus the measured audio/video start offset (PRD Section 8/12). */
data class RecordingResult(
    val recordedVideoUri: Uri,
    val recordingStartNs: Long,
    val musicStartNs: Long,
    val syncOffsetMs: Long
)
