// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 capad.io <capad.xyz@gmail.com>
// GlyphMaps - licensed under AGPL-3.0 (see LICENSE).
// Brand assets (name, icon, artwork) are NOT covered by the AGPL; see NOTICE.

package com.glyphnavtoy.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.glyphnavtoy.Brightness
import com.glyphnavtoy.BuildConfig
import com.glyphnavtoy.AppFooter
import com.glyphnavtoy.CaptureLogCard
import com.glyphnavtoy.DisplayModeGroup
import com.glyphnavtoy.DistanceGroup
import com.glyphnavtoy.HDivider
import com.glyphnavtoy.Header
import com.glyphnavtoy.Hero
import com.glyphnavtoy.ManeuverGroup
import com.glyphnavtoy.RouteSim
import com.glyphnavtoy.SystemFooter
import com.glyphnavtoy.glyph.AnimationMode
import com.glyphnavtoy.glyph.GlyphSettings
import com.glyphnavtoy.glyph.Maneuver
import com.glyphnavtoy.glyph.MatrixComposer
import com.glyphnavtoy.isNotificationAccessGranted
import com.glyphnavtoy.nav.NavState
import com.glyphnavtoy.nav.NavStateRepo
import com.glyphnavtoy.nav.PresetRoutes
import com.glyphnavtoy.rememberResumeKey
import com.glyphnavtoy.sendToService
import com.glyphnavtoy.service.MapsNotificationListener
import com.glyphnavtoy.stopService
import com.glyphnavtoy.ui.theme.GlyphColors
import com.glyphnavtoy.ui.theme.GlyphFonts
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The one screen. Hero + setup card (until permissions are granted) + the two
 * user settings inline + dev toolkit in dev builds. First run lays a
 * spotlight tour ([TourOverlay]) over the real controls instead of a separate
 * onboarding flow.
 */
