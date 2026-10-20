package com.kiskujab.arise.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiskujab.arise.core.ItemType
import com.kiskujab.arise.core.PlayerState
import com.kiskujab.arise.core.Rules
import com.kiskujab.arise.ui.components.Divider
import com.kiskujab.arise.ui.components.GlowText
import com.kiskujab.arise.ui.components.MonarchFlame
import com.kiskujab.arise.ui.components.RollingNumber
import com.kiskujab.arise.ui.components.SystemBar
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.components.flameColors
import com.kiskujab.arise.ui.components.tilt3d
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val MILESTONES = listOf(3, 7, 14, 21, 30, 50, 75, 100, 150, 200, 250, 300, 365, 500, 730, 1000)

/** Small flame + number for the header, always in sight. */
@Composable
fun StreakChip(streak: Int, activeToday: Boolean) {
    val (outer, inner) = flameColors(streak)
    val c = if (activeToday) inner else AriseColors.TextDim
    Row(
        Modifier
            .border(1.dp, c.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .background(outer.copy(alpha = if (activeToday) 0.18f else 0.06f), RoundedCornerShape(14.dp))
            .padding(start = 4.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonarchFlame(streak, if (activeToday) 1f else 0.3f, Modifier.size(26.dp))
        RollingNumber(streak.toLong(), AriseType.number.copy(fontSize = 16.sp), glow = outer, color = if (activeToday) AriseColors.Text else AriseColors.TextDim)
    }
}

/**
 * The login streak front and centre: a living flame, the count, what today means for it, and the
 * last seven days. The quest streak and freezes sit underneath.
 */
@Composable
fun StreakHero(state: PlayerState, today: Long, modifier: Modifier = Modifier) {
    val a = state.activity
    val streak = a.shown(today)
    val active = a.activeOn(today)
    val risk = a.atRisk(today)
    val (outer, inner) = flameColors(streak)
    val t = rememberInfiniteTransition(label = "hero")
    val pulse by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
    SystemPanel(modifier.tilt3d(5f), title = "STREAK", accent = if (active) inner else AriseColors.Blue) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 84.dp, height = 104.dp)) {
                MonarchFlame(streak, if (active) 1f else if (risk) 0.45f else 0.15f, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("LOGIN STREAK", style = AriseType.label.copy(color = inner))
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingNumber(streak.toLong(), AriseType.display.copy(fontSize = 44.sp), glow = outer)
                    Text(if (streak == 1) " DAY" else " DAYS", style = AriseType.header.copy(color = AriseColors.Text), modifier = Modifier.padding(bottom = 8.dp))
                }
                val (line, color) = when {
                    active -> "Today counts. Come back tomorrow." to AriseColors.Cyan
                    risk -> "Log anything today to keep the flame alive." to AriseColors.Gold.copy(alpha = pulse)
                    else -> "Do something in Arise today to light the flame." to AriseColors.TextDim
                }
                Text(line, style = AriseType.small.copy(color = color))
                Text("Best: ${a.best} days", style = AriseType.small.copy(color = AriseColors.TextFaint))
            }
        }
        Spacer(Modifier.height(10.dp))
        WeekStrip(state, today)
        val next = MILESTONES.firstOrNull { it > streak }
        if (next != null) {
            Spacer(Modifier.height(10.dp))
            val prev = MILESTONES.lastOrNull { it <= streak } ?: 0
            Row {
                Text("Next milestone: $next days", style = AriseType.small, modifier = Modifier.weight(1f))
                Text("${next - streak} to go", style = AriseType.small.copy(color = inner))
            }
            Spacer(Modifier.height(4.dp))
            SystemBar((streak - prev).toFloat() / (next - prev), height = 5.dp, color = outer)
        }
        Divider(inner)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("QUEST STREAK", style = AriseType.label)
                Row(verticalAlignment = Alignment.Bottom) {
                    GlowText("${state.streak}", AriseType.number, glow = AriseColors.Cyan)
                    Text(" days cleared in a row", style = AriseType.small, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("FREEZES", style = AriseType.label)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val have = state.count(ItemType.STREAK_FREEZE)
                    for (i in 0 until Rules.MAX_FREEZES) Snowflake(i < have)
                }
            }
        }
    }
}

/** The last seven days: lit for days the player was active. Today pulses until it's earned. */
@Composable
private fun WeekStrip(state: PlayerState, today: Long) {
    val a = state.activity
    val first = a.lastDay - a.current + 1
    val t = rememberInfiniteTransition(label = "week")
    val pulse by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    val (outer, inner) = flameColors(a.shown(today))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (d in (today - 6)..today) {
            val lit = a.current > 0 && d in first..a.lastDay
            val isToday = d == today
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(26.dp)) {
                    val r = size.minDimension / 2
                    when {
                        lit -> {
                            drawCircle(outer.copy(alpha = 0.35f), r)
                            drawCircle(inner, r * 0.55f)
                            drawCircle(Color.White.copy(alpha = 0.8f), r * 0.22f)
                        }
                        isToday -> drawCircle(inner.copy(alpha = pulse), r * 0.8f, style = Stroke(2.dp.toPx()))
                        else -> drawCircle(AriseColors.TextFaint.copy(alpha = 0.5f), r * 0.7f, style = Stroke(1.dp.toPx()))
                    }
                }
                Text(
                    LocalDate.ofEpochDay(d).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    style = AriseType.label.copy(fontSize = 11.sp, color = if (isToday) AriseColors.Text else AriseColors.TextFaint),
                )
            }
        }
    }
}

@Composable
private fun Snowflake(owned: Boolean) {
    val c = if (owned) AriseColors.Cyan else AriseColors.TextFaint.copy(alpha = 0.5f)
    Canvas(Modifier.size(18.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.minDimension / 2
        for (i in 0 until 3) {
            val ang = Math.toRadians(90.0 + i * 60.0)
            val dx = (kotlin.math.cos(ang) * r).toFloat()
            val dy = (kotlin.math.sin(ang) * r).toFloat()
            drawLine(c, androidx.compose.ui.geometry.Offset(cx - dx, cy - dy), androidx.compose.ui.geometry.Offset(cx + dx, cy + dy), 1.8.dp.toPx())
        }
        if (owned) drawCircle(c.copy(alpha = 0.25f), r)
    }
}
