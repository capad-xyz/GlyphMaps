// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 capad.io <capad.xyz@gmail.com>
// GlyphMaps - licensed under AGPL-3.0 (see LICENSE).
// Brand assets (name, icon, artwork) are NOT covered by the AGPL; see NOTICE.

package com.glyphnavtoy.service

import android.app.Notification
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
 *   android.title             = "60 m · Turn left onto ADM Rd"
 *   android.shortCriticalText = "60 m"
 *   android.template          = "android.app.Notification$ProgressStyle"
 * ```
 */
class MapsNotificationListener : NotificationListenerService() {

    private var writer: CaptureWriter? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        writer = if (com.glyphnavtoy.BuildConfig.IS_DEV) CaptureWriter(applicationContext) else null
        Log.i(TAG, "Listener connected. Capture=${if (writer != null) "ON (dev)" else "OFF (user)"}")
        replayActiveMapsNotifications()
    }

    private fun replayActiveMapsNotifications() {
        val active = try {
            activeNotifications
        } catch (t: Throwable) {
            Log.w(TAG, "activeNotifications unavailable", t)
            null
        } ?: return
        var n = 0
        for (sbn in active) {
            if (!isMapsPackage(sbn.packageName)) continue
            n++
            onNotificationPosted(sbn)
        }
        if (n > 0) Log.i(TAG, "Replayed $n active Maps notification(s)")
    }

    override fun onListenerDisconnected() {
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
        if (!isMapsPackage(sbn.packageName)) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(EXTRA_TEXT)?.toString().orEmpty()
        val shortCritical = extras.getString(EXTRA_SHORT_CRITICAL)
            ?: extras.getCharSequence(EXTRA_SHORT_CRITICAL)?.toString()

        if (com.glyphnavtoy.BuildConfig.IS_DEV) dumpRaw(sbn)

        if (!isNavNotification(sbn, title, text, shortCritical)) {
            logBoth("Non-nav Maps notification (category=${sbn.notification.category ?: "null"} template=${extras.getString(EXTRA_TEMPLATE) ?: "none"}) — skipped")
            return
        }

        val headline = title.ifBlank { text }

        when {
            headline.startsWith("Rerouting", ignoreCase = true) ||
                (headline.contains("Rerouting", ignoreCase = true) && headline.length < 24) -> {
                logBoth("Rerouting — skipped"); return
            }
            headline.startsWith("Starting navigation", ignoreCase = true) -> {
                logBoth("Starting navigation — skipped"); return
            }
            headline.startsWith("How was", ignoreCase = true) -> {
                logBoth("Post-trip survey — skipped"); return
            }
        }

        val distanceM = parseDistanceMeters(shortCritical)
            ?: parseDistanceFromTitlePrefix(headline)
            ?: parseDistanceMeters(text)
        val maneuverText = stripDistancePrefix(headline).ifBlank { stripDistancePrefix(text) }
        val rawManeuver = Maneuver.fromMapsString(normalizeForLookup(maneuverText))

        val progress = extras.takeIf { it.containsKey(EXTRA_PROGRESS) }?.getInt(EXTRA_PROGRESS)
        Speedometer.observe(progress)

        val threshold = Speedometer.thresholdMeters()
        val maneuver = when {
            distanceM == null -> rawManeuver
            distanceM <= threshold -> rawManeuver
            rawManeuver == Maneuver.LEFT -> Maneuver.FORWARD_LEFT
            rawManeuver == Maneuver.RIGHT -> Maneuver.FORWARD_RIGHT
            else -> rawManeuver
        }

        val morphTag = if (maneuver != rawManeuver) " (morphed from $rawManeuver)" else ""
        logBoth(
            "title=\"$title\" shortCritical=\"${shortCritical ?: "\"\"}\"  " +
                "→ maneuver=$maneuver$morphTag  distance=${distanceM ?: "(none)"}m  " +
                "speed=${Speedometer.speedKmhString()}  morphAt=${threshold}m"
        )

        forwardToRenderService(maneuver, distanceM)

        val street = extractStreetName(maneuverText)
        val eta = extras.getCharSequence(EXTRA_SUB_TEXT)?.toString()
            ?: extras.getString(EXTRA_SUB_TEXT)
        val progressMax = extras.takeIf { it.containsKey(EXTRA_PROGRESS_MAX) }?.getInt(EXTRA_PROGRESS_MAX)

        NavStateRepo.update(
            LiveNavSnapshot(
                maneuver = maneuver,
                rawManeuver = rawManeuver,
                distanceMeters = distanceM,
                title = title.ifBlank { headline },
                streetName = street,
                eta = eta,
                progressMeters = progress,
                progressMaxMeters = progressMax,
            )
        )

        captureRich(
            sbn = sbn,
            title = title.ifBlank { headline },
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
                put("template", extras.getString(EXTRA_TEMPLATE) ?: org.json.JSONObject.NULL)
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
        try {
            val iconBitmap = extractLargeIcon(extras)
            if (iconBitmap != null) {
                w.saveIconIfNew(iconBitmap, stripDistancePrefix(title))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "icon extract failed", t)
        }
    }

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
        if (!isMapsPackage(sbn.packageName)) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(EXTRA_TEXT)?.toString().orEmpty()
        val shortCritical = extras.getString(EXTRA_SHORT_CRITICAL)
        if (!isNavNotification(sbn, title, text, shortCritical)) return
        logBoth("Maps NAV notif REMOVED — releasing matrix to prior toy")
        clearRenderService()
        NavStateRepo.clear()
        Speedometer.reset()
    }

    private fun isMapsPackage(packageName: String?): Boolean =
        packageName != null && packageName in MAPS_PACKAGES

    private fun isNavNotification(
        sbn: StatusBarNotification,
        title: String,
        text: String,
        shortCritical: String?,
    ): Boolean {
        val n = sbn.notification
        if (n.category == Notification.CATEGORY_NAVIGATION || n.category == "navigation") {
            return true
        }
        val template = n.extras.getString(EXTRA_TEMPLATE).orEmpty()
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        if (template.contains("ProgressStyle") && ongoing) return true
        if (ongoing && !shortCritical.isNullOrBlank()) return true
        val blob = "$title $text".lowercase()
        return ongoing && NAV_HINTS.any { it in blob }
    }

    private fun parseDistanceMeters(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val normalized = s.replace('\u00a0', ' ').replace('\u202f', ' ').replace(',', '.')
        val m = DISTANCE_REGEX.find(normalized) ?: return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "m", "meter", "meters", "metre", "metres", "mtr", "mtrs" -> value.toInt()
            "km", "kilometer", "kilometers", "kilometre", "kilometres" -> (value * 1000).toInt()
            "ft", "feet" -> (value * 0.3048).toInt()
            "yd", "yds", "yard", "yards" -> (value * 0.9144).toInt()
            "mi", "mile", "miles" -> (value * 1609.34).toInt()
            else -> null
        }
    }

    private fun parseDistanceFromTitlePrefix(title: String): Int? {
        val before = TITLE_SPLIT.split(title, limit = 2).firstOrNull() ?: return null
        if (before == title) return parseDistanceMeters(title)
        return parseDistanceMeters(before)
    }

    private fun stripDistancePrefix(title: String): String {
        val parts = TITLE_SPLIT.split(title, limit = 2)
        return if (parts.size == 2 && parseDistanceMeters(parts[0]) != null) {
            parts[1].trim()
        } else {
            title.trim()
        }
    }

    private fun normalizeForLookup(text: String): String =
        text.lowercase()
            .replace('\u00a0', ' ')
            .replace(Regex("[\u2013\u2014\u2212]"), "-")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    private fun extractStreetName(phrase: String): String? {
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

    private fun dumpRaw(sbn: StatusBarNotification) {
        val n = sbn.notification
        val sb = StringBuilder()
        sb.appendLine("Maps notification POSTED")
        sb.appendLine("  postTime=${sbn.postTime}  id=${sbn.id}  tag=${sbn.tag ?: "(none)"}")
        sb.appendLine("  category=${n.category}  flags=0x${n.flags.toString(16)}")
        sb.appendLine("  channelId=${n.channelId}")
        sb.appendLine("  style template=${n.extras.getString(EXTRA_TEMPLATE) ?: "(none)"}")
        sb.appendLine("  extras")
        dumpBundle(n.extras, indent = "    ", out = sb)
        n.actions?.forEachIndexed { i, a ->
            sb.appendLine("  action[$i] title=\"${a.title}\"  semantic=${a.semanticAction}")
        }
        sb.toString().lineSequence().forEach { line ->
            if (line.isNotEmpty()) Log.v(DUMP_TAG, line)
        }
        writer?.appendRaw(sb.toString())
    }

    private fun dumpBundle(bundle: Bundle, indent: String, out: StringBuilder) {
        for (key in bundle.keySet().sorted()) {
            @Suppress("DEPRECATION") val value = bundle.get(key)
            val typeName = value?.javaClass?.simpleName ?: "null"
            when (value) {
                null -> out.appendLine("$indent$key (null)")
                is Bundle -> {
                    out.appendLine("$indent$key (Bundle)")
                    dumpBundle(value, "$indent  ", out)
                }
                is CharSequence -> out.appendLine("$indent$key ($typeName) = \"$value\"")
                is ArrayList<*> -> {
                    out.appendLine("$indent$key (ArrayList, size=${value.size})")
                    value.forEachIndexed { i, item ->
                        when (item) {
                            is Bundle -> {
                                out.appendLine("$indent  [$i] (Bundle)")
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

    private fun logBoth(line: String) {
        if (com.glyphnavtoy.BuildConfig.IS_DEV) Log.i(TAG, line)
        writer?.appendParsed(line)
    }

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
        private val MAPS_PACKAGES = setOf(
            "com.google.android.apps.maps",
            "com.google.android.apps.mapslite",
        )
        private const val EXTRA_TITLE = "android.title"
        private const val EXTRA_TEXT = "android.text"
        private const val EXTRA_SHORT_CRITICAL = "android.shortCriticalText"
        private const val EXTRA_SUB_TEXT = "android.subText"
        private const val EXTRA_PROGRESS = "android.progress"
        private const val EXTRA_PROGRESS_MAX = "android.progressMax"
        private const val EXTRA_TEMPLATE = "android.template"
        private val DISTANCE_REGEX = Regex(
            """(\d+(?:\.\d+)?)\s*(kilometres?|kilometers?|km|miles?|mi|metres?|meters?|mtrs?|yards?|yds?|yd|feet|ft|m)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val TITLE_SPLIT = Regex("""\\s*[·•\u2013\u2014|:]+\\s+""")
        private val NAV_HINTS = listOf(
            "turn ", "keep ", "head ", "exit", "arrive", "arriving",
            "roundabout", "continue ", "merge", "destination", "u-turn",
            "uturn", "fork", "ramp", "onto ",
        )
    }
}
