package com.kiskujab.arise.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiskujab.arise.ui.theme.AriseColors
import com.kiskujab.arise.ui.theme.AriseType
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ------------------------------------------------------------------ easing helpers

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

/** Overshooting ease-out, for pieces that snap into place. */
fun easeOutBack(t: Float, s: Float = 1.7f): Float {
    val x = clamp01(t) - 1f
    return x * x * ((s + 1f) * x + s) + 1f
}

fun easeOutCubic(t: Float): Float {
    val x = 1f - clamp01(t)
    return 1f - x * x * x
}

fun easeInCubic(t: Float): Float {
    val x = clamp01(t)
    return x * x * x
}

/** Sub-progress of a staggered part: 0 before [start], 1 after [start] + [length]. */
fun segment(progress: Float, start: Float, length: Float): Float = clamp01((progress - start) / length)

// ------------------------------------------------------------------ the logo, in pieces

private class LogoPiece(
    val path: Path,
    val from: Offset,
    val rotation: Float,
    val delay: Float,
    val pivot: Offset,
    val draw: DrawScope.(Path) -> Unit,
)

private fun poly(vararg pts: Float): Path = Path().apply {
    moveTo(pts[0], pts[1])
    var i = 2
    while (i < pts.size) {
        lineTo(pts[i], pts[i + 1]); i += 2
    }
    close()
}

private val BLADE_FILL = arrayOf(
    0f to Color.White,
    0.35f to Color(0xFFBFF4FF),
    0.75f to Color(0xFF4DB8FF),
    1f to Color(0xFF2F48C8),
)

private fun DrawScope.blade(path: Path) {
    drawPath(path, Color(0x2E4DB8FF), style = Stroke(7f, join = StrokeJoin.Round))
    drawPath(path, Color(0x597DF9FF), style = Stroke(3f, join = StrokeJoin.Round))
    drawPath(path, Brush.verticalGradient(*BLADE_FILL, startY = 26f, endY = 82f))
    drawPath(path, Color(0xFFDFFBFF), style = Stroke(0.6f, join = StrokeJoin.Round))
}

private val LOGO_PIECES: List<LogoPiece> by lazy {
    val star = Path().apply {
        moveTo(54f, 15f); lineTo(55.4f, 21.6f); lineTo(62f, 23f); lineTo(55.4f, 24.4f)
        lineTo(54f, 31f); lineTo(52.6f, 24.4f); lineTo(46f, 23f); lineTo(52.6f, 21.6f); close()
    }
    val sword = poly(54f, 48f, 56.2f, 54f, 56.2f, 76f, 54f, 84f, 51.8f, 76f, 51.8f, 54f)
    listOf(
        LogoPiece(poly(54f, 25f, 54f, 43f, 40.5f, 73.5f, 31f, 80f), Offset(-95f, 30f), -70f, 0.00f, Offset(44f, 55f)) { blade(it) },
        LogoPiece(poly(54f, 25f, 77f, 80f, 67.5f, 73.5f, 54f, 43f), Offset(95f, 30f), 70f, 0.08f, Offset(64f, 55f)) { blade(it) },
        LogoPiece(poly(38.5f, 62.5f, 69.5f, 62.5f, 67f, 66.8f, 41f, 66.8f), Offset(0f, 90f), 180f, 0.22f, Offset(54f, 64.6f)) {
            drawPath(it, Color(0xFF7DF9FF))
            drawPath(it, Color(0xFFDFFBFF), style = Stroke(0.4f))
        },
        LogoPiece(sword, Offset(0f, -110f), 0f, 0.34f, Offset(54f, 66f)) {
            drawPath(it, Color(0x59B388FF), style = Stroke(3.5f, join = StrokeJoin.Round))
            drawPath(it, Brush.verticalGradient(0f to Color(0xFFE9DDFF), 0.6f to Color(0xFFB388FF), 1f to Color(0xFF5B2FD6), startY = 44f, endY = 86f))
            drawLine(Color(0x99FFFFFF), Offset(54f, 48f), Offset(54f, 84f), 0.5f)
        },
        LogoPiece(star, Offset(0f, -70f), 540f, 0.46f, Offset(54f, 23f)) {
            drawCircle(Color(0x597DF9FF), 3.2f, Offset(54f, 23f))
            drawPath(it, Color.White)
        },
        LogoPiece(poly(36f, 40f, 37f, 42f, 36f, 44f, 35f, 42f), Offset(-60f, -40f), 200f, 0.52f, Offset(36f, 42f)) { drawPath(it, Color(0xD97DF9FF)) },
        LogoPiece(poly(73f, 46f, 73.8f, 47.6f, 73f, 49.2f, 72.2f, 47.6f), Offset(60f, -20f), -200f, 0.56f, Offset(73f, 47.6f)) { drawPath(it, Color(0xE6B388FF)) },
        LogoPiece(poly(68f, 33f, 68.6f, 34.2f, 68f, 35.4f, 67.4f, 34.2f), Offset(40f, -60f), 160f, 0.60f, Offset(68f, 34.2f)) { drawPath(it, Color(0xCCFFFFFF)) },
        LogoPiece(poly(41f, 30f, 41.5f, 31f, 41f, 32f, 40.5f, 31f), Offset(-40f, -60f), -160f, 0.64f, Offset(41f, 31f)) { drawPath(it, Color(0xCCB388FF)) },
    )
}

