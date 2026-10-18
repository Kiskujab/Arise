package com.kiskujab.arise.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiskujab.arise.core.EventType
import com.kiskujab.arise.data.EventEntity
import com.kiskujab.arise.ui.MainViewModel
import com.kiskujab.arise.ui.components.GlowText
import com.kiskujab.arise.ui.components.SystemButton
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType

fun eventAccent(type: String): Color = when (runCatching { EventType.valueOf(type) }.getOrNull()) {
    EventType.LEVEL_UP, EventType.PENALTY_CLEARED, EventType.SHIELD_USED, EventType.FREEZE_USED -> AriseColors.Cyan
    EventType.RANK_UP, EventType.TITLE -> AriseColors.Gold
    EventType.ITEM -> AriseColors.Purple
    EventType.LEVEL_DOWN, EventType.RANK_DOWN, EventType.ABSENT, EventType.PENALTY_FAILED,
    EventType.STREAK_BROKEN, EventType.PENALTY_ASSIGNED -> AriseColors.Red
    else -> AriseColors.Blue
}

private fun eventHeader(type: String): String = when (runCatching { EventType.valueOf(type) }.getOrNull()) {
    EventType.ITEM -> "ITEM"
    EventType.TITLE -> "TITLE"
    EventType.LEVEL_DOWN, EventType.RANK_DOWN, EventType.ABSENT, EventType.PENALTY_FAILED, EventType.STREAK_BROKEN -> "WARNING"
    EventType.PENALTY_ASSIGNED -> "PENALTY QUEST"
    EventType.DAY_RESULT -> "QUEST REPORT"
    else -> "NOTIFICATION"
}

/** Shows unseen System messages one by one. Level and rank ups get the full-screen treatment. */
@Composable
fun EventOverlay(vm: MainViewModel) {
    val events by vm.unseenEvents.collectAsStateWithLifecycle()
    val event = events.firstOrNull()
    AnimatedVisibility(event != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xD9000000))
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            if (event != null) {
                val type = runCatching { EventType.valueOf(event.type) }.getOrNull()
                if (type == EventType.LEVEL_UP || type == EventType.RANK_UP) {
                    Ascension(event, events.size) { vm.markSeen(event.id) }
                } else {
                    SystemMessage(event, events.size) { vm.markSeen(event.id) }
                }
            }
        }
    }
}

@Composable
private fun SystemMessage(event: EventEntity, total: Int, onOk: () -> Unit) {
    val accent = eventAccent(event.type)
    val scale = remember(event.id) { Animatable(0.85f) }
    LaunchedEffect(event.id) { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    Column(Modifier.padding(24.dp).scale(scale.value)) {
        SystemPanel(title = eventHeader(event.type), accent = accent) {
            GlowText(event.title, AriseType.title, glow = accent, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(event.message, style = AriseType.body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
            SystemButton("OK", onClick = onOk, accent = accent, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (total > 1) {
                Spacer(Modifier.height(8.dp))
                Text("${total - 1} more message(s)", style = AriseType.small, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun Ascension(event: EventEntity, total: Int, onOk: () -> Unit) {
    val accent = eventAccent(event.type)
    val haptics = LocalHapticFeedback.current
    val scale = remember(event.id) { Animatable(3f) }
    val alpha = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        alpha.animateTo(1f, tween(250))
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
    }
    val transition = rememberInfiniteTransition(label = "rays")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(30_000, easing = LinearEasing)), label = "rot")
    val pulse by transition.animateFloat(0.7f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse")

    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onOk), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f * pulse), Color.Transparent), c, size.minDimension * 0.6f), size.minDimension * 0.6f, c)
            rotate(rotation, c) {
                for (i in 0 until 16) {
                    rotate(i * 22.5f, c) {
                        drawLine(
                            Brush.verticalGradient(listOf(accent.copy(alpha = 0.0f), accent.copy(alpha = 0.25f * pulse), accent.copy(alpha = 0f)), c.y - size.maxDimension, c.y),
                            Offset(c.x, c.y),
                            Offset(c.x, c.y - size.maxDimension),
                            strokeWidth = 6.dp.toPx(),
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            GlowText(
                event.title.uppercase(),
                AriseType.display.copy(fontSize = 46.sp),
                glow = accent,
                modifier = Modifier.scale(scale.value),
                textAlign = TextAlign.Center,
                color = AriseColors.Text.copy(alpha = alpha.value),
            )
            Spacer(Modifier.height(16.dp))
            GlowText(event.message, AriseType.title, glow = accent, textAlign = TextAlign.Center, color = AriseColors.Text.copy(alpha = alpha.value))
            Spacer(Modifier.height(36.dp))
            Text(if (total > 1) "Tap to continue · ${total - 1} more" else "Tap to continue", style = AriseType.small)
        }
    }
}
