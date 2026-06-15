# GlyphNavToy — agent notes

Android app that renders Google Maps turn-by-turn maneuvers on the Nothing
Phone 4a Pro Glyph Matrix (13×13 LEDs, circular mask, 137 lit pixels).

## Building + pushing on this machine — JUST USE `push.cmd`

The build environment has **two non-obvious gotchas**. `push.cmd` already
handles both. Don't write a custom command unless you have a reason.

```
push.cmd
```

It builds (`:app:assembleDevDebug`), installs via adb, force-stops the running
service, and relaunches `MainActivity` — so the matrix shows your new code
immediately. Incremental builds take ~6s.

### Two flavors

- **dev** (`push.cmd`) — `com.glyphnavtoy.dev`, "Glyph Maps Dev", dark-red
  icon. All tools: DEV screen, route simulator, on-disk capture logging.
- **user** (`build-user.cmd`) — `com.glyphnavtoy`, "Glyph Maps", black icon.
  Clean product: no DEV button, no capture-to-disk.

`BuildConfig.IS_DEV` gates the difference. Both install side by side. Grant
notification access to only ONE at a time — two enabled listeners will fight
over the matrix during nav.

### The two gotchas (don't fight them, just set them)

1. **JDK 21, not the system JDK 17.** System Corretto 17 hits
   `UnixDomainSockets.connect0` "Invalid argument" trying to bind Gradle's
   loopback Pipe. Use Android Studio's bundled JBR (JDK 21):
   `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`

2. **Temp dir cannot have spaces / 8.3 short names.** Default `%TEMP%` is
   `C:\Users\Aadarsh Upadhyay\...` which AF_UNIX socket creation in Gradle
   workers can't handle (it tries `C:\Users\AADARS~1\...`). Point `TEMP`,
   `TMP`, and `java.io.tmpdir` at a clean path:
   `TEMP=C:\GradleTmp  TMP=C:\GradleTmp  GRADLE_OPTS=-Djava.io.tmpdir=C:\GradleTmp`

### Common failure modes

| Symptom | Cause | Fix |
|---|---|---|
| `Unable to establish loopback connection` | Wrong JDK OR temp path with spaces | `push.cmd` sets both |
| `adb: no devices/emulators found` | Phone disconnected | Plug in USB, or `adb connect 192.168.1.16:5555` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Signing key mismatch | `adb uninstall com.glyphnavtoy` first |
| Builds, but matrix shows old design | Service has cached the old code | `push.cmd` does `force-stop` + `am start` — don't skip those |

## Project layout (the parts you'll touch)

- `app/src/main/kotlin/com/glyphnavtoy/glyph/`
  - `Maneuver.kt` — enum of maneuvers + `fromMapsString` (the Google Maps
    vocabulary parser). When adding a new maneuver, add it here, then in
    `ArrowBitmaps`, `ArrowAnimations`, and `MainActivity`'s picker grid.
  - `ArrowBitmaps.kt` — static 6×13 string patterns. `X`=255 head,
    `o`=178 lit tail, `.`=off. Patterns are stamped at `ARROW_ORIGIN_Y=1`.
  - `ArrowAnimations.kt` — sparse `(x, y) → brightness` per frame, in
    matrix-space coords (y already includes the `ARROW_ORIGIN_Y` offset).
  - `MatrixFrame.kt` — 13×13 grid + circular mask widths
    `[5, 7, 11, 13, 13, 13, 13, 13, 13, 13, 11, 7, 5]`. Pattern columns
    outside the mask are silently dropped.
- `app/src/main/kotlin/com/glyphnavtoy/MainActivity.kt` — picker grid + the
  `when (maneuver)` glyph-char map used in the picker.