@Composable
fun HomeScreen() {
    val ctx = LocalContext.current

    var maneuver by remember { mutableStateOf(Maneuver.STRAIGHT) }
    var distance by remember { mutableFloatStateOf(250f) }
    var street by remember { mutableStateOf<String?>(null) }
    var instruction by remember { mutableStateOf<String?>(null) }
    var mode by remember {
        mutableStateOf(if (GlyphSettings.flowing) AnimationMode.FLOWING else AnimationMode.STATIC)
    }
    var live by remember { mutableStateOf(false) }   // don't claim the matrix on open
    var mask by remember { mutableStateOf(true) }

    // Route sim (dev)
    var routeIdx by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var stepIdx by remember { mutableIntStateOf(0) }

    // Clocks
    var offset by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    val manualState = NavState(maneuver, distance.roundToInt().takeIf { it > 0 }, street)

    val liveSnapshot by NavStateRepo.live.collectAsState()
    val heroState = liveSnapshot?.let { NavState(it.maneuver, it.distanceMeters, it.streetName) } ?: manualState
    val heroInstruction = if (liveSnapshot != null) null else instruction
    val heroEta = liveSnapshot?.eta
    val heroProgress = liveSnapshot?.let { s ->
        val max = s.progressMaxMeters ?: return@let null
        s.progressMeters?.toFloat()?.div(max.coerceAtLeast(1))?.coerceIn(0f, 1f)
    }

    // ---- permissions, kept live across returns from system UI ----
    val resumeKey = rememberResumeKey()
    val listenerGranted = remember(resumeKey) { isNotificationAccessGranted(ctx) }
    var postNotifGranted by remember(resumeKey) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val postNotifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> postNotifGranted = granted }
    val allGranted = listenerGranted && postNotifGranted

    // ---- matrix self-test (also the tour's grand finale) ----
    var testing by remember { mutableStateOf(false) }
    LaunchedEffect(testing) {
        if (testing) {
            sendToService(ctx, NavState(Maneuver.RIGHT, 300))
            delay(8_000)
            if (!live) stopService(ctx)
            testing = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(MatrixComposer.MARQUEE_TICK_MS)
            offset += MatrixComposer.MARQUEE_STEP
            tick += 1
        }
    }

    LaunchedEffect(running, routeIdx, stepIdx) {
        if (!running) return@LaunchedEffect
        val route = PresetRoutes.all[routeIdx]
        val step = route.steps.getOrNull(stepIdx)
        if (step == null) { running = false; stepIdx = 0; return@LaunchedEffect }
        maneuver = step.maneuver
        distance = step.distanceMeters.toFloat()
        street = step.streetName
        instruction = step.instruction
        delay(step.holdMillis)
        if (stepIdx + 1 >= route.steps.size) { running = false; stepIdx = 0 } else stepIdx += 1
    }

    LaunchedEffect(manualState, live) {
        if (live) sendToService(ctx, manualState)
    }

    val onManeuver: (Maneuver) -> Unit = {
        maneuver = it; running = false; street = null; instruction = null
    }

    val isDev = BuildConfig.IS_DEV

    // ---- spotlight tour ----
    val anchors = rememberTourAnchors()
    var tourActive by remember { mutableStateOf(!GlyphSettings.isOnboarded()) }
    var tourStep by remember { mutableIntStateOf(0) }
    val tourSteps = listOf(
        TourStep(
            key = "hero",
            title = "Your Glyph dial",
            body = "A live mirror of the phone's back. During a route it shows the " +
                "next turn, the distance counting down, and the ring closing in " +
                "as you approach.",
        ),
        TourStep(
            key = "setup",
            title = if (allGranted) "You're connected" else "One-tap setup",
            body = "GlyphMaps reads only Google Maps' navigation notifications — " +
                "on this phone, never sent anywhere. Grant opens your toggle " +
                "directly; flip it on and come straight back.",
            actionLabel = "GRANT",
            action = {
                if (!listenerGranted) openListenerToggle(ctx)
                else postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            actionDone = allGranted,
        ),
        TourStep(
            key = "mode",
            title = "Static or sweeping",
            body = "How the LEDs render the arrow: a steady glow, or a comet " +
                "that sweeps along the maneuver. Changes the real matrix instantly.",
        ),
        TourStep(
            key = "brightness",
            title = "Head & tail brightness",
            body = "The arrowhead and its trail have separate brightness. The " +
                "dial above previews exactly what the LEDs will do.",
        ),
        TourStep(
            key = "test",
            title = "Take it for a spin",
            body = "This lights a sample right-turn on the back of your phone " +
                "for a few seconds. Flip it over and enjoy.",
            actionLabel = "LIGHT IT UP",
            action = { testing = true },
            actionDone = testing,
        ),
    )
    // Auto-scroll each tour step's target into view.
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(tourStep, tourActive) {
        if (!tourActive) return@LaunchedEffect
        anchors[tourSteps[tourStep].key]?.let { r ->
            val topPad = with(density) { 120.dp.toPx() }
            scroll.animateScrollTo((scroll.value + r.top - topPad).roundToInt().coerceAtLeast(0))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            Header(navActive = liveSnapshot != null, listenerGranted = listenerGranted)

            Box(Modifier.tourTarget(anchors, "hero")) {
                Hero(
                    state = heroState, instruction = heroInstruction, mode = mode,
                    offset = offset, tick = tick,
                    idle = liveSnapshot == null && !isDev,
                    idleReady = allGranted || postNotifGranted, // listener is the one that matters
                    showMaskToggle = isDev, mask = mask, onMask = { mask = it },
                    eta = heroEta, routeProgress = heroProgress,
                )
            }

            // One-tap setup card. Stays visible during the tour even when all
            // granted (showing its ✓s) so the tour's setup step has a real
            // target to spotlight; disappears afterwards once complete.
            if (!allGranted || tourActive) {
                SetupCard(
                    modifier = Modifier.tourTarget(anchors, "setup"),
                    listenerGranted = listenerGranted,
                    postNotifGranted = postNotifGranted,
                    onGrantListener = { openListenerToggle(ctx) },
                    onGrantPostNotif = { postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }

            Box(Modifier.tourTarget(anchors, "mode")) {
                DisplayModeGroup(mode) {
                    mode = it; GlyphSettings.setFlowing(it == AnimationMode.FLOWING)
                }
            }
            Box(Modifier.tourTarget(anchors, "brightness")) { Brightness() }

            // Matrix self-test — the useful empty-state action.
            Box(Modifier.tourTarget(anchors, "test")) {
                TestMatrixRow(testing = testing, onTest = { testing = true })
            }

            if (isDev) {
                HDivider()
                DistanceGroup(distance) { distance = it }
                ManeuverGroup(maneuver, onManeuver)
                HDivider()
                RouteSim(
                    routeIdx = routeIdx,
                    onRoute = { routeIdx = it; stepIdx = 0; running = false },
                    running = running, stepIdx = stepIdx,
                    onStart = { running = true },
                    onPause = { running = false },
                    onReset = { running = false; stepIdx = 0; street = null; instruction = null },
                )
                HDivider()
                CaptureLogCard()
                HDivider()
                SystemFooter(
                    showSync = true,
                    live = live, onLive = { live = it },
                    onPush = { sendToService(ctx, manualState) },
                    onStop = { stopService(ctx); live = false },
                )
            }

            AppFooter()
        }

        if (tourActive) {
            TourOverlay(
                steps = tourSteps,
                stepIndex = tourStep,
                anchors = anchors,
                onStep = { tourStep = it },
                onFinish = {
                    GlyphSettings.setOnboarded()
                    tourActive = false
                },
            )
        }
    }
}

/**
 * Deep-links straight to THIS app's notification-access toggle — one tap, one
 * switch, back. Falls back to the full listener list on OEM builds that don't
 * handle the detail intent.
 */
internal fun openListenerToggle(ctx: Context) {
    val cn = ComponentName(ctx, MapsNotificationListener::class.java)
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(detail) }.onFailure {
        ctx.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun SetupCard(
    modifier: Modifier = Modifier,
    listenerGranted: Boolean,
    postNotifGranted: Boolean,
    onGrantListener: () -> Unit,
    onGrantPostNotif: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlyphColors.GlyphRadius.dp))
            .border(1.dp, GlyphColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(GlyphColors.GlyphRadius.dp))
            .background(GlyphColors.Accent.copy(alpha = 0.05f))
            .padding(18.dp),
    ) {
        val done = listenerGranted && postNotifGranted
        Text(
            if (done) "SETUP COMPLETE" else "FINISH SETUP",
            fontFamily = GlyphFonts.Mono, fontWeight = FontWeight.Bold,
            fontSize = 10.5.sp, letterSpacing = 2.4.sp,
            color = if (done) GlyphColors.Gps else GlyphColors.Accent,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (done) "GlyphMaps is connected and ready." else "Two taps and you're done.",
            fontFamily = GlyphFonts.Grotesk, fontSize = 12.5.sp, color = GlyphColors.TextDim,
        )
        Spacer(Modifier.height(14.dp))
        GrantRow(
            label = "Read Google Maps navigation",
            sub = "Opens your toggle directly — flip it on, come back",
            granted = listenerGranted, onGrant = onGrantListener,
        )
        Spacer(Modifier.height(10.dp))
        GrantRow(
            label = "Show the quiet nav notification",
            sub = "One system dialog — keeps the LEDs alive off-screen",
            granted = postNotifGranted, onGrant = onGrantPostNotif,
        )
    }
}

