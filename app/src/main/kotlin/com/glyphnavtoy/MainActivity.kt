package com.glyphnavtoy

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glyphnavtoy.capture.CaptureWriter
import com.glyphnavtoy.glyph.AnimationMode
import com.glyphnavtoy.glyph.GlyphSettings
import com.glyphnavtoy.glyph.Maneuver
import com.glyphnavtoy.glyph.MatrixComposer
import com.glyphnavtoy.nav.NavState
import com.glyphnavtoy.nav.NavStateRepo
import com.glyphnavtoy.nav.PresetRoutes
import com.glyphnavtoy.nav.instructionVerb
import com.glyphnavtoy.service.GlyphRenderService
import com.glyphnavtoy.service.MapsNotificationListener
import com.glyphnavtoy.ui.GlyphDial
import com.glyphnavtoy.ui.GlyphMark
import com.glyphnavtoy.ui.GlyphMatrix
import com.glyphnavtoy.ui.theme.GlyphColors
import com.glyphnavtoy.ui.theme.GlyphFonts
import com.glyphnavtoy.ui.theme.GlyphNavToyTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge black: bars transparent, light icons (bg is always black).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        GlyphSettings.init(applicationContext)
        setContent {
            GlyphNavToyTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GlyphColors.Bg)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    ControlPanel()
                }
            }
        }
    }
}

// ============================================================================
// Root
// ============================================================================

@Composable
private fun ControlPanel() {
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

    // Route sim
    var routeIdx by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var stepIdx by remember { mutableIntStateOf(0) }

    // Clocks
    var offset by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    val manualState = NavState(maneuver, distance.roundToInt().takeIf { it > 0 }, street)

    // Live Maps snapshot wins when present — the hero becomes a real product
    // readout during navigation, and a tester otherwise.
    val liveSnapshot by NavStateRepo.live.collectAsState()
    val heroState = liveSnapshot?.let { NavState(it.maneuver, it.distanceMeters, it.streetName) } ?: manualState
    val heroInstruction = if (liveSnapshot != null) null else instruction

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

    // The user (product) build is deliberately minimal — the live preview plus
    // the two settings a real user tunes. The dev build adds the manual
    // override, route simulator and capture-log tools on top.
    val isDev = BuildConfig.IS_DEV

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 22.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        item { Header() }
        item {
            Hero(
                state = heroState, instruction = heroInstruction, mode = mode,
                offset = offset, tick = tick,
                idle = liveSnapshot == null && !isDev,
                showMaskToggle = isDev, mask = mask, onMask = { mask = it },
            )
        }

        // Settings every user keeps.
        item { HDivider() }
        item {
            DisplayModeGroup(mode) {
                mode = it; GlyphSettings.setFlowing(it == AnimationMode.FLOWING)
            }
        }
        item { Brightness() }

        // Dev-only toolkit: manual override, route simulator, capture log.
        if (isDev) {
            item { HDivider() }
            item { DistanceGroup(distance) { distance = it } }
            item { ManeuverGroup(maneuver, onManeuver) }
            item { HDivider() }
            item {
                RouteSim(
                    routeIdx = routeIdx,
                    onRoute = { routeIdx = it; stepIdx = 0; running = false },
                    running = running, stepIdx = stepIdx,
                    onStart = { running = true },
                    onPause = { running = false },
                    onReset = { running = false; stepIdx = 0; street = null; instruction = null },
                )
            }
            item { HDivider() }
            item { CaptureLogCard() }
        }

        item { HDivider() }
        item {
            SystemFooter(
                showSync = isDev,
                live = live, onLive = { live = it },
                onPush = { sendToService(ctx, manualState) },
                onStop = { stopService(ctx); live = false },
            )
        }
        item { AppFooter() }
    }
}

// ============================================================================
// Header
// ============================================================================

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlyphMark(size = 32.dp)
            Text(
                "GlyphNav",
                fontFamily = GlyphFonts.Doto, fontWeight = FontWeight.Black,
                fontSize = 24.sp, color = GlyphColors.Text,
            )
        }
        StatusDot(label = "GPS", color = GlyphColors.Gps, live = true)
    }
}

// ============================================================================
// Hero — the Glyph dial
// ============================================================================