/**
 * The Arise emblem drawn from its parts. At [assembly] 0 the blades, crossguard, shadow sword and
 * star are scattered off-centre; they fly in and lock together by 1. [flash] lights the outline.
 */
@Composable
fun AriseLogo(assembly: Float, modifier: Modifier = Modifier, flash: Float = 0f, aura: Float = 1f) {
    Canvas(modifier) {
        val u = size.minDimension / 108f * 0.84f
        withTransform({
            translate(size.width / 2f, size.height / 2f)
            scale(u, u, Offset.Zero)
            translate(-54f, -54f)
        }) {
            val auraAlpha = clamp01(aura) * (0.35f + 0.65f * segment(assembly, 0.6f, 0.4f))
            if (auraAlpha > 0f) {
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0x8C4DB8FF).copy(alpha = 0.55f * auraAlpha),
                        0.6f to Color(0x2E2F6BFF).copy(alpha = 0.18f * auraAlpha),
                        1f to Color.Transparent,
                        center = Offset(54f, 56f), radius = 30f + 10f * flash,
                    ),
                    radius = 30f + 10f * flash, center = Offset(54f, 56f),
                )
            }
            for (piece in LOGO_PIECES) {
                val local = segment(assembly, piece.delay, 0.36f)
                if (local <= 0f) continue
                val e = easeOutBack(local, 1.3f)
                val inv = 1f - e
                withTransform({
                    translate(piece.from.x * inv, piece.from.y * inv)
                    rotate(piece.rotation * inv, piece.pivot)
                    val sc = 0.6f + 0.4f * easeOutCubic(local)
                    scale(sc, sc, piece.pivot)
                }) {
                    if (local < 1f) {
                        // Motion trail while the piece is flying.
                        drawPath(piece.path, AriseColors.Cyan.copy(alpha = 0.35f * (1f - local)), style = Stroke(5f, join = StrokeJoin.Round))
                    }
                    piece.draw(this, piece.path)
                }
            }
            if (flash > 0f) {
                val outline = poly(54f, 25f, 77f, 80f, 67.5f, 73.5f, 54f, 43f, 40.5f, 73.5f, 31f, 80f)
                drawPath(outline, Color.White.copy(alpha = flash), style = Stroke(1.2f + 4f * flash, join = StrokeJoin.Round))
                drawCircle(Color.White.copy(alpha = 0.5f * flash), 6f + 40f * (1f - flash), Offset(54f, 52f), style = Stroke(1f))
            }
        }
    }
}

// ------------------------------------------------------------------ 3D primitives

private data class P3(val x: Float, val y: Float, val z: Float)

private fun P3.rotY(a: Float): P3 {
    val c = cos(a); val s = sin(a)
    return P3(x * c + z * s, y, -x * s + z * c)
}

private fun P3.rotX(a: Float): P3 {
    val c = cos(a); val s = sin(a)
    return P3(x, y * c - z * s, y * s + z * c)
}

/** Projects with a little perspective. Returns screen offset and depth (-1 back … 1 front). */
private fun project(p: P3, center: Offset, radius: Float, camera: Float = 3f): Pair<Offset, Float> {
    val f = camera / (camera - p.z)
    return Offset(center.x + p.x * radius * f, center.y + p.y * radius * f) to p.z
}

private val SPHERE: List<P3> by lazy {
    val n = 110
    val golden = PI * (3.0 - sqrt(5.0))
    List(n) { i ->
        val y = 1f - (i / (n - 1f)) * 2f
        val r = sqrt(1f - y * y)
        val th = (golden * i).toFloat()
        P3(cos(th) * r, y, sin(th) * r)
    }
}

/**
 * A rotating sphere of mana points with two orbiting rings — the hunter's core. Pure Canvas, so it
 * runs on every device.
 */
