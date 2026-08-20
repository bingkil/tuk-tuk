package com.bingkil.tuktuk.storage

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Wraps the Storage Access Framework picker per PRD Section 3, no unrestricted filesystem access. */
object MediaSourceRepository {

    val SUPPORTED_MIME_TYPES = arrayOf(
        "audio/mpeg",
        "audio/mp4",
        "audio/x-m4a",
        "audio/aac",
        "audio/wav",
        "audio/x-wav",
        "video/mp4"
    )

    fun takePersistablePermission(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
