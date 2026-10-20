package com.kiskujab.arise.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiskujab.arise.core.Equipment
import com.kiskujab.arise.core.EventType
import com.kiskujab.arise.core.ItemType
import com.kiskujab.arise.core.Rank
import com.kiskujab.arise.core.Rules
import com.kiskujab.arise.core.Slots
import com.kiskujab.arise.data.EventEntity
import com.kiskujab.arise.data.ItemUse
import com.kiskujab.arise.ui.components.ChromaticText
import com.kiskujab.arise.ui.components.GlowText
import com.kiskujab.arise.ui.components.HexSigil
import com.kiskujab.arise.ui.components.Implosion
import com.kiskujab.arise.ui.components.LightRays
import com.kiskujab.arise.ui.components.MonarchFlame
import com.kiskujab.arise.ui.components.RankEmblem
import com.kiskujab.arise.ui.components.RollingNumber
import com.kiskujab.arise.ui.components.Scanlines
import com.kiskujab.arise.ui.components.Shatter
import com.kiskujab.arise.ui.components.Shockwave
import com.kiskujab.arise.ui.components.SparkFountain
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.components.TypewriterText
import com.kiskujab.arise.ui.components.flameColors
import com.kiskujab.arise.ui.components.reveal
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class Ascent { LEVEL_UP, RANK_UP, LEVEL_DOWN, RANK_DOWN }

/**
 * Level and rank changes, as a short cinematic: energy rushes in, the screen flashes and shakes,
 * the title slams down through a chromatic glitch and the level counter rolls over. Losses get a
 * red, glitching version instead — clear, but not a celebration.
 */
