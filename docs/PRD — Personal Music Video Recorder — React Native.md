# Product Requirements Document
## Personal Music Video Recorder — React Native

**Version:** 1.0  
**Platform:** Android + iOS  
**Framework:** React Native  
**Primary Goal:** Allow a user to select their own local audio/video file, choose a section of its audio, record a new video while listening to that section, and export a synchronized video containing the selected music and optionally the microphone audio.

---

# 1. Product Overview

The application provides a TikTok-style music-assisted video recording experience without social networking or an online music catalogue.

Users supply their own media from the device.

The core workflow is:

**Select local media → select music segment → record while music plays → preview → mix audio → export video**

The application should work entirely on-device and should not require an account, backend, cloud storage, or internet connection.

---

# 2. Product Goals

The MVP must allow users to:

1. Select an MP3, M4A, AAC, WAV, or supported audio file from the device.
2. Select an MP4 or other supported video file and use its audio track as the music source.
3. Preview the selected audio.
4. Select the starting position of the music.
5. Select a recording duration.
6. Record video using the device camera.
7. Hear the selected music during recording.
8. Record microphone audio simultaneously.
9. Keep the recorded video synchronized with the selected source music.
10. Preview the resulting composition.
11. Adjust music and microphone volume.
12. Export the composition as a standard MP4.
13. Save/share the resulting video using native OS functionality.

---

# 3. Non-Goals

The MVP does NOT include:

- Social feed
- User accounts
- Followers
- Likes
- Comments
- Messaging
- Cloud upload
- Music catalogue
- Commercial music licensing
- Video filters
- Beauty filters
- AR effects
- Stickers
- Text overlays
- Multi-clip timeline editor
- Cloud rendering
- Creator profiles
- Automatic beat detection

These can be considered future features.

---

# 4. Core User Journey

## Step 1 — Home

The user sees:

**Create Video**

Tapping it begins the workflow.

---

# 5. Media Selection

The application opens the native system file/media picker.

Supported source types should include:

### Audio

- MP3
- M4A
- AAC
- WAV

### Video

- MP4
- MOV where supported by the underlying platform

When a video is selected, only its audio track is used as the music source.

The original video track is ignored.

The application must obtain a durable local reference to the selected media for the duration of the editing session.

---

# 6. Music Clip Selection

After selecting media, display a music selection screen.

Display:

- File name
- Total duration
- Current playback position
- Waveform where practical
- Play/pause
- Seek control
- Selected recording window

Example:

```text
My Song.mp3
03:42

0:00 ━━━━━━━[████████████]━━━━━━━━ 3:42
               30 sec

Start: 01:17

[ Play Preview ]

[ Continue ]
```

The user must be able to drag the selected window across the source media.

For example:

**Source duration:** 3:42  
**Clip start:** 1:17  
**Recording duration:** 30 seconds

The application stores:

```text
sourceURI
sourceDuration
clipStart
clipDuration
```

---

# 7. Recording Duration

MVP presets:

- 15 seconds
- 30 seconds
- 60 seconds

Optional:

**Custom duration**

The selected duration cannot exceed the remaining duration of the source audio.

For example:

```text
Song duration: 180 seconds
Start: 150 seconds
Maximum recording duration: 30 seconds
```

---

# 8. Camera Screen

Display:

- Camera preview
- Front/rear camera switch
- Record button
- Selected song name
- Recording duration
- Countdown
- Elapsed recording time

Example:

```text
┌─────────────────────────────┐
│                             │
│                             │
│       CAMERA PREVIEW        │
│                             │
│                             │
│                             │
│ ♫ My Song                   │
│ 01:17                       │
│                             │
│        ● RECORD             │
└─────────────────────────────┘
```

---

# 9. Recording Countdown

Before recording begins, provide an optional countdown.

Default:

**3 seconds**

Possible future settings:

- Off
- 3 seconds
- 5 seconds
- 10 seconds

The selected music must NOT advance during the countdown.

---

# 10. Recording Behaviour

When recording starts, the application must coordinate two operations:

### Camera

Begin:

- video capture
- microphone capture

### Music player

Begin playback from:

```text
clipStart
```

The music playback and recording start times must be measured using a monotonic clock.

Do NOT assume that calling:

```text
startRecording()
playMusic()
```

means the two operations started simultaneously.

The implementation must record actual timing offsets.

Conceptually:

```text
recordingRequestedAt
recordingStartedAt

audioRequestedAt
audioStartedAt

syncOffset =
audioStartedAt - recordingStartedAt
```

This offset must be accounted for during final composition.

---

# 11. Recording Audio Architecture

Three logical media tracks exist.

### Track 1 — Video

Captured camera video.

### Track 2 — Microphone

Audio captured during recording.

### Track 3 — Music

Original audio selected by the user.

The final video must use the **original selected media file** for the music track.

The application must NOT depend on the microphone recording music played through the device speaker.

---

# 12. Headphone Behaviour

Headphones/Bluetooth audio should be supported where possible.

When headphones are connected:

- music plays through headphones
- microphone captures the user's voice/environment
- final export uses the original source audio

This provides the cleanest recording experience.

Speaker playback should also work, although some music may leak into the microphone recording.

---

# 13. Recording Completion

Recording ends when:

- User presses Stop
- Selected duration is reached
- Camera subsystem reports an error
- Recording is interrupted by the OS

Normal recording completion transitions automatically to Preview.

---

