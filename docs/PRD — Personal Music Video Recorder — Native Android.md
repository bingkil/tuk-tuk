# Product Requirements Document
## Personal Music Video Recorder — Native Android

**Version:** 1.0  
**Platform:** Android  
**Language:** Kotlin  
**UI:** Jetpack Compose  
**Primary Media Stack:** CameraX + AndroidX Media3

---

# 1. Product Overview

Build a native Android application that allows users to select music from their own device, select a portion of that music, record themselves while the music plays, mix the original music with the recorded camera/microphone audio, and export the result as an MP4.

The product provides the core music-recording experience associated with short-form video applications without providing social networking or a commercial music catalogue.

The application must be capable of operating entirely offline.

---

# 2. Primary Workflow

```text
Launch
   ↓
Select Media
   ↓
Audio / Video file
   ↓
Extract/read audio
   ↓
Choose clip
   ↓
Camera
   ↓
Countdown
   ↓
Record + Play Music
   ↓
Stop
   ↓
Preview
   ↓
Adjust audio
   ↓
Export
   ↓
Save / Share
```

---

# 3. Supported Input

Users must be able to select local media through Android's system picker / Storage Access Framework.

Priority formats:

### Audio

- MP3
- M4A
- AAC
- WAV

### Video

- MP4

Additional formats can be supported automatically where Android's installed codecs permit.

The application must not require unrestricted filesystem access.

---

# 4. MP4 as Music Source

If the user selects:

```text
holiday-video.mp4
```

the media contains:

```text
holiday-video.mp4
 ├── Video track
 └── Audio track
```

For the purpose of music selection:

```text
Video track → ignored
Audio track → backing music
```

An intermediate MP3 does NOT need to be generated.

Media3 should consume the relevant audio stream directly where supported.

---

# 5. Clip Selection

After source selection, retrieve:

- Duration
- MIME type
- Track information
- Audio availability

Reject video files that contain no usable audio.

Display:

```text
Song.mp3

00:00 ━━━━━━━━━●━━━━━━━━━━ 03:52

        [████████████]
            30 sec

Start 01:22

[▶ Preview]

[Continue]
```

Recording durations:

- 15 sec
- 30 sec
- 60 sec

The user selects the start position.

Store values in milliseconds.

---

# 6. Suggested Android Stack

Use:

### UI

**Kotlin + Jetpack Compose**

### Camera

**CameraX**

### Music Playback

**AndroidX Media3 / ExoPlayer**

### Media Inspection

**Media3 and Android media APIs as appropriate**

### Composition / Export

**Media3 Transformer**

### Audio Mixing

**Media3 composition/audio processing capabilities**

### File Selection

**Android system picker / Storage Access Framework**

### Sharing

**Android Sharesheet**

The architecture should avoid FFmpeg unless Media3 proves unable to satisfy a specific requirement.

---

# 7. Architecture

Recommended high-level structure:

```text
Compose UI
    │
    ▼
ViewModel
    │
    ▼
RecordingSession
    │
 ┌──┼───────────────────┐
 │  │                   │
 ▼  ▼                   ▼
CameraX  ExoPlayer   Media Repository
 │          │
 └─────┬────┘
       │
       ▼
Recording Result
       +
Selected Music
       │
       ▼
Media3 Composition
       │
       ▼
Transformer
       │
       ▼
final.mp4
```

---

# 8. Recording Session Model

Example domain model:

```kotlin
data class RecordingSession(
    val sourceUri: Uri,

    val sourceDurationMs: Long,

    val clipStartMs: Long,

    val clipDurationMs: Long,

    val recordedVideoUri: Uri?,

    val recordingStartNs: Long?,
    val musicStartNs: Long?,

    val syncOffsetMs: Long?,

    val microphoneVolume: Float,
    val musicVolume: Float,

    val exportedUri: Uri?
)
```

Timing-sensitive values should use Android's monotonic time source rather than wall-clock time.

---

# 9. Camera