@Composable
private fun Hero(
    state: NavState,
    instruction: String?,
    mode: AnimationMode,
    offset: Int,
    tick: Int,
    mask: Boolean,
    onMask: (Boolean) -> Unit,
    idle: Boolean = false,
    showMaskToggle: Boolean = true,
) {
    val dist = state.shortDistance()
    val isK = dist?.endsWith("k") == true
    val num = dist?.replace(Regex("[mk]+$"), "") ?: "—"
    val unit = if (dist == null) "" else if (isK) "km" else "m"
    val instr = instruction ?: state.maneuver.instructionVerb()
    val arrived = !idle && state.maneuver == Maneuver.ARRIVE
    val nearTurn = !idle && !arrived && (state.distanceMeters ?: Int.MAX_VALUE) <= 80
    val blinkOn = (offset / 3) % 2 == 0

    // Idle preview shows the arrow only (no distance marquee) — there's no real
    // turn to count down to yet.
    val frameState = if (idle) state.copy(distanceMeters = null) else state
    val frame = remember(frameState, offset, tick, mode) {
        MatrixComposer.compose(frameState, offset, mode, tick)
    }
    // No countdown ring when idle — it's a settings preview, not a live turn.
    val frac = when {
        idle -> 0f
        arrived -> 1f
        else -> state.distanceMeters?.let { (1f - (it.coerceAtMost(800).toFloat() / 800f)).coerceIn(0f, 1f) } ?: 0f
    }

    val accent = if (nearTurn) GlyphColors.Alert else GlyphColors.Accent
    val intensity = if (nearTurn) (if (blinkOn) 1f else 0.18f) else 1f

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel(if (idle) "Preview" else "Next maneuver")
            if (showMaskToggle) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .border(1.dp, GlyphColors.Line, RoundedCornerShape(7.dp))
                        .clickable { onMask(!mask) }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(
                        if (mask) "◐ MASKED" else "▦ FULL GRID",
                        fontFamily = GlyphFonts.Mono, fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp, letterSpacing = 2.4.sp, color = GlyphColors.TextDim,
                    )
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            GlyphDial(
                frame = frame, progress = frac, applyMask = mask,
                onColor = accent, ringColor = accent,
                intensity = if (idle) 0.85f else intensity,
            )
        }

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (idle) {
                Text(
                    "Waiting for navigation", fontFamily = GlyphFonts.Grotesk, fontSize = 21.sp,
                    color = GlyphColors.Text, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "START A ROUTE IN GOOGLE MAPS", fontFamily = GlyphFonts.Mono, fontSize = 9.5.sp,
                    letterSpacing = 2.4.sp, color = GlyphColors.TextFaint, textAlign = TextAlign.Center,
                )
                return@Column
            }
            Text(
                instr, fontFamily = GlyphFonts.Grotesk, fontSize = 21.sp,
                color = if (arrived) GlyphColors.Accent else GlyphColors.Text, textAlign = TextAlign.Center,
            )
            if (arrived) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "DESTINATION" + (state.nextStreet?.let { " · $it" } ?: ""),
                    fontFamily = GlyphFonts.Mono, fontSize = 10.5.sp, letterSpacing = 2.sp,
                    color = GlyphColors.TextFaint,
                )
            } else {
                Spacer(Modifier.height(14.dp))
                Text(
                    if (nearTurn) "TURN NOW" else "TURN IN",
                    fontFamily = GlyphFonts.Mono, fontSize = 9.5.sp, letterSpacing = 2.6.sp,
                    color = if (nearTurn) GlyphColors.Alert else GlyphColors.TextFaint,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        num, fontFamily = GlyphFonts.Doto, fontWeight = FontWeight.Black, fontSize = 60.sp,
                        color = if (nearTurn) GlyphColors.Alert else GlyphColors.Accent,
                    )
                    Text(
                        unit, fontFamily = GlyphFonts.Mono, fontSize = 17.sp, color = GlyphColors.TextDim,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(GlyphColors.TextFaint))
                    Text(
                        (state.nextStreet ?: "Manual override").uppercase(),
                        fontFamily = GlyphFonts.Mono, fontSize = 10.5.sp, letterSpacing = 1.4.sp,
                        color = GlyphColors.TextFaint,
                    )
                    if (mode == AnimationMode.FLOWING) {
                        Text("· SWEEP", fontFamily = GlyphFonts.Mono, fontSize = 10.5.sp,
                            letterSpacing = 1.4.sp, color = GlyphColors.Accent)
                    }
                }
            }
        }
    }
}

