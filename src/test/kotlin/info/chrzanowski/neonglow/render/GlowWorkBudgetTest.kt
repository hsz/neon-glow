package info.chrzanowski.neonglow.render

import org.junit.Assert.*
import org.junit.Test
import java.awt.geom.Rectangle2D

class GlowWorkBudgetTest {
    @Test
    fun `cold work is count bounded and shares a time deadline without limiting normal mode`() {
        var now = 0L
        val budget = GlowWorkBudget { now }
        repeat(GlowWorkBudget.MAX_GLYPHS) { assertTrue(budget.allowGlyph(true)) }
        assertFalse(budget.allowGlyph(true))
        repeat(GlowWorkBudget.MAX_IMAGES) { assertTrue(budget.allowImage(true)) }
        assertFalse(budget.allowImage(true))
        assertTrue(budget.allowGlyph(false))
        assertTrue(budget.allowImage(false))
        val timed = GlowWorkBudget { now }
        assertTrue(timed.allowGlyph(true))
        now += GlowWorkBudget.MAX_NANOS
        assertFalse(timed.allowGlyph(true))
        assertFalse(timed.allowImage(true))
        assertTrue(GlowWorkBudget { now }.allowImage(true))
    }

    @Test
    fun `raster guard rejects oversized and nonfinite geometry before allocation`() {
        val normal = Rectangle2D.Double(0.0, -20.0, 20.0, 20.0)
        assertTrue(GlowWorkBudget.safeRaster(normal, 2f, 35f))
        assertFalse(GlowWorkBudget.safeRaster(normal, Float.NaN, 6f))
        assertFalse(GlowWorkBudget.safeRaster(normal, Float.POSITIVE_INFINITY, 6f))
        assertFalse(GlowWorkBudget.safeRaster(normal, 1f, Float.NaN))
        assertFalse(GlowWorkBudget.safeRaster(Rectangle2D.Double(0.0, 0.0, 100_000.0, 30.0), 1f, 6f))
        assertFalse(GlowWorkBudget.safeRaster(Rectangle2D.Double(Double.NaN, 0.0, 20.0, 20.0), 1f, 6f))
    }
}