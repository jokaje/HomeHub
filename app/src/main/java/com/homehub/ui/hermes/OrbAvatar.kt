package com.homehub.ui.hermes

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.homehub.ui.theme.Teal
import com.homehub.ui.theme.Violet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Zustand des Avatars – steuert Bewegung, Farbe und Puls. */
enum class OrbState { IDLE, LISTENING, THINKING, SPEAKING }

private const val PARTICLE_COUNT = 620

private class Particle(
    var x: Float, var y: Float,
    var vx: Float = 0f, var vy: Float = 0f,
    val size: Float,
    val phase: Float,      // individueller Versatz für organisches Flackern
    val drift: Float       // wie stark der Punkt "eigenständig" wandert
)

/**
 * Der Hermes-Orb: eine lebendige Punktwolke.
 * - IDLE: ruhige Kugel mit sanfter Rotation und Atmung
 * - LISTENING: reagiert pulsierend auf die Mikrofon-Lautstärke [level]
 * - THINKING: Punkte wirbeln als Vortex
 * - SPEAKING: rhythmisches Pulsieren, während TTS spricht
 * - [shape]: Zielform; die Punkte fließen organisch in die neue Silhouette.
 */
@Composable
fun OrbAvatar(
    state: OrbState,
    shape: OrbShape,
    level: Float, // 0..1 Mikrofon-/Sprachpegel
    modifier: Modifier = Modifier
) {
    val particles = remember {
        val init = ShapeLibrary.points(OrbShape.ORB, PARTICLE_COUNT)
        val rnd = Random(42)
        init.map {
            Particle(
                x = it.x, y = it.y,
                size = 1.2f + rnd.nextFloat() * 2.2f,
                phase = rnd.nextFloat() * 2f * PI.toFloat(),
                drift = 0.4f + rnd.nextFloat() * 0.6f
            )
        }
    }
    val targets = remember(shape) { ShapeLibrary.points(shape, PARTICLE_COUNT) }

    // Frame-Treiber: löst pro Frame ein Recompose des Canvas aus
    var frameTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { frameTime = it }
        }
    }

    Canvas(modifier = modifier) {
        val t = frameTime / 1_000_000_000f
        step(particles, targets, state, level, t)
        drawOrb(particles, state, level, t)
    }
}

/** Physik-Schritt: Feder zur Zielposition + organisches Rauschen + zustandsabhängige Kräfte. */
private fun step(
    particles: List<Particle>,
    targets: List<Offset>,
    state: OrbState,
    level: Float,
    t: Float
) {
    val stiffness = if (state == OrbState.THINKING) 0.015f else 0.045f
    val damping = 0.86f

    // Globale Modulation
    val breathe = 1f + 0.04f * sin(t * 1.4f)                       // ruhiges Atmen
    val pulse = when (state) {
        OrbState.LISTENING -> 1f + level * 0.5f
        OrbState.SPEAKING -> 1f + 0.18f * sin(t * 9f) * (0.4f + level)
        else -> 1f
    }
    val scale = breathe * pulse
    val rot = when (state) {
        OrbState.IDLE -> t * 0.15f
        OrbState.THINKING -> t * 1.6f
        else -> t * 0.25f
    }
    val cosR = cos(rot); val sinR = sin(rot)

    for (i in particles.indices) {
        val p = particles[i]
        val raw = targets[i]
        // Zielpunkt: rotiert (nur Orb/Thinking rotieren als Ganzes, Formen bleiben aufrecht)
        val target = if (state == OrbState.THINKING) {
            Offset(raw.x * cosR - raw.y * sinR, raw.x * sinR + raw.y * cosR)
        } else raw

        // Organisches Rauschen: zwei überlagerte Sinusfelder pro Punkt
        val n = 0.018f * p.drift
        val nx = n * sin(t * 1.7f + p.phase) + n * 0.5f * sin(t * 3.3f + p.phase * 2.1f)
        val ny = n * cos(t * 1.3f + p.phase * 1.4f) + n * 0.5f * cos(t * 2.9f + p.phase)

        val tx = target.x * scale + nx
        val ty = target.y * scale + ny

        p.vx = (p.vx + (tx - p.x) * stiffness) * damping
        p.vy = (p.vy + (ty - p.y) * stiffness) * damping

        // THINKING: tangentiale Wirbelkraft
        if (state == OrbState.THINKING) {
            p.vx += -p.y * 0.004f
            p.vy += p.x * 0.004f
        }
        p.x += p.vx
        p.y += p.vy
    }
}

private fun DrawScope.drawOrb(particles: List<Particle>, state: OrbState, level: Float, t: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = min(size.width, size.height) / 2f * 0.82f

    // Weicher Glow hinter dem Orb
    val glowColor = when (state) {
        OrbState.LISTENING -> Teal
        OrbState.SPEAKING -> Violet
        OrbState.THINKING -> Violet
        else -> Teal
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = 0.20f + level * 0.15f), Color.Transparent),
            center = Offset(cx, cy),
            radius = radius * 1.5f
        ),
        radius = radius * 1.5f,
        center = Offset(cx, cy)
    )

    particles.forEachIndexed { i, p ->
        val px = cx + p.x * radius
        val py = cy + p.y * radius
        // Farbverlauf Teal -> Violett über die Wolke, leicht zeitversetzt schimmernd
        val mix = (sin(p.phase + t * 0.6f) + 1f) / 2f
        val c = lerpColor(Teal, Violet, mix)
        val alpha = 0.55f + 0.45f * sin(t * 2f + p.phase)
        drawCircle(
            color = c.copy(alpha = (0.35f + 0.65f * alpha).coerceIn(0.2f, 1f)),
            radius = p.size * (1f + level * 0.6f),
            center = Offset(px, py)
        )
        // dezente Lichtpunkte
        if (i % 23 == 0) {
            drawCircle(color = Color.White.copy(alpha = 0.5f * alpha), radius = p.size * 0.45f, center = Offset(px, py))
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f
)
