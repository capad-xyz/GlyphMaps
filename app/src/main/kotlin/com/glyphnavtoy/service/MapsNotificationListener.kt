// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 capad.io <capad.xyz@gmail.com>
// GlyphMaps - licensed under AGPL-3.0 (see LICENSE).
// Brand assets (name, icon, artwork) are NOT covered by the AGPL; see NOTICE.

package com.glyphnavtoy.service

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.glyphnavtoy.capture.CaptureWriter
import com.glyphnavtoy.glyph.Maneuver
import com.glyphnavtoy.nav.LiveNavSnapshot
import com.glyphnavtoy.nav.NavStateRepo
import com.glyphnavtoy.nav.Speedometer
import java.util.Locale

/**
 * Listens for Google Maps Live Updates, forwards parsed nav state to
 * [GlyphRenderService], and **persists every captured notification to disk
 * via [CaptureWriter]** so we never lose data even when ADB isn't connected.
 *
 * Captured Android 16 ProgressStyle format (verified live):
 * ```
 *   android.title             = "60 m · Turn left onto ADM Rd"   ← maneuver text
 *   android.shortCriticalText = "60 m"                            ← distance, pre-parsed
 *   android.template          = "android.app.Notification$ProgressStyle"
 * ```
 *
 * Parsing rules:
 *  - `shortCriticalText` is the cleanest distance source — Google has already
 *    formatted it ("60 m" / "1.5 km"). Regex out the number + unit.
 *  - `title` either starts with "<distance> · " (active turn approach) or
 *    just the maneuver phrase ("Head south", "Head toward ADM Rd",
 *    "Rerouting..."). Strip the prefix if present, then run
 *    [Maneuver.fromMapsString] after normalizing spaces → dashes.
 *  - "Rerouting..." → don't push (keep last frame; transient state).
 *  - Maneuvers far from the upcoming turn (> 300 m) morph to FORWARD_* as
 *    a "preview" icon. As the turn closes in, the direct LEFT/RIGHT shows.
 *
 * Two log streams are emitted:
 *  - `MapsNotifListener:I` — compact parser output
 *  - `MapsNotifDump:V`     — full notification.extras dump (regression fixture)
 * Both also persist to disk in `getExternalFilesDir(null)/captures/`.
 */
class MapsNotificationListener : NotificationListenerService() {

    private var writer: CaptureWriter? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Capture-to-disk is a DEV-only tool (writes street names locally for
        // parser tuning). In the user build the writer stays null, so every
        // writer?.… call below is a no-op — nothing is persisted. Privacy by
        // construction: the shipped app never writes nav data to storage.
        writer = if (com.glyphnavtoy.BuildConfig.IS_DEV) CaptureWriter(applicationContext) else null
        Log.i(TAG, "Listener connected. Capture=${if (writer != null) "ON (dev)" else "OFF (user)"}")
    }

    override fun onListenerDisconnected() {
        // The system can transiently unbind us (low memory, package update,
        // doze-aggressive periods). Ask immediately to be rebound so we don't
        // miss notifications while the user is out walking with the app in
        // the background. Available since API 24 — phone is way past that.
        Log.w(TAG, "Listener disconnected — requesting rebind")
        writer = null
        try {
            requestRebind(android.content.ComponentName(this, MapsNotificationListener::class.java))
        } catch (t: Throwable) {
            Log.w(TAG, "requestRebind failed", t)
        }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName != MAPS_PACKAGE) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(EXTRA_TITLE)?.toString().orEmpty()
        val shortCritical = extras.getString(EXTRA_SHORT_CRITICAL)

        // Full raw dump → logcat (DUMP_TAG) + persistent file. Lets us re-derive
        // the parser later if Maps changes its notification format. DEV-only:
        // dumping full Maps notification extras (street names, ETA) to logcat
        // would leak the user's route in the shipped build.
        if (com.glyphnavtoy.BuildConfig.IS_DEV) dumpRaw(sbn)

        // Filter non-nav Maps notifications. Post-trip surveys ("How was X?"),
        // place suggestions, and other ambient Maps chatter all share our
        // package filter but they aren't ProgressStyle nav notifications —
        // the category check distinguishes them.
        if (sbn.notification.category != "navigation") {
            logBoth("Non-nav Maps notification (category=${sbn.notification.category ?: "null"}) — skipped")
            return
        }

