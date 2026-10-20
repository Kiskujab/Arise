package com.kiskujab.arise.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiskujab.arise.R
import com.kiskujab.arise.core.Goals
import com.kiskujab.arise.ui.components.GlowText
import com.kiskujab.arise.ui.components.GoalsForm
import com.kiskujab.arise.ui.components.SystemButton
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.components.glowBorder
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType
import kotlinx.coroutines.delay

/** First launch: the System chooses you. */
@Composable
fun AwakeningScreen(onAwaken: (String, Goals) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var goals by remember { mutableStateOf(Goals()) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step = 5 }

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(step, transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(300)) }, label = "awaken") { s ->
            when (s) {
                0 -> Invitation(onAccept = { step = 1 })
                1 -> NameStep(name, { name = it.take(20) }, onNext = { step = 2 })
                2 -> GoalsStep(goals, { goals = it }, onNext = { step = 3 })
                3 -> RulesStep(onNext = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        step = 4
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        step = 5
                    }
                })
                4 -> Box(Modifier.fillMaxSize())
                else -> AriseFinale(name) { onAwaken(name, goals) }
            }
        }
    }
}

@Composable
private fun TypedText(text: String, startDelay: Long = 0, onDone: () -> Unit = {}) {
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        delay(startDelay)
        while (shown < text.length) {
            shown++
            delay(28)
        }
        onDone()
    }
    Text(text.take(shown), style = AriseType.body.copy(fontSize = 19.sp), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Invitation(onAccept: () -> Unit) {
    var line by remember { mutableIntStateOf(0) }
    var declined by remember { mutableStateOf(false) }
    val lines = listOf(
        "You have met the requirements to become a Player.",
        "From now on, the System will track your discipline: what you eat, how you move, how you care for yourself and how you use your phone.",
        "Will you accept?",
    )
    SystemPanel(title = "NOTIFICATION") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for (i in 0..minOf(line, lines.lastIndex)) {
                TypedText(lines[i], startDelay = if (i == 0) 500 else 250) { if (i == line) line++ }
            }
            if (line > lines.lastIndex) {
                if (declined) {
                    GlowText("There is no option to decline.", AriseType.bodyBold, glow = AriseColors.Red, color = AriseColors.Red, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                    SystemButton("ACCEPT", onClick = onAccept)
                    SystemButton(if (declined) "ACCEPT" else "DECLINE", onClick = { if (declined) onAccept() else declined = true }, accent = if (declined) AriseColors.Blue else AriseColors.Red)
                }
            }
        }
    }
}

@Composable
private fun NameStep(name: String, onName: (String) -> Unit, onNext: () -> Unit) {
    SystemPanel(title = "PLAYER REGISTRATION") {
        Text("State your name, Player.", style = AriseType.body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        BasicTextField(
            value = name,
            onValueChange = onName,
            singleLine = true,
            textStyle = AriseType.title.copy(color = AriseColors.Text, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(AriseColors.Cyan),
            modifier = Modifier
                .fillMaxWidth()
                .glowBorder(AriseColors.Blue, 4.dp, 0.6f)
                .background(AriseColors.Bg.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(14.dp),
        )
        Spacer(Modifier.height(18.dp))
        SystemButton("CONFIRM", onClick = onNext, enabled = name.isNotBlank(), modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun GoalsStep(goals: Goals, onChange: (Goals) -> Unit, onNext: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SystemPanel(title = "SET YOUR TARGETS") {
            Text(
                "These targets define your Daily Quest. Later you can give each weekday its own targets (e.g. fewer steps on Saturday) in SYSTEM → Targets. Changes take effect from the next day.",
                style = AriseType.small,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            GoalsForm(goals, onChange = onChange)
            Spacer(Modifier.height(18.dp))
            SystemButton("CONFIRM", onClick = onNext, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun RulesStep(onNext: () -> Unit) {
    val rules = listOf(
        "The System does not forget. Records can never be reset or erased.",
        "Each day locks at midnight. Phone usage for a day can be reported until 12:00 the next day.",
        "If you don't report, the worst possible result is recorded for everything.",
        "Failing the Daily Quest brings a Penalty Quest that corrects what went wrong.",
        "Levels can be lost. Every 5th level is a safe zone you can never fall below.",
        "The higher you climb, the more a mistake costs — but one day can never take more than ${com.kiskujab.arise.core.Rules.MAX_DAILY_LOSS} EXP.",
    )
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SystemPanel(title = "RULES OF THE SYSTEM", accent = AriseColors.Red) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rules.forEachIndexed { i, r ->
                    Row {
                        GlowText("${i + 1}.", AriseType.bodyBold, color = AriseColors.Red, glow = AriseColors.Red, modifier = Modifier.width(26.dp))
                        Text(r, style = AriseType.body)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            SystemButton("I UNDERSTAND", onClick = onNext, accent = AriseColors.Red, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun AriseFinale(name: String, onDone: () -> Unit) {
    val scale = remember { Animatable(0.4f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(900))
        scale.animateTo(1f, tween(1400))
        delay(1200)
        onDone()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(220.dp).scale(scale.value).alpha(alpha.value),
        )
        GlowText("ARISE", AriseType.display.copy(fontSize = 72.sp, letterSpacing = 12.sp), modifier = Modifier.scale(scale.value).alpha(alpha.value))
        Spacer(Modifier.height(18.dp))
        Box(Modifier.border(1.dp, AriseColors.Blue.copy(alpha = alpha.value), RoundedCornerShape(3.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
            GlowText("PLAYER ${name.uppercase()}", AriseType.header, modifier = Modifier.alpha(alpha.value))
        }
    }
}
