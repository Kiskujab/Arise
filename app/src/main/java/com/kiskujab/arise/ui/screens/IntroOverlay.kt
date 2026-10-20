package com.kiskujab.arise.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiskujab.arise.R
import com.kiskujab.arise.core.CriterionResult
import com.kiskujab.arise.core.CustomType
import com.kiskujab.arise.core.Keys
import com.kiskujab.arise.core.Rules
import com.kiskujab.arise.core.Scoring
import com.kiskujab.arise.core.Title
import com.kiskujab.arise.data.DayView
import com.kiskujab.arise.ui.Intro
import com.kiskujab.arise.ui.MainViewModel
import com.kiskujab.arise.ui.components.AriseLogo
import com.kiskujab.arise.ui.components.CountUpText
import com.kiskujab.arise.ui.components.GlowText
import com.kiskujab.arise.ui.components.LightRays
import com.kiskujab.arise.ui.components.RankEmblem
import com.kiskujab.arise.ui.components.Scanlines
import com.kiskujab.arise.ui.components.Shockwave
import com.kiskujab.arise.ui.components.SparkFountain
import com.kiskujab.arise.ui.components.SystemBar
import com.kiskujab.arise.ui.components.SystemButton
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.components.TypewriterText
import com.kiskujab.arise.ui.components.fmt
import com.kiskujab.arise.ui.components.reveal
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * The entry sequence: the System connects, the gate opens and the hunter's level, rank and records
 * are revealed. On the first open of the day the Daily Quest then "arrives" and has to be accepted.
 */