@Composable
fun LevelSequence(event: EventEntity, remaining: Int, onDone: () -> Unit) {
    val kind = when (runCatching { EventType.valueOf(event.type) }.getOrNull()) {
        EventType.RANK_UP -> Ascent.RANK_UP
        EventType.LEVEL_DOWN -> Ascent.LEVEL_DOWN
        EventType.RANK_DOWN -> Ascent.RANK_DOWN
        else -> Ascent.LEVEL_UP
    }
    val up = kind == Ascent.LEVEL_UP || kind == Ascent.RANK_UP
    val isRank = kind == Ascent.RANK_UP || kind == Ascent.RANK_DOWN
    val level = Regex("""level (\d+)""").find(event.message)?.groupValues?.get(1)?.toIntOrNull()
    val rank = Rank.entries.firstOrNull { event.message.endsWith(" ${it.title}.") }
    val previousRank = rank?.let { r -> Rank.entries.getOrNull(r.ordinal + if (up) -1 else 1) }
    val accent = when {
        !up -> AriseColors.Red
        kind == Ascent.RANK_UP && rank != null -> Color(rank.color)
        else -> AriseColors.Cyan
    }

    val haptics = LocalHapticFeedback.current
    val implode = remember(event.id) { Animatable(0f) }
    val flash = remember(event.id) { Animatable(0f) }
    val shake = remember(event.id) { Animatable(1f) }
    val shockA = remember(event.id) { Animatable(0f) }
    val shockB = remember(event.id) { Animatable(0f) }
    val shockC = remember(event.id) { Animatable(0f) }
    val sigil = remember(event.id) { Animatable(0f) }
    val slam = remember(event.id) { Animatable(0f) }
    val split = remember(event.id) { Animatable(16f) }
    val shards = remember(event.id) { Animatable(0f) }
    val descend = remember(event.id) { Animatable(0f) }
    val glitch = remember(event.id) { Animatable(0f) }
    val numberPop = remember(event.id) { Animatable(1f) }
    var shownLevel by remember(event.id) { mutableIntStateOf(level?.let { if (up) it - 1 else it + 1 } ?: 0) }
    var phase by remember(event.id) { mutableIntStateOf(0) }

    LaunchedEffect(event.id) {
        if (up) {
            launch {
                for (i in 0 until 7) {
                    delay(160L - i * 12L)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            implode.animateTo(1f, tween(if (isRank) 1200 else 950, easing = LinearEasing))
            phase = 1
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            launch { flash.snapTo(1f); flash.animateTo(0f, tween(600)) }
            launch { shake.snapTo(0f); shake.animateTo(1f, tween(700)) }
            launch { shockA.animateTo(1f, tween(1000, easing = LinearOutSlowInEasing)) }
            launch { delay(140); shockB.animateTo(1f, tween(1000, easing = LinearOutSlowInEasing)) }
            launch { delay(280); shockC.animateTo(1f, tween(1100, easing = LinearOutSlowInEasing)) }
            launch { sigil.animateTo(1f, tween(1600, easing = FastOutSlowInEasing)) }
            launch { split.animateTo(0f, tween(1000, easing = FastOutSlowInEasing)) }
            if (isRank) launch { shards.animateTo(1f, tween(1000)) }
            slam.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 240f))
            if (isRank) {
                descend.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                launch { shake.snapTo(0.3f); shake.animateTo(1f, tween(500)) }
                launch { flash.snapTo(0.6f); flash.animateTo(0f, tween(400)) }
            }
            delay(200)
            if (level != null) {
                shownLevel = level
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                numberPop.snapTo(1.6f)
                numberPop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 320f))
            }
            delay(300)
            phase = 2
        } else {
            phase = 1
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            launch { glitch.snapTo(1f); glitch.animateTo(0f, tween(1400)) }
            launch { shake.snapTo(0f); shake.animateTo(1f, tween(900)) }
            launch { sigil.animateTo(1f, tween(1200)) }
            if (isRank) launch { shards.animateTo(1f, tween(1100)) }
            slam.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 320f))
            launch { split.animateTo(0f, tween(1200)) }
            delay(500)
            if (level != null) {
                shownLevel = level
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            descend.animateTo(1f, tween(500))
            delay(300)
            phase = 2
        }
    }

    val idle = rememberInfiniteTransition(label = "levelIdle")
    val blink by idle.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink")

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2020408))
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = phase == 2, onClick = onDone)
            .graphicsLayer {
                val s = shake.value
                val amp = (if (up) 16f else 11f) * (1f - s)
                translationX = sin(s * 61f) * amp * density
                translationY = cos(s * 47f) * amp * 0.7f * density
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val tint = if (phase >= 1) 1f else implode.value * 0.5f
            drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.32f * tint), Color.Transparent), Offset(size.width / 2, size.height * 0.42f), size.maxDimension * 0.7f))
        }
        if (phase >= 1) {
            if (up) LightRays(accent, Modifier.fillMaxSize(), alpha = slam.value.coerceIn(0f, 1f))
            Scanlines(Modifier.fillMaxSize(), color = accent)
            if (up) SparkFountain(accent, Modifier.fillMaxSize(), intensity = slam.value.coerceIn(0f, 1f), origin = 0.48f)
        }
        Implosion(implode.value, accent, Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.Center).padding(bottom = 80.dp).size(340.dp)) {
            HexSigil(sigil.value, accent.copy(alpha = if (up) 0.9f else 0.6f), Modifier.fillMaxSize())
            Shockwave(shockA.value, accent, Modifier.fillMaxSize())
            Shockwave(shockB.value, Color.White, Modifier.fillMaxSize())
            Shockwave(shockC.value, accent, Modifier.fillMaxSize())
        }
        if (!up) GlitchBars(glitch.value, Modifier.fillMaxSize())

        Column(
            Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isRank && rank != null) {
                Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                    if (previousRank != null && shards.value == 0f) RankEmblem(previousRank, 120.dp)
                    Shatter(shards.value, previousRank?.let { Color(it.color) } ?: accent, Modifier.fillMaxSize())
                    if (descend.value > 0f) {
                        if (up) {
                            // Pillar of light the new emblem rides down on.
                            Canvas(Modifier.fillMaxSize()) {
                                val a = (1f - descend.value) * 0.8f + 0.15f
                                drawRect(
                                    Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = a), Color.White.copy(alpha = a), accent.copy(alpha = a), Color.Transparent), size.width * 0.3f, size.width * 0.7f),
                                    Offset(size.width * 0.3f, -size.height * 3f), Size(size.width * 0.4f, size.height * 3.6f),
                                )
                            }
                        }
                        RankEmblem(
                            rank, 130.dp,
                            Modifier.graphicsLayer {
                                val d = descend.value
                                translationY = (1f - d) * (if (up) -420f else 60f) * density
                                alpha = d.coerceIn(0f, 1f)
                                val s = if (up) 1f + (1f - d) * 0.6f else 0.9f + 0.1f * d
                                scaleX = s; scaleY = s
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            val title = when (kind) {
                Ascent.LEVEL_UP -> "LEVEL UP"
                Ascent.RANK_UP -> "RANK UP"
                Ascent.LEVEL_DOWN -> "LEVEL DOWN"
                Ascent.RANK_DOWN -> "RANK LOST"
            }
            val glitchJitter = if (!up && glitch.value > 0.05f) (Random((glitch.value * 30).toInt()).nextFloat() - 0.5f) * 30f * glitch.value else 0f
            ChromaticText(
                title,
                AriseType.display.copy(fontSize = 46.sp, letterSpacing = 3.sp),
                split.value + glitchJitter,
                glow = accent,
                modifier = Modifier.graphicsLayer {
                    val p = slam.value
                    val s = if (up) 2.8f - 1.8f * p else 1.25f - 0.25f * p
                    scaleX = s; scaleY = s
                    alpha = (p * 2f).coerceIn(0f, 1f)
                },
            )
            if (level != null && !isRank) {
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer {
                        scaleX = numberPop.value; scaleY = numberPop.value
                        alpha = slam.value.coerceIn(0f, 1f)
                    },
                ) {
                    GlowText("LV ", AriseType.title.copy(color = AriseColors.TextDim), glow = accent)
                    RollingNumber(shownLevel.toLong(), AriseType.display.copy(fontSize = 72.sp), glow = accent)
                }
            }
            if (isRank && rank != null) {
                Spacer(Modifier.height(6.dp))
                GlowText(rank.title.uppercase(), AriseType.title.copy(color = accent), glow = accent, modifier = Modifier.graphicsLayer { alpha = descend.value.coerceIn(0f, 1f) })
            }
            Spacer(Modifier.height(20.dp))
            if (phase == 2) {
                val lines = buildList {
                    when (kind) {
                        Ascent.LEVEL_UP -> {
                            add(event.message to AriseColors.Text)
                            if (level != null && level % Rules.SAFE_ZONE_STEP == 0) add("SAFE ZONE SECURED — you can no longer fall below level $level." to AriseColors.Gold)
                            if (level != null) Rank.entries.firstOrNull { it.minLevel > level }?.let {
                                val gap = it.minLevel - level
                                add("Rank ${it.label} in $gap level${if (gap == 1) "" else "s"}." to AriseColors.TextDim)
                            }
                        }
                        Ascent.RANK_UP -> {
                            add(event.message to AriseColors.Text)
                            if (rank != null && Slots.itemSlots(rank.minLevel) > Slots.itemSlots(rank.minLevel - 1)) {
                                add("+1 equipment slot unlocked." to AriseColors.Purple)
                            }
                            add("The System acknowledges your growth." to AriseColors.TextDim)
                        }
                        else -> {
                            add(event.message to AriseColors.Text)
                            add("Correct course. Every lost level can be won back." to AriseColors.TextDim)
                        }
                    }
                }
                lines.forEachIndexed { i, (text, color) ->
                    Text(text, style = AriseType.bodyBold.copy(color = color), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().reveal(i, 160))
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    if (remaining > 1) "TAP TO CONTINUE · ${remaining - 1} MORE" else "TAP TO CONTINUE",
                    style = AriseType.label.copy(color = accent.copy(alpha = blink)),
                )
            }
        }
        if (flash.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
    }
}