@Composable
fun ManaCore(size: Dp, color: Color, modifier: Modifier = Modifier, energy: Float = 1f) {
    val t = rememberInfiniteTransition(label = "core")
    val spin by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "spin")
    val orbit by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(5_000, easing = LinearEasing)), label = "orbit")
    val breathe by t.animateFloat(0.85f, 1f, infiniteRepeatable(tween(1_800), RepeatMode.Reverse), label = "breathe")
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension * 0.30f * breathe
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.40f * energy), Color.Transparent), c, r * 1.9f), r * 1.9f, c)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f * energy), color.copy(alpha = 0.25f), Color.Transparent), c, r * 0.7f), r * 0.7f, c)
        val tilt = 0.45f + 0.12f * sin(spin * 2f)
        val pts = SPHERE.map { project(it.rotY(spin).rotX(tilt), c, r) }.sortedBy { it.second }
        for ((o, z) in pts) {
            val depth = (z + 1f) / 2f
            drawCircle(color.copy(alpha = (0.15f + 0.85f * depth) * energy), (0.8f + 1.8f * depth).dp.toPx() * 0.6f, o)
            if (depth > 0.75f) drawCircle(Color.White.copy(alpha = (depth - 0.75f) * 3f * energy), 0.7.dp.toPx(), o)
        }
        // Rings: points on tilted circles, with a bright comet running along each.
        for (ring in 0 until 2) {
            val ringTilt = if (ring == 0) 1.15f else -0.7f
            val rr = if (ring == 0) 1.45f else 1.7f
            val steps = 72
            for (i in 0 until steps) {
                val a = (i / steps.toFloat()) * 2f * PI.toFloat()
                val p = P3(cos(a) * rr, 0f, sin(a) * rr).rotX(ringTilt).rotY(spin * (if (ring == 0) 0.6f else -0.4f))
                val (o, z) = project(p, c, r)
                val headDist = ((a - orbit * (if (ring == 0) 1f else -1.3f)) % (2 * PI).toFloat() + 2 * PI.toFloat()) % (2 * PI).toFloat()
                val head = 1f - (headDist / (2 * PI.toFloat()))
                val depth = (z / rr + 1f) / 2f
                val glow = if (head > 0.85f) (head - 0.85f) / 0.15f else 0f
                drawCircle(
                    (if (ring == 0) AriseColors.Cyan else AriseColors.Purple).copy(alpha = ((0.12f + 0.3f * depth) + glow * 0.8f) * energy),
                    (0.6f + 1.6f * glow).dp.toPx(), o,
                )
            }
        }
    }
}

private class Face(val pts: List<Pair<Offset, Float>>, val lit: Float, val normalZ: Float, val upper: Boolean)

/**
 * A slowly turning crystal with one facet pair per quest. Lit facets are the quests that are
 * currently met.
 */
@Composable
fun QuestCrystal(lit: Int, total: Int, size: Dp, modifier: Modifier = Modifier, color: Color = AriseColors.Cyan) {
    val t = rememberInfiniteTransition(label = "crystal")
    val spin by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "spin")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2_400), RepeatMode.Reverse), label = "bob")
    val n = total.coerceIn(3, 12)
    val litAnim by androidx.compose.animation.core.animateFloatAsState(lit.toFloat(), tween(700), label = "lit")
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2 + bob * 3.dp.toPx())
        val r = this.size.minDimension * 0.36f
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.10f + 0.25f * (litAnim / n)), Color.Transparent), c, r * 1.6f), r * 1.6f, c)
        val top = P3(0f, -1.25f, 0f)
        val bottom = P3(0f, 1.0f, 0f)
        val ring = List(n) { i -> val a = i * 2f * PI.toFloat() / n; P3(cos(a) * 0.75f, -0.1f, sin(a) * 0.75f) }
        fun tr(p: P3) = p.rotY(spin).rotX(0.32f)
        val faces = mutableListOf<Face>()
        for (i in 0 until n) {
            val a = tr(ring[i]); val b = tr(ring[(i + 1) % n])
            for (upper in listOf(true, false)) {
                val apex = tr(if (upper) top else bottom)
                // Face normal (for shading and back-face culling).
                val ux = a.x - apex.x; val uy = a.y - apex.y; val uz = a.z - apex.z
                val vx = b.x - apex.x; val vy = b.y - apex.y; val vz = b.z - apex.z
                var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
                if (!upper) { nx = -nx; ny = -ny; nz = -nz }
                val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-4f)
                val litness = clamp01(litAnim - i)
                faces += Face(listOf(project(apex, c, r), project(a, c, r), project(b, c, r)), litness, -nz / len, upper)
            }
        }
        for (f in faces.sortedBy { it.pts.sumOf { p -> p.second.toDouble() } }) {
            val path = Path().apply {
                moveTo(f.pts[0].first.x, f.pts[0].first.y); lineTo(f.pts[1].first.x, f.pts[1].first.y); lineTo(f.pts[2].first.x, f.pts[2].first.y); close()
            }
            val facing = f.normalZ
            val shade = 0.35f + 0.65f * clamp01((facing + 1f) / 2f)
            val base = if (f.upper) 1f else 0.75f
            if (facing < 0f) {
                drawPath(path, color.copy(alpha = 0.05f + 0.10f * f.lit))
            } else {
                val lit = f.lit
                drawPath(path, AriseColors.BgElevated.copy(alpha = 0.55f * (1f - lit)))
                if (lit > 0f) drawPath(path, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f * lit * shade), color.copy(alpha = 0.75f * lit * shade * base)), f.pts[0].first, f.pts[2].first))
                drawPath(path, color.copy(alpha = 0.25f + 0.6f * shade * (0.4f + 0.6f * lit)), style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
            }
        }
    }
}