# 14. Preview Screen

Display the composed result.

Controls:

- Play
- Pause
- Seek
- Restart
- Music volume
- Microphone/original recording volume

Example:

```text
┌───────────────────────────┐
│                           │
│       VIDEO PREVIEW       │
│                           │
└───────────────────────────┘

Original Sound
────────●────── 70%

Music
──────●──────── 50%

[ Export Video ]
```

Changing volume should update preview playback without requiring a complete final export where technically practical.

---

# 15. Audio Mixing

Default levels:

```text
Microphone: 100%
Music: 70%
```

The user can independently control both.

Conceptually:

```text
finalAudio =
    microphoneTrack * microphoneVolume
    +
    musicTrack * musicVolume
```

The implementation should avoid clipping where possible.

---

# 16. Export

Export:

**MP4 / H.264 + AAC**

Recommended initial output:

```text
Video:
H.264
1080p where source/device allows
30 fps or source frame rate

Audio:
AAC
44.1 or 48 kHz
Stereo where appropriate
```

The output must contain:

```text
Recorded camera video
+
Recorded microphone audio
+
Original selected music segment
```

The selected music must remain synchronized with the performance.

---

# 17. Saving and Sharing

After successful export:

```text
Video ready!

[ Save to Gallery ]

[ Share ]

[ Record Another ]
```

Use the platform's native save/share mechanisms.

No server upload is required.

---

# 18. React Native Architecture

React Native owns:

- Navigation
- Screens
- Application state
- Media-selection UI
- Clip-selection UI
- Recording UI
- Preview controls
- Volume controls
- Export progress UI

Native platform functionality owns:

- Camera recording
- Low-level audio playback where synchronization requires it
- Media metadata
- Media composition
- Audio mixing
- Encoding
- Final export

Do NOT perform video rendering or audio composition in JavaScript.

---

# 19. Recommended Technical Architecture

```text
React Native UI
      │
      ├── Media Picker
      │
      ├── Clip Selector
      │
      ├── Camera Screen
      │
      └── Preview
      │
      ▼
Native Media Bridge
      │
 ┌────┴─────┐
 │          │
Android     iOS
 │          │
Media3      AVFoundation
CameraX     AVCaptureSession
 │          │
 └────┬─────┘
      │
      ▼
Composition / Export
      │
      ▼
final.mp4
```

---

# 20. React Native State Model

Suggested session state:

```typescript
interface RecordingSession {
  sourceUri: string;
  sourceType: "audio" | "video";

  sourceDurationMs: number;

  clipStartMs: number;
  clipDurationMs: number;

  recordedVideoUri?: string;

  recordingStartTimestamp?: number;
  musicStartTimestamp?: number;

  syncOffsetMs?: number;

  microphoneVolume: number;
  musicVolume: number;

  exportedVideoUri?: string;
}
```

Persist only what is necessary.

Temporary working files should be deleted when the session is discarded.

---

# 21. Native Bridge Requirements

Expose a small, purpose-built API rather than exposing the complete native media frameworks.

Conceptually:

```typescript
prepareMusic(uri, startMs)

startRecording()

stopRecording()

previewComposition({
    recordingUri,
    musicUri,
    musicStartMs,
    syncOffsetMs,
    musicVolume,
    microphoneVolume
})

exportComposition(...)

cancelExport()
```

Progress events:

```text
recordingStarted
recordingStopped
exportStarted
exportProgress
exportCompleted
exportFailed
```

---

# 22. Performance Requirements

Recording must:

- maintain smooth camera preview
- avoid noticeable music interruption
- avoid UI blocking
- work without internet connectivity

Export must:

- run asynchronously
- display progress
- prevent accidental duplicate exports
- survive reasonable UI state changes
- clean up temporary files

The application must never load an entire large media file into JavaScript memory.

---

# 23. Synchronization Requirement

Synchronization is a critical product requirement.

Target:

**Music/video synchronization error should ideally remain below approximately 50 ms.**

Lip-sync/performance recording makes larger errors perceptible.

Synchronization testing must be performed on physical devices, not only emulators.

---

# 24. Permissions

Request permissions only when required.

Potential permissions:

- Camera
- Microphone
- Media/photo library where required by OS
- Storage/file access through system picker mechanisms

Prefer modern Android/iOS scoped file access rather than broad filesystem permissions.

---

# 25. Error Handling

Handle:

- Unsupported media
- Media without an audio track
- Corrupt file
- DRM-protected media
- Insufficient storage
- Camera unavailable
- Microphone unavailable
- Audio playback failure
- Recording interruption
- Export failure
- Permission denial
- User deletes/moves source media during session

Display actionable error messages.

---

# 26. Privacy

The MVP requires no backend.

All processing occurs locally.

No selected media or recordings leave the device unless the user explicitly shares them.

---

# 27. MVP Acceptance Criteria

The MVP is complete when a user can:

1. Launch the app.
2. Select their own MP3.
3. Select a 15/30/60-second section.
4. Preview that section.
5. Open the camera.
6. Start recording.
7. Hear the selected music during recording.
8. Record their performance.
9. Stop automatically or manually.
10. Preview the recording synchronized with the original music.
11. Adjust music volume.
12. Adjust microphone volume.
13. Export an MP4.
14. Play the MP4 in another application.
15. Save/share the MP4.
16. Repeat the workflow using the audio from a locally selected MP4.

The complete workflow must operate without a network connection.