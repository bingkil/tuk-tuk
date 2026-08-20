package com.bingkil.tuktuk

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bingkil.tuktuk.domain.ClipSelection
import com.bingkil.tuktuk.domain.RecordingResult
import com.bingkil.tuktuk.media.MediaInfo
import com.bingkil.tuktuk.storage.SessionStorage
import com.bingkil.tuktuk.ui.ExportScreen
import com.bingkil.tuktuk.ui.OnboardingScreen
import com.bingkil.tuktuk.ui.RecordingFlowScreen
import com.bingkil.tuktuk.ui.theme.TukTukTheme
import java.io.File

private sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data object Record : Screen
    data class Recorded(val mediaInfo: MediaInfo?, val clip: ClipSelection?, val result: RecordingResult) : Screen
    data class Export(val mediaInfo: MediaInfo?, val clip: ClipSelection?, val result: RecordingResult, val includeMic: Boolean) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionStorage.clearIntermediates(this)
        val prefs = getSharedPreferences("tuktuk_prefs", MODE_PRIVATE)
        setContent {
            TukTukTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by remember {
                        mutableStateOf<Screen>(
                            if (prefs.getBoolean("onboarded", false)) Screen.Home else Screen.Onboarding
                        )
                    }
                    val goHome = {
                        SessionStorage.clearIntermediates(this@MainActivity)
                        screen = Screen.Home
                    }

                    when (val current = screen) {
                        is Screen.Onboarding -> OnboardingScreen(
                            onGetStarted = {
                                prefs.edit().putBoolean("onboarded", true).apply()
                                screen = Screen.Home
                            }
                        )
                        is Screen.Home -> Scaffold(
                            bottomBar = {
                                BottomAppBar {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                        FloatingActionButton(
                                            onClick = { screen = Screen.Record },
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
                                        }
                                    }
                                }
                            }
                        ) { padding ->
                            HomeScreen(modifier = Modifier.padding(padding))
                        }
                        is Screen.Record -> RecordingFlowScreen(
                            onBack = goHome,
                            onRecorded = { mediaInfo, clip, result -> screen = Screen.Recorded(mediaInfo, clip, result) }
                        )
                        is Screen.Recorded -> RecordedScreen(
                            result = current.result,
                            hasMusic = current.mediaInfo != null,
                            onBack = goHome,
                            onContinue = { includeMic -> screen = Screen.Export(current.mediaInfo, current.clip, current.result, includeMic) }
                        )
                        is Screen.Export -> ExportScreen(
                            mediaInfo = current.mediaInfo,
                            clip = current.clip,
                            recording = current.result,
                            includeMic = current.includeMic,
                            onBack = goHome,
                            onExported = { file -> playFile(this@MainActivity, file) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val context = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.tuk_tuk_logo),
                contentDescription = "Tuk Tuk logo",
                modifier = Modifier.fillMaxWidth(0.5f)
            )
            Text("Tap + to record a new video", style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { openUrl(context, TERMS_OF_SERVICE_URL) }) {
                    Text("Terms of Service", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { openUrl(context, PRIVACY_POLICY_URL) }) {
                    Text("Privacy Policy", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                "© 2026 Bingkil",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val TERMS_OF_SERVICE_URL = "https://github.com/bingkil/tuk-tuk/blob/main/TERMS_OF_SERVICE.md"
private const val PRIVACY_POLICY_URL = "https://github.com/bingkil/tuk-tuk/blob/main/PRIVACY_POLICY.md"

private fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable
private fun RecordedScreen(result: RecordingResult, hasMusic: Boolean, onBack: () -> Unit, onContinue: (includeMic: Boolean) -> Unit) {
    val context = LocalContext.current
    var includeMic by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Recording complete", style = MaterialTheme.typography.headlineSmall)
        if (hasMusic) {
            Text("Measured sync offset: ${result.syncOffsetMs} ms", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Ambient recording — your mic captured the room audio directly.", style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = { playUri(context, result.recordedVideoUri) }, modifier = Modifier.fillMaxWidth()) {
            Text("Play Result")
        }
        if (hasMusic) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = includeMic, onCheckedChange = { includeMic = it })
                Text(if (includeMic) "Include my voice (mixed with music)" else "Music only (lip-sync style)")
            }
        }
        Button(
            onClick = { onContinue(includeMic) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Create Video", modifier = Modifier.padding(vertical = 6.dp))
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

private fun playFile(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "com.bingkil.tuktuk.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Play with"))
}

// CameraX recordings are saved as file:// Uris, which need the FileProvider indirection to be shareable.
private fun playUri(context: android.content.Context, uri: android.net.Uri) {
    if (uri.scheme == "file") {
        uri.path?.let { playFile(context, File(it)) }
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Play with"))
}
