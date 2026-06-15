package com.glyphnavtoy.glyph

import android.content.Context
import android.content.SharedPreferences

/**
 * Global head + tail brightness for the arrow.
 *
 *  - **head** (0-255): the bright arrowhead / chevron — what you read first.
 *  - **tail** (0-255): the dim trail behind it.
 *
 * One pair applies to every maneuver (the [headFor]/[tailFor] accessors ignore
 * the maneuver — they exist so call sites stay tidy and a future per-maneuver
 * mode could slot in without touching the renderer).
 *
 * Persisted with plain [SharedPreferences] (no extra dependency). An in-memory
 * cache backs every read so the ~10 Hz render loop never touches disk; writes
 * go through asynchronously via `apply()`.
 *
 * Lifecycle: call [init] once with any Context — MainActivity and the render
 * service both do it; the second call is a no-op. Before init, reads return the
 * defaults (safe, just not user-customised yet).
 */
object GlyphSettings {

    const val DEFAULT_HEAD = 255   // max
    const val DEFAULT_TAIL = 128   // ~50%

    private const val PREFS_NAME = "glyph_brightness"
    private const val KEY_HEAD = "head"
    private const val KEY_TAIL = "tail"
    private const val KEY_FLOWING = "flowing"
    private const val KEY_ONBOARDED = "onboarded"

    private var prefs: SharedPreferences? = null

    @Volatile var head: Int = DEFAULT_HEAD
        private set

    @Volatile var tail: Int = DEFAULT_TAIL
        private set

    /**
     * Display mode for the arrow on the real LEDs: true = SWEEPING FLOW (the
     * [ArrowSweep] comet), false = STATIC GLOW. Read by the render service every
     * frame so the in-app toggle actually changes the back of the phone.
     */
    @Volatile var flowing: Boolean = false
        private set

    /** Load persisted values into the in-memory cache. Idempotent. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        head = p.getInt(KEY_HEAD, DEFAULT_HEAD)
        tail = p.getInt(KEY_TAIL, DEFAULT_TAIL)
        flowing = p.getBoolean(KEY_FLOWING, false)
    }

    fun setFlowing(value: Boolean) {
        flowing = value
        prefs?.edit()?.putBoolean(KEY_FLOWING, value)?.apply()
    }

    fun headFor(@Suppress("UNUSED_PARAMETER") maneuver: Maneuver): Int = head
    fun tailFor(@Suppress("UNUSED_PARAMETER") maneuver: Maneuver): Int = tail

    fun setHead(value: Int) {
        head = value.coerceIn(0, 255)
        prefs?.edit()?.putInt(KEY_HEAD, head)?.apply()
    }

    fun setTail(value: Int) {
        tail = value.coerceIn(0, 255)
        prefs?.edit()?.putInt(KEY_TAIL, tail)?.apply()
    }

    /** Restore head + tail to factory defaults. */
    fun reset() {
        setHead(DEFAULT_HEAD)
        setTail(DEFAULT_TAIL)
    }

    // ---- App-level onboarding flag (shares the same prefs file) ----

    /** True once the user has completed (or skipped past) the welcome sheet. */
    fun isOnboarded(): Boolean = prefs?.getBoolean(KEY_ONBOARDED, false) ?: false

    fun setOnboarded() {
        prefs?.edit()?.putBoolean(KEY_ONBOARDED, true)?.apply()
    }
}
