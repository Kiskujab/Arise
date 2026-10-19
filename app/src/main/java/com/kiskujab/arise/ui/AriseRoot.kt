package com.kiskujab.arise.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
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
                    EventOverlay(vm)
                    if (celebrate) QuestCompleteBurst { celebrate = false }
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

@Composable
private fun MainScaffold(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.STATUS) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        UpdateBanner(vm)
        Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
            AnimatedContent(
                tab,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 6 * dir }) togetherWith
                        (fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 6 * dir })
                },
                label = "tab",
            ) { t ->
                when (t) {
                    Tab.STATUS -> StatusScreen(vm, onOpenQuests = { tab = Tab.QUESTS })
                    Tab.QUESTS -> QuestScreen(vm)
                    Tab.ITEMS -> GearScreen(vm)
                    Tab.LOG -> LogScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }
        BottomBar(tab) { tab = it }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, AriseColors.Bg, AriseColors.Bg)))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        for (t in Tab.entries) {
            val active = t == selected
            val c = if (active) AriseColors.Cyan else AriseColors.TextFaint
            val lift by animateFloatAsState(if (active) 1f else 0f, tween(250), label = "lift")
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onSelect(t) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(28.dp * lift).height(2.dp).background(AriseColors.Cyan.copy(alpha = lift)))
                Spacer(Modifier.height(4.dp))
                Icon(
                    t.icon, contentDescription = t.label, tint = c,
                    modifier = Modifier.size(22.dp).graphicsLayer { scaleX = 1f + 0.15f * lift; scaleY = 1f + 0.15f * lift },
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
