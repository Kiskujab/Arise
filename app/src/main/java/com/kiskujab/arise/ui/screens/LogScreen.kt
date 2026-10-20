package com.kiskujab.arise.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiskujab.arise.core.Codec
import com.kiskujab.arise.core.Grade
import com.kiskujab.arise.data.DayEntity
import com.kiskujab.arise.data.EventEntity
import com.kiskujab.arise.ui.MainViewModel
import com.kiskujab.arise.ui.components.Chip
import com.kiskujab.arise.ui.components.CountUpText
import com.kiskujab.arise.ui.components.Divider
import com.kiskujab.arise.ui.components.GlowText
import com.kiskujab.arise.ui.components.QuestMark
import com.kiskujab.arise.ui.components.SystemButton
import com.kiskujab.arise.ui.components.SystemPanel
import com.kiskujab.arise.ui.components.easeOutBack
import com.kiskujab.arise.ui.components.easeOutCubic
import com.kiskujab.arise.ui.components.reveal
import com.kiskujab.arise.ui.components.segment
import com.kiskujab.arise.ui.components.tilt3d
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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

private val GRADES = listOf("S", "A", "B", "C", "D", "E", "F")

/** Slides an element in from the side the first time it's composed (also as lists scroll). */
private fun Modifier.slideIn(index: Int): Modifier = composed {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index % 6) * 55L)
        anim.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 220f))
    }
    graphicsLayer {
        alpha = anim.value.coerceIn(0f, 1f)
        translationX = (1f - anim.value) * 120.dp.toPx()
        rotationY = (1f - anim.value) * -25f
        cameraDistance = 12f * density
    }
}

@Composable
fun LogScreen(vm: MainViewModel) {
    val days by vm.history.collectAsStateWithLifecycle()
    val events by vm.eventLog.collectAsStateWithLifecycle()
    var showSystemLog by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<DayEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.reveal(0)) {
                GlowText("CHRONICLE", AriseType.title.copy(fontSize = 22.sp, letterSpacing = 6.sp))
                Text("Every day the System has judged. None of it can be erased.", style = AriseType.small)
            }
        }
        item { TrajectoryPanel(days, Modifier.reveal(1)) }
        item { StatTiles(days, Modifier.reveal(2)) }
        item {
            SystemPanel(title = "GRADES", modifier = Modifier.reveal(3)) {
                GradeBars(days)
            }
        }
        item {
            SystemPanel(title = "RECORD", modifier = Modifier.reveal(4)) {
                Heatmap(days)
            }
        }
        item {
            SegmentSwitch(listOf("DAYS", "SYSTEM LOG"), if (showSystemLog) 1 else 0, Modifier.reveal(5)) { showSystemLog = it == 1 }
        }
        if (!showSystemLog) {
            if (days.isEmpty()) {
                item { Text("No finalized days yet. Your first record appears after the day's report window closes.", style = AriseType.small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
            itemsIndexed(days, key = { _, d -> d.epochDay }) { i, d ->
                TimelineDay(d, first = i == 0, last = i == days.lastIndex, modifier = Modifier.slideIn(i)) { selected = d }
            }
        } else {
            itemsIndexed(events, key = { _, e -> e.id }) { i, e -> TerminalEntry(e, Modifier.slideIn(i)) }
        }
    }

    selected?.let { d -> DayDetail(d) { selected = null } }
}

// ------------------------------------------------------------------ trajectory chart

@Composable
private fun TrajectoryPanel(days: List<DayEntity>, modifier: Modifier) {
    val recent = remember(days) { days.take(30).reversed() }
    SystemPanel(title = "TRAJECTORY", modifier = modifier) {
        if (recent.size < 2) {
            Text("The trajectory appears after two recorded days.", style = AriseType.small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            return@SystemPanel
        }
        val first = recent.first().levelAfter
        val last = recent.last().levelAfter
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LEVEL · LAST ${recent.size} DAYS", style = AriseType.label)
                val diff = last - first
                Text(
                    when {
                        diff > 0 -> "▲ $diff level${if (diff == 1) "" else "s"} gained"
                        diff < 0 -> "▼ ${-diff} level${if (diff == -1) "" else "s"} lost"
                        else -> "Holding steady"
                    },
                    style = AriseType.bodyBold.copy(color = if (diff >= 0) AriseColors.Cyan else AriseColors.Red),
                )
            }
            GlowText("LV $last", AriseType.number)
        }
        Spacer(Modifier.height(10.dp))
        TrajectoryChart(recent, Modifier.fillMaxWidth().height(180.dp))
    }
}