Use CameraX for:

- Preview
- Video capture
- Microphone recording
- Front camera
- Rear camera

Default:

**Front camera**

User can switch before recording.

Camera switching during an active recording is outside MVP scope.

---

# 10. Music Playback

Prepare the selected music before recording.

Seek to:

```text
clipStartMs
```

The player should be ready before the recording countdown completes.

This minimizes startup latency.

Do not initialize or buffer the selected music after the Record button has already been pressed.

---

# 11. Recording Sequence

Recommended conceptual sequence:

```text
Prepare Camera
Prepare ExoPlayer
Seek music to clipStart
Pause player

       ↓

Countdown

3
2
1

       ↓

Start CameraX recording
Capture actual recording-start timestamp

       ↓

Start ExoPlayer
Capture actual playback-start timestamp

       ↓

Record synchronization offset

       ↓

Continue until clipDuration reached
```

Do not rely on both APIs starting at precisely the same instant.

---

# 12. Timing

Use a monotonic time source such as:

```text
SystemClock.elapsedRealtimeNanos()
```

rather than:

```text
System.currentTimeMillis()
```

for synchronization measurement.

Conceptually:

```text
videoStart = elapsedRealtimeNanos()

musicStart = elapsedRealtimeNanos()

offset =
    musicStart - videoStart
```

The actual implementation should capture timestamps as close as possible to confirmed media start events rather than simply surrounding API calls.

---

# 13. Recording Screen

Compose screen:

```text
┌─────────────────────────────┐
│ ↻                           │
│                             │
│                             │
│       CAMERA PREVIEW        │
│                             │
│                             │
│                             │
│ ♫ Song.mp3                  │
│                             │
│           00:12             │
│                             │
│            ■                │
└─────────────────────────────┘
```

Controls:

- Camera switch
- Record
- Stop
- Cancel

---

# 14. Automatic Stop

If:

```text
clipDuration = 30 seconds
```

recording must stop automatically after approximately 30 seconds.

Music playback must also stop.

Do not allow the music source to continue beyond the selected clip.

---

# 15. Recorded Media

CameraX produces a recording containing:

```text
Video
+
Microphone audio
```

Call this:

```text
recording.mp4
```

The original selected music remains separate.

Example:

```text
recording.mp4
 ├── Camera video
 └── Microphone

song.mp3
 └── Original music
```

---

# 16. Final Composition

Create:

```text
recording.mp4
        │
        ├── video ───────────────┐
        │                        │
        └── microphone ──────┐   │
                             │   │
song.mp3                     │   │
        │                    │   │
        └── selected audio ──┼───┤
                             │   │
                             ▼   ▼
                         Composition
                             │
                             ▼
                         Transformer
                             │
                             ▼
                          final.mp4
```

For MP4 source music:

```text
source.mp4
   └── audio track
          ↓
       Composition
```

The source video's visual track must not appear in the exported video.

---

# 17. Audio Mix

Expose:

```text
Microphone
────────●──── 100%

Music
──────●────── 70%
```

Range:

```text
0% – 100%
```

Possible future enhancement:

```text
0% – 200%
```

MVP should prioritize predictable mixing and clipping prevention.

---

# 18. Preview

The user must be able to preview the intended result before final export.

Preview must approximate:

```text
Camera video
+
Microphone
+
Music
```

with synchronization matching the eventual exported file.

Changes to volume should be previewable.

---

# 19. Export

Use Media3 Transformer/Composition where supported.

Output target:

```text
Container:
MP4

Video:
H.264

Audio:
AAC

Resolution:
Prefer recorded resolution
Maximum MVP target: 1080p

Frame rate:
Preserve appropriate recording frame rate
```

Do not unnecessarily transcode video if the pipeline can safely avoid it, but correctness and compatibility take precedence over optimization in V1.

---

# 20. Export UI

Display:

```text
Creating video...

██████████████░░░░ 72%

Please keep the app open.
```