@Composable
private fun GrantRow(label: String, sub: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(7.dp).clip(CircleShape)
                .background(if (granted) GlyphColors.Gps else GlyphColors.Accent.copy(alpha = 0.6f)),
        )
        Column(Modifier.weight(1f)) {
            Text(label, fontFamily = GlyphFonts.Grotesk, fontSize = 13.5.sp, color = GlyphColors.Text)
            Text(sub, fontFamily = GlyphFonts.Grotesk, fontSize = 11.sp, color = GlyphColors.TextFaint)
        }
        if (granted) {
            Text("✓", color = GlyphColors.Gps, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(GlyphColors.Accent)
                    .clickable(onClick = onGrant)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    "GRANT", fontFamily = GlyphFonts.Mono, fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp, letterSpacing = 1.4.sp, color = GlyphColors.AccentText,
                )
            }
        }
    }
}

@Composable
private fun TestMatrixRow(testing: Boolean, onTest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlyphColors.GlyphRadius.dp))
            .border(1.dp, GlyphColors.Line, RoundedCornerShape(GlyphColors.GlyphRadius.dp))
            .clickable(enabled = !testing, onClick = onTest)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                if (testing) "Look at the back of your phone" else "Test the matrix",
                fontFamily = GlyphFonts.Grotesk, fontSize = 13.5.sp,
                color = if (testing) GlyphColors.Accent else GlyphColors.Text,
            )
            Text(
                if (testing) "Right turn · 300 m — switches off by itself"
                else "Flash a sample turn on the LEDs",
                fontFamily = GlyphFonts.Grotesk, fontSize = 11.sp, color = GlyphColors.TextFaint,
            )
        }
        Text(
            if (testing) "●" else "▶",
            color = if (testing) GlyphColors.Accent else GlyphColors.TextDim, fontSize = 13.sp,
        )
    }
}
