package com.bingkil.tuktuk.storage

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Keeps a private copy of picked music in app-internal storage so the user can reuse it later
 * without going through the system file picker again.
 */
object LocalMusicRepository {

    private const val LIBRARY_DIR = "music_library"

    private fun libraryDir(context: Context): File =
        File(context.filesDir, LIBRARY_DIR).apply { mkdirs() }

    fun listLibrary(context: Context): List<File> =
        libraryDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** Copies [sourceUri] into app-private storage, returning a file:// Uri for the copy, or null on failure. */
    fun copyIntoLibrary(context: Context, sourceUri: Uri, displayName: String): Uri? {
        return try {
            val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val destFile = File(libraryDir(context), "${System.currentTimeMillis()}_$safeName")
            val input = context.contentResolver.openInputStream(sourceUri) ?: return null
            input.use { source -> destFile.outputStream().use { dest -> source.copyTo(dest) } }
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            null
        }
    }

    /** Strips the uniqueness timestamp prefix added by [copyIntoLibrary] for display purposes. */
    fun friendlyName(file: File): String = file.name.substringAfter('_', file.name)
}
