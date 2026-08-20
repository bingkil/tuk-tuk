package com.bingkil.tuktuk.media

import android.net.Uri

data class MediaInfo(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val durationMs: Long,
    val hasAudioTrack: Boolean
)
