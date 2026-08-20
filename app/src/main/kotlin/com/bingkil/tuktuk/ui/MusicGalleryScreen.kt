package com.bingkil.tuktuk.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bingkil.tuktuk.media.MediaInfo
import com.bingkil.tuktuk.media.MediaInspector
import com.bingkil.tuktuk.storage.LocalMusicRepository
import com.bingkil.tuktuk.storage.MediaSourceRepository

/** Local gallery of previously used music, plus the option to pick a new file from the device (PRD Section 3 picker). */
@Composable
fun MusicGalleryScreen(
    onBack: () -> Unit,
    onMusicChosen: (MediaInfo) -> Unit
) {
    val context = LocalContext.current
    var libraryFiles by remember { mutableStateOf(LocalMusicRepository.listLibrary(context)) }
    var pickError by remember { mutableStateOf<String?>(null) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            MediaSourceRepository.takePersistablePermission(context, uri)
            val info = MediaInspector.inspect(context, uri)
            when {
                info == null -> pickError = "This audio cannot be used. Please select another file."
                !info.hasAudioTrack -> pickError = "\"${info.displayName}\" has no usable audio. Choose a different file."
                else -> {
                    pickError = null
                    val localUri = LocalMusicRepository.copyIntoLibrary(context, uri, info.displayName)
                    if (localUri == null) {
                        pickError = "Could not save this file. Please try again."
                    } else {
                        libraryFiles = LocalMusicRepository.listLibrary(context)
                        onMusicChosen(info.copy(uri = localUri))
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Choose Music", style = MaterialTheme.typography.headlineSmall)
        Button(
            onClick = { pickMedia.launch(MediaSourceRepository.SUPPORTED_MIME_TYPES) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Pick New From Device", modifier = Modifier.padding(vertical = 6.dp))
        }
        pickError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (libraryFiles.isEmpty()) {
            Text("No saved music yet. Pick a file to add it here.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Previously used", style = MaterialTheme.typography.labelLarge)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(libraryFiles) { file ->
                    OutlinedButton(
                        onClick = {
                            val info = MediaInspector.inspect(context, Uri.fromFile(file))
                            if (info != null) {
                                onMusicChosen(info.copy(displayName = LocalMusicRepository.friendlyName(file)))
                            } else {
                                pickError = "This saved file can no longer be read."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(LocalMusicRepository.friendlyName(file), modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }

        TextButton(onClick = onBack) { Text("Back") }
    }
}