// ============================================================================
// Controls — manual calibration
// ============================================================================

private val MROWS = listOf(
    Maneuver.KEEP_LEFT, Maneuver.STRAIGHT, Maneuver.KEEP_RIGHT,
    Maneuver.FORWARD_LEFT, Maneuver.UTURN, Maneuver.FORWARD_RIGHT,
    Maneuver.LEFT, Maneuver.ARRIVE, Maneuver.RIGHT,
)

private fun maneuverLabel(m: Maneuver): String = when (m) {
    Maneuver.KEEP_LEFT -> "Keep left"
    Maneuver.STRAIGHT -> "Straight"
    Maneuver.KEEP_RIGHT -> "Keep right"
    Maneuver.FORWARD_LEFT -> "Fork left"
    Maneuver.UTURN -> "U-turn"
    Maneuver.FORWARD_RIGHT -> "Fork right"
    Maneuver.LEFT -> "Turn left"
    Maneuver.ARRIVE -> "Arrive"
    Maneuver.RIGHT -> "Turn right"
    Maneuver.SHARP_LEFT -> "Sharp left"
    Maneuver.SHARP_RIGHT -> "Sharp right"
    Maneuver.ROUNDABOUT -> "Roundabout"
}

private val DIST_TICKS = listOf("Now", "300m", "600m", "1km", "1.5km")

/** Static vs sweeping animation — a real user preference, shown in both builds. */
@Composable
private fun DisplayModeGroup(mode: AnimationMode, onMode: (AnimationMode) -> Unit) {
    Group(label = "LED display mode") {
        Segmented(
            options = listOf(
                AnimationMode.STATIC to "STATIC GLOW",
                AnimationMode.FLOWING to "SWEEPING FLOW",
            ),
            value = mode, onChange = onMode,
        )
    }
}

/** Manual distance override — dev tool. */
@Composable
private fun DistanceGroup(distance: Float, onDistance: (Float) -> Unit) {
    Group(
        label = "Distance to turn",
        right = {
            Text(
                "${distance.roundToInt()}m",
                fontFamily = GlyphFonts.Doto, fontWeight = FontWeight.Black,
                fontSize = 16.sp, color = GlyphColors.Accent,
            )
        },
    ) {
        GlyphSlider(value = distance, min = 0f, max = 1500f, onChange = onDistance)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DIST_TICKS.forEach {
                Text(it, fontFamily = GlyphFonts.Mono, fontSize = 9.5.sp, color = GlyphColors.TextFaint)
            }
        }
    }
}

/** Manual maneuver picker — dev tool. */
@Composable
private fun ManeuverGroup(maneuver: Maneuver, onManeuver: (Maneuver) -> Unit) {
    Group(label = "Maneuver direction") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MROWS.chunked(3).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEach { m ->
                        ManeuverCell(
                            maneuver = m, selected = m == maneuver,
                            onClick = { onManeuver(m) }, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Head + tail brightness — drives both the live preview and the LEDs (the
 * composer reads [GlyphSettings] every frame). Self-contained: holds mirror
 * state for the sliders, writes straight through to [GlyphSettings].
 */
@Composable
private fun Brightness() {
    var head by remember { mutableIntStateOf(GlyphSettings.head) }
    var tail by remember { mutableIntStateOf(GlyphSettings.tail) }

    Group(
        label = "LED brightness",
        right = {
            Text(
                "RESET",
                fontFamily = GlyphFonts.Mono, fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp, letterSpacing = 1.6.sp, color = GlyphColors.TextDim,
                modifier = Modifier.clickable {
                    GlyphSettings.reset()
                    head = GlyphSettings.head
                    tail = GlyphSettings.tail
                },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BrightnessRow("Head", head) { head = it; GlyphSettings.setHead(it) }
            BrightnessRow("Tail", tail) { tail = it; GlyphSettings.setTail(it) }
        }
    }
}

@Composable
private fun BrightnessRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontFamily = GlyphFonts.Grotesk, fontSize = 13.5.sp, color = GlyphColors.TextDim)
            Text(
                "${value * 100 / 255}%",
                fontFamily = GlyphFonts.Doto, fontWeight = FontWeight.Black,
                fontSize = 14.sp, color = GlyphColors.Accent,
            )
        }
        GlyphSlider(value = value.toFloat(), min = 0f, max = 255f) { onChange(it.roundToInt()) }
    }
}