/** A gold coin spinning around its vertical axis. */
@Composable
fun SpinningCoin(size: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "coin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(3_200, easing = LinearEasing)), label = "a")
    Canvas(
        modifier.size(size).graphicsLayer {
            rotationY = angle
            cameraDistance = 8f * density
        },
    ) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension / 2
        val front = cos(Math.toRadians(angle.toDouble())).toFloat()
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A8), AriseColors.Gold, Color(0xFFB8860B)), c, r), r, c)
        drawCircle(Color(0xFF8A6408), r * 0.78f, c, style = Stroke(1.2.dp.toPx()))
        // A simple crown mark; mirrored on the back like a real coin would be.
        val w = r * 0.9f
        val crown = Path().apply {
            moveTo(c.x - w / 2, c.y + w * 0.25f); lineTo(c.x - w / 2, c.y - w * 0.2f); lineTo(c.x - w / 4, c.y + w * 0.02f)
            lineTo(c.x, c.y - w * 0.32f); lineTo(c.x + w / 4, c.y + w * 0.02f); lineTo(c.x + w / 2, c.y - w * 0.2f); lineTo(c.x + w / 2, c.y + w * 0.25f); close()
        }
        drawPath(crown, Color(0xFF8A6408).copy(alpha = 0.85f))
        val shine = abs(front)
        drawCircle(Color.White.copy(alpha = 0.35f * shine * shine), r * 0.3f, Offset(c.x - r * 0.35f, c.y - r * 0.35f))
    }
}

// ------------------------------------------------------------------ interaction

/** Tilts the element towards the finger, like a card picked up in a game, and springs back. */
fun Modifier.tilt3d(maxDegrees: Float = 7f): Modifier = composed {
    val rx = remember { Animatable(0f) }
    val ry = remember { Animatable(0f) }
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun tiltTo(pos: Offset) {
                    val nx = ((pos.x / size.width) - 0.5f) * 2f
                    val ny = ((pos.y / size.height) - 0.5f) * 2f
                    scope.launch { ry.animateTo(nx.coerceIn(-1f, 1f) * maxDegrees, spring(stiffness = 600f)) }
                    scope.launch { rx.animateTo(-ny.coerceIn(-1f, 1f) * maxDegrees, spring(stiffness = 600f)) }
                }
                tiltTo(down.position)
                scope.launch { press.animateTo(0.975f, spring(stiffness = 800f)) }
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed || change.isConsumed) break
                    tiltTo(change.position)
                }
                scope.launch { rx.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 300f)) }
                scope.launch { ry.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 300f)) }
                scope.launch { press.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f)) }
            }
        }
        .graphicsLayer {
            rotationX = rx.value
            rotationY = ry.value
            scaleX = press.value
            scaleY = press.value
            cameraDistance = 14f * density
        }
}

/**
 * Hold to confirm: the button charges while pressed and fires once full, with a burst. Used for
 * actions that can't be undone, so they can't happen by accident.
 */