        // Skip transient/setup states — keep whatever's on the matrix.
        when {
            title.startsWith("Rerouting", ignoreCase = true) -> {
                logBoth("Rerouting — skipped"); return
            }
            title.startsWith("Starting navigation", ignoreCase = true) -> {
                logBoth("Starting navigation — skipped"); return
            }
            // Post-trip survey prompt — explicit double-safety on top of the
            // category filter above. These end with "?" and start with phrases
            // like "How was".
            title.startsWith("How was", ignoreCase = true) -> {
                logBoth("Post-trip survey — skipped"); return
            }
        }

        val distanceM = parseDistanceMeters(shortCritical) ?: parseDistanceFromTitlePrefix(title)
        val maneuverText = stripDistancePrefix(title)
        val rawManeuver = Maneuver.fromMapsString(normalizeForLookup(maneuverText))

        // Feed Speedometer with the current progress so its EMA reflects
        // recent motion. This must run BEFORE thresholdMeters() so the
        // threshold uses today's speed reading.
        val progress = extras.takeIf { it.containsKey(EXTRA_PROGRESS) }?.getInt(EXTRA_PROGRESS)
        Speedometer.observe(progress)

        // Speed-aware morphing: matches Google Maps' own ~15s preview-to-
        // imperative window. Sharp turns intentionally skip the morph —
        // research showed Maps treats them as always-direct (the user needs
        // more reaction time and the SHARP icon itself communicates urgency).
        val threshold = Speedometer.thresholdMeters()
        val maneuver = when {
            distanceM == null -> rawManeuver
            distanceM <= threshold -> rawManeuver
            rawManeuver == Maneuver.LEFT -> Maneuver.FORWARD_LEFT
            rawManeuver == Maneuver.RIGHT -> Maneuver.FORWARD_RIGHT
            // SHARP_LEFT, SHARP_RIGHT, KEEP_*, ROUNDABOUT, U-turn — never morph.
            else -> rawManeuver
        }

        val morphTag = if (maneuver != rawManeuver) " (morphed from $rawManeuver)" else ""
        val parsedLine = "title=\"$title\" shortCritical=\"${shortCritical ?: ""}\"  " +
            "→ maneuver=$maneuver$morphTag  distance=${distanceM ?: "(none)"}m  " +
            "speed=${Speedometer.speedKmhString()}  morphAt=${threshold}m"
        logBoth(parsedLine)

        // Push the slim render-side state to the matrix service.
        forwardToRenderService(maneuver, distanceM)

        val street = extractStreetName(stripDistancePrefix(title))
        val eta = extras.getString(EXTRA_SUB_TEXT)
        val progressMax = extras.takeIf { it.containsKey(EXTRA_PROGRESS_MAX) }?.getInt(EXTRA_PROGRESS_MAX)

        // Push the FULL snapshot (street name, ETA, route progress) to the
        // shared repo. MainActivity collects this and renders the rich
        // "now navigating" view — keeps the in-app screen synced with what
        // Maps is actually saying without the user having to switch apps.
        NavStateRepo.update(
            LiveNavSnapshot(
                maneuver = maneuver,
                rawManeuver = rawManeuver,
                distanceMeters = distanceM,
                title = title,
                streetName = street,
                eta = eta,
                progressMeters = progress,
                progressMaxMeters = progressMax,
            )
        )