@Composable
private fun ManeuverCell(maneuver: Maneuver, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val frame = remember(maneuver) {
        MatrixComposer.compose(NavState(maneuver, null), 0, AnimationMode.STATIC, 0)
    }
    Column(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(GlyphColors.GlyphRadius.dp))
            .background(if (selected) GlyphColors.Accent.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) GlyphColors.Accent else GlyphColors.Line,
                RoundedCornerShape(GlyphColors.GlyphRadius.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlyphMatrix(
            frame = frame, modifier = Modifier.size(38.dp), applyMask = true,
            glow = selected, onColor = if (selected) GlyphColors.Accent else Color(0xFFD2D2D2),
            intensity = if (selected) 1f else 0.55f,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            maneuverLabel(maneuver), fontFamily = GlyphFonts.Grotesk, fontSize = 10.5.sp,
            color = if (selected) GlyphColors.Accent else GlyphColors.TextDim, maxLines = 1,
        )
    }
}

// ============================================================================
// Route simulation
// ============================================================================

@Composable
private fun RouteSim(
    routeIdx: Int,
    onRoute: (Int) -> Unit,
    running: Boolean,
    stepIdx: Int,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val route = PresetRoutes.all[routeIdx]
    val total = route.steps.size
    val progress = if (total == 0) 0f else ((stepIdx + if (running) 0.5f else 0f) / total).coerceIn(0f, 1f)

    Group(
        label = "Route simulation",
        right = { if (running) StatusDot(label = "RUNNING", color = GlyphColors.Accent, live = true) },
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth().height(46.dp)
                    .clip(RoundedCornerShape(GlyphColors.GlyphRadius.dp))
                    .border(1.dp, GlyphColors.LineStrong, RoundedCornerShape(GlyphColors.GlyphRadius.dp))
                    .clickable { open = !open }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    route.title, fontFamily = GlyphFonts.Grotesk, fontSize = 13.5.sp,
                    color = GlyphColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("▾", color = GlyphColors.TextDim, fontSize = 13.sp)
            }
            DropdownMenu(
                expanded = open, onDismissRequest = { open = false },
                modifier = Modifier.background(GlyphColors.SurfaceSolid),
            ) {
                PresetRoutes.all.forEachIndexed { i, r ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                r.title, fontFamily = GlyphFonts.Grotesk, fontSize = 13.sp,
                                color = if (i == routeIdx) GlyphColors.Accent else GlyphColors.Text,
                            )
                        },
                        onClick = { onRoute(i); open = false },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.12f))) {
            Box(modifier = Modifier.fillMaxWidth(progress).height(2.dp).background(GlyphColors.Accent))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "STEP ${(stepIdx + 1).coerceAtMost(total)} / $total",
            fontFamily = GlyphFonts.Mono, fontSize = 10.5.sp, letterSpacing = 1.sp, color = GlyphColors.TextFaint,
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running) {
                Btn("❚❚ PAUSE", BtnVariant.GHOST, Modifier.weight(1f), onPause)
            } else {
                Btn("▶ START", BtnVariant.PRIMARY, Modifier.weight(1f), onStart)
            }
            Btn("↺ RESET", BtnVariant.QUIET, Modifier.weight(1f), onReset)
        }
    }
}

// ============================================================================
// System footer
// ============================================================================

@Composable
private fun SystemFooter(
    showSync: Boolean,
    live: Boolean,
    onLive: (Boolean) -> Unit,
    onPush: () -> Unit,
    onStop: () -> Unit,
) {
    val ctx = LocalContext.current
    val notif = isNotificationAccessGranted(ctx)

    Group(label = if (showSync) "System" else "Setup") {
        SysRow("Maps notification listener", notif) {
            ctx.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        // Live-push controls are a developer convenience — the product is driven
        // by real Maps notifications, not a manual push.
        if (showSync) {
            HDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Live sync", fontFamily = GlyphFonts.Grotesk, fontSize = 13.5.sp, color = GlyphColors.Text)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (live) "Pushes to the matrix as you tweak" else "Push manually with the button",
                        fontFamily = GlyphFonts.Grotesk, fontSize = 12.sp, color = GlyphColors.TextFaint,
                    )
                }
                GlyphToggle(on = live, onChange = onLive)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("PUSH", BtnVariant.PRIMARY, Modifier.weight(1f), onPush)
                Btn("STOP", BtnVariant.QUIET, Modifier.weight(1f), onStop)
            }
        }
    }
}

