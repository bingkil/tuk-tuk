package com.musicvideocreator.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Saves an exported MP4 into the shared Movies collection via MediaStore (PRD Section 26). */
object MediaStoreRepository {

    fun saveVideoToGallery(context: Context, sourceFile: File): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MusicVideoCreator")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val itemUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        resolver.openOutputStream(itemUri)?.use { out ->
            sourceFile.inputStream().use { it.copyTo(out) }
        } ?: run {
            resolver.delete(itemUri, null, null)
            return null
        }

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)
        return itemUri
    }
}
