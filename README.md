<p align="center">
  <img src="docs/tuk-tuk-logo.png" alt="Tuk Tuk logo" width="200" />
</p>

<h1 align="center">Tuk Tuk</h1>

<p align="center">
  Record yourself singing or lip-syncing along to a music clip, then export a combined video —
  right from your phone.
</p>

<p align="center">
  <a href="https://github.com/bingkil/tuk-tuk/releases/latest/download/tuk-tuk-v0.1.0.apk">
    <strong>⬇️ Download the latest APK</strong>
  </a>
  &nbsp;·&nbsp;
  <a href="https://github.com/bingkil/tuk-tuk/releases">All releases</a>
</p>

## What is this?

Tuk Tuk is a native Android app (Kotlin + Jetpack Compose) for making quick lip-sync / cover
videos. Pick a song, trim the part you want, record yourself over it with the front camera, and
export a finished video with your mic mixed in (or music-only) — all on-device.

It also supports an **ambient recording mode**: if you'd rather play music from another app
(Spotify, YouTube, whatever), you can skip picking a clip entirely and just record — the phone
mic naturally picks up whatever's playing nearby along with your voice.

## Features

- 🎵 **Local music library** — pick audio/video files from your device once, reuse them anytime
- ✂️ **Clip trimming** — choose a duration and start point, with live preview
- 🎥 **Synced recording** — camera + mic recording plays your chosen clip back so you can perform
  along to it, with measured sync offset for accurate mixing
- 🎚️ **Mix control** — export with mic + music mixed together, or music-only (silent lip-sync)
- 🎙️ **Ambient recording mode** — no music picked? Just record; the mic captures whatever's
  playing around you
- 💾 **Save to Gallery / Share** — export straight to your device's media gallery or share sheet

## Tech stack

- Kotlin + Jetpack Compose, Material3
- CameraX (camera preview + recording)
- Media3 (ExoPlayer for playback, Transformer for export/composition)
- minSdk 29 / targetSdk 35, JVM 17

No backend, no accounts, no network dependency — everything happens locally on the device.

> ⚠️ **Music & copyright:** Tuk Tuk does not provide, host, or license any music. You are solely
> responsible for ensuring you have the rights or permission to use any music/audio in your
> recordings — whether imported directly or picked up ambiently from another app playing nearby.
> See the [Terms of Service](TERMS_OF_SERVICE.md#3-music--copyright-disclaimer) for details.

## Building

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is produced at `app\build\outputs\apk\debug\app-debug.apk`. Install it manually on
a device to test (no emulator/adb workflow is assumed by this project).

## Installing

Download the APK from the link at the top of this page (or the
[Releases page](https://github.com/bingkil/tuk-tuk/releases)), transfer it to your Android
device, and open it to install. Since it's not distributed via the Play Store, you'll need to
allow "install from unknown sources" for whichever app you use to open the file.

## Project structure

```
app/src/main/kotlin/com/bingkil/tuktuk/
├── MainActivity.kt        # top-level screen navigation
├── ui/                    # Compose screens (recording, music gallery, clip trimming, export)
├── storage/               # local music library, media source picking, save-to-gallery
├── media/                 # media inspection, audio focus handling
├── camera/                # CameraX wrapper
├── composition/           # audio/video mixing and export pipeline
└── domain/                # plain data models (clip selection, recording result)
```

## Documentation

See [`docs/HANDOVER.md`](docs/HANDOVER.md) for a detailed history of design decisions,
architecture notes, and known follow-ups — useful if you're picking up development on this
project.

## Status

Personal/hobby project, actively developed. Not published to the Play Store.

## License

Licensed under the [Apache License, Version 2.0](LICENSE). Copyright © 2026 Bingkil.

See also: [Terms of Service](TERMS_OF_SERVICE.md) · [Privacy Policy](PRIVACY_POLICY.md)