@Composable
fun HoldButton(
    text: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AriseColors.Blue,
    enabled: Boolean = true,
    holdMillis: Int = 1100,
) {
    val haptics = LocalHapticFeedback.current
    val charge = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val complete by rememberUpdatedState(onComplete)
    val c = if (enabled) accent else AriseColors.TextFaint
    val idle = rememberInfiniteTransition(label = "hold")
    val pulse by idle.animateFloat(0.5f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .glowBorder(c, 4.dp, if (enabled) 0.6f + 0.4f * maxOf(charge.value, pulse * 0.5f) else 0.4f)
                .clip(RoundedCornerShape(4.dp))
                .drawWithContent {
                    drawRect(c.copy(alpha = 0.12f))
                    drawRect(
                        Brush.horizontalGradient(listOf(c.copy(alpha = 0.25f), c.copy(alpha = 0.7f), AriseColors.Cyan.copy(alpha = 0.9f))),
                        size = Size(size.width * charge.value, size.height),
                    )
                    if (charge.value > 0f && charge.value < 1f) {
                        val x = size.width * charge.value
                        drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                    }
                    drawContent()
                    if (burst.value > 0f && burst.value < 1f) drawRect(Color.White.copy(alpha = 0.8f * (1f - burst.value)))
                }
                .graphicsLayer {
                    val s = 1f - 0.04f * charge.value + 0.08f * sin(burst.value * PI.toFloat())
                    scaleX = s; scaleY = s
                    translationX = if (charge.value > 0.6f && charge.value < 1f) sin(charge.value * 120f) * 2.dp.toPx() * charge.value else 0f
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onPress = {
                        val job: Job = scope.launch {
                            var lastTick = 0
                            charge.animateTo(1f, tween(((1f - charge.value) * holdMillis).toInt().coerceAtLeast(1), easing = LinearEasing)) {
                                val tick = (value * 5).toInt()
                                if (tick > lastTick) { lastTick = tick; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            launch { burst.snapTo(0.01f); burst.animateTo(1f, tween(500)) }
                            complete()
                            charge.animateTo(0f, tween(400))
                        }
                        tryAwaitRelease()
                        if (charge.value < 1f && job.isActive) {
                            job.cancel()
                            scope.launch { charge.animateTo(0f, tween(250)) }
                        }
                    })
                }
                .padding(horizontal = 22.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            GlowText(text, AriseType.header.copy(color = if (enabled) AriseColors.Text else AriseColors.TextFaint), glow = c)
        }
        Text(
            if (enabled) "HOLD TO CONFIRM" else "",
            style = AriseType.label.copy(fontSize = 10.sp, color = c.copy(alpha = 0.5f + 0.5f * pulse)),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ------------------------------------------------------------------ charts & glyphs

/** Six stats as a glowing hexagonal radar that draws itself and keeps scanning. */
@Composable
fun RadarChart(values: List<Pair<String, Int>>, modifier: Modifier = Modifier, color: Color = AriseColors.Cyan) {
    val measurer = rememberTextMeasurer()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 90f)) }
    val t = rememberInfiniteTransition(label = "radar")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4_000, easing = LinearEasing)), label = "sweep")
    val pulse by t.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1_300), RepeatMode.Reverse), label = "pulse")
    val labelStyle = AriseType.label.copy(fontSize = 12.sp, color = AriseColors.Text)
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 * 0.70f
        val n = values.size.coerceAtLeast(3)
        val maxV = (values.maxOfOrNull { it.second } ?: 1).coerceAtLeast(12) * 1.15f
        fun pt(i: Int, f: Float): Offset {
            val a = Math.toRadians(-90.0 + i * 360.0 / n)
            return Offset(c.x + (cos(a) * r * f).toFloat(), c.y + (sin(a) * r * f).toFloat())
        }
        for (ring in 1..4) {
            val f = ring / 4f
            val p = Path().apply { for (i in 0 until n) { val o = pt(i, f); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }; close() }
            drawPath(p, AriseColors.Blue.copy(alpha = 0.10f + 0.05f * ring), style = Stroke(1.dp.toPx()))
        }
        for (i in 0 until n) drawLine(AriseColors.Blue.copy(alpha = 0.25f), c, pt(i, 1f), 1.dp.toPx())
        rotate(sweep, c) {
            drawArc(
                Brush.sweepGradient(listOf(Color.Transparent, Color.Transparent, color.copy(alpha = 0.22f)), c),
                -90f - 60f, 60f, true, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
            )
        }
        val g = grow.value
        val data = Path().apply {
            values.forEachIndexed { i, (_, v) ->
                val o = pt(i, (v / maxV).coerceIn(0.08f, 1f) * g)
                if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
            close()
        }
        drawPath(data, Brush.radialGradient(listOf(color.copy(alpha = 0.45f), AriseColors.BlueDeep.copy(alpha = 0.25f)), c, r))
        drawPath(data, color.copy(alpha = 0.35f * pulse), style = Stroke(6.dp.toPx(), join = StrokeJoin.Round))
        drawPath(data, color, style = Stroke(1.8.dp.toPx(), join = StrokeJoin.Round))
        values.forEachIndexed { i, (label, v) ->
            val o = pt(i, (v / maxV).coerceIn(0.08f, 1f) * g)
            drawCircle(Color.White, 2.5.dp.toPx(), o)
            drawCircle(color.copy(alpha = 0.5f * pulse), 5.dp.toPx(), o)
            val text = "$label $v"
            val layout = measurer.measure(text, labelStyle)
            val lp = pt(i, 1.24f)
            drawText(layout, topLeft = Offset(lp.x - layout.size.width / 2f, lp.y - layout.size.height / 2f))
        }
    }
}

