# Tuk Tuk — Handover Document

**Date:** 2026-08-18
**Repo:** https://github.com/bingkil/tuk-tuk.git (branch `main`)
**Local path:** `c:\Users\cprakoso\dev\music-video-creator`

## What this app is

A native Android app (Kotlin + Jetpack Compose) for recording yourself singing/lip-syncing
along to a chosen music clip via the front camera, then exporting a combined video (music +
optionally your own mic audio mixed in). Originally called "Music Video Creator"
(`com.musicvideocreator`), fully rebranded to **"Tuk Tuk"** (`com.bingkil.tuktuk`).

Reference docs (original PRD/plan, written for the old name/package — still useful for
feature intent, ignore the old package name in them):
- `docs/PRD — Personal Music Video Recorder — Native Android.md`
- `docs/Implementation Plan — Native Android.md`
- `docs/tuk-tuk-logo.png` — source logo used for palette + launcher icon

## Environment constraints (important!)

- **No adb / no USB file transfer (MTP)** — the user's org blocks both. The only way to test
  is: build the APK here, user installs it manually (email/drive/etc.), user reports results
  back via chat. **You cannot install or launch the app yourself.**
- **No logcat access.** Any diagnostics must be shown in-app (Compose `Text`), never relied
  on via `Log.d`.