/** Quiet bottom-of-screen credit + privacy note. */
@Composable
private fun AppFooter() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "GLYPH MAPS · v${BuildConfig.VERSION_NAME}",
            fontFamily = GlyphFonts.Mono, fontSize = 9.5.sp, letterSpacing = 1.6.sp,
            color = GlyphColors.TextFaint,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Independent app · reads only Google Maps navigation · on-device only",
            fontFamily = GlyphFonts.Grotesk, fontSize = 11.sp, color = GlyphColors.TextFaint,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Dev-only capture log — tails today's parsed-notification file written by the
 * listener (DEV builds only). Lets you watch what the parser is extracting from
 * Google Maps in real time without `adb logcat`.
 */
@Composable
private fun CaptureLogCard() {
    val ctx = LocalContext.current
    val writer = remember { CaptureWriter(ctx) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var summary by remember { mutableStateOf<CaptureWriter.Summary?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            lines = writer.recentParsedLines(40)
            summary = writer.summary()
            delay(1000)
        }
    }

    Group(
        label = "Capture log",
        right = {
            summary?.let {
                Text(
                    "${it.fileCount} files · ${it.totalBytes / 1024}KB",
                    fontFamily = GlyphFonts.Mono, fontSize = 9.5.sp,
                    letterSpacing = 1.sp, color = GlyphColors.TextFaint,
                )
            }
        },
    ) {
        val scroll = rememberScrollState()
        LaunchedEffect(lines.size) { scroll.scrollTo(scroll.maxValue) }
        Box(
            modifier = Modifier
                .fillMaxWidth().height(220.dp)
                .clip(RoundedCornerShape(GlyphColors.GlyphRadius.dp))
                .border(1.dp, GlyphColors.Line, RoundedCornerShape(GlyphColors.GlyphRadius.dp))
                .background(GlyphColors.SurfaceSolid)
                .padding(12.dp),
        ) {
            if (lines.isEmpty()) {
                Text(
                    "No captures yet today.\nNavigate with Google Maps to populate.",
                    fontFamily = GlyphFonts.Mono, fontSize = 11.sp, color = GlyphColors.TextDim,
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(scroll)) {
                    lines.forEach { line ->
                        Text(
                            line, fontFamily = GlyphFonts.Mono, fontSize = 10.sp,
                            color = GlyphColors.TextDim, lineHeight = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SysRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(7.dp).clip(CircleShape)
                .background(if (granted) GlyphColors.Gps else Color.White.copy(alpha = 0.25f)),
        )
        Text(
            label, fontFamily = GlyphFonts.Grotesk, fontSize = 13.5.sp,
            color = if (granted) GlyphColors.Text else GlyphColors.TextDim, modifier = Modifier.weight(1f),
        )
        if (granted) {
            Text("OK", fontFamily = GlyphFonts.Mono, fontSize = 10.sp, letterSpacing = 1.4.sp, color = GlyphColors.TextFaint)
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, GlyphColors.LineStrong, RoundedCornerShape(8.dp))
                    .clickable(onClick = onFix)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text("FIX", fontFamily = GlyphFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
                    letterSpacing = 1.4.sp, color = GlyphColors.Text)
            }
        }
    }
}

// ============================================================================
// Primitives
// ============================================================================

@Composable
private fun HDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(GlyphColors.Line))
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier,
        fontFamily = GlyphFonts.Mono, fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp, letterSpacing = 2.4.sp, color = GlyphColors.TextFaint,
    )
}

@Composable
private fun Group(label: String, right: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel(label)
            right?.invoke()
        }
        content()
    }
}

