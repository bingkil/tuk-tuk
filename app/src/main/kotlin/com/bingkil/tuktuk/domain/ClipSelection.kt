package com.bingkil.tuktuk.domain

/** The user's chosen playback window within a source track, in milliseconds. */
data class ClipSelection(
    val startMs: Long,
    val durationMs: Long
)
