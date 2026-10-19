package com.kiskujab.arise.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiskujab.arise.core.CriterionResult
import com.kiskujab.arise.core.CustomType
import com.kiskujab.arise.core.DayClock
import com.kiskujab.arise.core.FinalizeInput
import com.kiskujab.arise.core.Finalizer
import com.kiskujab.arise.core.Keys
import com.kiskujab.arise.core.PlayerState
import com.kiskujab.arise.core.Rules
import com.kiskujab.arise.data.DayEntity
import com.kiskujab.arise.data.DayView
import com.kiskujab.arise.ui.MainViewModel
import com.kiskujab.arise.ui.components.Chip
import com.kiskujab.arise.ui.components.Divider
import com.kiskujab.arise.ui.components.GlowText
import com.kiskujab.arise.ui.components.QuestMark
import com.kiskujab.arise.ui.components.SystemButton
import com.kiskujab.arise.ui.components.SystemCheck
import com.kiskujab.arise.ui.components.SystemNumberField
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.components.SystemStepper
import com.kiskujab.arise.ui.components.fmt
import com.kiskujab.arise.ui.components.reveal
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestScreen(vm: MainViewModel) {
    val todayView by vm.today.collectAsStateWithLifecycle()
    val yesterdayView by vm.yesterday.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val state by vm.playerState.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val trials by vm.trials.collectAsStateWithLifecycle()
    val t = todayView ?: return
    val p = player ?: return

    val date = LocalDate.ofEpochDay(t.epochDay)
    val midnight = date.plusDays(1).atStartOfDay()
    val yesterdayOpen = yesterdayView?.let { y -> y.epochDay > p.lastFinalizedDay && DayClock.canEditPhone(LocalDate.ofEpochDay(y.epochDay), now) } == true
    val edit: ((DayEntity) -> DayEntity) -> Unit = { vm.editDay(t.epochDay, it) }
    val r = t.record
    val key = t.epochDay

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SystemPanel(title = "DAILY QUEST", modifier = Modifier.reveal(0)) {
            GlowText("Preparations to Become Powerful", AriseType.title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)) + " · locks in " + countdown(now, midnight),
                style = AriseType.small,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            val chips = buildList {
                if (t.vacation) add("VACATION" to AriseColors.Purple)
                if (r.booster) add("XP BOOSTER ×2" to AriseColors.Blue)
                if (r.shield) add("SHIELD UP" to AriseColors.Cyan)
                if (r.skipDay) add("SKIP DAY" to AriseColors.Gold)
                if (t.epochDay == p.awakenedEpochDay) add("AWAKENING DAY" to AriseColors.Cyan)
            }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    chips.forEach { (txt, c) -> Chip(txt, c) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Projection(t, p.awakenedEpochDay, state)
        }

        if (yesterdayOpen) {
            val y = yesterdayView!!
            YesterdayPanel(y, now = now, onEdit = { e -> vm.editDay(y.epochDay, e) }, onSeal = { vm.seal(y.epochDay) })
        }

        t.penalty?.let { pq ->
            SystemPanel(title = "PENALTY QUEST", accent = AriseColors.Red, modifier = Modifier.reveal(1)) {
                Text("Yesterday's failures must be corrected today:", style = AriseType.small)
                Spacer(Modifier.height(6.dp))
                pq.describe(t.customs).forEach { line ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        GlowText("!", AriseType.bodyBold, color = AriseColors.Red, glow = AriseColors.Red, modifier = Modifier.width(18.dp))
                        Text(line, style = AriseType.body)
                    }
                }
            }
        }

        TrialsPanel(trials, Modifier.reveal(2))

        val g = t.goals
        QuestSection("NUTRITION", "from Yazio") {
            QuestRow(t.result(Keys.KCAL), "Calories eaten", "max ${g.kcal} kcal") {
                SystemNumberField(r.kcal?.toString() ?: "", { v -> edit { it.copy(kcal = v.toIntOrNull()) } }, Modifier.width(120.dp), "kcal", resetKey = key)
            }
            QuestRow(t.result(Keys.PROTEIN), "Protein", "min ${g.protein} g") {
                SystemNumberField(r.protein?.toString() ?: "", { v -> edit { it.copy(protein = v.toIntOrNull()) } }, Modifier.width(120.dp), "g", resetKey = key)
            }
            if (t.score.kcalOver > Rules.KCAL_TOLERANCE && r.kcal != null) {
                Text("Over the limit by ${t.score.kcalOver} kcal — the penalty grows exponentially.", style = AriseType.small.copy(color = AriseColors.Red))
            }
        }

        QuestSection("MOVEMENT", "from Yazio") {
            val m = t.result(Keys.MOVEMENT)
            QuestRow(m, "Steps", "${g.steps} steps") {
                SystemNumberField(r.steps?.toString() ?: "", { v -> edit { it.copy(steps = v.toIntOrNull()) } }, Modifier.width(120.dp), resetKey = key)
            }
            QuestRow(null, "Active calories", "${g.activeKcal} kcal burned") {
                SystemNumberField(r.activeKcal?.toString() ?: "", { v -> edit { it.copy(activeKcal = v.toIntOrNull()) } }, Modifier.width(120.dp), "kcal", resetKey = key)
            }
        }

        QuestSection("BRACES & TEETH", null) {
            QuestRow(t.result(Keys.BANDS), "Rubber bands worn", "${fmt(g.bandHours)} h · extra hours give bonus XP") {
                SystemNumberField(r.bandHours?.let { fmt(it) } ?: "", { v -> edit { it.copy(bandHours = v.toDoubleOrNull()?.coerceIn(0.0, 24.0)) } }, Modifier.width(120.dp), "h", decimal = true, resetKey = key)
            }
            QuestRow(t.result(Keys.BRUSHING), "Tooth brushing", "${g.brushCount}× per day") {
                SystemStepper(r.brushCount ?: 0, { n -> edit { it.copy(brushCount = n) } })
            }
        }

        QuestSection("SKINCARE", null) {
            QuestRow(t.result(Keys.SKINCARE), "Evening skincare", if (t.vacation) "optional on vacation" else "done tonight") {
                SystemCheck(r.skincare == true, { c -> edit { it.copy(skincare = c) } })
            }
        }

        QuestSection("PHONE USAGE", "from AppBlock · can be reported until 12:00 tomorrow") {
            QuestRow(t.result(Keys.SCREEN), "Screen time", "max ${hm(g.screenMinutes)}") {
                DurationField(r.screenMinutes, { v -> edit { it.copy(screenMinutes = v) } }, key)
            }
            QuestRow(t.result(Keys.DISTRACTION), "Distraction", "max ${g.distractionPct}%") {
                SystemNumberField(r.distractionPct?.toString() ?: "", { v -> edit { it.copy(distractionPct = v.toIntOrNull()?.coerceIn(0, 100)) } }, Modifier.width(120.dp), "%", resetKey = key)
            }
        }

        QuestSection("SELF-DEVELOPMENT", "bonus only · never penalized", AriseColors.Cyan) {
            QuestRow(null, "Learning / growth time", "+1 XP per ${Rules.PRODUCTIVITY_MINUTES_PER_XP.toInt()} min, up to ${Rules.PRODUCTIVITY_MAX_XP.toInt()} XP") {
                DurationField(r.productivityMinutes, { v -> edit { it.copy(productivityMinutes = v) } }, key)
            }
        }

        if (t.customs.isNotEmpty()) {
            QuestSection("CUSTOM QUESTS", null) {
                val values = r.values.custom
                for (c in t.customs) {
                    val res = t.result(Keys.custom(c.id))
                    val target = com.kiskujab.arise.core.Scoring.effectiveCustomTarget(c, t.vacation, t.penalty)
                    when (c.type) {
                        CustomType.CHECK -> QuestRow(res, c.name, "yes / no · ${c.stat.name}") {
                            SystemCheck((values[c.id] ?: 0.0) >= 1.0, { checked -> edit { it.copy(customJson = setCustom(it, c.id, if (checked) 1.0 else null)) } })
                        }
                        else -> QuestRow(res, c.name, "${if (c.type == CustomType.AT_LEAST) "min" else "max"} ${fmt(target)} ${c.unit} · ${c.stat.name}") {
                            SystemNumberField(values[c.id]?.let { fmt(it) } ?: "", { v -> edit { it.copy(customJson = setCustom(it, c.id, v.toDoubleOrNull())) } }, Modifier.width(120.dp), c.unit.take(5), decimal = true, resetKey = key)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun DayView.result(key: String): CriterionResult? = score.results.firstOrNull { it.key == key }

private fun setCustom(day: DayEntity, id: Long, value: Double?): String {
    val m = com.kiskujab.arise.core.Codec.decodeCustom(day.customJson).toMutableMap()
    if (value == null) m.remove(id) else m[id] = value
    return com.kiskujab.arise.core.Codec.encodeCustom(m)
}

fun hm(minutes: Int): String = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

/** What happens if the day ended right now. */
@Composable
private fun Projection(t: DayView, awakenedDay: Long, state: PlayerState) {
    var expanded by remember { mutableStateOf(false) }
    val result = remember(t, state) {
        Finalizer.finalize(
            FinalizeInput(
                epochDay = t.epochDay, values = t.record.values, baseGoals = t.baseGoals, penalty = t.penalty,
                customs = t.customs, vacation = t.vacation, absent = t.record.isEmpty && !t.record.skipDay,
                modifiers = t.record.modifiers.copy(newcomer = t.epochDay == awakenedDay),
                nextDayBaseGoals = t.baseGoals, nextDayVacation = false, seed = 0,
            ),
            state,
        )
    }
    val color = if (result.appliedDelta >= 0) AriseColors.Cyan else AriseColors.Red
    Column(Modifier.fillMaxWidth()) {
        Divider()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("IF THE DAY ENDED NOW", style = AriseType.label)
                Text(
                    "${t.doneCount}/${t.questCount} quests · " + if (result.questCleared) "quest cleared" else "quest failed",
                    style = AriseType.small,
                )
            }
            GlowText(result.grade.label, AriseType.title, color = color, glow = color)
            Spacer(Modifier.width(14.dp))
            val shown by androidx.compose.animation.core.animateIntAsState(result.appliedDelta, androidx.compose.animation.core.tween(600), label = "xp")
            GlowText((if (shown >= 0) "+" else "") + "$shown XP", AriseType.number, color = color, glow = color)
        }
        Text(
            if (expanded) "▴ hide breakdown" else "▾ show breakdown",
            style = AriseType.small.copy(color = AriseColors.Blue),
            modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 6.dp),
        )
        if (expanded) {
            for (line in result.xpLines) {
                Row(Modifier.fillMaxWidth()) {
                    Text(line.label, style = AriseType.small, modifier = Modifier.weight(1f))
                    Text((if (line.amount >= 0) "+" else "") + line.amount, style = AriseType.small.copy(color = if (line.amount >= 0) AriseColors.Cyan else AriseColors.Red))
                }
            }
            if (result.xpLines.isEmpty()) Text("No XP change.", style = AriseType.small)
        }
    }
}

@Composable
private fun YesterdayPanel(y: DayView, now: java.time.LocalDateTime, onEdit: ((DayEntity) -> DayEntity) -> Unit, onSeal: () -> Unit) {
    val r = y.record
    val deadline = DayClock.phoneDeadline(LocalDate.ofEpochDay(y.epochDay))
    SystemPanel(title = "YESTERDAY'S REPORT", accent = AriseColors.Gold) {
        Text(
            "Report yesterday's phone usage from AppBlock. Closes in ${countdown(now, deadline)}. Everything else is locked.",
            style = AriseType.small,
        )
        Spacer(Modifier.height(10.dp))
        QuestRow(y.result(Keys.SCREEN), "Screen time", "max ${hm(y.goals.screenMinutes)}") {
            DurationField(r.screenMinutes, { v -> onEdit { it.copy(screenMinutes = v) } }, y.epochDay)
        }
        QuestRow(y.result(Keys.DISTRACTION), "Distraction", "max ${y.goals.distractionPct}%") {
            SystemNumberField(r.distractionPct?.toString() ?: "", { v -> onEdit { it.copy(distractionPct = v.toIntOrNull()?.coerceIn(0, 100)) } }, Modifier.width(120.dp), "%", accent = AriseColors.Gold, resetKey = y.epochDay)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Yesterday: ${y.doneCount}/${y.questCount} quests " + if (y.score.questCleared) "· cleared" else "· failed so far",
            style = AriseType.small.copy(color = if (y.score.questCleared) AriseColors.Cyan else AriseColors.TextDim),
        )
        Spacer(Modifier.height(12.dp))
        SystemButton("SEAL YESTERDAY", onClick = onSeal, accent = AriseColors.Gold, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun QuestSection(title: String, source: String?, accent: Color = AriseColors.Blue, content: @Composable ColumnScope.() -> Unit) {
    SystemPanel(title = title, accent = accent) {
        if (source != null) {
            Text(source, style = AriseType.small, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun QuestRow(result: CriterionResult?, label: String, target: String, input: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (result != null) {
            QuestMark(met = result.met, modifier = Modifier.width(28.dp))
        } else {
            Spacer(Modifier.width(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = AriseType.bodyBold)
            Text(target, style = AriseType.small)
        }
        Spacer(Modifier.width(8.dp))
        input()
    }
}

/** Hours + minutes input, as AppBlock shows durations. Empty means "not reported". */
@Composable
private fun DurationField(minutes: Int?, onChange: (Int?) -> Unit, resetKey: Any) {
    var h by remember(resetKey) { mutableStateOf(minutes?.let { (it / 60).toString() } ?: "") }
    var m by remember(resetKey) { mutableStateOf(minutes?.let { (it % 60).toString() } ?: "") }
    fun push() {
        onChange(if (h.isEmpty() && m.isEmpty()) null else (h.toIntOrNull() ?: 0) * 60 + (m.toIntOrNull() ?: 0))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SystemNumberField(h, { h = it.take(2); push() }, Modifier.width(66.dp), "h", resetKey = resetKey)
        Spacer(Modifier.width(6.dp))
        SystemNumberField(m, { m = it.take(2); push() }, Modifier.width(72.dp), "m", resetKey = resetKey)
    }
}