@Composable
private fun TrajectoryChart(days: List<DayEntity>, modifier: Modifier) {
    val draw = remember(days.size) { Animatable(0f) }
    LaunchedEffect(days.size) { draw.animateTo(1f, tween(1600, easing = FastOutSlowInEasing)) }
    val t = rememberInfiniteTransition(label = "chart")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600)), label = "pulse")
    val measurer = rememberTextMeasurer()
    val labelStyle = AriseType.label.copy(fontSize = 11.sp, color = AriseColors.TextFaint)
    Canvas(modifier) {
        val n = days.size
        val minL = days.minOf { it.levelAfter } - 1
        val maxL = days.maxOf { it.levelAfter } + 1
        val left = 30.dp.toPx()
        val lineTop = 6.dp.toPx()
        val lineBottom = size.height * 0.66f
        val barMid = size.height * 0.86f
        val barHalf = size.height * 0.12f
        val stepX = (size.width - left) / (n - 1)
        fun x(i: Int) = left + i * stepX
        fun y(level: Int) = lineBottom - (level - minL).toFloat() / (maxL - minL).coerceAtLeast(1) * (lineBottom - lineTop)

        // Grid with level labels.
        for (k in 0..2) {
            val lvl = minL + (maxL - minL) * k / 2
            val gy = y(lvl)
            drawLine(AriseColors.Blue.copy(alpha = 0.12f), Offset(left, gy), Offset(size.width, gy), 1.dp.toPx())
            val layout = measurer.measure("$lvl", labelStyle)
            drawText(layout, topLeft = Offset(0f, gy - layout.size.height / 2f))
        }
        drawLine(AriseColors.Blue.copy(alpha = 0.25f), Offset(left, barMid), Offset(size.width, barMid), 1.dp.toPx())

        val line = Path()
        val area = Path()
        days.forEachIndexed { i, d ->
            val p = Offset(x(i), y(d.levelAfter))
            if (i == 0) { line.moveTo(p.x, p.y); area.moveTo(p.x, lineBottom); area.lineTo(p.x, p.y) } else { line.lineTo(p.x, p.y); area.lineTo(p.x, p.y) }
        }
        area.lineTo(x(n - 1), lineBottom)
        area.close()

        val progress = draw.value
        val revealX = left + (size.width - left) * progress
        clipRect(right = revealX) {
            drawPath(area, Brush.verticalGradient(listOf(AriseColors.Cyan.copy(alpha = 0.30f), Color.Transparent), lineTop, lineBottom))
        }
        val measure = PathMeasure()
        measure.setPath(line, false)
        val seg = Path()
        measure.getSegment(0f, measure.length * progress, seg, true)
        drawPath(seg, AriseColors.Cyan.copy(alpha = 0.3f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(seg, AriseColors.Cyan, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val maxAbs = days.maxOf { abs(it.xpDelta) }.coerceAtLeast(1)
        days.forEachIndexed { i, d ->
            val shown = segment(progress, i / n.toFloat() * 0.85f, 0.15f)
            if (shown <= 0f) return@forEachIndexed
            val h = d.xpDelta.toFloat() / maxAbs * barHalf * easeOutBack(shown)
            val c = if (d.xpDelta >= 0) AriseColors.Cyan else AriseColors.Red
            val w = (stepX * 0.55f).coerceIn(2.dp.toPx(), 10.dp.toPx())
            drawRect(c.copy(alpha = 0.75f), Offset(x(i) - w / 2, if (h >= 0) barMid - h else barMid), Size(w, abs(h)))
            val p = Offset(x(i), y(d.levelAfter))
            drawCircle(gradeColor(d.grade), 3.dp.toPx() * easeOutBack(shown), p)
        }
        if (progress >= 1f) {
            val p = Offset(x(n - 1), y(days.last().levelAfter))
            drawCircle(AriseColors.Cyan.copy(alpha = 1f - pulse), 4.dp.toPx() + 14.dp.toPx() * pulse, p, style = Stroke(1.5.dp.toPx()))
            drawCircle(Color.White, 3.5.dp.toPx(), p)
        }
    }
}

// ------------------------------------------------------------------ tiles & bars

private fun gradeScore(g: String?): Int = when (g) {
    "S" -> 6; "A" -> 5; "B" -> 4; "C" -> 3; "D" -> 2; "E" -> 1; else -> 0
}

@Composable
private fun StatTiles(days: List<DayEntity>, modifier: Modifier) {
    val recent = days.take(30)
    val clears = recent.count { it.questCleared }
    val rate = if (recent.isEmpty()) 0 else (clears * 100.0 / recent.size).roundToInt()
    val avg = if (recent.isEmpty()) null else GRADES.reversed()[(recent.map { gradeScore(it.grade) }.average()).roundToInt().coerceIn(0, 6)]
    val best = recent.maxOfOrNull { it.xpDelta } ?: 0
    val total = recent.sumOf { it.xpDelta }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("CLEAR RATE", Modifier.weight(1f), AriseColors.Cyan) {
                CountUpText(rate.toLong(), AriseType.number.copy(fontSize = 24.sp), durationMillis = 1100, glow = AriseColors.Cyan, suffix = "%")
            }
            Tile("AVG GRADE", Modifier.weight(1f), gradeColor(avg)) {
                GradeHex(avg ?: "–", 40.dp)
            }
            Tile("BEST DAY", Modifier.weight(1f), AriseColors.Gold) {
                CountUpText(best.toLong(), AriseType.number.copy(fontSize = 24.sp), durationMillis = 1100, glow = AriseColors.Gold, prefix = "+")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("EXP · 30 DAYS", Modifier.weight(1f), if (total >= 0) AriseColors.Blue else AriseColors.Red) {
                CountUpText(abs(total).toLong(), AriseType.number.copy(fontSize = 22.sp), durationMillis = 1300, prefix = if (total >= 0) "+" else "−")
            }
            Tile("S-GRADE DAYS", Modifier.weight(1f), AriseColors.Gold) {
                CountUpText(days.count { it.grade == "S" }.toLong(), AriseType.number.copy(fontSize = 22.sp), durationMillis = 1000, glow = AriseColors.Gold)
            }
        }
    }
}

@Composable
private fun Tile(label: String, modifier: Modifier, color: Color, content: @Composable () -> Unit) {
    SystemPanel(modifier.tilt3d(9f), accent = color) {
        Text(label, style = AriseType.label.copy(fontSize = 11.sp, color = color), modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) { content() }
    }
}

/** A grade as a glowing hexagon badge that spins in. */
@Composable
private fun GradeHex(grade: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val c = gradeColor(grade)
    val spin = remember(grade) { Animatable(0f) }
    LaunchedEffect(grade) { spin.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 160f)) }
    Box(
        modifier.size(size).graphicsLayer {
            rotationY = (1f - spin.value) * 360f
            cameraDistance = 10f * density
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            val path = Path()
            for (i in 0 until 6) {
                val a = Math.toRadians(-90.0 + i * 60.0)
                val px = center.x + (cos(a) * r * 0.95f).toFloat()
                val py = center.y + (sin(a) * r * 0.95f).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path, Brush.verticalGradient(listOf(c.copy(alpha = 0.35f), AriseColors.Bg.copy(alpha = 0.9f))))
            drawPath(path, c.copy(alpha = 0.25f), style = Stroke(5.dp.toPx()))
            drawPath(path, c, style = Stroke(1.5.dp.toPx()))
        }
        GlowText(grade, AriseType.title.copy(fontSize = (size.value * 0.42f).sp), color = AriseColors.Text, glow = c)
    }
}

