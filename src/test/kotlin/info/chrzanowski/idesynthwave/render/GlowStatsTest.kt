package info.chrzanowski.idesynthwave.render

import com.intellij.openapi.diagnostic.Logger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Rectangle
import java.awt.image.BufferedImage

class GlowStatsTest {

    private var now = 1_000L
    private val atlas = GlyphGlowAtlas(capacity = 10) { _, _, _ -> GlowMask(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB_PRE), 0, 0) }
    private val stats = GlowStats(enabled = true, periodNanos = 1_000_000, logger = Logger.getInstance(GlowStatsTest::class.java), clock = { now })

    private fun key(glyph: Int) = GlyphKey(glyph, "Monospaced", 0, 13f, 0xFF00FF, 1f, 6f)

    @Test
    fun `disabled stats never report`() {
        val disabled = GlowStats(enabled = false, periodNanos = 1, clock = { now })
        repeat(5) { now += 10; disabled.recordPaint(100, 10, 0, atlas) }
        assertEquals(0L, disabled.reports)
        assertNull(disabled.lastReport)
    }

    @Test
    fun `report aggregates the period and tracks atlas deltas`() {
        atlas.get(key(1)) { Rectangle(0, 0, 1, 1) }
        stats.recordPaint(200, 50, 1, atlas) // opens the period
        atlas.get(key(1)) { Rectangle(0, 0, 1, 1) }
        atlas.get(key(2)) { Rectangle(0, 0, 1, 1) }
        now += 500_000
        stats.recordPaint(800, 150, 0, atlas)
        assertEquals(0L, stats.reports)

        now += 500_000
        stats.recordPaint(500, 100, 2, atlas)

        assertEquals(1L, stats.reports)
        val line = stats.lastReport!!
        assertTrue(line, line.startsWith("synthwave: paints=3 "))
        assertTrue(line, "paint avg=500µs max=800µs (150 glyphs)" in line)
        assertTrue(line, "glyphs/paint=100" in line)
        assertTrue(line, "fallback segments=3" in line)
        assertTrue(line, "atlas hits=1 misses=1 size=2" in line)
        assertTrue(line, "evictions=0" in line)
        // 1500 µs of painting in a 1 ms period → 150 % "cpu" (the clock is synthetic).
        assertTrue(line, "cpu≈150.00%" in line)
    }

    @Test
    fun `counters restart after a report`() {
        stats.recordPaint(100, 10, 0, atlas)
        now += 1_000_000
        stats.recordPaint(100, 10, 0, atlas)
        assertEquals(1L, stats.reports)

        now += 1_000_000
        stats.recordPaint(300, 30, 0, atlas)
        assertEquals(2L, stats.reports)
        assertTrue(stats.lastReport!!, "paints=1 paint avg=300µs max=300µs (30 glyphs)" in stats.lastReport!!)
    }

    @Test
    fun `cleared atlas does not produce negative deltas`() {
        atlas.get(key(1)) { Rectangle(0, 0, 1, 1) }
        atlas.get(key(1)) { Rectangle(0, 0, 1, 1) }
        stats.recordPaint(100, 10, 0, atlas)
        atlas.clear()
        atlas.get(key(2)) { Rectangle(0, 0, 1, 1) }
        now += 1_000_000
        stats.recordPaint(100, 10, 0, atlas)
        assertTrue(stats.lastReport!!, "atlas hits=0 misses=1 size=1" in stats.lastReport!!)
    }
}
