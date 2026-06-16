# Publishing Glyph Maps to Google Play

Everything the code can't do for you, in one place. The app code/config is
release-ready (signed AAB, minified, logging gated, Play-friendly permissions);
the items below are Play Console paperwork + two real blockers (Nothing key,
hosted privacy policy).

---

## 0. Build the release bundle

```
build-release.cmd          # builds the signed USER App Bundle
```
Output: `app/build/outputs/bundle/userRelease/app-user-release.aab`

It is signed with the **upload key** in `upload-keystore.jks` (credentials in
the gitignored `keystore.properties`). **Back up both files** — losing them
means resetting your Play upload key.
- Keystore password / key password: see `keystore.properties`.
- Alias: `upload`.

> Enroll in **Play App Signing** when you create the app — Google then holds the
> real app-signing key and `upload-keystore.jks` is only your upload key
> (recoverable if lost).

---

## 1. Real blockers (do these or the app won't be accepted)

1. **Host the privacy policy.** Put `PRIVACY_POLICY.md` at a public HTTPS URL
   (GitHub Pages, a gist, your site) and fill in `[YOUR_EMAIL]`. Required for the
   store listing AND the in-app disclosure (notification-access apps must have one).
2. **Closed testing, if this is a personal Play account created after 13 Nov 2023.**
   Google requires **≥12 testers opted in for 14 continuous days** on a closed
   track *before* you can apply for production. Org accounts (registered legal
   entity) are exempt. Budget 2+ weeks. (See the testing track in §7.)
3. **In-app prominent disclosure before requesting Notification Access.** Play's
   Notification Listener policy wants an explicit in-app screen stating the app
   reads Google Maps navigation notifications, which the user accepts *before*
   you send them to the system settings. The current Setup card routes straight
   to settings — add a short consent screen first. (Code change, small.)

### Not a blocker: the `NothingKey`

`AndroidManifest.xml` ships `NothingKey = "test"`. **This is fine — no key is
required to drive the Matrix.** The Glyph Matrix SDK only needs an API key for
publishing **Glyph Toys** (carousel widgets); this app drives the Matrix
directly via `setAppMatrixFrame`, which needs no key. `"test"` is exactly what
Nothing's own example project ships, and the Matrix lights up with it. Leave it
as-is. (`setAppMatrixFrame` does require Nothing OS system version `20250801`+,
which Android-16-era 4a Pro phones have.)

### Heads-up: target API & account