/**
 * A flame of mana for the login streak. [heat] 0 is a dying ember (no activity yet today), 1 a
 * full flame. The colour climbs with the streak.
 */
@Composable
fun MonarchFlame(streak: Int, heat: Float, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "flame")
    val time by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1_400, easing = LinearEasing)), label = "t")
    val slow by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_600, easing = LinearEasing)), label = "s")
    val (outer, mid) = flameColors(streak)
    val embers = remember { val r = Random(11); List(14) { Triple(r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat()) } }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val base = h * 0.92f
        val cx = w / 2
        val hh = h * (0.35f + 0.55f * heat)
        drawCircle(Brush.radialGradient(listOf(outer.copy(alpha = 0.35f * (0.3f + heat)), Color.Transparent), Offset(cx, base - hh * 0.35f), w * 0.6f), w * 0.6f, Offset(cx, base - hh * 0.35f))
        fun flame(width: Float, height: Float, phase: Float, brush: Brush) {
            val sway = sin(time + phase) * width * 0.18f
            val tip = sin(time * 2f + phase) * width * 0.25f
            val p = Path().apply {
                moveTo(cx, base)
                cubicTo(cx - width * 0.95f, base - height * 0.05f, cx - width * 0.75f + sway, base - height * 0.55f, cx + tip, base - height)
                cubicTo(cx + width * 0.55f + sway, base - height * 0.6f, cx + width * 1.0f, base - height * 0.1f, cx, base)
                close()
            }
            drawPath(p, brush)
        }
        flame(w * 0.42f, hh, 0f, Brush.verticalGradient(listOf(outer.copy(alpha = 0f), outer.copy(alpha = 0.85f), outer), base - hh, base))
        flame(w * 0.30f, hh * 0.78f, 1.3f, Brush.verticalGradient(listOf(mid.copy(alpha = 0.1f), mid, mid), base - hh * 0.78f, base))
        flame(w * 0.17f, hh * 0.5f, 2.6f, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White), base - hh * 0.5f, base))
        if (heat > 0.3f) {
            for ((x0, phase, speed) in embers) {
                val life = (slow * speed + phase) % 1f
                val x = cx + (x0 - 0.5f) * w * 0.7f + sin(life * 8f + x0 * 6f) * w * 0.05f
                val y = base - hh * 0.4f - life * h * 0.6f
                drawCircle(mid.copy(alpha = (1f - life) * heat), (1.6f * (1f - life) + 0.6f).dp.toPx(), Offset(x, y))
            }
        }
    }
}

fun flameColors(streak: Int): Pair<Color, Color> = when {
    streak >= 100 -> AriseColors.Gold to Color(0xFFFFF3C4)
    streak >= 30 -> AriseColors.Purple to Color(0xFFE9DDFF)
    streak >= 7 -> AriseColors.BlueDeep to AriseColors.Cyan
    else -> Color(0xFF2F48C8) to AriseColors.Blue
}

/** Digits that roll vertically when the value changes. */
@Composable
fun RollingNumber(value: Long, style: TextStyle, modifier: Modifier = Modifier, glow: Color = AriseColors.Blue, color: Color = style.color) {
    var previous by remember { androidx.compose.runtime.mutableLongStateOf(value) }
    val up = value >= previous
    LaunchedEffect(value) { previous = value }
    Row(modifier) {
        val digits = value.toString()
        digits.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val dir = if (up) 1 else -1
                    (slideInVertically(spring(dampingRatio = 0.6f, stiffness = 300f)) { it * dir } + fadeIn()) togetherWith
                        (slideOutVertically(tween(220)) { -it * dir } + fadeOut(tween(220)))
                },
                label = "digit$i",
            ) { c -> GlowText(c.toString(), style, color = color, glow = glow) }
        }
    }
}

/** Text split into red and cyan ghosts — the System glitching under too much power. */
@Composable
fun ChromaticText(text: String, style: TextStyle, split: Float, modifier: Modifier = Modifier, glow: Color = AriseColors.Cyan, color: Color = AriseColors.Text) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (split != 0f) {
            androidx.compose.material3.Text(
                text, style = style.copy(color = Color(0xFFFF2E63).copy(alpha = 0.75f)),
                modifier = Modifier.graphicsLayer { translationX = -split * density; translationY = split * 0.3f * density },
            )
            androidx.compose.material3.Text(
                text, style = style.copy(color = AriseColors.Cyan.copy(alpha = 0.75f)),
                modifier = Modifier.graphicsLayer { translationX = split * density; translationY = -split * 0.3f * density },
            )
        }
        GlowText(text, style, color = color, glow = glow)
    }
}

