package com.bingkil.tuktuk.storage

import android.content.Context

/**
 * Cleans up temporary intermediate files (recordings, exports, debug spikes) created in
 * app-private storage, per PRD Section 22. Never touches the user's original picked media,
 * since that lives outside app-private storage and is only referenced by Uri.
 */
object SessionStorage {

    private val TEMP_PREFIXES = listOf("recording_", "export_", "spike_")

    fun clearIntermediates(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: return
        dir.listFiles()?.forEach { file ->
            if (TEMP_PREFIXES.any { prefix -> file.name.startsWith(prefix) }) {
                file.delete()
            }
        }
    }
}