- Build command: `.\gradlew.bat assembleDebug` from the repo root (PowerShell). Takes ~1–5
  min depending on Gradle daemon state. If you run it twice back-to-back you may hit daemon
  contention (a "busy" daemon can't be reused) which just makes it slow, not broken.
- No image-editing tool is available in this environment — if you need to touch icons/images
  again, you're limited to copying/placing existing PNGs, not cropping/resizing them.

## Tech stack

- Kotlin + Jetpack Compose, Material3
- minSdk 29, compileSdk/targetSdk 35, JVM target 17
- CameraX 1.4.0 (camera preview + video recording)
- Media3 1.4.1 (ExoPlayer for playback/preview, Transformer for export/composition)
  - **Gotcha:** this version is old enough that some newer Media3 APIs seen in online docs
    don't exist (e.g. `EditedMediaItemSequence.withAudioFrom/withVideoFrom` factories) — must
    use the deprecated `EditedMediaItemSequence(vararg EditedMediaItem)` constructor instead.
    Always verify against actual compile errors, not just the latest online docs.
- No dependency injection framework, no Room/DataStore — just `SharedPreferences` for a couple
  of flags and plain files in app-private storage.

## Current screen/navigation architecture

`MainActivity.kt` holds a private sealed interface `Screen` and a single `when` block that
swaps composables in-place (no Jetpack Navigation library, just manual state):

```
Screen.Onboarding      -- one-time welcome screen (SharedPreferences "tuktuk_prefs"/"onboarded")
Screen.Home            -- landing screen with bottom bar + "+" FAB
Screen.Record          -- data object, no params; delegates to RecordingFlowScreen
Screen.Recorded(mediaInfo, clip, result)
Screen.Export(mediaInfo, clip, result, includeMic)
```

`Screen.Home` is wrapped in a `Scaffold` with a `BottomAppBar` containing a centered "+"
`FloatingActionButton` (plain `Text("+")`, not an icon library) that navigates to
`Screen.Record`.

`Screen.Record` renders **`ui/RecordingFlowScreen.kt`**, which owns its own private
sub-navigation (`RecordingSubScreen`: `Recording` / `MusicGallery` / `SelectClip(mediaInfo)`)
plus `mediaInfo`/`clip` state local to that flow:

```
RecordingScreen (round record button + "Choose Music" button)
   --onChooseMusic--> MusicGalleryScreen (saved library + "Pick New From Device")
        --onMusicChosen--> ClipSelectionScreen (trim clip, "Choose this music" button)
             --onContinue--> back to RecordingScreen with mediaInfo+clip now set
   --onRecorded--> bubbles up (mediaInfo, clip, result) to MainActivity -> Screen.Recorded
```

This means: **recording is entered first** (via the bottom "+"), and choosing music is a
sub-step reached from inside the recording page, not a prerequisite screen before it. The
record button itself is disabled until both `mediaInfo` and `clip` are non-null.

## Key files (package `com.bingkil.tuktuk`)

```
MainActivity.kt                  -- top-level Screen state machine, Home screen, Recorded screen
ui/
  OnboardingScreen.kt             -- one-time welcome screen
  RecordingFlowScreen.kt          -- NEW: owns the record/choose-music/trim-clip sub-flow
  RecordingScreen.kt              -- camera preview + round record button + countdown + choose-music button
  MusicGalleryScreen.kt           -- NEW: local music library list + "Pick New From Device"
  ClipSelectionScreen.kt          -- duration chips, start slider, live preview, "Choose this music"
  ExportScreen.kt                 -- export progress/complete/failed/cancelled + Save to Gallery + Share
  theme/
    Color.kt                      -- palette extracted from the Tuk Tuk logo
    Theme.kt                      -- TukTukTheme (light/dark Material3 color schemes)
storage/
  MediaSourceRepository.kt        -- SAF OpenDocument MIME filters + persistable permission
  LocalMusicRepository.kt         -- NEW: copies picked audio into app-private "music_library"
  MediaStoreRepository.kt         -- Save to Gallery (MediaStore insert)
  SessionStorage.kt               -- cleans up temp recording_/export_/spike_ files on Home/back
media/
  MediaInfo.kt, MediaInspector.kt -- duration/mime/audio-track probing via MediaExtractor
  AudioFocusManager.kt            -- request/abandon AudioFocusRequest wrapper
camera/
  CameraController.kt             -- CameraX preview + video capture wrapper
composition/
  CompositionBuilder.kt, AudioMixProcessor.kt, VideoExporter.kt, CompositionSpike.kt (+ Goertzel/OutputAudioAnalyzer debug tools)
domain/
  ClipSelection.kt, RecordingResult.kt
```

## What's been done, in order

### 1. Original feature build-out (Phases 0–6 of the implementation plan)
All confirmed working by the user on a real device:
- Media picking (SAF), clip trimming (duration chips + start slider + preview)
- Camera recording synced to music playback, with measured sync offset
- Composition/export mixing mic + music (or music-only "lip-sync" mode via a toggle)
- Save to Gallery + Share
- Audio focus handling, lifecycle-interruption safety (app backgrounded mid-recording),
  corrupt-file handling, export-cancel cleanup, temp file cleanup

Known simplifications carried over from this phase:
- No cross-track audio clipping limiter (relies on default mic 100% / music 70% gain to avoid
  clipping) — flagged to user, not fixed.
- No true PRD Section 32 edge-case test pass (very short/2h+ clips, VBR MP3, multi-track MP4,
  mono audio, Bluetooth output) — these are on-device testing tasks, not code changes.

### 2. Git/GitHub setup
- Repo initialized fresh (`git init`, new `.gitignore`), pushed to
  `https://github.com/bingkil/tuk-tuk.git` on `main`, authenticated as the `bingkil` GitHub
  account via `gh` CLI (`gh auth switch --hostname github.com --user bingkil` +
  `gh auth setup-git`).
- **Gotcha:** don't embed the username in the HTTPS remote URL
  (`https://bingkil@github.com/...`) — GitHub has disabled basic-auth password prompts, so
  that fails. Keep the remote URL plain and let the `gh` credential helper handle auth.

### 3. Rebrand to "Tuk Tuk"
- Full package rename `com.musicvideocreator` → `com.bingkil.tuktuk` (all Kotlin sources,
  `AndroidManifest.xml`, `app/build.gradle.kts` namespace/applicationId/FileProvider
  authority).
- App display name → "Tuk Tuk" (`strings.xml`, `settings.gradle.kts`, `Theme.TukTuk`).
- Color palette extracted from `docs/tuk-tuk-logo.png` → `ui/theme/Color.kt` (teal/coral/
  purple/gold/cream/ink) + `ui/theme/Theme.kt` (`TukTukTheme`, Material3 light/dark schemes:
  primary=teal, secondary=coral, tertiary=purple).
- Launcher icon: **known simplification** — no image-editing tool was available, so the raw
  square logo PNG was copied directly into `res/mipmap-xxxhdpi/ic_launcher.png` and
  `ic_launcher_round.png`, relying on Android's automatic density scaling. **This is NOT a
  true adaptive icon** (no separate foreground/background XML layers). If a proper icon is
  ever needed, this should be regenerated with a real icon tool (e.g. Android Studio's Image
  Asset Studio, or `imagemagick` if available in a different environment).
- `ui/OnboardingScreen.kt`: one-time welcome screen (logo + tagline + "Get Started"), gated by
  `SharedPreferences("tuktuk_prefs", "onboarded")`.
- Typography/button styling pass across all screens (headline titles, themed primary/coral/
  outlined buttons) — done for `MainActivity.kt`, `ClipSelectionScreen.kt`, `ExportScreen.kt`.
  `RecordingScreen.kt` colors are inherited automatically from `TukTukTheme` (Material3
  propagates `colorScheme` to all descendant components without per-file overrides).

### 4. Latest feature round: navigation overhaul + local music library
(This is the most recent, least "battle-tested on device" work — see Testing Needed below.)

- **Bug fix:** the clip-preview time counter didn't move during playback or while dragging the
  slider. Fixed in `ClipSelectionScreen.kt` by polling `player.currentPosition` every 100ms
  into a new `playbackPositionMs` state while `isPreviewing` is true (previously there was a
  single `delay(clipDurationMs)` with no position polling at all).
- **Dark mode by default:** `MainActivity.kt` now calls `TukTukTheme(darkTheme = true)`
  explicitly at the `setContent` call site (the composable's own default,
  `isSystemInDarkTheme()`, was left unchanged so it's still reusable elsewhere).
- **Bottom bar + "+" navigation:** `Screen.Home` is now wrapped in a `Scaffold` +
  `BottomAppBar` with a centered "+" `FloatingActionButton` that navigates to
  `Screen.Record`. The Home screen's old direct "Select Music" button was **removed** — music
  selection now happens inside the recording flow (see below). Debug tools (Composition
  Spike) are still on Home, behind a "Show debug tools" toggle.
- **`RecordingScreen.kt` reworked:**
  - `mediaInfo`/`clip` params are now **nullable** (recording can be entered with no music
    chosen yet).
  - `ExoPlayer` is built empty and populated via
    `LaunchedEffect(mediaInfo?.uri, clip) { ... }` once both become non-null (was previously
    built eagerly assuming non-null values — don't revert this without keeping the null
    guards).
  - The small "Record"/"Stop" `Button` was replaced with a custom circular `RecordButton`
    composable (red filled circle, 84dp, white inner square while recording) — a private
    composable at the bottom of the file.
  - A normal-size "Choose Music" / "Change Music" `Button` was added below the round button,
    visible only when `phase == RecordingPhase.Idle`, calling a new required `onChooseMusic`
    param.
- **New `ui/RecordingFlowScreen.kt`:** owns the record ↔ choose-music ↔ trim-clip sub-flow via
  a private `RecordingSubScreen` sealed interface, so `MainActivity`'s top-level `Screen.Record`
  can be a parameterless `data object`. Bridges back to `MainActivity` via
  `onRecorded(mediaInfo, clip, result)`.
- **New `storage/LocalMusicRepository.kt`:** copies (never moves/deletes) picked audio into
  `context.filesDir/music_library` with a timestamp-prefixed filename for uniqueness;
  `friendlyName(file)` strips the prefix for display. Lives outside
  `getExternalFilesDir()`, so `SessionStorage.clearIntermediates()` never touches it.
- **New `ui/MusicGalleryScreen.kt`:** shows the local library as a list of tappable rows, plus
  a "Pick New From Device" button that reuses the existing SAF `OpenDocument` +
  `MediaInspector` validation flow, then additionally copies the picked file into the library
  before continuing to clip trimming.
- **`ClipSelectionScreen.kt` "Continue" button renamed to "Choose this music"** to match the
  new flow's terminology.
- Verified with `assembleDebug` — `BUILD SUCCESSFUL`. **Not yet confirmed by the user on a
  real device.**

## Testing needed next (nothing below has been confirmed on-device yet)

1. Recording page: round record button stays disabled until music is chosen via "Choose
   Music"; enabling correctly once a clip is picked and trimmed.
2. Music Gallery: picking an existing saved track vs. "Pick New From Device" both correctly
   lead into clip trimming and back into the recording page with the right track attached.
3. A file picked once should reappear in the Music Gallery on a second visit without
   re-picking from the system file browser.
4. Clip preview: the position counter should now visibly count up during playback and while
   dragging the slider (previously reported as stuck).
5. Dark mode should be active by default on first launch.
6. Bottom "+" button should be reachable/tappable and correctly open the recording page.
7. General regression pass on the existing confirmed features (recording, sync offset, export,
   save to gallery, share) since the navigation model changed significantly.

## Known open simplifications / possible follow-ups

- Launcher icon is a placeholder (see above) — revisit if a real adaptive icon is wanted.
- No audio clipping limiter on the mix (mic 100% / music 70% defaults only).
- No PRD Section 32 edge-case device test pass yet.
- Music Gallery UI is intentionally minimal (plain `OutlinedButton` rows) — user said "we need
  to design this page as well, but let's do it later" when this was built, so a proper visual
  design pass on `MusicGalleryScreen.kt` is expected to be requested later.
- No delete/rename functionality in the Music Gallery yet (only add + select).

## 5. Ambient-audio recording mode

Fetching audio directly from YouTube was attempted and investigated exhaustively (multiple
player clients, a real PO-Token provider server, a JS signature/n-challenge solver) but
ultimately abandoned: YouTube's PO-Token/SABR enforcement blocks reliable media downloads from
a lightweight client app, and even a full workaround setup still hit 403s in testing. Spotify's
official SDK was also ruled out — it only allows remote-controlling the Spotify app, never
exposes raw audio bytes (DRM), so it can never be embedded into an exported video.

Instead, landed on a much simpler idea: **let the user play any external app's audio out loud
(Spotify, YouTube, anything) and just pick it up naturally through the phone mic while
recording** — low fidelity by nature, but real and immediate, with zero extra infrastructure or
licensing.

This was previously impossible because the recording flow **hard-required** a chosen music clip
before recording could even start. Changes made, all in the already-nullable
`mediaInfo`/`clip` params flowing through the app:

- **`ui/RecordingScreen.kt`:** the round record button is now enabled as soon as the camera is
  ready, regardless of whether music was picked (`enabled = isCameraReady`, dropped the
  `mediaInfo != null && clip != null` requirement). The main recording `LaunchedEffect` now
  branches on `clip`:
  - **Synced mode** (clip present): unchanged — requests audio focus, plays the local music clip
    through the in-app `ExoPlayer`, measures `syncOffsetMs`, auto-stops after `clip.durationMs`.
  - **Ambient mode** (clip null): **does not** request audio focus or touch the `ExoPlayer` at
    all — audio focus (`AUDIOFOCUS_GAIN`) would pause/duck whatever's playing in another app,
    which is exactly the sound we want to keep capturing. Recording just starts on tap and only
    stops when the user taps the record button again (no auto-stop, since there's no clip
    duration to time against). `syncOffsetMs` is reported as `0` (not meaningful in this mode).
  - Idle-phase hint text now explains the ambient option instead of implying music is mandatory.
- **`ui/RecordingFlowScreen.kt` / `MainActivity.kt`:** `onRecorded`/`Screen.Recorded`/
  `Screen.Export` now carry nullable `MediaInfo?`/`ClipSelection?` end-to-end instead of
  requiring both to be non-null. `RecordedScreen` takes a new `hasMusic: Boolean` param — when
  false (ambient), it hides the "include my voice / music only" mix switch entirely (there's
  only one audio source: the mic recording itself) and shows different copy.
- **`ui/ExportScreen.kt`:** now accepts nullable `mediaInfo`/`clip`. When both are present, the
  export path is completely unchanged (Media3 Transformer composition via
  `CompositionBuilder`). When both are `null` (ambient), it **skips Transformer entirely** —
  `CameraController` already records with `.withAudioEnabled()`, so the raw CameraX output file
  already *is* the final video (camera + mic, which already captured the ambient room audio).
  The "export" is just a file copy of `recording.recordedVideoUri` into the usual
  `export_<timestamp>.mp4` path, done on `Dispatchers.IO`, so it still goes through the same
  Save to Gallery / Share UI as a normal export.
- `CompositionBuilder.kt` is untouched — it's simply not invoked in ambient mode.
- Verified with `assembleDebug` — `BUILD SUCCESSFUL`, and **confirmed working on a real device**
  by the user: recording with no music picked correctly captures external audio (e.g.
  Spotify/YouTube playing nearby) through the mic and produces a usable exported video.

## Useful gotchas for whoever picks this up



- `multi_replace_string_in_file`-style batch edits can fail with a generic "must be array"
  error on edits with heavy Kotlin string-template (`$`) escaping — fall back to sequential
  single-replacement edits in that case.
- `MediaExtractor`/`ExoPlayer` both handle `file://` Uris directly with no `FileProvider`
  indirection needed, as long as they're used in-process (not shared to another app via
  `Intent`). `FileProvider` is only needed for the existing `playFile()`/share-intent paths
  that hand a Uri to another app.
- Android's `ContentResolver.getType(uri)` has built-in `file://` scheme support (guesses MIME
  from extension), but `ContentResolver.query(uri, ...)` does not reliably support `file://`
  scheme — don't rely on `OpenableColumns.DISPLAY_NAME` queries for local files coming from
  `LocalMusicRepository`; use the filename directly instead (see `friendlyName()`).
