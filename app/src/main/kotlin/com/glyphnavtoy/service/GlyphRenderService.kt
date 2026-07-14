// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 capad.io <capad.xyz@gmail.com>
// GlyphMaps - licensed under AGPL-3.0 (see LICENSE).
// Brand assets (name, icon, artwork) are NOT covered by the AGPL; see NOTICE.

package com.glyphnavtoy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.glyphnavtoy.MainActivity
import com.glyphnavtoy.R
import com.glyphnavtoy.glyph.GlyphRenderer
import com.glyphnavtoy.glyph.Maneuver
import com.glyphnavtoy.glyph.MatrixComposer
import com.glyphnavtoy.glyph.StartupAnimations
import com.glyphnavtoy.nav.NavState
import com.glyphnavtoy.nav.instructionVerb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service. One forever-loop at [MatrixComposer.MARQUEE_TICK_MS]:
 *
 *  - Marquee scroll offset increments every tick.
 *  - When the maneuver changes, snapshot the tick counter — for the next
 *    `framesFor(maneuver).size` ticks we render the animation; after that we
 *    fall back to the static [ArrowBitmaps] pattern.
 *  - Each tick: compose arrow region (animation frame OR static) + marquee
 *    distance and push the merged frame to the Matrix.
 */
class GlyphRenderService : Service() {

    private lateinit var renderer: GlyphRenderer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    @Volatile private var currentState: NavState? = null
    /** When the last nav update arrived. Drives the idle watchdog. */
    @Volatile private var lastStateElapsedMs: Long = 0L
    @Volatile private var stopping = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        com.glyphnavtoy.glyph.GlyphSettings.init(applicationContext)
        createNotificationChannel()
        startForegroundWith(buildNotification("Navigating…"))
        // Arm the watchdog from birth: if no nav state ever arrives (stray
        // start), we self-terminate after IDLE_TIMEOUT_MS instead of sitting
        // on the matrix.
        lastStateElapsedMs = android.os.SystemClock.elapsedRealtime()
        renderer = GlyphRenderer(this)
        renderer.connect {
            loopJob = scope.launch { matrixLoop() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let(::intentToNavState)?.let { incoming ->
            // Preserve the previous distance if this update didn't carry one
            // (Maps sometimes re-posts with shortCriticalText empty between
            // ticks — without this carryover the marquee would blank out).
            val merged = if (incoming.distanceMeters == null) {
                incoming.copy(distanceMeters = currentState?.distanceMeters)
            } else {
                incoming
            }
            currentState = merged
            lastStateElapsedMs = android.os.SystemClock.elapsedRealtime()
            updateOngoingNotification(merged)
        }
        // START_NOT_STICKY: when we stop (nav ended), Android must NOT recreate
        // us with a null intent. That resurrection was re-claiming the matrix
        // forever and was the root of "it stays active all the time."
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy — releasing matrix to prior Glyph toy")
        loopJob?.cancel()
        scope.cancel()
        renderer.disconnect() // closeAppMatrix() + unInit() → hands matrix back
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Single render loop, alive only while navigating.
     *
     * Release happens two ways:
     *  - **Primary**: the listener calls stopService() on onNotificationRemoved
     *    → onDestroy → renderer.disconnect() → matrix returns to the user's toy.
     *  - **Backup (this watchdog)**: if no fresh nav update arrives for
     *    [IDLE_TIMEOUT_MS] (a missed removal callback, GPS loss after nav, dev
     *    push left stale), we release + stopSelf ourselves. We never hog the
     *    matrix when the user isn't actively navigating.
     */
    private suspend fun matrixLoop() {
        var marqueeOffset = 0
        var animTick = 0
        var startupTick = 0
        var startupDone = false
        var releasedDisplay = false
        while (true) {
            val now = android.os.SystemClock.elapsedRealtime()

            // Watchdog: stale for too long → fully release and stop.
            if (!stopping && now - lastStateElapsedMs > IDLE_TIMEOUT_MS) {
                Log.i(TAG, "idle watchdog fired (${IDLE_TIMEOUT_MS}ms stale) — releasing + stopping")
                releaseAndStop()
                return
            }

            val state = currentState
            if (state != null) {
                startupDone = true
                releasedDisplay = false
                // Honour the user's display-mode toggle on the real LEDs.
                val mode = if (com.glyphnavtoy.glyph.GlyphSettings.flowing) {
                    com.glyphnavtoy.glyph.AnimationMode.FLOWING
                } else {
                    com.glyphnavtoy.glyph.AnimationMode.STATIC
                }
                renderer.push(MatrixComposer.compose(state, marqueeOffset, mode, animTick))
            } else if (!startupDone && startupTick < StartupAnimations.STARTUP_TICKS) {
                renderer.push(StartupAnimations.frameAt(StartupAnimations.DEFAULT_CONCEPT, startupTick))
                startupTick++
                if (startupTick >= StartupAnimations.STARTUP_TICKS) startupDone = true
            } else if (!releasedDisplay) {
                renderer.clear() // relinquish display immediately
                releasedDisplay = true
            }
            delay(MatrixComposer.MARQUEE_TICK_MS)
            marqueeOffset += MatrixComposer.MARQUEE_STEP
            animTick += 1
        }
    }

    /** Hand the matrix back, drop the foreground notification, terminate. */
    private fun releaseAndStop() {
        if (stopping) return
        stopping = true
        renderer.clear() // immediate display release (closeAppMatrix)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf() // → onDestroy → full disconnect (unInit)
    }

    // --- foreground notification ---

    private fun startForegroundWith(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateOngoingNotification(state: NavState) {
        val verb = state.maneuver.instructionVerb()
        val distance = state.distanceMeters?.let { " · $it m" } ?: ""
        startForegroundWith(buildNotification("$verb$distance"))
    }

    private fun buildNotification(contentText: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_stat_glyph)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GlyphMaps navigation",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows while GlyphMaps is driving the Glyph Matrix during navigation."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "GlyphRenderService"
        private const val CHANNEL_ID = "glyph_nav_status"
        private const val NOTIF_ID = 1001

        /** No fresh nav update for this long → release the matrix + stop.
         *  Maps re-posts every 2-5 s during nav, so 20 s only fires when nav
         *  has actually ended (and the removal callback was somehow missed). */
        private const val IDLE_TIMEOUT_MS = 20_000L

        const val EXTRA_MANEUVER = "maneuver"
        const val EXTRA_DISTANCE_M = "distance_m"

        fun intentToNavState(intent: Intent): NavState? {
            val maneuverName = intent.getStringExtra(EXTRA_MANEUVER) ?: return null
            val maneuver = runCatching { Maneuver.valueOf(maneuverName) }.getOrNull() ?: return null
            val distance = intent.getIntExtra(EXTRA_DISTANCE_M, -1).takeIf { it >= 0 }
            return NavState(maneuver = maneuver, distanceMeters = distance)
        }
    }
}
