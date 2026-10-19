package com.kiskujab.arise.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kiskujab.arise.core.TrialSpan
import com.kiskujab.arise.core.TrialState
import com.kiskujab.arise.data.TrialCard
import com.kiskujab.arise.ui.components.Chip
import com.kiskujab.arise.ui.components.Divider
import com.kiskujab.arise.ui.components.GlowText
import com.kiskujab.arise.ui.components.SystemBar
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType

/** 3-Day Trial and Weekly Raid objectives with live progress. */
@Composable
fun TrialsPanel(cards: List<TrialCard>, modifier: Modifier = Modifier) {
    SystemPanel(modifier, title = "TRIALS", accent = AriseColors.Gold) {
        if (cards.isEmpty()) {
            Text("The first Weekly Raid begins on Monday.", style = AriseType.small)
            return@SystemPanel
        }
        val groups = cards.groupBy { it.trial.span }
        TrialSpan.entries.forEachIndexed { gi, span ->
            val list = groups[span] ?: return@forEachIndexed
            if (gi > 0) Divider(AriseColors.Gold)
            val first = list.first()
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowText(span.label.uppercase(), AriseType.header.copy(color = AriseColors.Text), glow = AriseColors.Gold, modifier = Modifier.weight(1f))
                Text(
                    if (first.daysLeft <= 1) "last day" else "${first.daysLeft} days left",
                    style = AriseType.small,
                )
            }
            Text(
                "Reward: +${span.xp} XP · +${span.gold} gold" + if (span == TrialSpan.RAID) " · clear both for a Random Box" else "",
                style = AriseType.small.copy(color = AriseColors.Gold),
            )
            Spacer(Modifier.height(8.dp))
            for (c in list) TrialRow(c)
        }
        Spacer(Modifier.height(6.dp))
        Text("Trials only reward. Failing one costs nothing.", style = AriseType.small.copy(color = AriseColors.TextFaint))
    }
}

@Composable
private fun TrialRow(c: TrialCard) {
    val (chip, color) = when (c.progress.state) {
        TrialState.ACTIVE -> null to AriseColors.Gold
        TrialState.SECURED -> "SECURED" to AriseColors.Cyan
        TrialState.CLEARED -> "CLEARED" to AriseColors.Cyan
        TrialState.FAILED -> "FAILED" to AriseColors.Red
    }
    val barColor: Color = when {
        c.progress.state == TrialState.FAILED -> AriseColors.Red
        c.progress.lowerIsBetter && c.progress.fraction > 0.85f -> AriseColors.Red
        else -> color
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.trial.kind.title, style = AriseType.bodyBold, modifier = Modifier.weight(1f))
            if (chip != null) {
                Chip(chip, color)
                Spacer(Modifier.width(8.dp))
            }
            Text(c.progress.text, style = AriseType.label.copy(color = AriseColors.Text))
        }
        Text(c.description, style = AriseType.small)
        Spacer(Modifier.height(5.dp))
        SystemBar(c.progress.fraction, height = 6.dp, color = barColor)
        if (c.progress.lowerIsBetter) {
            Text("Bar shows how much of the budget is used.", style = AriseType.small.copy(color = AriseColors.TextFaint))
        }
    }
}
