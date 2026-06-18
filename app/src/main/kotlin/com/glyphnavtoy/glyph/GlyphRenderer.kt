// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 capad.io <capad.xyz@gmail.com>
// GlyphMaps - licensed under AGPL-3.0 (see LICENSE).
// Brand assets (name, icon, artwork) are NOT covered by the AGPL; see NOTICE.

package com.glyphnavtoy.glyph

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.glyphnavtoy.nav.NavState
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager

/**
 * Owns the [GlyphMatrixManager] connection. Translates [NavState] → [MatrixFrame]
 * via [MatrixComposer], then pushes the raw `int[]` to the Matrix via
 * `setAppMatrixFrame(int[])`.
 *
 * Why the raw `int[]` overload (not the structured `GlyphMatrixFrame.Builder`):
 * we already build a 169-pixel array in [MatrixFrame], so going through the
 * Builder + Bitmap path would be a needless conversion that also forces us
 * to use the SDK's text rendering for digits (which we can't size-control).
 */
class GlyphRenderer(private val context: Context) {

    private var gm: GlyphMatrixManager? = null
    @Volatile private var connected = false

    fun connect(onReady: () -> Unit) {
        val manager = GlyphMatrixManager.getInstance(context.applicationContext)
        gm = manager
        manager.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                manager.register(Glyph.DEVICE_25111p)
                connected = true
                Log.i(TAG, "GlyphMatrixManager connected + registered for 4a Pro")
                onReady()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                connected = false
                Log.w(TAG, "GlyphMatrixManager disconnected")
            }
        })
    }

    fun render(state: NavState) {
        val manager = gm
        if (manager == null || !connected) {
            Log.w(TAG, "render() called before connect ready — skipping (will retry on connect)")
            return
        }
        push(MatrixComposer.compose(state))
        // Distance is route content — keep it out of logcat in the user build.
        if (com.glyphnavtoy.BuildConfig.IS_DEV) {
            Log.d(TAG, "rendered ${state.maneuver} @ ${state.distanceMeters ?: '-'}m")
        }
    }

    /** Lower-level: push an arbitrary frame. Used by the preview test path. */
    fun push(frame: MatrixFrame) {
        val manager = gm ?: return
        if (!connected) return
        try {
            manager.setAppMatrixFrame(frame.toIntArray())
        } catch (t: Throwable) {
            // GlyphException is the documented throw; catch broadly so the
            // service doesn't crash if the SDK changes the contract.
            Log.e(TAG, "setAppMatrixFrame failed", t)
        }
    }

    fun clear() {
        try {
            gm?.closeAppMatrix()
        } catch (t: Throwable) {
            Log.w(TAG, "closeAppMatrix failed", t)
        }
    }

    fun disconnect() {
        clear()
        gm?.unInit()
        gm = null
        connected = false
    }

    companion object {
        private const val TAG = "GlyphRenderer"
    }
}
