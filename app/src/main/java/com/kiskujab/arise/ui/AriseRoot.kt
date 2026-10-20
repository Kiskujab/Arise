package com.kiskujab.arise.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.kiskujab.arise.ui.components.Starfield
import com.kiskujab.arise.ui.screens.ItemActivation
import com.kiskujab.arise.ui.screens.PurchaseReveal
import com.kiskujab.arise.ui.screens.StreakBanner
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiskujab.arise.data.TimeStatus
import com.kiskujab.arise.ui.components.GlowText
import com.kiskujab.arise.ui.components.ParticleBackground
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.screens.AwakeningScreen
import com.kiskujab.arise.ui.screens.EventOverlay
import com.kiskujab.arise.ui.screens.GearScreen
import com.kiskujab.arise.ui.screens.IntroOverlay
import com.kiskujab.arise.ui.screens.QuestCompleteBurst
import com.kiskujab.arise.ui.screens.LogScreen
import com.kiskujab.arise.ui.screens.QuestScreen
import com.kiskujab.arise.ui.screens.SettingsScreen
import com.kiskujab.arise.ui.screens.StatusScreen
import com.kiskujab.arise.ui.screens.UpdateBanner
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class Tab(val label: String, val icon: ImageVector) {
    STATUS("STATUS", Icons.Filled.Person),
    QUESTS("QUESTS", Icons.Filled.List),
    ITEMS("GEAR", Icons.Filled.Star),
    LOG("LOG", Icons.Filled.DateRange),
    SETTINGS("SYSTEM", Icons.Filled.Settings),
}

