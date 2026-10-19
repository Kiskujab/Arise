package com.kiskujab.arise.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType
import kotlin.random.Random

/** Text with a soft neon glow. */
@Composable
fun GlowText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = style.color,
    glow: Color = AriseColors.Blue,
    textAlign: TextAlign? = null,
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, shadow = Shadow(glow.copy(alpha = 0.85f), Offset.Zero, 18f)),
        textAlign = textAlign,
    )
}

/** Draws a glowing rounded border outside the element. */
fun Modifier.glowBorder(color: Color, radius: Dp = 6.dp, intensity: Float = 1f): Modifier = drawBehind {
    val r = radius.toPx()
    for (i in 4 downTo 1) {
        val spread = i * 2.5.dp.toPx()
        drawRoundRect(
            color = color.copy(alpha = 0.07f * intensity * (5 - i)),
            topLeft = Offset(-spread / 2, -spread / 2),
            size = Size(size.width + spread, size.height + spread),
            cornerRadius = CornerRadius(r + spread / 2),
            style = Stroke(width = spread),
        )
    }
    drawRoundRect(color = color.copy(alpha = 0.95f * intensity), cornerRadius = CornerRadius(r), style = Stroke(width = 1.2.dp.toPx()))
}

/** The System window: translucent dark panel with a glowing frame and corner brackets. */
@Composable
fun SystemPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color = AriseColors.Blue,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .glowBorder(accent)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.verticalGradient(listOf(AriseColors.Panel, Color(0xF0060B16))),
            )
            .drawBehind {
                // Corner brackets.
                val l = 14.dp.toPx()
                val w = 2.dp.toPx()
                val c = accent
                drawLine(c, Offset(0f, 0f), Offset(l, 0f), w)
                drawLine(c, Offset(0f, 0f), Offset(0f, l), w)
                drawLine(c, Offset(size.width, 0f), Offset(size.width - l, 0f), w)
                drawLine(c, Offset(size.width, 0f), Offset(size.width, l), w)
                drawLine(c, Offset(0f, size.height), Offset(l, size.height), w)
                drawLine(c, Offset(0f, size.height), Offset(0f, size.height - l), w)
                drawLine(c, Offset(size.width, size.height), Offset(size.width - l, size.height), w)
                drawLine(c, Offset(size.width, size.height), Offset(size.width, size.height - l), w)
            }
            .padding(16.dp),
    ) {
        if (title != null) {
            PanelHeader(title, accent)
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

@Composable
fun PanelHeader(title: String, accent: Color = AriseColors.Blue) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, accent))))
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .border(1.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                .background(accent.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            GlowText(title, AriseType.header, color = AriseColors.Text, glow = accent)
        }
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(accent, Color.Transparent))))
    }
}

@Composable
fun SystemButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AriseColors.Blue,
    enabled: Boolean = true,
) {
    val c = if (enabled) accent else AriseColors.TextFaint
    Box(
        modifier
            .glowBorder(c, 4.dp, if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(4.dp))
            .background(c.copy(alpha = 0.14f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlowText(text, AriseType.header.copy(color = if (enabled) AriseColors.Text else AriseColors.TextFaint), glow = c)
    }
}

/** A thin glowing progress bar. [marker] (0..1) draws a tick, e.g. the safe-zone floor. */
@Composable
fun SystemBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = AriseColors.Blue,
    height: Dp = 8.dp,
    marker: Float? = null,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(AriseColors.BgElevated)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(height / 2)),
    ) {
        val shine = rememberInfiniteTransition(label = "shine")
        val sweep by shine.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "sweep")
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color, AriseColors.Cyan)))
                .drawWithContent {
                    drawContent()
                    val x = sweep * size.width
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent), x - 30.dp.toPx(), x + 30.dp.toPx()))
                },
        )
        if (marker != null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .padding(start = maxWidth * marker.coerceIn(0f, 1f))
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(AriseColors.Gold),
                )
            }
        }
    }
}

/** Slowly rising mana particles behind everything. */
@Composable
fun ParticleBackground(modifier: Modifier = Modifier, color: Color = AriseColors.Blue) {
    val particles = remember {
        val r = Random(7)
        List(46) { Triple(r.nextFloat(), r.nextFloat(), 0.3f + r.nextFloat() * 0.7f) }
    }
    val transition = rememberInfiniteTransition(label = "particles")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val pulse by transition.animateFloat(0.6f, 1f, infiniteRepeatable(tween(3_000), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                listOf(AriseColors.BlueDeep.copy(alpha = 0.16f * pulse), Color.Transparent),
                center = Offset(size.width / 2, size.height * 0.25f),
                radius = size.maxDimension * 0.7f,
            ),
        )
        for ((x, y0, speed) in particles) {
            val y = ((y0 - t * speed) % 1f + 1f) % 1f
            val alpha = (0.15f + 0.5f * speed) * (1f - y * 0.6f)
            val px = x * size.width + kotlin.math.sin((t * 6.28f + x * 10f).toDouble()).toFloat() * 8f
            drawCircle(color.copy(alpha = alpha * 0.35f), radius = 5f * speed, center = Offset(px, y * size.height))
            drawCircle(AriseColors.Cyan.copy(alpha = alpha), radius = 1.6f * speed + 0.6f, center = Offset(px, y * size.height))
        }
    }
}