        // Rich machine-parseable record + Google's own maneuver icon, for
        // offline analytics and pixel-art ground truth.
        captureRich(
            sbn = sbn,
            title = title,
            shortCritical = shortCritical,
            rawManeuver = rawManeuver,
            maneuver = maneuver,
            distanceM = distanceM,
            street = street,
            eta = eta,
            progress = progress,
            progressMax = progressMax,
            threshold = threshold,
        )
    }

    /**
     * Write the JSONL event record and extract Google's maneuver icon. Kept
     * separate from the hot-path parsing so a failure here (bad icon, IO
     * error) can never break the matrix-render path.
     */
    private fun captureRich(
        sbn: StatusBarNotification,
        title: String,
        shortCritical: String?,
        rawManeuver: Maneuver,
        maneuver: Maneuver,
        distanceM: Int?,
        street: String?,
        eta: String?,
        progress: Int?,
        progressMax: Int?,
        threshold: Int,
    ) {
        val w = writer ?: return
        val extras = sbn.notification.extras

        // --- JSONL event record ---
        try {
            val obj = org.json.JSONObject().apply {
                put("ts", sbn.postTime)
                put("title", title)
                put("shortCritical", shortCritical ?: org.json.JSONObject.NULL)
                put("rawManeuver", rawManeuver.name)
                put("maneuver", maneuver.name)
                put("morphed", maneuver != rawManeuver)
                put("distanceM", distanceM ?: org.json.JSONObject.NULL)
                put("street", street ?: org.json.JSONObject.NULL)
                put("eta", eta ?: org.json.JSONObject.NULL)
                put("progress", progress ?: org.json.JSONObject.NULL)
                put("progressMax", progressMax ?: org.json.JSONObject.NULL)
                put("speedMps", String.format(Locale.US, "%.2f", Speedometer.speedMps.value).toDouble())
                put("morphAtM", threshold)
                put("category", sbn.notification.category ?: org.json.JSONObject.NULL)
                put("template", extras.getString("android.template") ?: org.json.JSONObject.NULL)
                // progressSegments: capture lengths + colors (colorInt encodes
                // traffic level — red/yellow/green legs). Useful later.
                val segs = extras.get("android.progressSegments")
                if (segs is ArrayList<*>) {
                    val arr = org.json.JSONArray()
                    segs.forEach { seg ->
                        if (seg is android.os.Bundle) {
                            arr.put(org.json.JSONObject().apply {
                                put("length", seg.getInt("length"))
                                put("colorInt", seg.getInt("colorInt"))
                            })
                        }
                    }
                    put("segments", arr)
                }
            }
            w.appendEventJson(obj.toString())
        } catch (t: Throwable) {
            Log.w(TAG, "JSONL event write failed", t)
        }

        // --- Google's maneuver icon (ground truth for our pixel art) ---
        try {
            val iconBitmap = extractLargeIcon(extras)
            if (iconBitmap != null) {
                // Dedup key = the maneuver phrase (distance stripped) so we
                // capture one icon per maneuver *type*, with an example title.
                w.saveIconIfNew(iconBitmap, stripDistancePrefix(title))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "icon extract failed", t)
        }
    }

    /** Pull the 144×144 maneuver bitmap out of `android.largeIcon`. */
    private fun extractLargeIcon(extras: android.os.Bundle): android.graphics.Bitmap? {
        val icon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable("android.largeIcon", android.graphics.drawable.Icon::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable("android.largeIcon") as? android.graphics.drawable.Icon
        } ?: return null

        val drawable = icon.loadDrawable(this) ?: return null
        if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        // Fallback: rasterize any drawable to a bitmap.
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 144
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 144
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName != MAPS_PACKAGE) return
        // Only the NAVIGATION notification ending means nav is over. Other Maps
        // notifications (post-trip survey, place cards) share the package but
        // must NOT tear down the matrix mid-route.
        if (sbn.notification.category != "navigation") return
        logBoth("Maps NAV notif REMOVED — releasing matrix to prior toy")
        clearRenderService()
        NavStateRepo.clear()
        // Wipe speed history so the next trip starts with a clean EMA — old
        // speed shouldn't taint a brand-new route on a different mode.
        Speedometer.reset()
    }

    // ============ Parsing helpers ============

    private fun parseDistanceMeters(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val m = DISTANCE_REGEX.find(s) ?: return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "m" -> value.toInt()
            "km" -> (value * 1000).toInt()
            "ft" -> (value * 0.3048).toInt()
            "mi" -> (value * 1609.34).toInt()
            else -> null
        }
    }

    private fun parseDistanceFromTitlePrefix(title: String): Int? {
        val before = title.substringBefore(" · ", missingDelimiterValue = "")
        if (before.isEmpty()) return null
        return parseDistanceMeters(before)
    }

    private fun stripDistancePrefix(title: String): String =
        if (" · " in title) title.substringAfter(" · ") else title

    private fun normalizeForLookup(text: String): String =
        text.lowercase().replace(' ', '-')

    /**
     * Best-effort street name from a maneuver phrase. Tries each pattern in
     * order; first match wins.
     *
     * Examples:
     *   "Turn left onto ADM Rd"            → "ADM Rd"
     *   "Turn left to stay on PC Colony Rd" → "PC Colony Rd"
     *   "Head south on Rajendra Nagar Br"  → "Rajendra Nagar Br"
     *   "Head toward ADM Rd"               → "ADM Rd"
     *   "At the roundabout, take the…"    → null  (no road name in the phrase)
     */
    private fun extractStreetName(phrase: String): String? {
        // Order matters — more specific patterns first to avoid false matches
        // (e.g. " on " is a substring of " onto " so checking onto first wins).
        val markers = listOf(" onto ", " to stay on ", " toward ", " on ")
        for (m in markers) {
            val idx = phrase.indexOf(m, ignoreCase = true)
            if (idx >= 0) {
                val name = phrase.substring(idx + m.length).trim()
                if (name.isNotEmpty()) return name
            }
        }
        return null
    }

    // ============ Raw dump (logcat + persistent file) ============

    private fun dumpRaw(sbn: StatusBarNotification) {
        val n = sbn.notification
        val sb = StringBuilder()
        sb.appendLine("════════ Maps notification POSTED ════════")
        sb.appendLine("  postTime=${sbn.postTime}  id=${sbn.id}  tag=${sbn.tag ?: "(none)"}")
        sb.appendLine("  category=${n.category}  flags=0x${n.flags.toString(16)}")
        sb.appendLine("  channelId=${n.channelId}")
        sb.appendLine("  style template=${n.extras.getString("android.template") ?: "(none)"}")
        sb.appendLine("  extras ↓")
        dumpBundle(n.extras, indent = "    ", out = sb)
        n.actions?.forEachIndexed { i, a ->
            sb.appendLine("  action[$i] title=\"${a.title}\"  semantic=${a.semanticAction}")
        }
        sb.appendLine("════════════════════════════════════════════════")

        // Mirror to logcat (line by line, so log filter -s DUMP_TAG works)
        sb.toString().lineSequence().forEach { line ->
            if (line.isNotEmpty()) Log.v(DUMP_TAG, line)
        }
        // Persist whole block as one entry
        writer?.appendRaw(sb.toString())
    }

    private fun dumpBundle(bundle: Bundle, indent: String, out: StringBuilder) {
        for (key in bundle.keySet().sorted()) {
            @Suppress("DEPRECATION") val value = bundle.get(key)
            val typeName = value?.javaClass?.simpleName ?: "null"
            when (value) {
                null -> out.appendLine("$indent$key (null)")
                is Bundle -> {
                    out.appendLine("$indent$key (Bundle) ↓")
                    dumpBundle(value, "$indent  ", out)
                }
                is CharSequence -> out.appendLine("$indent$key ($typeName) = \"$value\"")
                is ArrayList<*> -> {
                    out.appendLine("$indent$key (ArrayList, size=${value.size}) ↓")
                    value.forEachIndexed { i, item ->
                        when (item) {
                            is Bundle -> {
                                out.appendLine("$indent  [$i] (Bundle) ↓")
                                dumpBundle(item, "$indent    ", out)
                            }
                            else -> out.appendLine("$indent  [$i] (${item?.javaClass?.simpleName}) = $item")
                        }
                    }
                }
                else -> out.appendLine("$indent$key ($typeName) = $value")
            }
        }
    }

    /**
     * Log a parsed line to logcat AND persist to disk — DEV builds only.
     * Parsed lines can include notification content (titles, street names), so
     * the shipped user build logs nothing here (and `writer` is already null).
     */
    private fun logBoth(line: String) {
        if (com.glyphnavtoy.BuildConfig.IS_DEV) Log.i(TAG, line)
        writer?.appendParsed(line)
    }

    // ============ Service IPC ============

    private fun forwardToRenderService(maneuver: Maneuver, distanceMeters: Int?) {
        val intent = Intent(this, GlyphRenderService::class.java).apply {
            putExtra(GlyphRenderService.EXTRA_MANEUVER, maneuver.name)
            distanceMeters?.let { putExtra(GlyphRenderService.EXTRA_DISTANCE_M, it) }
        }
        startForegroundServiceCompat(intent)
    }

    private fun clearRenderService() {
        stopService(Intent(this, GlyphRenderService::class.java))
    }

    private fun startForegroundServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    companion object {
        private const val TAG = "MapsNotifListener"
        private const val DUMP_TAG = "MapsNotifDump"
        private const val MAPS_PACKAGE = "com.google.android.apps.maps"

        private const val EXTRA_TITLE = "android.title"
        private const val EXTRA_SHORT_CRITICAL = "android.shortCriticalText"
        private const val EXTRA_SUB_TEXT = "android.subText"
        private const val EXTRA_PROGRESS = "android.progress"
        private const val EXTRA_PROGRESS_MAX = "android.progressMax"

        /** Matches "60 m", "1.5 km", "300 ft", "2 mi" — number + optional decimal + unit. */
        private val DISTANCE_REGEX = Regex("""(\d+(?:\.\d+)?)\s*(km|mi|m|ft)\b""", RegexOption.IGNORE_CASE)
    }
}