@Composable
private fun GradeBars(days: List<DayEntity>) {
    val counts = remember(days) { GRADES.map { g -> days.count { it.grade == g } } }
    val grow = remember(days.size) { Animatable(0f) }
    LaunchedEffect(days.size) { grow.animateTo(1f, tween(1400)) }
    val measurer = rememberTextMeasurer()
    val labelStyle = AriseType.header.copy(fontSize = 12.sp, color = AriseColors.Text)
    val countStyle = AriseType.label.copy(fontSize = 12.sp, color = AriseColors.TextDim)
    val maxC = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val n = GRADES.size
        val slot = size.width / n
        val barW = slot * 0.5f
        val bottom = size.height - 22.dp.toPx()
        val top = 18.dp.toPx()
        GRADES.forEachIndexed { i, g ->
            val c = gradeColor(g)
            val f = easeOutBack(segment(grow.value, i * 0.07f, 0.5f), 1.2f)
            val h = (bottom - top) * counts[i] / maxC * f
            val x = slot * i + (slot - barW) / 2
            drawRect(c.copy(alpha = 0.10f), Offset(x, top), Size(barW, bottom - top))
            if (h > 0f) {
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), c, c.copy(alpha = 0.4f)), bottom - h, bottom), Offset(x, bottom - h), Size(barW, h))
                drawRect(c.copy(alpha = 0.35f), Offset(x - 3.dp.toPx(), bottom - h - 3.dp.toPx()), Size(barW + 6.dp.toPx(), h + 3.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
            val lab = measurer.measure(g, labelStyle)
            drawText(lab, topLeft = Offset(x + barW / 2 - lab.size.width / 2f, bottom + 4.dp.toPx()))
            val cnt = measurer.measure("${(counts[i] * easeOutCubic(grow.value)).roundToInt()}", countStyle)
            drawText(cnt, topLeft = Offset(x + barW / 2 - cnt.size.width / 2f, (bottom - h - cnt.size.height - 4.dp.toPx()).coerceAtLeast(0f)))
        }
    }
}

