package com.kiskujab.arise.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiskujab.arise.core.Rank
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/** Fades and slides content up when it first appears, staggered by [index]. */
fun Modifier.reveal(index: Int = 0, step: Long = 70L): Modifier = composed {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * step)
        anim.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = anim.value
        translationY = (1f - anim.value) * 36.dp.toPx()
    }
}

/**
 * System text that types itself out. The full text is laid out from the start (the rest is
 * invisible) so centered lines don't jump around.
 */
@Composable
fun TypewriterText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    startDelay: Long = 0L,
    charMillis: Long = 26L,
    color: Color = style.color,
    glow: Color = AriseColors.Blue,
    textAlign: TextAlign? = null,
) {
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        delay(startDelay)
        while (shown < text.length) {
            delay(charMillis)
            shown++
        }
    }
    val visible = text.take(shown)
    val annotated = buildAnnotatedString {
        append(visible)
        withStyle(SpanStyle(color = Color.Transparent, shadow = Shadow(Color.Transparent))) { append(text.drop(shown)) }
    }
    Text(
        annotated,
        modifier = modifier,
        style = style.copy(color = color, shadow = Shadow(glow.copy(alpha = 0.85f), Offset.Zero, 18f)),
        textAlign = textAlign,
    )
}

/** A number that counts up to [target]. */
@Composable
fun CountUpText(
    target: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    durationMillis: Int = 900,
    startDelay: Long = 0L,
    color: Color = style.color,
    glow: Color = AriseColors.Blue,
    prefix: String = "",
    suffix: String = "",
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) {
        delay(startDelay)
        anim.animateTo(target.toFloat(), tween(durationMillis, easing = FastOutSlowInEasing))
    }
    GlowText(prefix + anim.value.roundToLong() + suffix, style, modifier, color, glow)
}

private fun hexagon(cx: Float, cy: Float, r: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val a = Math.toRadians(-90.0 + i * 60.0)
        val x = cx + (cos(a) * r).toFloat()
        val y = cy + (sin(a) * r).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The hunter's rank as a glowing hexagon crest with a slowly turning rune ring. */
@Composable
fun RankEmblem(rank: Rank, size: Dp, modifier: Modifier = Modifier) {
    val color = Color(rank.color)
    val t = rememberInfiniteTransition(label = "emblem")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "spin")
    val pulse by t.animateFloat(0.65f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.45f * pulse), Color.Transparent), c, r), r, c)
            rotate(spin, c) {
                drawCircle(
                    color.copy(alpha = 0.7f), r * 0.92f, c,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f, 2f, 7f))),
                )
            }
            rotate(-spin * 0.6f, c) {
                for (i in 0 until 6) {
                    val a = Math.toRadians(i * 60.0)
                    val p = Offset(c.x + (cos(a) * r * 0.82f).toFloat(), c.y + (sin(a) * r * 0.82f).toFloat())
                    drawCircle(AriseColors.Cyan.copy(alpha = 0.8f * pulse), 2.dp.toPx(), p)
                }
            }
            val hex = hexagon(c.x, c.y, r * 0.68f)
            drawPath(hex, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), AriseColors.Bg.copy(alpha = 0.9f)), c.y - r, c.y + r))
            for (i in 3 downTo 1) {
                drawPath(hex, color.copy(alpha = 0.12f * pulse), style = Stroke(i * 5.dp.toPx()))
            }
            drawPath(hex, color, style = Stroke(2.5.dp.toPx()))
            drawPath(hexagon(c.x, c.y, r * 0.58f), color.copy(alpha = 0.45f), style = Stroke(1.dp.toPx()))
        }
        val label = rank.label
        val fontSize = when {
            label.length <= 2 -> (size.value * 0.30f).sp
            else -> (size.value * 0.105f).coerceAtLeast(9f).sp
        }
        GlowText(label, AriseType.display.copy(fontSize = fontSize, letterSpacing = 1.sp), color = AriseColors.Text, glow = color)
    }
}

/** A ring that bursts outward once [progress] runs 0 → 1. */
@Composable
fun Shockwave(progress: Float, color: Color, modifier: Modifier = Modifier) {
    if (progress <= 0f || progress >= 1f) return
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 * (0.3f + progress * 1.4f)
        drawCircle(color.copy(alpha = (1f - progress) * 0.9f), r, c, style = Stroke((1f - progress) * 10.dp.toPx() + 1f))
        drawCircle(AriseColors.Cyan.copy(alpha = (1f - progress) * 0.5f), r * 0.82f, c, style = Stroke(1.5.dp.toPx()))
    }
}

/** CRT-like scanlines and a sweeping beam, for System screens. */
@Composable
fun Scanlines(modifier: Modifier = Modifier, color: Color = AriseColors.Blue) {
    val t = rememberInfiniteTransition(label = "scan")
    val sweep by t.animateFloat(-0.2f, 1.2f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "sweep")
    Canvas(modifier) {
        val step = 4.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(color.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        val by = sweep * size.height
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.10f), Color.Transparent), by - 60.dp.toPx(), by + 60.dp.toPx()),
            Offset(0f, by - 60.dp.toPx()), Size(size.width, 120.dp.toPx()),
        )
    }
}

/** Rotating light rays behind a celebration. */
@Composable
fun LightRays(color: Color, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val t = rememberInfiniteTransition(label = "rays")
    val rotation by t.animateFloat(0f, 360f, infiniteRepeatable(tween(30_000, easing = LinearEasing)), label = "rot")
    val pulse by t.animateFloat(0.7f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f * pulse * alpha), Color.Transparent), c, size.minDimension * 0.6f), size.minDimension * 0.6f, c)
        rotate(rotation, c) {
            for (i in 0 until 16) {
                rotate(i * 22.5f, c) {
                    drawLine(
                        Brush.verticalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = 0.25f * pulse * alpha), color.copy(alpha = 0f)), c.y - size.maxDimension, c.y),
                        c, Offset(c.x, c.y - size.maxDimension), strokeWidth = 6.dp.toPx(),
                    )
                }
            }
        }
    }
}