/** Horizontal tears across the screen, for losses. */
@Composable
private fun GlitchBars(amount: Float, modifier: Modifier) {
    if (amount <= 0.01f) return
    Canvas(modifier) {
        val r = Random((amount * 36).toInt())
        repeat(10) {
            val y = r.nextFloat() * size.height
            val h = (3f + r.nextFloat() * 26f) * density
            val dx = (r.nextFloat() - 0.5f) * 90f * density * amount
            drawRect(AriseColors.Red.copy(alpha = 0.22f * amount), Offset(dx, y), Size(size.width, h))
            drawRect(AriseColors.Cyan.copy(alpha = 0.12f * amount), Offset(-dx, y + h * 0.3f), Size(size.width, h * 0.4f))
        }
    }
}

// ------------------------------------------------------------------ login streak

/** Drops in from the top when today's first action lights the flame. Doesn't block the screen. */
@Composable
fun StreakBanner(streak: Int, onDone: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val drop = remember(streak) { Animatable(0f) }
    val heat = remember(streak) { Animatable(0.1f) }
    var shown by remember(streak) { mutableIntStateOf((streak - 1).coerceAtLeast(0)) }
    LaunchedEffect(streak) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { drop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
        heat.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        shown = streak
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        delay(2600)
        drop.animateTo(0f, tween(350))
        onDone()
    }
    val (outer, inner) = flameColors(streak)
    val milestone = streak in setOf(3, 7, 14, 21, 30, 50, 75, 100, 150, 200, 250, 300, 365, 500, 730, 1000)
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer {
                translationY = (1f - drop.value) * -220f * density
                alpha = drop.value.coerceIn(0f, 1f)
            }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDone),
    ) {
        SystemPanel(accent = inner) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp)) {
                    MonarchFlame(streak, heat.value, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (milestone) "STREAK MILESTONE" else "LOGIN STREAK", style = AriseType.label.copy(color = inner))
                    Row(verticalAlignment = Alignment.Bottom) {
                        RollingNumber(shown.toLong(), AriseType.display.copy(fontSize = 34.sp), glow = outer)
                        Text(if (shown == 1) "  DAY" else "  DAYS", style = AriseType.header.copy(color = AriseColors.Text), modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Text(
                        when {
                            streak == 1 -> "The flame is lit. Come back tomorrow to feed it."
                            milestone -> "$streak days in a row. The System is impressed."
                            else -> "The flame grows stronger. Keep showing up."
                        },
                        style = AriseType.small,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ items and gear

/** Shared stage for item and gear reveals: dark backdrop, rays, magic circle and tap-to-close. */
@Composable
private fun RevealStage(color: Color, ready: Boolean, flash: Float, onDone: () -> Unit, content: @Composable () -> Unit) {
    val idle = rememberInfiniteTransition(label = "stage")
    val blink by idle.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF0020408))
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = ready, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        if (ready) LightRays(color, Modifier.fillMaxSize(), alpha = 0.8f)
        Scanlines(Modifier.fillMaxSize(), color = color)
        content()
        if (ready) {
            Text(
                "TAP TO CLOSE",
                style = AriseType.label.copy(color = color.copy(alpha = blink)),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp),
            )
        }
        if (flash > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash)))
    }
}

