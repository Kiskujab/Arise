package com.kiskujab.arise.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiskujab.arise.R
import com.kiskujab.arise.core.Rank
import com.kiskujab.arise.core.Rules
import com.kiskujab.arise.core.Stat
import com.kiskujab.arise.core.Title
import com.kiskujab.arise.ui.MainViewModel
import com.kiskujab.arise.ui.components.Chip
import com.kiskujab.arise.ui.components.CountUpText
import com.kiskujab.arise.ui.components.RankEmblem
import com.kiskujab.arise.ui.components.reveal
import com.kiskujab.arise.ui.components.Divider
import com.kiskujab.arise.ui.components.GlowText
import com.kiskujab.arise.ui.components.SystemBar
import com.kiskujab.arise.ui.components.SystemButton
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale

@Composable
fun StatusScreen(vm: MainViewModel, onOpenQuests: () -> Unit) {
    val player by vm.player.collectAsStateWithLifecycle()
    val state by vm.playerState.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var showTitles by remember { mutableStateOf(false) }
    val p = player ?: return

    val level = state.level
    val rank = state.rank
    val rankColor = Color(rank.color)
    val cur = Rules.xpForLevel(level)
    val next = Rules.xpForLevel(level + 1)
    val progress = if (level >= Rules.MAX_LEVEL) 1f else ((state.xp - cur).toFloat() / (next - cur).coerceAtLeast(1))
    val activeTitle = runCatching { Title.valueOf(p.activeTitle) }.getOrDefault(Title.PLAYER)

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(52.dp))
            GlowText("ARISE", AriseType.title.copy(fontSize = 24.sp, letterSpacing = 8.sp))
        }

        SystemPanel(title = "STATUS", modifier = Modifier.reveal(0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoLine("NAME", p.name)
                    InfoLine("JOB", if (rank >= Rank.S) "Monarch" else "Player")
                    Row(Modifier.clickable { showTitles = true }, verticalAlignment = Alignment.CenterVertically) {
                        InfoLine("TITLE", activeTitle.displayName)
                        Text("  ▾", style = AriseType.label.copy(color = AriseColors.Blue))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CountUpText(level.toLong(), AriseType.display.copy(fontSize = 56.sp), durationMillis = 700)
                    Text("LEVEL", style = AriseType.header)
                }
            }
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankEmblem(rank, 64.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("RANK", style = AriseType.label)
                    Text(rank.title, style = AriseType.bodyBold.copy(color = rankColor))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("GOLD", style = AriseType.label)
                    CountUpText(state.gold, AriseType.number.copy(color = AriseColors.Gold), durationMillis = 700, glow = AriseColors.Gold, suffix = " G")
                }
            }
            Spacer(Modifier.height(14.dp))
            Row {
                Text("EXP", style = AriseType.label, modifier = Modifier.weight(1f))
                Text(
                    if (level >= Rules.MAX_LEVEL) "MAX" else "${state.xp - cur} / ${next - cur}",
                    style = AriseType.label.copy(color = AriseColors.Text),
                )
            }
            Spacer(Modifier.height(6.dp))
            SystemBar(progress, height = 10.dp)
            Spacer(Modifier.height(6.dp))
            Row {
                Text("Safe zone: Lv ${Rules.safeFloor(level)}", style = AriseType.small.copy(color = AriseColors.Gold), modifier = Modifier.weight(1f))
                Text("Best: Lv ${state.maxLevel}", style = AriseType.small)
            }
            nextRankLine(level)?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = AriseType.small)
            }
        }

        val t = today
        if (t != null) {
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
            val accent = if (t.penalty != null) AriseColors.Red else AriseColors.Blue
            SystemPanel(title = if (t.penalty != null) "PENALTY ACTIVE" else "DAILY QUEST", accent = accent, modifier = Modifier.reveal(1)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (t.score.questCleared) "All quests complete" else "${t.doneCount} / ${t.questCount} complete",
                            style = AriseType.bodyBold.copy(color = if (t.score.questCleared) AriseColors.Cyan else AriseColors.Text),
                        )
                        Text("Locks in ${countdown(now, midnight)}", style = AriseType.small)
                    }
                    if (t.vacation) Chip("VACATION", AriseColors.Purple)
                }
                Spacer(Modifier.height(10.dp))
                SystemBar(t.doneCount.toFloat() / t.questCount.coerceAtLeast(1), color = accent)
                Spacer(Modifier.height(14.dp))
                SystemButton("OPEN QUESTS", onClick = onOpenQuests, accent = accent, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }

        val trials by vm.trials.collectAsStateWithLifecycle()
        if (trials.isNotEmpty()) {
            SystemPanel(title = "TRIALS", accent = AriseColors.Gold, modifier = Modifier.reveal(2).clickable(onClick = onOpenQuests)) {
                for (c in trials) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.trial.kind.title, style = AriseType.bodyBold)
                            Text(c.trial.span.label + " · " + if (c.daysLeft <= 1) "last day" else "${c.daysLeft} days left", style = AriseType.small)
                        }
                        Text(c.progress.text, style = AriseType.label.copy(color = AriseColors.Gold))
                    }
                    SystemBar(c.progress.fraction, height = 4.dp, color = AriseColors.Gold)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        SystemPanel(title = "STATS", modifier = Modifier.reveal(3)) {
            val stats = Stat.entries
            for (row in stats.chunked(2)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    for (s in row) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            GlowText(s.name, AriseType.header.copy(color = AriseColors.Text), modifier = Modifier.width(48.dp))
                            Column {
                                CountUpText(state.statValue(s).toLong(), AriseType.number, durationMillis = 800)
                                Text(s.full + " · " + statSource(s), style = AriseType.small.copy(fontSize = 12.sp))
                            }
                        }
                    }
                }
            }
        }

        val c = state.counters
        val daysSince = (now.toLocalDate().toEpochDay() - p.awakenedEpochDay + 1).coerceAtLeast(1)
        SystemPanel(title = "RECORDS", modifier = Modifier.reveal(4)) {
            RecordRow("Current streak", "${state.streak} days", AriseColors.Cyan)
            RecordRow("Best streak", "${state.bestStreak} days")
            RecordRow("Streak freezes", "${state.count(com.kiskujab.arise.core.ItemType.STREAK_FREEZE)} / ${Rules.MAX_FREEZES}")
            Divider()
            RecordRow("Days since awakening", daysSince.toString())
            RecordRow("Days recorded", c.daysTracked.toString())
            RecordRow("Daily Quests cleared", c.questClears.toString())
            RecordRow("S-grade days", c.perfectDays.toString())
            RecordRow("Absent days", c.daysAbsent.toString(), if (c.daysAbsent > 0) AriseColors.Red else AriseColors.Text)
            RecordRow("Penalties cleared / failed", "${c.penaltiesCleared} / ${c.penaltiesFailed}")
            Divider(AriseColors.Purple)
            val ratio = if (c.daysTracked == 0) 0.0 else c.vacationDays * 100.0 / c.daysTracked
            RecordRow("Time on vacation", String.format(Locale.US, "%.1f%%  (%d of %d days)", ratio, c.vacationDays, c.daysTracked), AriseColors.Purple)
            RecordRow("Total EXP", state.xp.toString())
            RecordRow("Gold earned", "${c.goldEarned} G", AriseColors.Gold)
            RecordRow("Trials cleared", c.trialsCleared.toString())
        }

        SystemButton("TITLES (${state.titles.size}/${Title.entries.size})", onClick = { showTitles = true }, accent = AriseColors.Gold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
    }

    if (showTitles) {
        Dialog(onDismissRequest = { showTitles = false }) {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                SystemPanel(title = "TITLES", accent = AriseColors.Gold) {
                    for (title in Title.entries) {
                        val owned = title in state.titles
                        val active = title == activeTitle
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = owned) { vm.setTitle(title); showTitles = false }
                                .padding(vertical = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (owned) title.displayName else "???",
                                    style = AriseType.bodyBold.copy(color = if (owned) (if (active) AriseColors.Gold else AriseColors.Text) else AriseColors.TextFaint),
                                    modifier = Modifier.weight(1f),
                                )
                                if (active) Chip("EQUIPPED", AriseColors.Gold)
                            }
                            Text(title.description, style = AriseType.small.copy(color = if (owned) AriseColors.TextDim else AriseColors.TextFaint))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AriseType.label, modifier = Modifier.width(64.dp))
        Text(value, style = AriseType.bodyBold)
    }
}

@Composable
private fun RecordRow(label: String, value: String, color: Color = AriseColors.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = AriseType.body.copy(color = AriseColors.TextDim), modifier = Modifier.weight(1f))
        Text(value, style = AriseType.bodyBold.copy(color = color), textAlign = TextAlign.End)
    }
}

private fun statSource(s: Stat) = when (s) {
    Stat.STR -> "movement"
    Stat.VIT -> "nutrition"
    Stat.AGI -> "braces & teeth"
    Stat.INT -> "self-development"
    Stat.PER -> "phone focus"
    Stat.CHA -> "skincare"
}

private fun nextRankLine(level: Int): String? {
    val next = Rank.entries.firstOrNull { it.minLevel > level } ?: return null
    return "Next rank: ${next.label} at Lv ${next.minLevel}"
}

fun countdown(now: LocalDateTime, target: LocalDateTime): String {
    val d = Duration.between(now, target).coerceAtLeast(Duration.ZERO)
    return String.format(Locale.US, "%02d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart())
}