/** A magic circle that draws itself: outer ring, rune ticks, and a turning hexagram. */
@Composable
fun HexSigil(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "sigil")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "rot")
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 * 0.92f
        val p = clamp01(progress)
        val sw = 1.5.dp.toPx()
        drawArc(color.copy(alpha = 0.8f), -90f, 360f * segment(p, 0f, 0.5f), false, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(sw * 1.4f))
        drawArc(color.copy(alpha = 0.45f), 90f, -360f * segment(p, 0.1f, 0.5f), false, topLeft = Offset(c.x - r * 0.86f, c.y - r * 0.86f), size = Size(r * 1.72f, r * 1.72f), style = Stroke(sw))
        rotate(rot, c) {
            val ticks = 36
            val shown = (ticks * segment(p, 0.25f, 0.5f)).toInt()
            for (i in 0 until shown) {
                val a = Math.toRadians(i * 360.0 / ticks)
                val r1 = r * 0.88f; val r2 = r * (if (i % 3 == 0) 0.78f else 0.83f)
                drawLine(color.copy(alpha = 0.7f), Offset(c.x + (cos(a) * r1).toFloat(), c.y + (sin(a) * r1).toFloat()), Offset(c.x + (cos(a) * r2).toFloat(), c.y + (sin(a) * r2).toFloat()), sw)
            }
        }
        rotate(-rot * 1.5f, c) {
            val tri = segment(p, 0.45f, 0.55f)
            if (tri > 0f) {
                for (k in 0 until 2) {
                    val path = Path()
                    for (i in 0..3) {
                        val a = Math.toRadians(-90.0 + k * 60.0 + i * 120.0)
                        val o = Offset(c.x + (cos(a) * r * 0.74f).toFloat(), c.y + (sin(a) * r * 0.74f).toFloat())
                        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                    }
                    drawPath(path, color.copy(alpha = 0.6f * tri), style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 4f * tri + 1f, r * 4f))))
                }
            }
        }
    }
}

/** Streaks rushing into the centre as [progress] goes 0 → 1. */
@Composable
fun Implosion(progress: Float, color: Color, modifier: Modifier = Modifier, count: Int = 70) {
    val parts = remember(count) { val r = Random(5); List(count) { Triple(r.nextFloat() * 2f * PI.toFloat(), 0.7f + r.nextFloat() * 0.8f, r.nextFloat() * 0.35f) } }
    if (progress <= 0f || progress >= 1f) return
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val maxR = size.maxDimension * 0.75f
        for ((a, dist, delay) in parts) {
            val local = segment(progress, delay, 1f - delay)
            if (local <= 0f) continue
            val e = easeInCubic(local)
            val r0 = maxR * dist * (1f - e)
            val r1 = r0 + maxR * 0.12f * (0.3f + e)
            val dx = cos(a); val dy = sin(a)
            drawLine(
                Brush.linearGradient(listOf(color.copy(alpha = 0.9f * local), Color.Transparent), Offset(c.x + dx * r0, c.y + dy * r0), Offset(c.x + dx * r1, c.y + dy * r1)),
                Offset(c.x + dx * r0, c.y + dy * r0), Offset(c.x + dx * r1, c.y + dy * r1), (1f + 2f * e).dp.toPx(), StrokeCap.Round,
            )
        }
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = progress), color.copy(alpha = 0.6f * progress), Color.Transparent), c, 30.dp.toPx() + 60.dp.toPx() * progress), 30.dp.toPx() + 60.dp.toPx() * progress, c)
    }
}

/** A never-ending fountain of sparks from the bottom centre. */
@Composable
fun SparkFountain(color: Color, modifier: Modifier = Modifier, intensity: Float = 1f, origin: Float = 0.62f) {
    val parts = remember { val r = Random(21); List(60) { floatArrayOf(r.nextFloat(), (r.nextFloat() - 0.5f) * 1.4f, 0.6f + r.nextFloat() * 0.8f, r.nextFloat()) } }
    val t = rememberInfiniteTransition(label = "sparks")
    val time by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_200, easing = LinearEasing)), label = "t")
    Canvas(modifier) {
        if (intensity <= 0f) return@Canvas
        val cx = size.width / 2
        val cy = size.height * origin
        for (p in parts) {
            val life = (time * p[2] + p[0]) % 1f
            val vx = p[1] * size.width * 0.45f
            val vy = size.height * 0.55f * p[2]
            val x = cx + vx * life
            val y = cy - vy * life + size.height * 0.45f * life * life
            val a = (1f - life) * intensity
            val col = if (p[3] > 0.7f) Color.White else color
            drawCircle(col.copy(alpha = a), (2.2f * (1f - life) + 0.6f).dp.toPx(), Offset(x, y))
        }
    }
}