/**
 * Using an item: a Random Box shakes harder and harder until it bursts open and the prize rises
 * out of it; any other item spins up inside a magic circle and is activated.
 */
@Composable
fun ItemActivation(use: ItemUse, onDone: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val box = use.item == ItemType.RANDOM_BOX && use.obtained != null
    val shown = use.obtained ?: use.item
    val color = Color(shown.rarity.color)
    val charge = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val shock = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val sigil = remember { Animatable(0f) }
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(use) {
        launch { sigil.animateTo(1f, tween(1400)) }
        if (box) {
            launch {
                var gap = 220L
                while (charge.value < 1f) {
                    delay(gap)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    gap = (gap * 0.82f).toLong().coerceAtLeast(45L)
                }
            }
            charge.animateTo(1f, tween(1600, easing = LinearEasing))
        } else {
            spin.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
        phase = 1
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { flash.snapTo(1f); flash.animateTo(0f, tween(550)) }
        launch { shock.animateTo(1f, tween(900)) }
        rise.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 180f))
        phase = 2
    }
    RevealStage(color, phase == 2, flash.value, onDone) {
        Box(Modifier.size(320.dp).padding(bottom = 140.dp), contentAlignment = Alignment.Center) {
            HexSigil(sigil.value, color, Modifier.fillMaxSize())
            Shockwave(shock.value, color, Modifier.fillMaxSize())
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                if (box && phase == 0) {
                    val c = charge.value
                    ItemGlyph(
                        ItemType.RANDOM_BOX, Color(ItemType.RANDOM_BOX.rarity.color), true, 96.dp,
                        Modifier.graphicsLayer {
                            translationX = sin(c * 90f) * 10f * c * density
                            rotationZ = sin(c * 70f) * 12f * c
                            val s = 1f + 0.25f * c
                            scaleX = s; scaleY = s
                        },
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        // Light leaking through the cracks.
                        val cc = Offset(size.width / 2, size.height / 2)
                        for (i in 0 until 8) {
                            val a = Math.toRadians(i * 45.0 + c * 90.0)
                            val len = size.minDimension * (0.2f + 0.9f * c)
                            drawLine(Color.White.copy(alpha = 0.7f * c), cc, Offset(cc.x + (cos(a) * len).toFloat(), cc.y + (sin(a) * len).toFloat()), (1f + 3f * c) * density)
                        }
                    }
                } else {
                    ItemGlyph(
                        shown, color, true, 110.dp,
                        Modifier.graphicsLayer {
                            val r = if (box) rise.value else spin.value
                            rotationY = (1f - spin.value) * 720f
                            translationY = (1f - r) * 60f * density
                            val s = if (box) 0.3f + 0.7f * rise.value else 0.5f + 0.5f * spin.value + 0.15f * rise.value
                            scaleX = s; scaleY = s
                            cameraDistance = 10f * density
                        },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            if (phase >= 1) {
                ChromaticText(
                    if (box) "ITEM OBTAINED" else "ACTIVATED",
                    AriseType.display.copy(fontSize = 30.sp),
                    split = (1f - rise.value.coerceIn(0f, 1f)) * 12f,
                    glow = color,
                )
                Spacer(Modifier.height(10.dp))
                TypewriterText(shown.displayName.uppercase(), AriseType.title.copy(color = color), charMillis = 35, glow = color, textAlign = TextAlign.Center)
                Text(shown.rarity.label.uppercase(), style = AriseType.label.copy(color = color))
                Spacer(Modifier.height(12.dp))
                Text(use.message, style = AriseType.body, textAlign = TextAlign.Center, modifier = Modifier.reveal(2, 200))
            }
        }
    }
}

/** A bought piece of equipment materialises in the Armory. */
@Composable
fun PurchaseReveal(e: Equipment, onDone: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val color = Color(e.rarity.color)
    val form = remember(e) { Animatable(0f) }
    val implode = remember(e) { Animatable(0f) }
    val flash = remember(e) { Animatable(0f) }
    val shock = remember(e) { Animatable(0f) }
    var phase by remember(e) { mutableIntStateOf(0) }
    LaunchedEffect(e) {
        implode.animateTo(1f, tween(800, easing = LinearEasing))
        phase = 1
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { flash.snapTo(0.9f); flash.animateTo(0f, tween(500)) }
        launch { shock.animateTo(1f, tween(900)) }
        form.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 160f))
        phase = 2
    }
    RevealStage(color, phase == 2, flash.value, onDone) {
        Implosion(implode.value, color, Modifier.fillMaxSize())
        Box(Modifier.size(320.dp).padding(bottom = 140.dp), contentAlignment = Alignment.Center) {
            HexSigil(form.value, color, Modifier.fillMaxSize())
            Shockwave(shock.value, color, Modifier.fillMaxSize())
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                if (phase >= 1) {
                    EquipmentGlyph(
                        e, true, 110.dp,
                        Modifier.graphicsLayer {
                            val f = form.value
                            rotationY = (1f - f) * 540f
                            scaleX = 0.4f + 0.6f * f; scaleY = 0.4f + 0.6f * f
                            cameraDistance = 10f * density
                        },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            if (phase >= 1) {
                ChromaticText("ACQUIRED", AriseType.display.copy(fontSize = 32.sp), split = (1f - form.value.coerceIn(0f, 1f)) * 12f, glow = color)
                Spacer(Modifier.height(10.dp))
                TypewriterText(e.displayName.uppercase(), AriseType.title.copy(color = color), charMillis = 35, glow = color, textAlign = TextAlign.Center)
                Text(e.rarity.label.uppercase(), style = AriseType.label.copy(color = color))
                Spacer(Modifier.height(12.dp))
                Text(e.description, style = AriseType.body, textAlign = TextAlign.Center, modifier = Modifier.reveal(2, 200))
                Spacer(Modifier.height(6.dp))
                Text("Equip it in LOADOUT — changes apply from tomorrow.", style = AriseType.small, textAlign = TextAlign.Center, modifier = Modifier.reveal(3, 200))
            }
        }
    }
}
