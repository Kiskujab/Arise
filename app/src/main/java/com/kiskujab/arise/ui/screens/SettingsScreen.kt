package com.kiskujab.arise.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiskujab.arise.BuildConfig
import com.kiskujab.arise.core.CustomType
import com.kiskujab.arise.core.Rank
import com.kiskujab.arise.core.Rules
import com.kiskujab.arise.core.Stat
import com.kiskujab.arise.ui.MainViewModel
import com.kiskujab.arise.ui.components.Chip
import com.kiskujab.arise.ui.components.Divider
import com.kiskujab.arise.ui.components.GlowText
import com.kiskujab.arise.ui.components.GoalsForm
import com.kiskujab.arise.ui.components.SystemButton
import com.kiskujab.arise.ui.components.SystemNumberField
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.components.SystemStepper
import com.kiskujab.arise.ui.components.fmt
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val player by vm.player.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val pending by vm.pendingGoals.collectAsStateWithLifecycle()
    val vacations by vm.vacations.collectAsStateWithLifecycle()
    val customs by vm.customs.collectAsStateWithLifecycle()
    val notif by vm.notificationSettings.collectAsStateWithLifecycle()
    val p = player ?: return
    val t = today ?: return
    val todayEpoch = t.epochDay
    val fmtDate = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ------------------------------------------------ player
        SystemPanel(title = "PLAYER") {
            var name by remember(p.name) { mutableStateOf(p.name) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    singleLine = true,
                    textStyle = AriseType.bodyBold.copy(color = AriseColors.Text),
                    cursorBrush = SolidColor(AriseColors.Cyan),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, AriseColors.Blue.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(12.dp),
                )
                Spacer(Modifier.width(10.dp))
                SystemButton("SAVE", onClick = { vm.setName(name) }, enabled = name.isNotBlank() && name != p.name)
            }
        }

        // ------------------------------------------------ vacation
        SystemPanel(title = "VACATION MODE", accent = AriseColors.Purple) {
            val active = vacations.firstOrNull { it.covers(todayEpoch) }
            val planned = vacations.firstOrNull { it.startDay > todayEpoch }
            val status = when {
                active != null && active.endDay == todayEpoch -> "Active today · ends tonight"
                active != null -> "Active since ${LocalDate.ofEpochDay(active.startDay).format(fmtDate)}"
                planned != null -> "Starts ${LocalDate.ofEpochDay(planned.startDay).format(fmtDate)}"
                else -> "Off"
            }
            GlowText(status, AriseType.bodyBold, color = AriseColors.Purple, glow = AriseColors.Purple)
            Spacer(Modifier.height(6.dp))
            Text(
                "On vacation the quests are lenient (half movement, +15% calories, +50% screen time, 4 h less rubber bands, skincare optional), " +
                    "but every gain and loss is halved and no Penalty Quests are given. Changes take effect from tomorrow. " +
                    "The share of time you spend on vacation is always shown on your Status.",
                style = AriseType.small,
            )
            Spacer(Modifier.height(12.dp))
            val running = (active != null && active.endDay == null) || planned != null
            SystemButton(
                if (running) "END VACATION" else "START VACATION TOMORROW",
                onClick = { if (running) vm.endVacation() else vm.startVacation() },
                accent = AriseColors.Purple,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        // ------------------------------------------------ goals
        SystemPanel(title = "TARGETS") {
            var draft by remember(t.baseGoals, pending) { mutableStateOf(pending ?: t.baseGoals) }
            if (pending != null) {
                Text("New targets are scheduled for tomorrow.", style = AriseType.small.copy(color = AriseColors.Gold))
                Spacer(Modifier.height(8.dp))
            }
            GoalsForm(draft) { draft = it }
            Spacer(Modifier.height(14.dp))
            SystemButton(
                "SAVE · APPLIES TOMORROW",
                onClick = { vm.setGoals(draft) },
                enabled = draft != (pending ?: t.baseGoals),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        // ------------------------------------------------ custom quests
        SystemPanel(title = "CUSTOM QUESTS") {
            val visible = customs.filter { it.archivedDay == null || it.archivedDay > todayEpoch }
            if (visible.isEmpty()) Text("No custom quests yet.", style = AriseType.small)
            for (c in visible) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = AriseType.bodyBold)
                        val type = CustomType.valueOf(c.type)
                        val desc = when (type) {
                            CustomType.CHECK -> "Yes / No"
                            CustomType.AT_LEAST -> "At least ${fmt(c.target)} ${c.unit}"
                            CustomType.AT_MOST -> "At most ${fmt(c.target)} ${c.unit}"
                        }
                        val state = when {
                            c.createdDay > todayEpoch -> " · starts tomorrow"
                            c.archivedDay != null -> " · ends tonight"
                            else -> ""
                        }
                        Text("$desc · ${c.stat}$state", style = AriseType.small)
                    }
                    if (c.archivedDay == null) {
                        Text(
                            "RETIRE",
                            style = AriseType.label.copy(color = AriseColors.Red),
                            modifier = Modifier.clickable { vm.archiveCustom(c.id) }.padding(8.dp),
                        )
                    }
                }
            }
            Divider()
            AddCustomForm { name, type, target, unit, stat -> vm.addCustom(name, type, target, unit, stat) }
        }

        // ------------------------------------------------ notifications
        SystemPanel(title = "NOTIFICATIONS") {
            NotifRow("Morning: Daily Quest", notif.morningEnabled, notif.morningMinute,
                { e -> vm.updateNotifications { it.copy(morningEnabled = e) } },
                { m -> vm.updateNotifications { it.copy(morningMinute = m) } })
            NotifRow("Phone report reminder", notif.phoneReminderEnabled, notif.phoneReminderMinute,
                { e -> vm.updateNotifications { it.copy(phoneReminderEnabled = e) } },
                { m -> vm.updateNotifications { it.copy(phoneReminderMinute = m.coerceAtMost(11 * 60 + 45)) } })
            NotifRow("Evening warning", notif.eveningEnabled, notif.eveningMinute,
                { e -> vm.updateNotifications { it.copy(eveningEnabled = e) } },
                { m -> vm.updateNotifications { it.copy(eveningMinute = m) } })
            NotifRow("Final warning", notif.finalEnabled, notif.finalMinute,
                { e -> vm.updateNotifications { it.copy(finalEnabled = e) } },
                { m -> vm.updateNotifications { it.copy(finalMinute = m) } })
            Divider()
            ToggleRow("Daytime pings & quotes", notif.pingsEnabled) { e -> vm.updateNotifications { it.copy(pingsEnabled = e) } }
            if (notif.pingsEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("Every", style = AriseType.body, modifier = Modifier.weight(1f))
                    SystemStepper(notif.pingIntervalHours, { n -> vm.updateNotifications { it.copy(pingIntervalHours = n.coerceAtLeast(1)) } }, max = 6)
                    Text("  h", style = AriseType.label)
                }
                TimeRow("From", notif.pingStartMinute) { m -> vm.updateNotifications { it.copy(pingStartMinute = m) } }
                TimeRow("Until", notif.pingEndMinute) { m -> vm.updateNotifications { it.copy(pingEndMinute = m) } }
            }
        }

        // ------------------------------------------------ rules
        SystemPanel(title = "SYSTEM RULES", accent = AriseColors.Red) {
            val lines = listOf(
                "A perfect day is worth about 100 EXP, plus a streak bonus of up to +25%.",
                "Each quest scores 0–100%. Below 50% overall, the day costs EXP. Losses are multiplied by your level (×${String.format(Locale.US, "%.2f", Rules.levelFactor(20))} at Lv 20, ×${String.format(Locale.US, "%.2f", Rules.levelFactor(60))} at Lv 60).",
                "Calories over the limit cost EXP exponentially: +200 kcal is a scratch, +600 hurts, +1000 can drop you to your safe zone.",
                "Levels ${Rules.SAFE_ZONE_STEP}, ${Rules.SAFE_ZONE_STEP * 2}, ${Rules.SAFE_ZONE_STEP * 3}… are safe zones. Once reached, you can never fall below them.",
                "Rubber band hours over the goal and movement over the goal give bonus EXP. Self-development time only ever rewards.",
                "Failing the Daily Quest gives a Penalty Quest the next day that tightens only what went wrong. Clearing it gives EXP; failing it costs more.",
                "If you don't open the app for a day, everything counts as the worst possible result.",
                "Every 7-day streak earns a Streak Freeze. Rare items drop after cleared quests and at every safe zone.",
            )
            lines.forEach { Text("• $it", style = AriseType.small.copy(color = AriseColors.Text), modifier = Modifier.padding(vertical = 3.dp)) }
            Divider(AriseColors.Red)
            Text("RANKS", style = AriseType.label)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Rank.entries.forEach { Chip("${it.label} · Lv ${it.minLevel}", Color(it.color)) }
            }
        }

        SystemPanel(title = "ABOUT") {
            Text("Arise v${BuildConfig.VERSION_NAME}", style = AriseType.bodyBold)
            Text("The System does not forget. There is no reset.", style = AriseType.small)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCustomForm(onAdd: (String, CustomType, Double, String, Stat) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CustomType.CHECK) }
    var target by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var stat by remember { mutableStateOf(Stat.INT) }
    var formKey by remember { mutableStateOf(0) }

    Text("NEW QUEST", style = AriseType.label)
    Spacer(Modifier.height(6.dp))
    BasicTextField(
        value = name,
        onValueChange = { name = it.take(30) },
        singleLine = true,
        textStyle = AriseType.bodyBold.copy(color = AriseColors.Text),
        cursorBrush = SolidColor(AriseColors.Cyan),
        decorationBox = { inner ->
            if (name.isEmpty()) Text("Quest name (e.g. Read, Cold shower)", style = AriseType.body.copy(color = AriseColors.TextFaint))
            inner()
        },
        modifier = Modifier.fillMaxWidth().border(1.dp, AriseColors.Blue.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(12.dp),
    )
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CustomType.entries.forEach { ty -> Selectable(ty.label, ty == type) { type = ty } }
    }
    if (type != CustomType.CHECK) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SystemNumberField("", { target = it }, Modifier.weight(1f), decimal = true, placeholder = "target", resetKey = formKey)
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = unit,
                onValueChange = { unit = it.take(10) },
                singleLine = true,
                textStyle = AriseType.bodyBold.copy(color = AriseColors.Text),
                cursorBrush = SolidColor(AriseColors.Cyan),
                decorationBox = { inner ->
                    if (unit.isEmpty()) Text("unit", style = AriseType.body.copy(color = AriseColors.TextFaint))
                    inner()
                },
                modifier = Modifier.weight(1f).border(1.dp, AriseColors.Blue.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(12.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("STAT", style = AriseType.label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Stat.entries.forEach { s -> Selectable(s.name, s == stat) { stat = s } }
    }
    Spacer(Modifier.height(12.dp))
    val targetValue = if (type == CustomType.CHECK) 1.0 else target.toDoubleOrNull()
    SystemButton(
        "ADD · STARTS TOMORROW",
        onClick = {
            onAdd(name, type, targetValue ?: 1.0, unit, stat)
            name = ""; target = ""; unit = ""; formKey++
        },
        enabled = name.isNotBlank() && targetValue != null && targetValue > 0,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Selectable(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = if (selected) AriseColors.Cyan else AriseColors.TextFaint
    Text(
        label,
        style = AriseType.label.copy(color = c),
        modifier = Modifier
            .border(1.dp, c, RoundedCornerShape(3.dp))
            .background(if (selected) c.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = AriseType.body, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AriseColors.Cyan,
                checkedTrackColor = AriseColors.BlueDeep,
                uncheckedThumbColor = AriseColors.TextDim,
                uncheckedTrackColor = AriseColors.BgElevated,
            ),
        )
    }
}

@Composable
private fun NotifRow(label: String, enabled: Boolean, minute: Int, onToggle: (Boolean) -> Unit, onTime: (Int) -> Unit) {
    ToggleRow(label, enabled, onToggle)
    if (enabled) TimeRow("at", minute, onTime)
    Spacer(Modifier.height(4.dp))
}

/** Time adjustable in 15-minute steps. */
@Composable
private fun TimeRow(label: String, minute: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp)) {
        Text(label, style = AriseType.small, modifier = Modifier.weight(1f))
        Text("−", style = AriseType.title.copy(color = AriseColors.Blue), modifier = Modifier.clickable { onChange(((minute - 15) + 1440) % 1440) }.padding(horizontal = 14.dp, vertical = 4.dp))
        GlowText(String.format(Locale.US, "%02d:%02d", minute / 60, minute % 60), AriseType.number, textAlign = TextAlign.Center, modifier = Modifier.width(84.dp))
        Text("+", style = AriseType.title.copy(color = AriseColors.Blue), modifier = Modifier.clickable { onChange((minute + 15) % 1440) }.padding(horizontal = 14.dp, vertical = 4.dp))
    }
}