// ------------------------------------------------------------------ heatmap

/** Last 5 weeks, one cell per day, coloured by grade. Cells pop in; today pulses. */
@Composable
private fun Heatmap(days: List<DayEntity>) {
    val byDay = remember(days) { days.associateBy { it.epochDay } }
    val today = LocalDate.now().toEpochDay()
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, tween(1200)) }
    val t = rememberInfiniteTransition(label = "heat")
    val pulse by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "p")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (week in 4 downTo 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (dow in 6 downTo 0) {
                    val epoch = today - week * 7 - dow
                    val idx = (4 - week) * 7 + (6 - dow)
                    val d = byDay[epoch]
                    val c = when {
                        d == null -> AriseColors.BgElevated
                        d.vacation -> AriseColors.Purple.copy(alpha = 0.6f)
                        else -> gradeColor(d.grade).copy(alpha = 0.85f)
                    }
                    val f = easeOutBack(segment(pop.value, idx / 35f * 0.6f, 0.4f), 2f)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .graphicsLayer { scaleX = f; scaleY = f; alpha = f.coerceIn(0f, 1f) }
                            .background(c, RoundedCornerShape(3.dp))
                            .border(1.5.dp, if (epoch == today) AriseColors.Cyan.copy(alpha = pulse) else Color.Transparent, RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            for (g in GRADES) {
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

// ------------------------------------------------------------------ switch, timeline, terminal

@Composable
private fun SegmentSwitch(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val pos by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.6f, stiffness = 300f), label = "seg")
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(1.dp, AriseColors.Blue.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .background(AriseColors.Panel, RoundedCornerShape(6.dp)),
    ) {
        val w = maxWidth / options.size
        Box(
            Modifier
                .offset(x = w * pos)
                .width(w)
                .fillMaxHeight()
                .padding(3.dp)
                .background(Brush.horizontalGradient(listOf(AriseColors.BlueDeep.copy(alpha = 0.5f), AriseColors.Cyan.copy(alpha = 0.35f))), RoundedCornerShape(4.dp))
                .border(1.dp, AriseColors.Cyan, RoundedCornerShape(4.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (i == selected) GlowText(label, AriseType.header) else Text(label, style = AriseType.header.copy(color = AriseColors.TextDim))
                }
            }
        }
    }
}

@Composable
private fun TimelineDay(d: DayEntity, first: Boolean, last: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = gradeColor(d.grade)
    Row(modifier.fillMaxWidth().heightIn(min = 76.dp)) {
        // The spine of the timeline with the day's grade as a node.
        Box(Modifier.width(52.dp).heightIn(min = 76.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val x = size.width / 2
                drawLine(
                    Brush.verticalGradient(listOf(AriseColors.Blue.copy(alpha = if (first) 0f else 0.5f), AriseColors.Blue.copy(alpha = 0.5f), AriseColors.Blue.copy(alpha = if (last) 0f else 0.5f))),
                    Offset(x, 0f), Offset(x, size.height), 2.dp.toPx(),
                )
            }
            GradeHex(d.grade ?: "?", 42.dp)
        }
        Row(
            Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
                .tilt3d(5f)
                .border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .background(Brush.horizontalGradient(listOf(c.copy(alpha = 0.10f), AriseColors.Panel)), RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(LocalDate.ofEpochDay(d.epochDay).format(DateTimeFormatter.ofPattern("EEE, MMM d yyyy", Locale.ENGLISH)), style = AriseType.bodyBold)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        d.absent -> Chip("ABSENT", AriseColors.Red)
                        d.questCleared -> Chip("CLEARED", AriseColors.Cyan)
                        else -> Chip("FAILED", AriseColors.Red)
                    }
                    if (d.vacation) Chip("VAC", AriseColors.Purple)
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
}

/** The System log as a terminal feed. */
@Composable
private fun TerminalEntry(e: EventEntity, modifier: Modifier) {
    val c = eventAccent(e.type)
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(c.copy(alpha = 0.10f), Color.Transparent)), RoundedCornerShape(4.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
    ) {
        Canvas(Modifier.padding(top = 4.dp).size(12.dp)) {
            val r = size.minDimension / 2
            val p = Path().apply { moveTo(r, 0f); lineTo(2 * r, r); lineTo(r, 2 * r); lineTo(0f, r); close() }
            drawPath(p, c.copy(alpha = 0.3f))
            drawPath(p, c, style = Stroke(1.5.dp.toPx()))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("> ", style = AriseType.label.copy(color = c))
                Text(e.title, style = AriseType.bodyBold.copy(color = c))
            }
            Text(e.message, style = AriseType.small)
            Text(
                Instant.ofEpochMilli(e.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH)),
                style = AriseType.small.copy(color = AriseColors.TextFaint, fontSize = 12.sp),
            )
        }
    }
}

// ------------------------------------------------------------------ day detail

@Composable
private fun DayDetail(d: DayEntity, onDismiss: () -> Unit) {
    val result = remember(d) { Codec.decodeResult(d.resultJson) }
    val pop = remember(d) { Animatable(0f) }
    LaunchedEffect(d) { pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 260f)) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .graphicsLayer {
                    val s = 0.8f + 0.2f * pop.value
                    scaleX = s; scaleY = s
                    alpha = pop.value.coerceIn(0f, 1f)
                    rotationX = (1f - pop.value) * 30f
                    cameraDistance = 12f * density
                },
        ) {
            SystemPanel(title = "QUEST REPORT", accent = gradeColor(d.grade)) {
                GradeHex(d.grade ?: "?", 72.dp, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(8.dp))
                Text(LocalDate.ofEpochDay(d.epochDay).format(DateTimeFormatter.ofPattern("EEEE, MMMM d yyyy", Locale.ENGLISH)), style = AriseType.bodyBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                if (d.absent) {
                    Spacer(Modifier.height(6.dp))
                    Text("No report was made. The worst result was recorded.", style = AriseType.small.copy(color = AriseColors.Red), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                if (result != null) {
                    Divider()
                    result.results.forEachIndexed { i, r ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp).reveal(i, 50)) {
                            QuestMark(met = r.met, failed = !r.met && r.mandatory, modifier = Modifier.width(28.dp))
                            Text(r.label, style = AriseType.body, modifier = Modifier.weight(1f))
                            QualityBar(r.quality.toFloat(), if (r.met) AriseColors.Cyan else if (r.mandatory) AriseColors.Red else AriseColors.TextDim)
                            Spacer(Modifier.width(8.dp))
                            Text("${(r.quality * 100).toInt()}%", style = AriseType.bodyBold, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
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
                        Text("Safe zone or gear absorbed part of the loss.", style = AriseType.small.copy(color = AriseColors.Gold))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Total", style = AriseType.bodyBold, modifier = Modifier.weight(1f))
                        val xc = if (d.xpDelta >= 0) AriseColors.Cyan else AriseColors.Red
                        CountUpText(abs(d.xpDelta).toLong(), AriseType.number, durationMillis = 900, color = xc, glow = xc, prefix = if (d.xpDelta >= 0) "+" else "−", suffix = " XP")
                    }
                }
                Spacer(Modifier.height(14.dp))
                SystemButton("CLOSE", onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun QualityBar(q: Float, color: Color) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(q) { anim.animateTo(q.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing)) }
    Canvas(Modifier.width(54.dp).height(6.dp)) {
        drawRoundRect(AriseColors.BgElevated, cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        drawRoundRect(color, size = Size(size.width * anim.value, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
    }
}