@Composable
fun AriseRoot(vm: MainViewModel) {
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val timeStatus by vm.timeStatus.collectAsStateWithLifecycle()
    val intro by vm.intro.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val unseen by vm.unseenEvents.collectAsStateWithLifecycle()
    val streakBurst by vm.streakBurst.collectAsStateWithLifecycle()
    val itemFx by vm.itemFx.collectAsStateWithLifecycle()
    val purchaseFx by vm.purchaseFx.collectAsStateWithLifecycle()
    var celebrate by remember { mutableStateOf(false) }
    var lastCleared by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    // Celebrate the moment the last quest of the day is completed.
    LaunchedEffect(today?.epochDay, today?.score?.questCleared) {
        val t = today ?: return@LaunchedEffect
        val prev = lastCleared
        if (prev != null && prev.first == t.epochDay && !prev.second && t.score.questCleared) celebrate = true
        lastCleared = t.epochDay to t.score.questCleared
    }

    Box(Modifier.fillMaxSize().background(AriseColors.Bg)) {
        Starfield(Modifier.fillMaxSize())
        ParticleBackground()
        val status = timeStatus
        when {
            !loaded -> Unit
            status is TimeStatus.Anomaly -> AnomalyScreen(status.lastSeen)
            player == null -> AwakeningScreen(onAwaken = vm::awaken)
            else -> {
                MainScaffold(vm)
                val kind = intro
                if (kind != null) {
                    IntroOverlay(vm, kind)
                } else {
                    val item = itemFx
                    val bought = purchaseFx
                    when {
                        item != null -> ItemActivation(item, onDone = vm::dismissItemFx)
                        bought != null -> PurchaseReveal(bought, onDone = vm::dismissPurchaseFx)
                        else -> {
                            EventOverlay(vm)
                            if (celebrate && unseen.isEmpty()) QuestCompleteBurst { celebrate = false }
                        }
                    }
                    val burst = streakBurst
                    if (burst != null && unseen.isEmpty() && item == null && bought == null) {
                        StreakBanner(burst, onDone = vm::dismissStreakBurst)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp)) { data ->
            SystemPanel(Modifier.padding(horizontal = 16.dp)) {
                Text(data.visuals.message, style = AriseType.bodyBold)
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun MainScaffold(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.STATUS) }
    var direction by remember { mutableIntStateOf(1) }
    val warp = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    fun go(t: Tab) {
        if (t == tab) return
        direction = if (t.ordinal > tab.ordinal) 1 else -1
        tab = t
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            warp.snapTo(0f)
            warp.animateTo(1f, tween(TAB_MILLIS + 150, easing = FastOutSlowInEasing))
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        UpdateBanner(vm)
        Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
            AnimatedContent(
                tab,
                transitionSpec = {
                    fadeIn(tween(TAB_MILLIS - 150, delayMillis = 150)) togetherWith fadeOut(tween(TAB_MILLIS - 200))
                },
                label = "tab",
            ) { t ->
                // -1 = leaving, 0 = shown, 1 = arriving. The screens swing like faces of a cube.
                val p by transition.animateFloat(
                    transitionSpec = { tween(TAB_MILLIS, easing = FastOutSlowInEasing) },
                    label = "swing",
                ) { s ->
                    when (s) {
                        EnterExitState.PreEnter -> 1f
                        EnterExitState.Visible -> 0f
                        EnterExitState.PostExit -> -1f
                    }
                }
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val d = direction
                        val x = p * d
                        translationX = x * size.width * 0.28f
                        rotationY = -x * 32f
                        val sc = 1f - abs(p) * 0.14f
                        scaleX = sc
                        scaleY = sc
                        cameraDistance = 9f * density
                        transformOrigin = TransformOrigin(if (x > 0f) 0f else 1f, 0.5f)
                    },
                ) {
                    when (t) {
                        Tab.STATUS -> StatusScreen(vm, onOpenQuests = { go(Tab.QUESTS) })
                        Tab.QUESTS -> QuestScreen(vm)
                        Tab.ITEMS -> GearScreen(vm)
                        Tab.LOG -> LogScreen(vm)
                        Tab.SETTINGS -> SettingsScreen(vm)
                    }
                }
            }
            WarpSweep(warp.value, direction, Modifier.fillMaxSize())
        }
        BottomBar(tab, ::go)
    }
}

private const val TAB_MILLIS = 620

/** A beam of light that crosses the screen during a tab change, dragging scanlines with it. */
@Composable
private fun WarpSweep(progress: Float, direction: Int, modifier: Modifier) {
    if (progress <= 0f || progress >= 1f) return
    Canvas(modifier) {
        val e = progress
        val x = if (direction > 0) size.width * (1.1f - 1.2f * e) else size.width * (-0.1f + 1.2f * e)
        val band = 70.dp.toPx()
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, AriseColors.Cyan.copy(alpha = 0.22f * (1f - e)), Color.White.copy(alpha = 0.35f * (1f - e)), AriseColors.Blue.copy(alpha = 0.18f * (1f - e)), Color.Transparent), x - band, x + band),
            Offset(x - band, 0f), Size(band * 2, size.height),
        )
        drawLine(Color.White.copy(alpha = 0.8f * (1f - e)), Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
        val step = 6.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(AriseColors.Cyan.copy(alpha = 0.06f * (1f - e)), Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val slot by animateFloatAsState(selected.ordinal.toFloat(), spring(dampingRatio = 0.62f, stiffness = 260f), label = "slot")
    val ring = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        ring.snapTo(0f)
        ring.animateTo(1f, tween(650))
    }
    val idle = rememberInfiniteTransition(label = "bar")
    val pulse by idle.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse")
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, AriseColors.Bg, AriseColors.Bg)))
            .navigationBarsPadding(),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val n = Tab.entries.size
            val w = size.width / n
            val cx = w * (slot + 0.5f)
            val top = 4.dp.toPx()
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, AriseColors.Blue.copy(alpha = 0.35f), Color.Transparent)),
                Offset(0f, top), Offset(size.width, top), 1.dp.toPx(),
            )
            drawCircle(Brush.radialGradient(listOf(AriseColors.Cyan.copy(alpha = 0.28f * pulse), Color.Transparent), Offset(cx, size.height * 0.42f), w * 0.55f), w * 0.55f, Offset(cx, size.height * 0.42f))
            drawLine(AriseColors.Cyan, Offset(cx - 18.dp.toPx(), top), Offset(cx + 18.dp.toPx(), top), 2.5.dp.toPx(), StrokeCap.Round)
            drawLine(AriseColors.Cyan.copy(alpha = 0.3f), Offset(cx - 26.dp.toPx(), top), Offset(cx + 26.dp.toPx(), top), 7.dp.toPx(), StrokeCap.Round)
            val r = ring.value
            if (r < 1f) {
                drawCircle(AriseColors.Cyan.copy(alpha = 1f - r), 14.dp.toPx() + 26.dp.toPx() * r, Offset(cx, size.height * 0.42f), style = Stroke(1.5.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 6.dp)) {
            for (t in Tab.entries) {
                val active = t == selected
                val c = if (active) AriseColors.Cyan else AriseColors.TextFaint
                val lift by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.4f, stiffness = 320f), label = "lift")
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(t) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(4.dp))
                    Icon(
                        t.icon, contentDescription = t.label, tint = c,
                        modifier = Modifier.size(22.dp).graphicsLayer {
                            val sc = 1f + 0.22f * lift
                            scaleX = sc; scaleY = sc
                            translationY = -3.dp.toPx() * lift
                        },
                    )
                    Spacer(Modifier.height(2.dp))
                    if (active) {
                        GlowText(t.label, AriseType.header.copy(fontSize = AriseType.header.fontSize * 0.7f), color = c)
                    } else {
                        Text(t.label, style = AriseType.header.copy(fontSize = AriseType.header.fontSize * 0.7f, color = c))
                    }
                }
            }
        }
    }
}

@Composable
private fun AnomalyScreen(lastSeen: Long) {
    val time = Instant.ofEpochMilli(lastSeen).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        SystemPanel(title = "WARNING", accent = AriseColors.Red) {
            GlowText("TIME ANOMALY DETECTED", AriseType.title, color = AriseColors.Red, glow = AriseColors.Red, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "The device clock is earlier than a moment the System has already witnessed ($time).\n\n" +
                    "Time cannot be turned back. Restore the correct date and time to continue.",
                style = AriseType.body,
                textAlign = TextAlign.Center,
            )
        }
    }
}
