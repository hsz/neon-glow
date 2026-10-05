package info.chrzanowski.neonglow.render

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.TimeUnit

/**
 * Aggregates per-paint counters and logs them periodically when the IDE runs with
 * `-Dide.neon.glow.debug=true`. Disabled instances cost one boolean check per paint.
 *
 * Logged line: paints in the period, average/max paint cost (with the glyph count of the most expensive paint),
 * average glyphs per paint, segments that needed the per-character layout fallback, atlas hits/misses in the
 * period, atlas size and footprint, evictions, and the share of wall-clock time spent painting the glow.
 */
class GlowStats(
    val enabled: Boolean = System.getProperty(DEBUG_PROPERTY, "false").toBoolean(),
    private val periodNanos: Long = TimeUnit.SECONDS.toNanos(10),
    private val logger: Logger = Logger.getInstance(GlowStats::class.java),
    private val clock: () -> Long = System::nanoTime,
) {

    private var periodStart = 0L
    private var paints = 0L
    private var totalCostMicros = 0L
    private var maxCostMicros = 0L
    private var maxCostGlyphs = 0
    private var totalGlyphs = 0L
    private var totalFallbackSegments = 0L
    private var hitsAtPeriodStart = 0L
    private var missesAtPeriodStart = 0L

    /** Number of log lines emitted so far (tests). */
    var reports: Long = 0
        private set

    /** The most recently logged line (tests). */
    var lastReport: String? = null
        private set

    fun recordPaint(costMicros: Long, glyphs: Int, fallbackSegments: Int, atlas: GlyphGlowAtlas) {
        if (!enabled) return
        val now = clock()
        if (periodStart == 0L) {
            periodStart = now
            hitsAtPeriodStart = atlas.hits
            missesAtPeriodStart = atlas.misses
        }
        paints++
        totalCostMicros += costMicros
        if (costMicros > maxCostMicros) {
            maxCostMicros = costMicros
            maxCostGlyphs = glyphs
        }
        totalGlyphs += glyphs
        totalFallbackSegments += fallbackSegments
        val elapsed = now - periodStart
        if (elapsed >= periodNanos) {
            report(elapsed, atlas)
            periodStart = now
            paints = 0
            totalCostMicros = 0
            maxCostMicros = 0
            maxCostGlyphs = 0
            totalGlyphs = 0
            totalFallbackSegments = 0
            // Counters reset on atlas.clear(); clamp so a cleared atlas never yields negative deltas.
            hitsAtPeriodStart = atlas.hits
            missesAtPeriodStart = atlas.misses
        }
    }

    private fun report(elapsedNanos: Long, atlas: GlyphGlowAtlas) {
        val seconds = elapsedNanos / 1_000_000_000.0
        val avgCost = if (paints > 0) totalCostMicros.toDouble() / paints else 0.0
        val avgGlyphs = if (paints > 0) totalGlyphs.toDouble() / paints else 0.0
        val cpuPercent = if (seconds > 0) totalCostMicros / (seconds * 10_000.0) else 0.0
        // atlas.clear() resets its counters mid-period; fall back to the absolute values in that case.
        val cleared = atlas.hits < hitsAtPeriodStart || atlas.misses < missesAtPeriodStart
        val hits = if (cleared) atlas.hits else atlas.hits - hitsAtPeriodStart
        val misses = if (cleared) atlas.misses else atlas.misses - missesAtPeriodStart
        reports++
        val line = String.format(
            "[NeonGlow] paints=%d paint avg=%.0fµs max=%dµs (%d glyphs) glyphs/paint=%.0f fallback segments=%d " +
                "atlas hits=%d misses=%d size=%d (%.1f MB) evictions=%d cpu≈%.2f%%",
            paints, avgCost, maxCostMicros, maxCostGlyphs, avgGlyphs, totalFallbackSegments,
            hits, misses, atlas.size, atlas.bytes / (1024.0 * 1024.0), atlas.evictions, cpuPercent,
        )
        lastReport = line
        logger.info(line)
    }

    companion object {
        const val DEBUG_PROPERTY: String = "ide.neon.glow.debug"

        /** Shared no-op instance for code paths that never log. */
        val DISABLED: GlowStats = GlowStats(enabled = false)
    }
}