@Composable
fun IntroOverlay(vm: MainViewModel, kind: Intro) {
    val player by vm.player.collectAsStateWithLifecycle()
    val state by vm.playerState.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val trials by vm.trials.collectAsStateWithLifecycle()
    val p = player ?: return
    var stage by remember { mutableIntStateOf(0) } // 0 = profile, 1 = daily quest
    val fade = remember { Animatable(1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun finish() {
        scope.launch {
            fade.animateTo(0f, tween(320))
            vm.dismissIntro()
        }
    }

    fun advance() {
        if (stage == 0 && kind == Intro.DAILY) stage = 1 else if (stage == 0) finish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Color(0xF204060C))
            .clickable(remember { MutableInteractionSource() }, indication = null) { advance() },
    ) {
        Scanlines(Modifier.fillMaxSize())
        if (stage == 0) {
            ProfileReveal(
                name = p.name,
                title = runCatching { Title.valueOf(p.activeTitle) }.getOrDefault(Title.PLAYER).displayName,
                state = state,
                daily = kind == Intro.DAILY,
                onDone = { advance() },
            )
        } else {
            val t = today
            if (t != null) DailyArrival(t, trials, onAccept = { finish() }) else LaunchedEffect(Unit) { finish() }
        }
        if (stage == 0) {
            Text(
                "TAP TO SKIP",
                style = AriseType.label.copy(color = AriseColors.TextFaint),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun ProfileReveal(
    name: String,
    title: String,
    state: com.kiskujab.arise.core.PlayerState,
    daily: Boolean,
    onDone: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val gate = remember { Animatable(0f) }
    val logo = remember { Animatable(0f) }
    val logoFlash = remember { Animatable(0f) }
    val logoShock = remember { Animatable(0f) }
    val emblemScale = remember { Animatable(0f) }
    val shock = remember { Animatable(0f) }
    var showName by remember { mutableStateOf(false) }
    var showRank by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // The emblem's pieces fly in from every side and lock together.
        launch { gate.animateTo(1f, tween(1250, easing = FastOutSlowInEasing)) }
        launch {
            for (i in 0 until 5) {
                delay(190)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        logo.animateTo(1f, tween(1250, easing = LinearEasing))
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { logoFlash.snapTo(1f); logoFlash.animateTo(0f, tween(700)) }
        launch { logoShock.animateTo(1f, tween(800)) }
        delay(250)
        showName = true
        delay(550)
        showRank = true
        emblemScale.snapTo(2.6f)
        launch { emblemScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 320f)) }
        delay(160)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { shock.animateTo(1f, tween(700)) }
        delay(500)
        showStats = true
        delay(if (daily) 1700 else 1500)
        onDone()
    }

    val level = state.level
    val rank = state.rank
    val rankColor = Color(rank.color)
    val cur = Rules.xpForLevel(level)
    val next = Rules.xpForLevel(level + 1)
    val progress = if (level >= Rules.MAX_LEVEL) 1f else ((state.xp - cur).toFloat() / (next - cur).coerceAtLeast(1))
    val hour = LocalDateTime.now().hour
    val greeting = when {
        !daily -> "WELCOME BACK"
        hour < 5 -> "THE NIGHT IS STILL YOURS"
        hour < 12 -> "A NEW DAY BEGINS"
        hour < 18 -> "THE DAY AWAITS"
        else -> "THE DAY IS NOT OVER"
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TypewriterText("[SYSTEM] Player identified.", AriseType.label.copy(color = AriseColors.Blue), charMillis = 18)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Shockwave(logoShock.value, AriseColors.Cyan, Modifier.size(300.dp))
            Canvas(Modifier.fillMaxSize()) {
                val sw = 3.dp.toPx()
                val sweep = 360f * gate.value
                drawArc(
                    Brush.sweepGradient(listOf(AriseColors.Cyan, AriseColors.BlueDeep, AriseColors.Purple, AriseColors.Cyan)),
                    -90f, sweep, false, style = Stroke(sw, cap = StrokeCap.Round),
                    topLeft = Offset(sw, sw), size = Size(size.width - 2 * sw, size.height - 2 * sw),
                )
                drawCircle(
                    Brush.radialGradient(listOf(AriseColors.BlueDeep.copy(alpha = 0.35f * gate.value), Color.Transparent)),
                    size.minDimension / 2,
                )
            }
            AriseLogo(
                logo.value, Modifier.size(170.dp).graphicsLayer {
                    val s = 1f + 0.08f * logoFlash.value
                    scaleX = s; scaleY = s
                },
                flash = logoFlash.value,
            )
        }
        Spacer(Modifier.height(14.dp))
        if (showName) {
            TypewriterText(greeting, AriseType.label.copy(color = AriseColors.TextDim, letterSpacing = 4.sp), charMillis = 22, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            TypewriterText(name.uppercase(), AriseType.title.copy(fontSize = 26.sp, letterSpacing = 4.sp), startDelay = 150, charMillis = 45, textAlign = TextAlign.Center)
            Spacer(Modifier.height(2.dp))
            TypewriterText("〈 $title 〉", AriseType.small.copy(color = AriseColors.Gold), startDelay = 350, glow = AriseColors.Gold, textAlign = TextAlign.Center)
        } else {
            Spacer(Modifier.height(72.dp))
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
            if (showRank) {
                Shockwave(shock.value, rankColor, Modifier.size(260.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RankEmblem(rank, 116.dp, Modifier.graphicsLayer { scaleX = emblemScale.value; scaleY = emblemScale.value })
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text("LEVEL", style = AriseType.header)
                        CountUpText(level.toLong(), AriseType.display.copy(fontSize = 54.sp), durationMillis = 850)
                        Text(rank.title.uppercase(), style = AriseType.label.copy(color = rankColor))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        AnimatedVisibility(showStats, enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SystemBar(progress, height = 8.dp)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (level >= Rules.MAX_LEVEL) "MAX LEVEL" else "${state.xp - cur} / ${next - cur} EXP to level ${level + 1}",
                    style = AriseType.small,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("STREAK", state.streak.toLong(), "days", AriseColors.Cyan, 0)
                    StatTile("GOLD", state.gold, "G", AriseColors.Gold, 1)
                    StatTile("CLEARS", state.counters.questClears.toLong(), "quests", AriseColors.Purple, 2)
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Long, unit: String, color: Color, index: Int) {
    SystemPanel(Modifier.width(96.dp).reveal(index, 110), accent = color) {
        Text(label, style = AriseType.label.copy(color = color, fontSize = 11.sp), modifier = Modifier.align(Alignment.CenterHorizontally))
        CountUpText(value, AriseType.number, Modifier.align(Alignment.CenterHorizontally), durationMillis = 700, startDelay = index * 110L, glow = color)
        Text(unit, style = AriseType.small.copy(fontSize = 11.sp), modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/** "[Daily Quest has arrived]" — the quest list types in line by line and has to be accepted. */
@Composable
private fun DailyArrival(t: DayView, trials: List<com.kiskujab.arise.data.TrialCard>, onAccept: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        enter.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 260f))
    }
    val accent = if (t.penalty != null) AriseColors.Red else AriseColors.Blue
    val bang = rememberInfiniteTransition(label = "bang")
    val pulse by bang.animateFloat(0.4f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    val quests = t.score.results.filter { it.mandatory }

    Box(Modifier.fillMaxSize()) {
        LightRays(accent, Modifier.fillMaxSize(), alpha = 0.5f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .graphicsLayer {
                    scaleX = 0.8f + 0.2f * enter.value
                    scaleY = 0.8f + 0.2f * enter.value
                    alpha = enter.value.coerceIn(0f, 1f)
                },
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(24.dp))
            SystemPanel(title = "QUEST INFO", accent = accent) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    GlowText("!", AriseType.display.copy(fontSize = 30.sp), color = accent, glow = accent, modifier = Modifier.graphicsLayer { alpha = pulse })
                    Spacer(Modifier.width(12.dp))
                    TypewriterText("Daily Quest has arrived.", AriseType.title, charMillis = 30)
                }
                Spacer(Modifier.height(10.dp))
                GlowText("[ Preparations to Become Powerful ]", AriseType.bodyBold.copy(color = AriseColors.Cyan), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                quests.forEachIndexed { i, r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).reveal(i + 3, 120), verticalAlignment = Alignment.CenterVertically) {
                        Text("▸", style = AriseType.bodyBold.copy(color = accent), modifier = Modifier.width(18.dp))
                        Text(r.label, style = AriseType.bodyBold, modifier = Modifier.weight(1f))
                        Text(questTarget(r, t), style = AriseType.body.copy(color = if (r.met) AriseColors.Cyan else AriseColors.TextDim))
                    }
                }
                t.penalty?.let { pq ->
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.reveal(quests.size + 4, 120)) {
                        GlowText("PENALTY QUEST", AriseType.header.copy(color = AriseColors.Red), glow = AriseColors.Red)
                        pq.describe(t.customs).forEach { Text("! $it", style = AriseType.small.copy(color = AriseColors.Text)) }
                    }
                }
                if (trials.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.reveal(quests.size + 5, 120)) {
                        Text("ACTIVE TRIALS", style = AriseType.label)
                        trials.forEach { c ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text("${c.trial.span.label} · ${c.trial.kind.title}", style = AriseType.small.copy(color = AriseColors.Gold), modifier = Modifier.weight(1f))
                                Text(c.progress.text, style = AriseType.small)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "WARNING: Failure to complete the Daily Quest will result in an appropriate penalty.",
                    style = AriseType.small.copy(color = AriseColors.Red),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().reveal(quests.size + 6, 120),
                )
                Spacer(Modifier.height(16.dp))
                SystemButton("ACCEPT", onClick = onAccept, accent = accent, modifier = Modifier.align(Alignment.CenterHorizontally).reveal(quests.size + 7, 120))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The target of a quest as one short line. */
fun questTarget(r: CriterionResult, t: DayView): String {
    val g = t.goals
    return when (r.key) {
        Keys.KCAL -> "≤ ${g.kcal} kcal"
        Keys.PROTEIN -> "≥ ${g.protein} g"
        Keys.MOVEMENT -> "${g.steps} steps"
        Keys.SCREEN -> "≤ ${hm(g.screenMinutes)}"
        Keys.DISTRACTION -> "≤ ${g.distractionPct}%"
        Keys.BANDS -> "${fmt(g.bandHours)} h"
        Keys.BRUSHING -> "${g.brushCount}×"
        Keys.SKINCARE -> "tonight"
        else -> {
            val c = t.customs.find { Keys.custom(it.id) == r.key } ?: return ""
            val target = Scoring.effectiveCustomTarget(c, t.vacation, t.penalty)
            when (c.type) {
                CustomType.CHECK -> "done"
                CustomType.AT_LEAST -> "≥ ${fmt(target)} ${c.unit}".trim()
                CustomType.AT_MOST -> "≤ ${fmt(target)} ${c.unit}".trim()
            }
        }
    }
}

/** Full-screen burst when the last quest of the day is completed. */
@Composable
fun QuestCompleteBurst(onDone: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scale = remember { Animatable(3f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { alpha.animateTo(1f, tween(200)) }
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
        delay(1800)
        alpha.animateTo(0f, tween(400))
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(Color(0xCC000000))
            .clickable(remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        LightRays(AriseColors.Cyan, Modifier.fillMaxSize())
        SparkFountain(AriseColors.Cyan, Modifier.fillMaxSize(), intensity = alpha.value, origin = 0.5f)
        Shockwave(((3f - scale.value) / 2f).coerceIn(0f, 0.999f), AriseColors.Cyan, Modifier.size(360.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            GlowText(
                "QUEST\nCOMPLETE",
                AriseType.display.copy(fontSize = 44.sp),
                glow = AriseColors.Cyan,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
            )
            Spacer(Modifier.height(12.dp))
            GlowText("The Daily Quest has been cleared.", AriseType.bodyBold, glow = AriseColors.Cyan)
            Text("Rewards are granted when the day is sealed.", style = AriseType.small)
        }
    }
}
