package com.kiskujab.arise.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kiskujab.arise.core.Goals
import com.kiskujab.arise.ui.theme.AriseType

/** Editable list of every goal. Invalid input keeps the previous value. */
@Composable
fun GoalsForm(goals: Goals, onChange: (Goals) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GoalRow("Calorie limit", "Yazio · eaten", goals.kcal.toString(), "kcal") { v -> v.toIntOrNull()?.takeIf { it in 800..6000 }?.let { onChange(goals.copy(kcal = it)) } }
        GoalRow("Protein goal", "Yazio", goals.protein.toString(), "g") { v -> v.toIntOrNull()?.takeIf { it in 10..400 }?.let { onChange(goals.copy(protein = it)) } }
        GoalRow("Steps goal", "Yazio", goals.steps.toString(), "steps") { v -> v.toIntOrNull()?.takeIf { it in 500..50000 }?.let { onChange(goals.copy(steps = it)) } }
        GoalRow("Active kcal goal", "Yazio · burned", goals.activeKcal.toString(), "kcal") { v -> v.toIntOrNull()?.takeIf { it in 10..3000 }?.let { onChange(goals.copy(activeKcal = it)) } }
        GoalRow("Screen time limit", "AppBlock", goals.screenMinutes.toString(), "min") { v -> v.toIntOrNull()?.takeIf { it in 15..1440 }?.let { onChange(goals.copy(screenMinutes = it)) } }
        GoalRow("Distraction limit", "AppBlock", goals.distractionPct.toString(), "%") { v -> v.toIntOrNull()?.takeIf { it in 1..100 }?.let { onChange(goals.copy(distractionPct = it)) } }
        GoalRow("Rubber bands", "per day", fmt(goals.bandHours), "h", decimal = true) { v -> v.toDoubleOrNull()?.takeIf { it in 1.0..24.0 }?.let { onChange(goals.copy(bandHours = it)) } }
        GoalRow("Tooth brushing", "per day", goals.brushCount.toString(), "×") { v -> v.toIntOrNull()?.takeIf { it in 1..5 }?.let { onChange(goals.copy(brushCount = it)) } }
    }
}

@Composable
private fun GoalRow(label: String, source: String, value: String, suffix: String, decimal: Boolean = false, onValue: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = AriseType.bodyBold)
            Text(source, style = AriseType.small)
        }
        Spacer(Modifier.width(8.dp))
        SystemNumberField(value, onValue, Modifier.width(132.dp), suffix = suffix, decimal = decimal)
    }
    Spacer(Modifier.height(2.dp))
}

fun fmt(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else String.format(java.util.Locale.US, "%.1f", d)
