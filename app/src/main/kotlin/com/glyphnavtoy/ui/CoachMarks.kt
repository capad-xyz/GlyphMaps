// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 capad.io <capad.xyz@gmail.com>
// GlyphMaps - licensed under AGPL-3.0 (see LICENSE).
// Brand assets (name, icon, artwork) are NOT covered by the AGPL; see NOTICE.

package com.glyphnavtoy.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glyphnavtoy.ui.theme.GlyphColors
import com.glyphnavtoy.ui.theme.GlyphFonts
import kotlin.math.roundToInt

/**
 * Spotlight walkthrough ("coach marks"): a dark scrim over the real screen
 * with a glowing cut-out that travels from control to control, and a floating
 * card that explains the highlighted thing — with the step's real action
 * button inline when there is one (e.g. permission grants).
 *
 * Wiring: targets register their on-screen bounds with
 * [Modifier.tourTarget]; [TourOverlay] draws above the content inside the
 * same Box and looks bounds up by key. The overlay consumes all touches while
 * active, except that the highlighted region's action is offered as a button
 * on the card — so nothing under the scrim ever gets tapped by accident.
 */
data class TourStep(
    val key: String,
    val title: String,
    val body: String,
    /** Optional real action, offered as a button on the card (e.g. GRANT). */
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    /** When true the action is done (shows ✓ and Next instead). */
    val actionDone: Boolean = false,
)

/** Shared registry of target bounds, keyed by tour-step key. */
@Composable
fun rememberTourAnchors(): SnapshotStateMap<String, Rect> = remember { mutableStateMapOf() }

/** Registers this element's root-space bounds as tour target [key]. */
fun Modifier.tourTarget(anchors: SnapshotStateMap<String, Rect>, key: String): Modifier =
    onGloballyPositioned { anchors[key] = it.boundsInRoot() }

@Composable
fun TourOverlay(
    steps: List<TourStep>,
    stepIndex: Int,
    anchors: SnapshotStateMap<String, Rect>,
    onStep: (Int) -> Unit,
    onFinish: () -> Unit,
) {
    val step = steps.getOrNull(stepIndex) ?: return
    val rawTarget = anchors[step.key] ?: return
    val density = LocalDensity.current

    // Anchors are captured in ROOT (window) space, but this overlay may sit
    // inside inset padding (status bar). Convert into the overlay's own space
    // by subtracting our origin, or every hole lands too low by the inset.
    var overlayOrigin by remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    val target = rawTarget.translate(-overlayOrigin.x, -overlayOrigin.y)

    // Animate the spotlight from one target to the next.
    val left by animateFloatAsState(target.left, tween(360), label = "l")
    val top by animateFloatAsState(target.top, tween(360), label = "t")
    val right by animateFloatAsState(target.right, tween(360), label = "r")
    val bottom by animateFloatAsState(target.bottom, tween(360), label = "b")

    val pulse = rememberInfiniteTransition(label = "tourPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "g",
    )

    val padPx = with(density) { 10.dp.toPx() }
    val hole = Rect(left - padPx, top - padPx, right + padPx, bottom + padPx)
    val corner = with(density) { 16.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() }
            // Consume every tap that isn't on the card's own buttons — the
            // dimmed UI underneath must not be interactable during the tour.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        // Scrim with the punched-out spotlight + pulsing outline.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            drawRect(Color.Black.copy(alpha = 0.82f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(hole.left, hole.top),
                size = Size(hole.width, hole.height),
                cornerRadius = CornerRadius(corner, corner),
                blendMode = BlendMode.Clear,
            )
            drawRoundRect(
                color = GlyphColors.Accent.copy(alpha = glow),
                topLeft = Offset(hole.left, hole.top),
                size = Size(hole.width, hole.height),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = with(density) { 1.5.dp.toPx() }),
            )
        }

        // Floating card: below the hole when it fits, else above it, else —
        // when the target is taller than the leftover space (e.g. the hero) —
        // pinned to the bottom edge so it never sits on the spotlight.
        var containerH by remember { mutableIntStateOf(0) }
        var cardH by remember(stepIndex) { mutableIntStateOf(0) }
        val gapPx = with(density) { 18.dp.toPx() }
        val edgePx = with(density) { 16.dp.toPx() }
        val fitsBelow = hole.bottom + gapPx + cardH + edgePx <= containerH
        val fitsAbove = hole.top - gapPx - cardH >= edgePx
        val cardY = when {
            cardH == 0 || fitsBelow -> hole.bottom + gapPx
            fitsAbove -> hole.top - gapPx - cardH
            else -> (containerH - cardH - edgePx).coerceAtLeast(0f)
        }

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { containerH = it.size.height },
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, cardY.roundToInt()) }
                    .padding(horizontal = 22.dp)
                    .fillMaxWidth()
                    .onGloballyPositioned { cardH = it.size.height }
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlyphColors.SurfaceSolid)
                    .border(1.dp, GlyphColors.LineStrong, RoundedCornerShape(16.dp))
                    .padding(20.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        steps.forEachIndexed { i, _ ->
                            Box(
                                Modifier.size(if (i == stepIndex) 7.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(if (i <= stepIndex) GlyphColors.Accent else GlyphColors.Line),
                            )
                        }
                    }
                    Text(
                        "SKIP TOUR",
                        fontFamily = GlyphFonts.Mono, fontSize = 9.5.sp, letterSpacing = 1.6.sp,
                        color = GlyphColors.TextFaint,
                        modifier = Modifier.clickable(onClick = onFinish).padding(4.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    step.title,
                    fontFamily = GlyphFonts.Grotesk, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, color = GlyphColors.Text,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    step.body,
                    fontFamily = GlyphFonts.Grotesk, fontSize = 13.5.sp,
                    lineHeight = 20.sp, color = GlyphColors.TextDim,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (stepIndex > 0) {
                        Text(
                            "BACK",
                            fontFamily = GlyphFonts.Mono, fontWeight = FontWeight.Bold,
                            fontSize = 11.sp, letterSpacing = 1.6.sp, color = GlyphColors.TextDim,
                            modifier = Modifier.clickable { onStep(stepIndex - 1) }.padding(8.dp),
                        )
                    } else Spacer(Modifier.width(1.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (step.actionLabel != null && !step.actionDone) {
                            TourButton(step.actionLabel, primary = true) { step.action?.invoke() }
                            TourButton("LATER", primary = false) { advance(stepIndex, steps.size, onStep, onFinish) }
                        } else {
                            if (step.actionLabel != null && step.actionDone) {
                                Text("✓", color = GlyphColors.Gps, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            TourButton(
                                if (stepIndex == steps.lastIndex) "DONE" else "NEXT",
                                primary = true,
                            ) { advance(stepIndex, steps.size, onStep, onFinish) }
                        }
                    }
                }
            }
        }
    }
}

private fun advance(i: Int, count: Int, onStep: (Int) -> Unit, onFinish: () -> Unit) {
    if (i + 1 >= count) onFinish() else onStep(i + 1)
}

@Composable
private fun TourButton(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) GlyphColors.Accent else Color.Transparent)
            .border(
                1.dp,
                if (primary) GlyphColors.Accent else GlyphColors.LineStrong,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            fontFamily = GlyphFonts.Mono, fontWeight = FontWeight.Bold,
            fontSize = 11.sp, letterSpacing = 1.4.sp,
            color = if (primary) GlyphColors.AccentText else GlyphColors.Text,
        )
    }
}
