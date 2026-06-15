# Privacy Policy — Glyph Maps

**Last updated: 14 June 2026**

Glyph Maps ("the app", "we") displays Google Maps turn-by-turn directions on
the rear Glyph Matrix of the Nothing Phone (4a) Pro. This policy explains
exactly what the app accesses and what it does — and does not — do with it.

**Summary: Glyph Maps processes everything on your device and transmits
nothing. There are no accounts, no analytics, no ads, and no servers.**

## What we access

To show your next turn on the Glyph Matrix, the app uses **Notification
Access** to read the active Google Maps navigation notification only. It is
filtered by the Google Maps package name and the `navigation` notification
category; all other notifications — including other Maps notifications — are
ignored. From that one notification we read the maneuver type (turn left,
roundabout, arrive, etc.) and the distance to the turn.

## Why we need it

Android does not expose a public turn-by-turn API. The live navigation
notification is the only way to mirror your next maneuver to the Matrix in
real time without a Google account or API key.

## What we do with it

The maneuver and distance are converted on-device into a dot-matrix arrow and
drawn on the Glyph Matrix while you navigate. The Matrix is released the moment
navigation ends.

## What we do NOT do

- We do **not** send notification content, location, or any data off your
  device. The app contains no networking code.
- We do **not** use analytics, advertising, tracking, or crash-reporting SDKs.
- We do **not** require or offer an account, sign-in, or profile.
- We do **not** sell or share any data with third parties — there is nothing
  to share.

## On-device processing

All parsing and rendering happen locally on your phone, in memory.

## Data we store on your device

The app saves only your display preferences (LED head/tail brightness and the
static/sweep animation mode) in private app storage so they persist between
sessions. This never leaves the device and is removed when you uninstall.

**Developer ("Dev") build only:** the separate developer build can write a
local capture log of parsed Maps directions to the app's private storage, used
solely to tune the parser. This is absent from the public release build and is
never transmitted. Clearing the app's data or uninstalling removes it.

## Permissions explained

- **Notification Access** — read the Google Maps navigation notification (core
  function).
- **Foreground Service (Special Use)** — keep the Matrix updating during a
  route.
- **Post Notifications** — show the required foreground-service notification.
- **Nothing Glyph (`com.nothing.ketchum.ENABLE`)** — draw on the Glyph Matrix.

## Data retention

Nothing is retained off-device. Local preferences (and any dev-only log)
persist until you clear app data or uninstall.

## Children

Glyph Maps is a utility with no data collection and is not directed at
children. We do not knowingly collect data from anyone, including children
under 13.

## Changes

We may update this policy; the "Last updated" date will change and the current
version will always be available at the policy URL.

## Contact

Questions: **[YOUR_EMAIL]**

---

Glyph Maps is an independent app and is not affiliated with, endorsed by, or
sponsored by Google or Nothing Technology. "Google Maps" and "Nothing" are
trademarks of their respective owners.