Where Media3 provides useful progress information, expose it to the ViewModel and UI.

The user should be able to cancel an export.

---

# 21. Output

Successful export screen:

```text
✓ Video Ready

[ Play ]

[ Save to Gallery ]

[ Share ]

[ Create Another ]
```

Save using modern Android media APIs.

Use Android Sharesheet for sharing.

---

# 22. Temporary Storage

Use application cache/storage for intermediate files.

Potential files:

```text
/cache/session-{uuid}/
    recording.mp4
    preview.*
    export.tmp
```

Delete temporary session data when:

- User discards recording
- Export completes and intermediates are unnecessary
- Session expires
- User explicitly starts over

Never delete the user's original selected media.

---

# 23. Lifecycle Handling

Handle Android lifecycle changes carefully.

Important cases:

- Screen locks
- App backgrounds
- Incoming phone call
- Audio focus loss
- Camera becomes unavailable
- Bluetooth device disconnects
- Process receives memory pressure

Active recording should fail safely rather than producing an apparently successful corrupted video.

---

# 24. Audio Focus

The application must manage Android audio focus.

During recording:

- selected music is controlled by the application
- external music applications should not compete for playback
- interruptions must be handled

The application must detect significant playback interruptions that invalidate synchronization.

---

# 25. Bluetooth

Bluetooth audio introduces additional latency.

The application should support Bluetooth where practical but must not assume Bluetooth playback latency equals speaker/wired-headphone latency.

Bluetooth synchronization should therefore be explicitly tested.

It may be documented as best-effort in MVP if device-specific latency prevents deterministic monitoring synchronization.

The exported music track itself should remain correctly aligned because it comes from the original media source rather than captured speaker output.

---

# 26. Permissions

Potential permissions:

```text
CAMERA
RECORD_AUDIO
```

Media access should preferentially use Android's system picker / Storage Access Framework rather than requesting broad storage access.

Saving to the media library should use appropriate modern MediaStore APIs.

---

# 27. DRM

DRM-protected media is outside MVP scope.

If Android cannot decode/read the selected media, display:

**This audio cannot be used. Please select another file.**

The application must not attempt to circumvent DRM.

---

# 28. Offline Requirement

After installation, the complete workflow must function with:

```text
Wi-Fi OFF
Mobile Data OFF
```

Including:

- Media selection
- Playback
- Recording
- Preview
- Composition
- Export
- Saving

Sharing may naturally require connectivity depending on the destination application.

---

# 29. Performance Targets

### Camera

Smooth preview appropriate to device capability.

### Recording

No significant dropped frames attributable to application logic.

### Playback

Music must already be buffered/prepared before recording begins.

### Synchronization

Target:

**≤50 ms perceived synchronization error where hardware permits.**

### Export

Export must not block the Compose UI thread.

---

# 30. Minimum Device Strategy

Initial target recommendation:

**Android 10+**

A later engineering spike should determine whether supporting older Android versions materially increases complexity.

Test at minimum:

- Pixel/reference Android device
- Samsung mid-range
- Samsung flagship
- Lower-performance Android device

Camera/audio behaviour varies significantly between manufacturers.

---

# 31. Testing Strategy

Synchronization requires physical-device testing.

Create a repeatable sync test.

For example, prepare an audio track containing:

```text
3
2
1
BEEP
```

Record a visual action exactly on BEEP:

```text
BEEP → clap
```

Inspect exported video frame/audio timing.

Measure:

```text
expected event timestamp
vs.
video event timestamp
vs.
music event timestamp
```

Run repeatedly.

This provides objective synchronization measurements instead of relying only on subjective testing.

---

# 32. Edge Cases

Test:

- Very short audio
- 2+ hour MP3
- Variable-bitrate MP3
- MP4 containing multiple audio tracks
- MP4 with no audio
- Corrupt MP3
- Unusual sample rates
- Mono audio
- Bluetooth headphones
- Wired headphones
- Device speaker
- Front camera
- Rear camera
- User stops after 1 second
- User backgrounds app
- Incoming call
- Low disk space
- Permission revoked
- Export cancelled

