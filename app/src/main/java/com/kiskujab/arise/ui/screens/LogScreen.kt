package com.kiskujab.arise.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiskujab.arise.core.Codec
import com.kiskujab.arise.core.Grade
import com.kiskujab.arise.data.DayEntity
import com.kiskujab.arise.ui.MainViewModel
import com.kiskujab.arise.ui.components.Chip
import com.kiskujab.arise.ui.components.Divider
import com.kiskujab.arise.ui.components.GlowText
import com.kiskujab.arise.ui.components.QuestMark
import com.kiskujab.arise.ui.components.SystemButton
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun gradeColor(grade: String?): Color = when (grade) {
    Grade.S.name -> AriseColors.Gold
    Grade.A.name -> AriseColors.Cyan
    Grade.B.name -> AriseColors.Blue
    Grade.C.name -> AriseColors.Purple
    Grade.D.name -> Color(0xFFFF9F1C)
    Grade.E.name -> Color(0xFFFF6B6B)
    Grade.F.name -> AriseColors.Red
    else -> AriseColors.TextFaint
}

@Composable
fun LogScreen(vm: MainViewModel) {
    val days by vm.history.collectAsStateWithLifecycle()
    val events by vm.eventLog.collectAsStateWithLifecycle()
    var showSystemLog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<DayEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SystemPanel(title = "RECORD") {
                Heatmap(days)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    SystemButton("DAYS", onClick = { showSystemLog = false }, accent = if (!showSystemLog) AriseColors.Cyan else AriseColors.TextFaint)
                    SystemButton("SYSTEM LOG", onClick = { showSystemLog = true }, accent = if (showSystemLog) AriseColors.Cyan else AriseColors.TextFaint)
                }
            }
        }
        if (!showSystemLog) {
            if (days.isEmpty()) {
                item { Text("No finalized days yet. Your first record appears after the day's report window closes.", style = AriseType.small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
            items(days, key = { it.epochDay }) { d -> DayCard(d) { selected = d } }
        } else {
            items(events, key = { it.id }) { e ->
                val c = eventAccent(e.type)
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Box(Modifier.width(3.dp).heightIn(min = 36.dp).background(c))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(e.title, style = AriseType.bodyBold.copy(color = c))
                        Text(e.message, style = AriseType.small)
                        Text(
                            Instant.ofEpochMilli(e.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH)),
                            style = AriseType.small.copy(color = AriseColors.TextFaint),
                        )
                    }
                }
            }
        }
    }

    selected?.let { d -> DayDetail(d) { selected = null } }
}

/** Last 5 weeks, one cell per day, coloured by grade. */
@Composable
private fun Heatmap(days: List<DayEntity>) {
    val byDay = remember(days) { days.associateBy { it.epochDay } }
    val today = LocalDate.now().toEpochDay()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (week in 4 downTo 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (dow in 6 downTo 0) {
                    val epoch = today - week * 7 - dow
                    val d = byDay[epoch]
                    val c = when {
                        d == null -> AriseColors.BgElevated
                        d.vacation -> AriseColors.Purple.copy(alpha = 0.6f)
                        else -> gradeColor(d.grade).copy(alpha = 0.85f)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(c, RoundedCornerShape(3.dp))
                            .border(1.dp, if (epoch == today) AriseColors.Cyan else Color.Transparent, RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            for (g in listOf("S", "A", "B", "C", "D", "E", "F")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(gradeColor(g), RoundedCornerShape(2.dp)))
                    Text(" $g", style = AriseType.small)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(AriseColors.Purple, RoundedCornerShape(2.dp)))
                Text(" vac", style = AriseType.small)
            }
        }
    }
}

@Composable
private fun DayCard(d: DayEntity, onClick: () -> Unit) {
    val c = gradeColor(d.grade)
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .background(AriseColors.Panel, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).border(1.5.dp, c, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            GlowText(d.grade ?: "?", AriseType.title, color = c, glow = c)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(LocalDate.ofEpochDay(d.epochDay).format(DateTimeFormatter.ofPattern("EEE, MMM d yyyy", Locale.ENGLISH)), style = AriseType.bodyBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    d.absent -> Chip("ABSENT", AriseColors.Red)
                    d.questCleared -> Chip("CLEARED", AriseColors.Cyan)
                    else -> Chip("FAILED", AriseColors.Red)
                }
                if (d.vacation) Chip("VACATION", AriseColors.Purple)
                if (d.penalty != null) Chip("PENALTY", Color(0xFFFF9F1C))
                if (d.skipDay) Chip("SKIP", AriseColors.Gold)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            val xc = if (d.xpDelta >= 0) AriseColors.Cyan else AriseColors.Red
            GlowText((if (d.xpDelta >= 0) "+" else "") + d.xpDelta, AriseType.number, color = xc, glow = xc)
            Text("Lv ${d.levelAfter}", style = AriseType.small)
        }
    }
}

@Composable
private fun DayDetail(d: DayEntity, onDismiss: () -> Unit) {
    val result = remember(d) { Codec.decodeResult(d.resultJson) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
            SystemPanel(title = "QUEST REPORT", accent = gradeColor(d.grade)) {
                Text(LocalDate.ofEpochDay(d.epochDay).format(DateTimeFormatter.ofPattern("EEEE, MMMM d yyyy", Locale.ENGLISH)), style = AriseType.bodyBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                if (d.absent) {
                    Spacer(Modifier.height(6.dp))
                    Text("No report was made. The worst result was recorded.", style = AriseType.small.copy(color = AriseColors.Red), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                if (result != null) {
                    Divider()
                    for (r in result.results) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            QuestMark(met = r.met, failed = !r.met && r.mandatory, modifier = Modifier.width(28.dp))
                            Text(r.label, style = AriseType.body, modifier = Modifier.weight(1f))
                            Text("${(r.quality * 100).toInt()}%", style = AriseType.bodyBold)
                        }
                    }
                    Divider()
                    for (line in result.xpLines) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text(line.label, style = AriseType.small, modifier = Modifier.weight(1f))
                            Text((if (line.amount >= 0) "+" else "") + line.amount, style = AriseType.small.copy(color = if (line.amount >= 0) AriseColors.Cyan else AriseColors.Red))
                        }
                    }
                    if (d.xpDelta != result.xpLines.sumOf { it.amount }) {
                        Text("Safe zone absorbed part of the loss.", style = AriseType.small.copy(color = AriseColors.Gold))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("Total", style = AriseType.bodyBold, modifier = Modifier.weight(1f))
                        Text((if (d.xpDelta >= 0) "+" else "") + d.xpDelta + " XP", style = AriseType.bodyBold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                SystemButton("CLOSE", onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
