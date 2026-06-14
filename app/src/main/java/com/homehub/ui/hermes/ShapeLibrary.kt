package com.homehub.ui.hermes

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Liefert Ziel-Punktwolken für den Orb. Alle Formen sind auf [-1, 1] normiert.
 * Der Orb morpht zwischen diesen Formen, wenn Hermes über passende Dinge spricht.
 */
enum class OrbShape { ORB, SHIP, HEART, STAR, HOUSE, ROCKET, TREE, CLOUD, FLAME }

object ShapeLibrary {

    /** Erkennung: Wenn Hermes z.B. von einem Schiff erzählt, formt sich der Orb zum Schiff. */
    private val keywords: List<Pair<OrbShape, List<String>>> = listOf(
        OrbShape.SHIP to listOf("schiff", "boot", "segel", "ship", "boat", "kahn", "fähre"),
        OrbShape.HEART to listOf("herz", "liebe", "heart", "love"),
        OrbShape.STAR to listOf("stern", "star", "galaxie", "kosmos"),
        OrbShape.HOUSE to listOf("haus", "zuhause", "home", "wohnung", "gebäude"),
        OrbShape.ROCKET to listOf("rakete", "rocket", "weltraum", "start ins all"),
        OrbShape.TREE to listOf("baum", "wald", "tree", "forest", "pflanze"),
        OrbShape.CLOUD to listOf("wolke", "cloud", "wetter", "regen", "himmel"),
        OrbShape.FLAME to listOf("feuer", "flamme", "fire", "kerze", "brennt")
    )

    fun detectShape(text: String): OrbShape? {
        val lower = text.lowercase()
        return keywords.firstOrNull { (_, words) -> words.any { it in lower } }?.first
    }

    /** Punktwolke für eine Form mit [count] Punkten, deterministisch pro Form. */
    fun points(shape: OrbShape, count: Int): List<Offset> {
        val rnd = Random(shape.ordinal * 7919 + 13)
        return when (shape) {
            OrbShape.ORB -> sphere(count, rnd)
            OrbShape.SHIP -> polygonCloud(SHIP_POLY, count, rnd, outlineRatio = 0.45f)
            OrbShape.HEART -> heart(count, rnd)
            OrbShape.STAR -> polygonCloud(starPoly(5, 1f, 0.45f), count, rnd, outlineRatio = 0.5f)
            OrbShape.HOUSE -> polygonCloud(HOUSE_POLY, count, rnd, outlineRatio = 0.5f)
            OrbShape.ROCKET -> polygonCloud(ROCKET_POLY, count, rnd, outlineRatio = 0.45f)
            OrbShape.TREE -> polygonCloud(TREE_POLY, count, rnd, outlineRatio = 0.4f)
            OrbShape.CLOUD -> cloud(count, rnd)
            OrbShape.FLAME -> flame(count, rnd)
        }
    }

    // ---------- Generatoren ----------

    /** Kugel-Projektion: dichte Mitte, weicher Rand – der "Ruhezustand" des Orbs. */
    private fun sphere(count: Int, rnd: Random): List<Offset> = List(count) {
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        // sqrt für gleichmäßige Flächenverteilung, leicht zur Mitte gewichtet
        val r = sqrt(rnd.nextFloat()) * 0.95f
        Offset(cos(a) * r, sin(a) * r)
    }

    private fun heart(count: Int, rnd: Random): List<Offset> = List(count) {
        // Parametrische Herzkurve, gefüllt durch zufällige Skalierung
        val t = rnd.nextFloat() * 2f * PI.toFloat()
        val s = sqrt(rnd.nextFloat())
        val x = 16f * sin(t).let { it * it * it }
        val y = 13f * cos(t) - 5f * cos(2f * t) - 2f * cos(3f * t) - cos(4f * t)
        Offset(x / 17f * s, -y / 17f * s)
    }

    private fun cloud(count: Int, rnd: Random): List<Offset> {
        // Drei überlappende Kreise
        val centers = listOf(Offset(-0.45f, 0.1f) to 0.42f, Offset(0.05f, -0.15f) to 0.55f, Offset(0.5f, 0.12f) to 0.4f)
        return List(count) {
            val (c, r) = centers[rnd.nextInt(centers.size)]
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val rr = sqrt(rnd.nextFloat()) * r
            Offset(c.x + cos(a) * rr, c.y + sin(a) * rr * 0.8f)
        }
    }

    private fun flame(count: Int, rnd: Random): List<Offset> = List(count) {
        val y = rnd.nextFloat() * 2f - 1f           // -1 unten .. 1 oben
        val width = (1f - (y + 1f) / 2f) * 0.55f + 0.05f // oben spitz
        val x = (rnd.nextFloat() * 2f - 1f) * width * (0.7f + 0.3f * sin(y * 6f))
        Offset(x, -y)
    }

