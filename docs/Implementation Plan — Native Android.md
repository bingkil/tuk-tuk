# Implementation Plan — Personal Music Video Recorder (Native Android)

Companion to [`PRD — Personal Music Video Recorder — Native Android.md`](./PRD%20—%20Personal%20Music%20Video%20Recorder%20—%20Native%20Android.md). Read Section 36 of that PRD first — this plan is ordered to de-risk the items raised there.

**Test strategy:** simulator is available on this machine but is not used for sync/camera/mic validation — those require a real device. Each phase below ends with "build APK, install on real device via adb, verify" rather than emulator testing.

---

## Phase 0 — Project Scaffolding

1. New Android Studio project: Kotlin, Jetpack Compose, min SDK 29 (Android 10), target latest stable.
2. Add dependencies: CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`, `camera-video`), Media3 (`media3-exoplayer`, `media3-transformer`, `media3-effect`, `media3-common`), Compose BOM.
3. Set up module skeleton per PRD Section 33 (`ui/`, `domain/`, `media/`, `camera/`, `composition/`, `storage/`) as empty packages — do not pre-build abstractions before the code that needs them exists.
4. Configure `adb`-based install task (`./gradlew installDebug` or manual `adb install`).

**Verify:** empty Compose app builds, installs, and launches on the real device via adb.

---

## Phase 1 — Spike: Media3 Dual-Audio Composition (PRD 36.1)

This is the highest-risk unknown in the whole project and must be proven before any UI is built.

1. Hardcode two short local audio files + one local video file (no picker, no UI — a single `Button` triggering a function is enough).
2. Build a `Composition` with: video track (from a dummy recording), microphone-equivalent audio track, and a music audio track, each at a distinct volume.
3. Run through `Transformer`, produce an MP4, and manually verify (by ear) both audio tracks are present, mixed, and not clipping.

**Verify:** exported MP4 plays in a standard Android player with both audio sources audibly mixed at their configured volumes.

**Decision gate:** if Media3 cannot do this cleanly, stop and re-evaluate the architecture (PRD Section 6 assumption) before proceeding to Phase 2.

---

## Phase 2 — Media Selection & Clip Selection

1. `MediaSourceRepository`: wraps Storage Access Framework picker (audio + video MIME filters per PRD Section 3).
2. `MediaInspector`: reads duration, MIME type, track info, audio-track presence via Media3/`MediaExtractor`. Reject video with no usable audio (PRD Section 4).
3. `ClipSelection` domain model + UI: waveform-free scrubber, start position, 15/30/60s duration chips (PRD Section 4).
4. Preview playback of the selected clip window via ExoPlayer.

**Verify:** on real device, select an MP3 and an MP4-with-audio from local storage, preview the trimmed window, confirm video-only MP4 (no audio) is rejected with the PRD Section 27-style message.

---

## Phase 3 — Camera + Recording Sequence

1. `CameraController` (CameraX): preview, front/rear switch, default front camera (PRD Section 9).
2. `RecordingController`: countdown (3-2-1), then start CameraX recording and ExoPlayer music playback per the sequence in PRD Section 11.
3. Capture actual start timestamps via `SystemClock.elapsedRealtimeNanos()` at the confirmed-start callback of each API (not around the call site) — PRD Section 12. Compute and store `syncOffsetMs`.
4. Automatic stop at `clipDurationMs` (PRD Section 14); manual stop/cancel controls.
5. Resolve PRD 36.2 here: decide and implement how `syncOffsetMs` is consumed downstream (trim/pad shorter-started track) — write this decision back into the PRD once implemented.

**Verify:** run the repeatable sync test from PRD Section 31 (countdown + BEEP + clap) on the real device, at least 5 times, and record measured offsets.

---

## Phase 4 — Composition & Export

1. `CompositionBuilder`: assemble recorded video + microphone audio + music audio (trimmed to clip window, offset-corrected) into a `Composition`.
2. `AudioMixProcessor`: apply microphone/music volume levels (PRD Section 17), default mic 100% / music 70%, clipping prevention.
3. `VideoExporter`: run `Transformer`, expose progress to ViewModel, support cancellation (PRD Section 20).
4. Output MP4/H.264/AAC, prefer recorded resolution up to 1080p (PRD Section 19).

**Verify:** export completes on real device without blocking UI; exported file plays correctly in the default Android gallery/player app; cancel mid-export leaves no corrupt output file.

---

## Phase 5 — Preview

Resolve PRD 36.4: implement live dual-source preview (ExoPlayer for music + recorded video/mic playback, volume-adjustable in real time) rather than re-rendering a temp file per slider change, unless Phase 1/4 findings make that impractical.

**Verify:** adjusting mic/music sliders during preview audibly updates the mix without a multi-second re-render delay.

---

## Phase 6 — Save, Share, Lifecycle, Edge Cases

1. `MediaStoreRepository`: save export via modern MediaStore APIs (PRD Section 26).
2. Android Sharesheet integration.
3. Audio focus handling (PRD Section 24), lifecycle interruption handling (PRD Section 23) — recording fails safely rather than producing a silently corrupt file.
4. Temporary session storage cleanup (PRD Section 22).
5. Work through the edge case list in PRD Section 32 on the real device (interruption during recording, permission revoked mid-flow, low storage, etc.).

**Verify:** each MVP acceptance criterion in PRD Section 34 passes end-to-end on the real device, offline (Wi-Fi + mobile data off, PRD Section 28).

---

## Phase 7 — Multi-Device Pass

Repeat Phase 3's sync test and Phase 4/5 export/preview checks on the device tiers listed in PRD Section 30 (mid-range and lower-performance Samsung/Android devices), not just the primary dev device. Record measured sync error and export time per device.

---

## Build & Install Reference

```powershell
# Debug build + install on connected real device
./gradlew installDebug

# Or build APK then install manually
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Confirm `adb devices` shows the real device (not an emulator) as the active/only target before installing.
