// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 capad.io <capad.xyz@gmail.com>
// GlyphMaps - licensed under AGPL-3.0 (see LICENSE).
// Brand assets (name, icon, artwork) are NOT covered by the AGPL; see NOTICE.

package com.glyphnavtoy.capture

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Append-only writer for capturing Maps notification data to disk.
 *
 * Solves the problem that `adb logcat` is a circular ring buffer (~256 KB to
 * 1 MB on most devices) — anything older than a few minutes of active
 * navigation is lost the moment ADB isn't connected. By writing to a
 * regular file in the app's external-files dir, we preserve every captured
 * notification for as long as the device has free space.
 *
 * **Storage location:** `getExternalFilesDir(null)/captures/`
 *  - On the phone this resolves to
 *    `/storage/emulated/0/Android/data/com.glyphnavtoy/files/captures/`
 *  - No runtime permissions required (Android 10+ scoped storage rules)
 *  - Pullable via `adb pull` without root
 *  - Auto-removed when the app is uninstalled (no leftover personal data)
 *
 * **File layout:**
 *  - `YYYY-MM-DD_parsed.log` — one compact line per notification
 *    (`title="…" shortCritical="…" → maneuver=… distance=…m`)
 *  - `YYYY-MM-DD_raw.log` — full Notification.extras dump (multi-line) so
 *    we can re-derive the parser if Maps changes its format
 *  - `YYYY-MM-DD_events.jsonl` — one JSON object per notification, machine-
 *    parseable for analytics ("what distances does each maneuver appear at",
 *    "title format distribution", "speed vs morph correctness")
 *  - `icons/<hash>.png` — Google's OWN 144×144 maneuver icon, deduped by
 *    pixel-content hash. This is the ground-truth icon set for perfecting
 *    our pixel art — one PNG per distinct icon Maps renders
 *  - `icons/index.tsv` — `hash \t exampleTitle` so we know which title
 *    produced each saved icon
 *  - Files rotate at midnight (local time). No size cap yet — typical
 *    nav session is ~5 events/min × 1 KB raw = ~300 KB/hour, fine for
 *    multi-hour captures.
 *
 * **Thread safety:** [NotificationListenerService] callbacks all fire on
 * the same binder thread sequentially, so plain appendText is safe. If we
 * ever multi-thread the listener, wrap in a Mutex.
 */
class CaptureWriter(context: Context) {

    private val captureDir: File = File(context.getExternalFilesDir(null), DIR_NAME).also {
        if (!it.exists()) it.mkdirs()
    }

    private val iconsDir: File = File(captureDir, "icons").also {
        if (!it.exists()) it.mkdirs()
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun appendParsed(line: String) {
        append("parsed", "${timeFormat.format(Date())}  $line")
    }

    fun appendRaw(block: String) {
        append("raw", block)
    }

    /** Append one machine-parseable JSON record to today's events.jsonl. */
    fun appendEventJson(json: String) {
        appendExt("events", "jsonl", json)
    }

    /**
     * Save Google's own maneuver icon, deduped by pixel-content hash. Returns
     * true if this was a NEW icon (first time seen). Cheap to call on every
     * notification — the hash check short-circuits dupes without writing.
     *
     * One PNG per distinct icon → ends up as ~15-20 files (Google's full
     * maneuver icon set) regardless of how many notifications we capture.
     */
    fun saveIconIfNew(bitmap: Bitmap, exampleTitle: String): Boolean {
        val hash = bitmap.contentHash()
        val file = File(iconsDir, "$hash.png")
        if (file.exists()) return false
        return try {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Record which title first produced this icon.
            File(iconsDir, "index.tsv").appendText("$hash\t$exampleTitle\n")
            Log.i(TAG, "Saved new maneuver icon $hash.png from \"$exampleTitle\"")
            true
        } catch (e: IOException) {
            Log.w(TAG, "saveIcon failed", e)
            false
        }
    }

    /**
     * Fast content hash of a bitmap: downsample to 16×16, sum ARGB into a
     * rolling hash. Two visually identical Maps icons collapse to the same
     * hash even if they arrived in different notifications. Not crypto — just
     * a dedup key, collisions astronomically unlikely for our ~20 icons.
     */
    private fun Bitmap.contentHash(): String {
        val scaled = Bitmap.createScaledBitmap(this, 16, 16, false)
        var h = 1125899906842597L // prime seed
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                h = 31 * h + scaled.getPixel(x, y)
            }
        }
        if (scaled != this) scaled.recycle()
        return java.lang.Long.toHexString(h)
    }

    /** Append [text] (followed by a newline) to today's `kind` .log file. */
    private fun append(kind: String, text: String) = appendExt(kind, "log", text)

    private fun appendExt(kind: String, ext: String, text: String) {
        val file = File(captureDir, "${dateFormat.format(Date())}_$kind.$ext")
        try {
            file.appendText("$text\n")
        } catch (e: IOException) {
            Log.w(TAG, "append to ${file.name} failed", e)
        }
    }

    private fun todayFile(kind: String): File {
        val name = "${dateFormat.format(Date())}_$kind.log"
        return File(captureDir, name)
    }

    /**
     * Tail of today's parsed log — used by the in-app live viewer.
     * Returns the last [maxLines] lines, newest last. Empty if the file
     * doesn't exist yet (e.g. listener hasn't received anything today).
     */
    fun recentParsedLines(maxLines: Int = 30): List<String> {
        val file = todayFile("parsed")
        if (!file.exists()) return emptyList()
        return try {
            file.readLines().takeLast(maxLines)
        } catch (e: IOException) {
            Log.w(TAG, "tail of ${file.name} failed", e)
            emptyList()
        }
    }

    /**
     * Snapshot of what's currently on disk — for the in-app status card.
     *
     * `lastEventEpochMs` reads the file mtime of the newest file rather than
     * tracking it in-memory, so the value survives process death — if the
     * listener service was killed and rebound after a walk, the UI still
     * shows the genuine "last write" time when you reopen the app.
     */
    fun summary(): Summary {
        val files = captureDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        val totalBytes = files.sumOf { it.length() }
        return Summary(
            fileCount = files.size,
            totalBytes = totalBytes,
            newestFile = files.firstOrNull()?.name,
            lastEventEpochMs = files.firstOrNull()?.lastModified(),
            dirAbsolutePath = captureDir.absolutePath,
        )
    }

    data class Summary(
        val fileCount: Int,
        val totalBytes: Long,
        val newestFile: String?,
        /** Epoch ms of the most recent append, or null if nothing captured yet. */
        val lastEventEpochMs: Long?,
        val dirAbsolutePath: String,
    )

    companion object {
        private const val TAG = "CaptureWriter"
        private const val DIR_NAME = "captures"
    }
}