---

# 33. Suggested Module Structure

```text
app/

ui/
    home/
    media/
    clip/
    camera/
    preview/
    export/

domain/
    RecordingSession
    ClipSelection
    AudioMix

media/
    MediaSourceRepository
    MusicPlayer
    MediaInspector

camera/
    CameraController
    RecordingController

composition/
    CompositionBuilder
    AudioMixProcessor
    VideoExporter

storage/
    SessionStorage
    MediaStoreRepository
```

Keep CameraX, Media3 and storage implementation details outside Compose screens.

---

# 34. MVP Acceptance Criteria

The Android MVP is complete when a user can successfully:

1. Select a local MP3.
2. Preview it.
3. Select a start position.
4. Select 15, 30 or 60 seconds.
5. Open the front/rear camera.
6. Start a countdown.
7. Hear the selected music.
8. Record themselves while the music plays.
9. Capture microphone audio.
10. Stop manually or automatically.
11. Preview the result.
12. Change microphone volume.
13. Change music volume.
14. Export a synchronized MP4.
15. Play the exported MP4 in Android's normal media applications.
16. Save the MP4 to the media library.
17. Share it through Android Sharesheet.
18. Repeat the complete workflow using the audio track from a user-selected MP4.
19. Complete the entire workflow offline.

---

# 35. MVP Success Definition

The primary success criterion is not the number of editing features.

It is:

> **A user selects a song, chooses a section, presses Record, performs while hearing that music, and receives an exported video in which their performance remains convincingly synchronized with the clean original music.**

If synchronization, recording reliability, playback latency, or exported audio quality is poor, the MVP is not considered complete even if all UI functionality exists.

---

# 36. Engineering Review Notes

Reviewed 2026-08-17. Overall this PRD is implementable as written; the following risks and gaps should be resolved before/during implementation rather than discovered mid-build.

### 36.1 Unvalidated assumption: Media3 dual-audio mixing

Section 6 assumes Media3 Transformer/Composition can mix two independent audio tracks (microphone + music) at independent volumes into one output. This capability level varies by Media3 version and is the single riskiest assumption in the architecture — the whole "avoid FFmpeg" decision depends on it holding true.

**Action:** Spike this first, before any UI work. Prove: two audio tracks + one video track → correctly volume-mixed MP4 via Transformer. If it doesn't hold up cleanly, the architecture needs to be revisited before further investment.

### 36.2 Sync offset is measured but never consumed

Section 11/12 describe capturing `syncOffsetMs` between recording start and music start, but Section 16's composition diagram never shows what uses that offset. Decide explicitly: does composition trim/pad the earlier-started track by `syncOffsetMs`? This must be specified before implementing `CompositionBuilder`.

### 36.3 Export time vs. device tier

Section 19 prefers "recorded resolution" while Section 30 spans flagship to low-performance devices — recorded resolution/frame rate (and therefore Transformer re-encode cost) could vary widely. Section 29 has no numeric export-time target. Recommend setting one (even a rough one) per device tier so "must not block UI thread" has a concrete performance bar to test against.

### 36.4 Preview mixing implementation is unspecified

Section 18 doesn't say whether preview mixing is real-time dual playback (ExoPlayer + overlay) or a rendered temp-file composite. Real-time is far cheaper for slider interactions (Section 17); re-compositing a temp file per volume change would be too slow for a usable preview. Clarify before building the preview screen.

### 36.5 React Native alternative — decision

A parallel React Native PRD was evaluated for the same product. Recommendation: **proceed with native Android** (as this PRD specifies). All sync-critical work (CameraX, Media3 composition, timestamp capture) must be native regardless of framework choice, so React Native would only add a UI layer plus a JS/Native bridge hop at exactly the point where timing precision matters most — with no cross-platform payoff until iOS is an actual near-term target. Revisit if/when iOS support is required.