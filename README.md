# Glyph Maps

**Turn-by-turn navigation on the back of your Nothing Phone (4a) Pro.**

Glyph Maps mirrors Google Maps' next turn — the maneuver arrow and the
distance to it — onto the rear Glyph Matrix while you navigate. Glance at the
back of your phone instead of the screen.

<!-- DEMO GIF -->
<!-- Drop a screen/back-of-phone recording here:  ![demo](docs/demo.gif)  -->
<p align="center"><em>(demo GIF — see "Recording a demo" below)</em></p>

---

## What it does

- Shows the **next maneuver** (turn left/right, sharp turns, keep left/right,
  forks, roundabouts, U-turns, "arrived") as a clean dot-matrix arrow.
- Scrolls the **distance to the turn** ("300m", "1.5k") beneath the arrow.
- Reads directions straight from Google Maps' Android 16 Live Updates — no
  Google API key, no account, no extra setup.
- **Only claims the Matrix while you're actually navigating.** Start a route
  and it takes over; end the route and it hands the Matrix straight back to
  whatever Glyph toy you normally run.

## Requirements

- **Nothing Phone (4a) Pro** (the model with the circular Glyph Matrix)
- **Nothing OS 4.x / Android 16** or newer (for Maps Live Updates)
- **Google Maps** with Live Updates (default on recent versions)

## Install

1. Download the latest `app-debug.apk` from
   [Releases](../../releases) (or build from source — see below).
2. Sideload it: copy to the phone and tap it, or `adb install app-debug.apk`.
   (Android will warn about installing from an unknown source — that's normal
   for a sideloaded app.)
3. Open **Glyph Maps**. The first-run screen walks you through two permissions:
   - **Notification access** — so it can read Maps' live directions. *Required.*
   - **Unrestricted battery** — so updates stay instant in the background.
     *Recommended.*
4. Open Google Maps, start any route. Flip your phone over — the Matrix lights
   up with your next turn. That's it.

## Privacy

- The app reads **only Google Maps' navigation notification** (filtered by
  package + the `navigation` category). It ignores everything else, even other
  Maps notifications.
- Nothing leaves your phone. There's no network code, no analytics, no account.
- Notification access is a powerful permission — the source is here so you can
  verify exactly what it does (`service/MapsNotificationListener.kt`).

> Note: a developer capture log (for tuning the parser) can write Maps
> directions to the app's private storage in the **dev** build only. The public
> **user** build writes nothing and logs no notification content (both are
> gated behind `BuildConfig.IS_DEV`, and all logging is stripped from release).

## Using it

- **It runs itself.** No need to keep the app open — once permissions are
  granted, starting Maps navigation triggers the Matrix automatically, even
  with the app closed.
- **Brightness** is tunable (independent head + tail) on the main screen — dial
  it to taste; values persist.
- The Matrix is released the moment navigation ends, so your normal Glyph toy
  comes right back.

---

## How it works

The 4a Pro restricts *Glyph Toys* to always-on-display only, with a 1-minute
update cadence — far too slow for live navigation. Glyph Maps sidesteps that:
it's a normal app that drives the Matrix directly with `setAppMatrixFrame`,
only while a route is active.

```
Google Maps  (Android 16 Live Updates notification, ~every 2-5s)
    │
    ▼
MapsNotificationListener   reads title + distance, maps Google's maneuver
    │                      vocabulary → our 12-maneuver set, derives speed
    ▼
GlyphRenderService         foreground service, alive ONLY during nav;
    │                      releases the Matrix on route end
    ▼
MatrixComposer → GlyphRenderer → setAppMatrixFrame()
    │
    ▼
Glyph Matrix  (137 LEDs, circular 13×13)
```

Speed-aware preview: a far-off turn shows a "continue, then turn" arrow and
flips to the direct turn icon as you approach — matching Google's own ~15s
preview window, scaled to your current speed.

### Project layout

```
app/src/main/kotlin/com/glyphnavtoy/
  MainActivity.kt              UI (main + hidden dev tools)
  glyph/
    Maneuver.kt                12-maneuver set + Google-vocabulary mapping
    ArrowBitmaps.kt            dot-matrix arrow patterns (head/tail chars)
    MatrixFrame.kt             13×13 grid + circular LED mask
    MatrixComposer.kt          NavState → frame (arrow + distance marquee)
    GlyphRenderer.kt           owns GlyphMatrixManager, pushes frames
    GlyphSettings.kt           per-maneuver brightness, persisted
    DigitFont.kt               3×5 pixel font for the distance marquee
  nav/
    NavState.kt, NavStateRepo.kt, Speedometer.kt
  service/
    GlyphRenderService.kt      foreground render loop (nav-only lifecycle)
    MapsNotificationListener.kt  reads Maps, parses, forwards
  capture/CaptureWriter.kt     dev-only on-device logging
```

## Build from source

```
push.cmd             # build + install + relaunch the dev build on a device
build-release.cmd    # build the signed USER release App Bundle (.aab) for Play
```

The dev APK lands in `app/build/outputs/apk/dev/debug/`; the release bundle in
`app/build/outputs/bundle/userRelease/app-user-release.aab`.

**Publishing:** see [`PUBLISHING.md`](PUBLISHING.md) for the full Play Store
checklist (signing, Data Safety, permission declarations, store copy) and
[`PRIVACY_POLICY.md`](PRIVACY_POLICY.md).

### Build environment note

This was developed on a Windows machine that needs two non-default settings
(baked into `build.cmd`):

1. **JDK 21** (Android Studio's bundled JBR), not system JDK 17 — JDK 17 hit
   an "Unable to establish loopback connection" error here (Gradle's AF_UNIX
   worker pipe).
2. **A space-free temp dir** (`C:\GradleTmp`) — the default temp path's
   8.3-truncated form broke AF_UNIX socket creation.

On a normal setup, plain `./gradlew :app:assembleDebug` should just work.

## Roadmap

- [x] Gate the dev capture logging + all notification logging off for release
- [x] Real release build (signed, minified, resource-shrunk AAB)
- [x] Play-ready: privacy policy, Data Safety, permission declarations
- [ ] Production Nothing Glyph key (replace the `test` key) — see PUBLISHING.md
- [ ] Auto-dim (ambient light / time of day)
- [ ] ETA / speed display modes

## Recording a demo

The good shot is the **back of the phone** (the Matrix) during a route —
that needs an external camera. For the in-app UI, screen-record with:

```
adb shell screenrecord /sdcard/demo.mp4      # Ctrl-C to stop
adb pull /sdcard/demo.mp4
```

Convert to GIF with ffmpeg, then drop it at `docs/demo.gif` and uncomment the
image line at the top of this file.

## Credits

Built for the Nothing community. Uses the
[Glyph Matrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).
Not affiliated with Nothing Technology or Google.
