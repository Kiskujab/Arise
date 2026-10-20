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
import com.kiskujab.arise.ui.components.TypewriterText
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import com.kiskujab.arise.ui.components.SystemButton
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType

fun eventAccent(type: String): Color = when (runCatching { EventType.valueOf(type) }.getOrNull()) {
    EventType.LEVEL_UP, EventType.PENALTY_CLEARED, EventType.SHIELD_USED, EventType.FREEZE_USED -> AriseColors.Cyan
    EventType.RANK_UP, EventType.TITLE -> AriseColors.Gold
    EventType.ITEM, EventType.GEAR -> AriseColors.Purple
    EventType.UNLOCK, EventType.TRIAL_CLEARED -> AriseColors.Gold
    EventType.TRIAL_FAILED -> AriseColors.TextDim
    EventType.LEVEL_DOWN, EventType.RANK_DOWN, EventType.ABSENT, EventType.PENALTY_FAILED,
    EventType.STREAK_BROKEN, EventType.PENALTY_ASSIGNED -> AriseColors.Red
    else -> AriseColors.Blue
}

private fun eventHeader(type: String): String = when (runCatching { EventType.valueOf(type) }.getOrNull()) {
    EventType.ITEM -> "ITEM"
    EventType.UNLOCK -> "UNLOCKED"
    EventType.TRIAL_CLEARED, EventType.TRIAL_FAILED -> "TRIAL"
    EventType.GEAR -> "ARMORY"
    EventType.TITLE -> "TITLE"
    EventType.LEVEL_DOWN, EventType.RANK_DOWN, EventType.ABSENT, EventType.PENALTY_FAILED, EventType.STREAK_BROKEN -> "WARNING"
    EventType.PENALTY_ASSIGNED -> "PENALTY QUEST"
    EventType.DAY_RESULT -> "QUEST REPORT"
    else -> "NOTIFICATION"
}

/** Shows unseen System messages one by one. Level and rank changes get a full-screen cinematic. */
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
                if (type == EventType.LEVEL_UP || type == EventType.RANK_UP || type == EventType.LEVEL_DOWN || type == EventType.RANK_DOWN) {
                    LevelSequence(event, events.size) { vm.markSeen(event.id) }
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
    val flip = remember(event.id) { Animatable(-80f) }
    LaunchedEffect(event.id) {
        launch { flip.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 180f)) }
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    Column(
        Modifier.padding(24.dp).scale(scale.value).graphicsLayer {
            rotationX = flip.value
            cameraDistance = 14f * density
            alpha = (1f - kotlin.math.abs(flip.value) / 80f).coerceIn(0f, 1f)
        },
    ) {
        SystemPanel(title = eventHeader(event.type), accent = accent) {
            TypewriterText(event.title, AriseType.title, modifier = Modifier.fillMaxWidth(), charMillis = 22, glow = accent, textAlign = TextAlign.Center)
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
