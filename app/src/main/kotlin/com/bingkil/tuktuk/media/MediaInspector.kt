package com.bingkil.tuktuk.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

/** Reads duration, MIME type, and audio-track presence for a picked file, per PRD Section 5. */
object MediaInspector {

    private const val TAG = "MediaInspector"

    /** Returns null if the file is corrupt/unreadable/undecodable (PRD Section 27). */
    fun inspect(context: Context, uri: Uri): MediaInfo? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)

            var hasAudio = false
            var durationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) hasAudio = true
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                }
            }

            return MediaInfo(
                uri = uri,
                displayName = queryDisplayName(context, uri),
                mimeType = context.contentResolver.getType(uri),
                durationMs = durationUs / 1000,
                hasAudioTrack = hasAudio
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read media", e)
            return null
        } finally {
            extractor.release()
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
        }
        return uri.lastPathSegment ?: "Unknown"
    }
}