/** Shards of a shattered emblem flying apart as [progress] goes 0 → 1. */
@Composable
fun Shatter(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val shards = remember { val r = Random(13); List(16) { floatArrayOf(r.nextFloat() * 2f * PI.toFloat(), 0.5f + r.nextFloat(), (r.nextFloat() - 0.5f) * 720f, 6f + r.nextFloat() * 10f) } }
    if (progress <= 0f || progress >= 1f) return
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val e = easeOutCubic(progress)
        for (s in shards) {
            val d = size.minDimension * 0.6f * s[1] * e
            val o = Offset(c.x + cos(s[0]) * d, c.y + sin(s[0]) * d + size.height * 0.2f * progress * progress)
            val sz = s[3].dp.toPx()
            rotate(s[2] * e, o) {
                val p = Path().apply { moveTo(o.x, o.y - sz); lineTo(o.x + sz * 0.6f, o.y + sz * 0.5f); lineTo(o.x - sz * 0.5f, o.y + sz * 0.3f); close() }
                drawPath(p, color.copy(alpha = 1f - progress))
                drawPath(p, Color.White.copy(alpha = 0.6f * (1f - progress)), style = Stroke(1.dp.toPx()))
            }
        }
    }
}

/** A light comet that keeps running along the edge of its element. */
fun Modifier.edgeRunner(color: Color, radius: Dp = 6.dp, periodMillis: Int = 5200): Modifier = composed {
    val phase = remember { Random.nextFloat() }
    val t = rememberInfiniteTransition(label = "edge")
    val run = t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMillis, easing = LinearEasing)), label = "run")
    drawWithContent {
        drawContent()
        val w = size.width
        val h = size.height
        val perimeter = 2 * (w + h)
        if (perimeter <= 0f) return@drawWithContent
        val head = ((run.value + phase) % 1f) * perimeter
        val len = min(perimeter * 0.12f, 160.dp.toPx())
        val segs = 14
        for (i in 0 until segs) {
            val d = head - len * i / segs
            val p = pointOnRect(((d % perimeter) + perimeter) % perimeter, w, h)
            val a = 1f - i / segs.toFloat()
            drawCircle(color.copy(alpha = 0.55f * a), (2.4f * a + 0.6f).dp.toPx(), p)
        }
        drawCircle(Color.White.copy(alpha = 0.9f), 1.6.dp.toPx(), pointOnRect(head, w, h))
    }
}

private fun pointOnRect(d: Float, w: Float, h: Float): Offset = when {
    d < w -> Offset(d, 0f)
    d < w + h -> Offset(w, d - w)
    d < 2 * w + h -> Offset(w - (d - w - h), h)
    else -> Offset(0f, h - (d - 2 * w - h))
}

/** A slowly drifting 3D starfield, for depth behind the System windows. */
@Composable
fun Starfield(modifier: Modifier = Modifier, color: Color = AriseColors.Blue) {
    val stars = remember { val r = Random(31); List(70) { floatArrayOf((r.nextFloat() - 0.5f) * 2f, (r.nextFloat() - 0.5f) * 2f, r.nextFloat()) } }
    val t = rememberInfiniteTransition(label = "stars")
    val time by t.animateFloat(0f, 1f, infiniteRepeatable(tween(40_000, easing = LinearEasing)), label = "t")
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height * 0.42f)
        for (s in stars) {
            val z = ((s[2] - time) % 1f + 1f) % 1f // 1 = far, 0 = near
            val f = 1f / (0.15f + z)
            val o = Offset(c.x + s[0] * size.width * 0.18f * f, c.y + s[1] * size.height * 0.18f * f)
            if (o.x < 0 || o.x > size.width || o.y < 0 || o.y > size.height) continue
            val near = 1f - z
            drawCircle(color.copy(alpha = 0.08f + 0.35f * near * near), (0.5f + 1.6f * near * near).dp.toPx(), o)
        }
    }
}

/** Small helper so overlays can stack a dim background quickly. */
fun Modifier.dim(alpha: Float): Modifier = background(Color.Black.copy(alpha = alpha.coerceIn(0f, 1f)))