@Composable
private fun StatusDot(label: String, color: Color, live: Boolean) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "a",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color.copy(alpha = if (live) a else 1f)))
        Text(label, fontFamily = GlyphFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
            letterSpacing = 1.8.sp, color = GlyphColors.TextDim)
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(GlyphColors.GlyphRadius.dp))
            .border(1.dp, GlyphColors.LineStrong, RoundedCornerShape(GlyphColors.GlyphRadius.dp)),
    ) {
        options.forEachIndexed { i, (v, label) ->
            val sel = v == value
            Box(
                modifier = Modifier.weight(1f).height(44.dp)
                    .background(if (sel) GlyphColors.Accent else Color.Transparent)
                    .clickable { onChange(v) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontFamily = GlyphFonts.Mono, fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp, letterSpacing = 1.2.sp,
                    color = if (sel) GlyphColors.AccentText else GlyphColors.TextDim,
                )
            }
            if (i < options.lastIndex) {
                Box(Modifier.width(1.dp).height(44.dp).background(GlyphColors.LineStrong))
            }
        }
    }
}

@Composable
private fun GlyphSlider(value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val frac = ((value - min) / (max - min)).coerceIn(0f, 1f)
    fun emit(x: Float) = onChange((min + (x / widthPx) * (max - min)).coerceIn(min, max))
    Box(
        modifier = Modifier
            .fillMaxWidth().height(32.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { emit(it.x) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> change.consume(); emit(change.position.x) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(32.dp)) {
            val cy = size.height / 2f
            val tw = 2.dp.toPx()
            drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, cy), Offset(size.width, cy), tw, StrokeCap.Round)
            val fx = size.width * frac
            drawLine(GlyphColors.Accent, Offset(0f, cy), Offset(fx, cy), tw, StrokeCap.Round)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(GlyphColors.Accent.copy(alpha = 0.5f), GlyphColors.Accent.copy(alpha = 0f)),
                    center = Offset(fx, cy), radius = 16.dp.toPx(),
                ),
                radius = 16.dp.toPx(), center = Offset(fx, cy),
            )
            drawCircle(GlyphColors.Bg, 9.dp.toPx(), Offset(fx, cy))
            drawCircle(GlyphColors.Accent, 7.dp.toPx(), Offset(fx, cy))
        }
    }
}

@Composable
private fun GlyphToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 50.dp, height = 28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) GlyphColors.Accent else Color.White.copy(alpha = 0.08f))
            .border(1.dp, if (on) GlyphColors.Accent else GlyphColors.LineStrong, RoundedCornerShape(999.dp))
            .clickable { onChange(!on) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = if (on) 23.dp else 2.dp)
                .size(21.dp).clip(CircleShape)
                .background(if (on) GlyphColors.AccentText else Color(0xFFCFCFCF)),
        )
    }
}

private enum class BtnVariant { PRIMARY, GHOST, QUIET }

@Composable
private fun Btn(label: String, variant: BtnVariant, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (variant == BtnVariant.PRIMARY) GlyphColors.Accent else Color.Transparent
    val fg = when (variant) {
        BtnVariant.PRIMARY -> GlyphColors.AccentText
        BtnVariant.GHOST -> GlyphColors.Text
        BtnVariant.QUIET -> GlyphColors.TextDim
    }
    val borderColor = when (variant) {
        BtnVariant.PRIMARY -> GlyphColors.Accent
        BtnVariant.GHOST -> GlyphColors.LineStrong
        BtnVariant.QUIET -> GlyphColors.Line
    }
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(GlyphColors.GlyphRadius.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(GlyphColors.GlyphRadius.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = GlyphFonts.Mono, fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp, letterSpacing = 1.6.sp, color = fg)
    }
}

// ============================================================================
// Service + permission helpers
// ============================================================================

private fun sendToService(ctx: Context, state: NavState) {
    val intent = Intent(ctx, GlyphRenderService::class.java).apply {
        putExtra(GlyphRenderService.EXTRA_MANEUVER, state.maneuver.name)
        state.distanceMeters?.let { putExtra(GlyphRenderService.EXTRA_DISTANCE_M, it) }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
    else ctx.startService(intent)
}

private fun stopService(ctx: Context) {
    ctx.stopService(Intent(ctx, GlyphRenderService::class.java))
}

private fun isNotificationAccessGranted(ctx: Context): Boolean {
    val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
    val ours = "${ctx.packageName}/${MapsNotificationListener::class.java.name}"
    return flat.split(':').any { it == ours }
}
