# Driving Claude on GlyphMaps

A field guide for getting fast, high-quality output from Claude Code on this
repo. Written for the co-owner workflow: you design and decide, Claude builds,
reviews, and ships.

## The one build command

```
push.cmd          # dev flavor → build, install, force-stop, relaunch (~6s incremental)
build-user.cmd    # user flavor (the clean product build)
```

Never ask for (or hand-write) a raw `gradlew` command. The environment has two
traps that `push.cmd` already handles: the system JDK 17 breaks Gradle's
loopback socket (needs Android Studio's bundled JDK 21), and the default
`%TEMP%` path contains spaces that AF_UNIX socket creation can't handle
(needs `TEMP=C:\GradleTmp`). If a build fails with
`Unable to establish loopback connection`, it's one of those two — not the code.

If install fails: phone unplugged (`adb connect 192.168.1.16:5555`) or a
signing mismatch (`adb uninstall com.glyphnavtoy` first).

**Only grant Notification Access to ONE flavor at a time** — two enabled
listeners fight over the matrix during navigation.

## The glyph design pipeline

Arrow designs are drawn in the builder, not in code:

1. Open `Desktop/nothing-glyph-matrix/glyph-builder.html`.
2. Work the **Maneuver workflow** chips: chip → adjust → `C` to center →
   **Save → next**. Keep the CENTER chip green and the mask warning clear.
   Leave the green distance text OFF the design — the app draws digits live.
3. When all 12 are saved: **Export all (Kotlin)** → **Download** →
   hand Claude the `GlyphFrames.kt` file (or paste the blob).
4. Claude translates the 169-value row-major frames into `ArrowBitmaps.kt`
   patterns and follows through to `ArrowSweep`/`ArrowAnimations` when the
   animation needs to track a changed static design.

When adding a *new* maneuver (not just restyling): `Maneuver.kt` (enum +
`fromMapsString`), `ArrowBitmaps.kt`, `ArrowAnimations.kt`, and the picker
grid — say "new maneuver" and Claude will hit all four.

## Handing off a task

What makes a hand-off land on the first try:

- **Point at things by number**: "review PR #2 then merge", "fix issue #1".
- **State the verb you want**: *review then merge* / *plan first, don't touch
  code* / *just do it*. Claude plans by default on big asks; say "quick fix"
  to skip ceremony.
- **For design changes**: maneuver name + exported frame + one line of intent
  ("head should read brighter at distance").
- **For bugs**: what you saw, what you expected, and whether it was the dev or
  user flavor. A screenshot of the matrix (or the app) beats prose.

## Review & merge flow

- `/code-review` — reviews the current working diff / branch.
- `/review <PR#>` — reviews a GitHub PR.
- `/code-review ultra` — deep multi-agent cloud review. **User-triggered and
  billed**; Claude cannot launch it for you.
- Norm: Claude reviews before merging, branches before committing on `main`,
  and asks before anything irreversible or outward-facing (releases, force
  pushes, closing issues).

## Guardrails Claude follows here

- No merge without reviewing the diff first.
- No touching signing configs, keystores, or release/versioning without asking.
- `NothingKey="test"` in the manifest is **correct** — no key is needed for
  `setAppMatrixFrame` (keys are only for Glyph Toys). Don't "fix" it.
- The manifest deliberately omits the battery-optimization exemption; leave it.

## Where things live

| Thing | Place |
|---|---|
| Build gotchas + project map | `CLAUDE.md` |
| Play Store gates (disclosure, policy URL, testing track) | `PUBLISHING.md` |
| Arrow patterns / animations / mask | `app/src/main/kotlin/com/glyphnavtoy/glyph/` |
| Screens (Home / Settings / Onboarding) | `app/src/main/kotlin/com/glyphnavtoy/ui/` |
| Shared UI primitives + NavHost | `MainActivity.kt` |
| Maps parsing + live snapshot | `service/MapsNotificationListener.kt`, `nav/NavStateRepo.kt` |
| Privacy policy (hosted) | `https://glyphmaps.capad.fyi/privacy` (source: `docs/privacy-policy.html`) |
