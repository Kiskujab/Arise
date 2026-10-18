package com.kiskujab.arise.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiskujab.arise.update.UpdateState
import com.kiskujab.arise.ui.MainViewModel
import com.kiskujab.arise.ui.components.GlowText
import com.kiskujab.arise.ui.components.SystemBar
import com.kiskujab.arise.ui.components.SystemButton
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType

/** "[SYSTEM] Update available" strip shown above every tab until installed or dismissed. */
@Composable
fun UpdateBanner(vm: MainViewModel) {
    val state by vm.updateState.collectAsStateWithLifecycle()
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val visible = !dismissed && (state is UpdateState.Available || state is UpdateState.Downloading || state is UpdateState.Ready ||
        (state as? UpdateState.Failed)?.info != null)
    AnimatedVisibility(visible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            UpdateCard(state, onInstall = { vm.installUpdate() }, onDismiss = { dismissed = true })
        }
    }
}

@Composable
fun UpdateCard(state: UpdateState, onInstall: () -> Unit, onDismiss: (() -> Unit)? = null) {
    val info = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.Ready -> state.info
        is UpdateState.Failed -> state.info
        else -> null
    } ?: return
    SystemPanel(accent = AriseColors.Gold) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                GlowText("UPDATE AVAILABLE · v${info.versionName}", AriseType.header.copy(color = AriseColors.Text), glow = AriseColors.Gold)
                if (info.notes.isNotBlank()) {
                    Text(info.notes.lineSequence().first(), style = AriseType.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onDismiss != null && state !is UpdateState.Downloading) {
                Text("✕", style = AriseType.title.copy(color = AriseColors.TextDim), modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        when (state) {
            is UpdateState.Downloading -> {
                SystemBar(state.progress, color = AriseColors.Gold)
                Spacer(Modifier.height(4.dp))
                Text("Downloading… ${(state.progress * 100).toInt()}%", style = AriseType.small)
            }
            is UpdateState.Failed -> {
                Text(state.message, style = AriseType.small.copy(color = AriseColors.Red))
                Spacer(Modifier.height(8.dp))
                SystemButton("RETRY", onClick = onInstall, accent = AriseColors.Gold, modifier = Modifier.align(Alignment.End))
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your records stay intact.", style = AriseType.small, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SystemButton("INSTALL", onClick = onInstall, accent = AriseColors.Gold)
            }
        }
    }
}