/** Numeric field in the System style. Keeps its own text so typing feels natural. */
@Composable
fun SystemNumberField(
    initial: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimal: Boolean = false,
    enabled: Boolean = true,
    accent: Color = AriseColors.Blue,
    placeholder: String = "—",
    /** Change it (e.g. the day) to reload [initial]. */
    resetKey: Any? = null,
) {
    var text by remember(resetKey) { mutableStateOf(initial) }
    Row(
        modifier
            .border(1.dp, (if (enabled) accent else AriseColors.TextFaint).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .background(AriseColors.Bg.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) {
                androidx.compose.material3.Text(placeholder, style = AriseType.number.copy(color = AriseColors.TextFaint, fontSize = AriseType.number.fontSize * 0.85f))
            }
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    val filtered = raw.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.replace(',', '.').take(7)
                    text = filtered
                    onValue(filtered)
                },
                enabled = enabled,
                singleLine = true,
                textStyle = AriseType.number.copy(fontSize = AriseType.number.fontSize * 0.85f, color = if (enabled) AriseColors.Text else AriseColors.TextDim),
                cursorBrush = SolidColor(AriseColors.Cyan),
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (suffix != null) {
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Text(suffix, style = AriseType.label)
        }
    }
}

/** − value + stepper. */
@Composable
fun SystemStepper(value: Int, onChange: (Int) -> Unit, enabled: Boolean = true, max: Int = 9) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StepButton("−", enabled && value > 0) { onChange(value - 1) }
        GlowText(value.toString(), AriseType.number, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        StepButton("+", enabled && value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = if (enabled) AriseColors.Blue else AriseColors.TextFaint
    Box(
        Modifier
            .size(36.dp)
            .border(1.dp, c, RoundedCornerShape(4.dp))
            .background(c.copy(alpha = 0.12f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(label, style = AriseType.title.copy(color = c))
    }
}

/** Yes/No toggle as a glowing check box. */
@Composable
fun SystemCheck(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = when {
        !enabled -> AriseColors.TextFaint
        checked -> AriseColors.Cyan
        else -> AriseColors.Blue
    }
    Box(
        Modifier
            .size(40.dp)
            .glowBorder(c, 4.dp, if (checked) 1f else 0.5f)
            .background(if (checked) c.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) { onChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Canvas(Modifier.size(20.dp)) {
                val w = 3.dp.toPx()
                drawLine(AriseColors.Text, Offset(size.width * 0.1f, size.height * 0.55f), Offset(size.width * 0.4f, size.height * 0.85f), w, StrokeCap.Round)
                drawLine(AriseColors.Text, Offset(size.width * 0.4f, size.height * 0.85f), Offset(size.width * 0.95f, size.height * 0.15f), w, StrokeCap.Round)
            }
        }
    }
}

/** Small status mark: ✓ met, ✕ failed, ○ pending. Completing a quest pops the mark with a ring burst. */
@Composable
fun QuestMark(met: Boolean, failed: Boolean = false, modifier: Modifier = Modifier) {
    val (txt, c) = when {
        met -> "✓" to AriseColors.Cyan
        failed -> "✕" to AriseColors.Red
        else -> "○" to AriseColors.TextFaint
    }
    val haptics = LocalHapticFeedback.current
    var previous by remember { mutableStateOf(met) }
    val scale = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(met) {
        if (met && !previous) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            launch {
                scale.snapTo(1.9f)
                scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f))
            }
            ring.snapTo(0.01f)
            ring.animateTo(1f, tween(650))
        }
        previous = met
    }
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        val r = ring.value
        if (r > 0f && r < 1f) {
            Canvas(Modifier.size(24.dp)) {
                drawCircle(AriseColors.Cyan.copy(alpha = 1f - r), radius = size.minDimension / 2 * (0.3f + r * 1.5f), style = Stroke(2.dp.toPx()))
            }
        }
        GlowText(txt, AriseType.title, modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }, color = c, glow = c)
    }
}

@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .border(1.dp, color.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        androidx.compose.material3.Text(text, style = AriseType.label.copy(color = color, fontSize = AriseType.label.fontSize * 0.9f))
    }
}

@Composable
fun Divider(color: Color = AriseColors.Blue) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .height(1.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = 0.5f), Color.Transparent))),
    )
}