- **Target API:** currently `targetSdk 35` (Android 15) — valid for new
  submissions until **31 Aug 2026**, after which Google requires **API 36**
  (Android 16). Bump `compileSdk`/`targetSdk` to 36 before then (the 4a Pro runs
  Android 16, so it's a safe move now).
- **Play Console account:** $25 one-time fee + **identity verification** (D-U-N-S
  for an org, ID for personal) and 2-step verification are now mandatory.

---

## 2. Store listing copy (ready to paste)

- **Title (≤30):** `Glyph Maps – Turn Arrows`
- **Short description (≤80):** `Mirror Google Maps turn-by-turn directions onto your Nothing Glyph Matrix.`
- **Full description:**

```
Glance at the back of your phone instead of the screen.

Glyph Maps mirrors your next Google Maps turn — the maneuver arrow and the
distance to it — onto the rear Glyph Matrix of the Nothing Phone (4a) Pro while
you navigate. Flip your phone face-down on the mount and your next turn lights
up across 137 LEDs in clean, minimal NothingOS style.

WHAT IT DOES
• Shows the next maneuver as a dot-matrix arrow: turn left/right, sharp turns,
  keep left/right, forks, roundabouts, U-turns, and "arrived".
• Shows the distance to the turn beneath the arrow.
• Claims the Matrix ONLY while you're navigating, then hands it straight back
  to your usual Glyph toy when the route ends.
• Speed-aware preview: a far-off turn shows a "continue, then turn" hint and
  flips to the direct icon as you approach.

MAKE IT YOURS
• Adjustable LED brightness — independent head and tail.
• Static glow or sweeping-flow animation.
• Built-in maneuver picker and a route simulator to preview every arrow.

PRIVATE BY DESIGN
Everything is processed on your device. There is no network code, no
analytics, no ads, and no account. The app reads only the Google Maps
navigation notification (via Notification Access) and ignores everything else.
Nothing ever leaves your phone.

REQUIREMENTS — PLEASE READ
• Nothing Phone (4a) Pro with the circular Glyph Matrix.
• Google Maps installed, with its turn-by-turn navigation actively running —
  Glyph Maps mirrors Maps; it is not a standalone navigation app and does not
  provide routing.
• Grant Notification Access on first launch (the app guides you).

Glyph Maps is an independent app. It is not affiliated with, endorsed by, or
sponsored by Google or Nothing Technology. "Google Maps" and "Nothing" are
trademarks of their respective owners.
```

- **Category:** Maps & Navigation (alt: Tools)
- **Tags:** navigation, nothing phone, glyph
- **Price:** Free

---

## 3. Graphical assets needed

| Asset | Spec | Suggested shot |
|---|---|---|
| App icon | 512×512 PNG, ≤1 MB | Black tile, warm-glow arrow on a stylized circular matrix |
| Feature graphic | 1024×500 PNG/JPG, no alpha | Phone face-down showing the lit Matrix arrow + tagline |
| Phone screenshots (2–8) | PNG/JPG, 1080p+ | (1) hero dial "TURN IN 300m"; (2) maneuver picker grid; (3) brightness head/tail sliders; (4) static-vs-sweep toggle; (5) route simulator running; (6) real back-of-phone photo of the lit Matrix mid-route |
| (Optional) promo video | YouTube URL | 15–30s back-of-phone clip during a live route |

Tablet/Wear/TV screenshots not required (phone-only).

---

## 4. Data Safety form

- **Does your app collect or share any required user data types?** → **No.**
  Notification content is accessed and processed **ephemerally on-device only**,
  never transmitted, so it is not "collected" under Play's definition.
- Personal info / messages / location / app activity → **not collected.**
- Data encrypted in transit → N/A (no transmission).
- Data deletion → N/A.

(Accurate because the shipped `user` build does no on-disk capture and no
content logging — both are gated behind `IS_DEV`.)

---

## 5. Sensitive-permission declarations

**Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`)** — submit:

> Glyph Maps's only function is to read Google Maps turn-by-turn navigation
> notifications and render the maneuver (turn direction + distance) on the
> Nothing Phone Glyph Matrix LED display. Notification access is the core and
> only feature. No Android API other than NotificationListenerService exposes
> live Maps maneuver data. We read only `com.google.android.apps.maps`
> notifications with `category=navigation`; all other notifications and apps are
> ignored. All parsing is 100% on-device. No notification content is
> transmitted, sold, or shared with any third party.

You must also show a **prominent in-app disclosure** before requesting
Notification Access (an on-screen statement that the app reads Google Maps
navigation notifications to render arrows, which the user affirmatively
accepts) and link the privacy policy.

**Foreground service (`FOREGROUND_SERVICE_SPECIAL_USE`)** — submit:

> This foreground service maintains the active connection to the Nothing Glyph
> Matrix hardware (via the Nothing Glyph Matrix SDK) and pushes maneuver frames
> to the LED display while the user is navigating. No standard foreground-
> service type fits: it is not location, media, camera, mic, data-sync, or
> connected-device — it drives an on-device proprietary LED matrix in response
> to navigation events. The service runs only during active navigation and
> self-terminates after navigation ends (20s idle watchdog).

---

## 6. Content rating (IARC)

Answer **No** to every content category (no violence, sexual content,
profanity, controlled substances, gambling, user-generated content, location
sharing, personal-info sharing). Result: **Everyone / PEGI 3.**

---

## 7. Pre-launch checklist (human-only)

1. [ ] Host **`PRIVACY_POLICY.md`** at an HTTPS URL; fill in `[YOUR_EMAIL]`.
2. [ ] Add the **in-app prominent disclosure** before the Notification-Access ask (§1.3).
3. [ ] Run `build-release.cmd`; confirm `app-user-release.aab` is produced and signed.
4. [ ] Back up `upload-keystore.jks` + `keystore.properties` somewhere safe.
5. [ ] Create the Play Console account: pay $25, complete **identity verification**
       + 2-step verification; create the app and enable **Play App Signing**.
6. [ ] Paste store listing (Section 2); upload icon, feature graphic, screenshots (Section 3).
7. [ ] Complete **Data Safety** (Section 4) and **IARC** rating (Section 6).
8. [ ] Submit **Notification access** + **special-use FGS** declarations (Section 5).
9. [ ] Upload the AAB to a **closed testing** track; install on a real 4a Pro;
       grant Notification Access to only the one flavor; run a live Maps route
       end-to-end; confirm the Matrix lights and releases correctly.
10. [ ] **Personal accounts (post-13-Nov-2023):** keep ≥12 testers opted in for
        **14 continuous days** on closed testing before applying for production.
11. [ ] Set contact email/support, select countries, confirm Free, accept
        policies, then apply for production / submit for review.

---

## Notes on changes already made for release

- Release is **signed** (upload key) and **minified + resource-shrunk** (R8 with
  keep rules for the Glyph SDK, the system-instantiated services, and the
  `Maneuver` enum); all `Log.*` calls are stripped from the release build.
- **All notification-content logging is gated behind `IS_DEV`** — the shipped
  `user` build logs nothing sensitive and writes no capture file.
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` was removed** (manifest + the in-app
  ask). It's the highest-scrutiny permission on Play and the foreground service
  already keeps the Matrix updating during a route, so it bought nothing. If you
  decide you want it back, re-add the `<uses-permission>` and the System-card
  row — but expect a Play permissions review.
- **`allowBackup="false"`** — an app handling notification-derived data
  shouldn't auto-back-up; the backup rules were empty stubs anyway.
- Version is **1.0.0** (`versionCode 1`). Bump `versionCode` +1 for every
  subsequent upload.