    // ---------- Polygon-Hilfen ----------

    private fun starPoly(spikes: Int, outer: Float, inner: Float): List<Offset> {
        val pts = mutableListOf<Offset>()
        for (i in 0 until spikes * 2) {
            val r = if (i % 2 == 0) outer else inner
            val a = (i.toFloat() / (spikes * 2)) * 2f * PI.toFloat() - PI.toFloat() / 2f
            pts += Offset(cos(a) * r, sin(a) * r)
        }
        return pts
    }

    /** Segelschiff-Silhouette: Rumpf + Mast + zwei Segel. */
    private val SHIP_POLY = listOf(
        // Rumpf
        Offset(-0.9f, 0.45f), Offset(-0.65f, 0.8f), Offset(0.65f, 0.8f), Offset(0.9f, 0.45f),
        Offset(0.05f, 0.45f),
        // Mast hoch
        Offset(0.05f, -0.9f),
        // großes Segel (nach links)
        Offset(-0.75f, 0.35f), Offset(0.0f, 0.35f), Offset(0.0f, -0.85f),
        // kleines Segel (nach rechts)
        Offset(0.1f, -0.75f), Offset(0.6f, 0.3f), Offset(0.1f, 0.3f),
        Offset(0.1f, 0.45f), Offset(-0.9f, 0.45f)
    )

    private val HOUSE_POLY = listOf(
        Offset(-0.7f, 0.8f), Offset(-0.7f, -0.1f), Offset(0f, -0.8f),
        Offset(0.7f, -0.1f), Offset(0.7f, 0.8f), Offset(-0.7f, 0.8f)
    )

    private val ROCKET_POLY = listOf(
        Offset(0f, -1f), Offset(0.28f, -0.45f), Offset(0.28f, 0.45f),
        Offset(0.6f, 0.85f), Offset(0.2f, 0.7f), Offset(0f, 0.95f),
        Offset(-0.2f, 0.7f), Offset(-0.6f, 0.85f), Offset(-0.28f, 0.45f),
        Offset(-0.28f, -0.45f), Offset(0f, -1f)
    )

    private val TREE_POLY = listOf(
        Offset(0f, -1f), Offset(0.5f, -0.35f), Offset(0.25f, -0.35f),
        Offset(0.65f, 0.25f), Offset(0.12f, 0.25f), Offset(0.12f, 0.8f),
        Offset(-0.12f, 0.8f), Offset(-0.12f, 0.25f), Offset(-0.65f, 0.25f),
        Offset(-0.25f, -0.35f), Offset(-0.5f, -0.35f), Offset(0f, -1f)
    )

    /**
     * Verteilt Punkte über ein Polygon: ein Teil auf der Kontur (klare Silhouette),
     * der Rest als Füllung (Point-in-Polygon).
     */
    private fun polygonCloud(poly: List<Offset>, count: Int, rnd: Random, outlineRatio: Float): List<Offset> {
        val result = ArrayList<Offset>(count)
        val outlineCount = (count * outlineRatio).toInt()

        // Kantenlängen für gleichmäßige Konturverteilung
        val edges = poly.zipWithNext()
        val lengths = edges.map { (a, b) -> dist(a, b) }
        val total = lengths.sum().coerceAtLeast(0.0001f)

        for (i in 0 until outlineCount) {
            var d = rnd.nextFloat() * total
            for ((idx, e) in edges.withIndex()) {
                if (d <= lengths[idx]) {
                    val t = if (lengths[idx] == 0f) 0f else d / lengths[idx]
                    result += lerp(e.first, e.second, t)
                    break
                }
                d -= lengths[idx]
            }
        }
        // Füllung
        var guard = 0
        while (result.size < count && guard < count * 60) {
            guard++
            val p = Offset(rnd.nextFloat() * 2f - 1f, rnd.nextFloat() * 2f - 1f)
            if (pointInPolygon(p, poly)) result += p
        }
        while (result.size < count) result += result[rnd.nextInt(result.size)]
        return result
    }

    private fun dist(a: Offset, b: Offset): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun lerp(a: Offset, b: Offset, t: Float) =
        Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    private fun pointInPolygon(p: Offset, poly: List<Offset>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val pi = poly[i]; val pj = poly[j]
            val intersects = (pi.y > p.y) != (pj.y > p.y) &&
                p.x < (pj.x - pi.x) * (p.y - pi.y) / ((pj.y - pi.y).takeIf { abs(it) > 1e-6f } ?: 1e-6f) + pi.x
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }
}
