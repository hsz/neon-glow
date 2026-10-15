package info.chrzanowski.neonglow.render

import com.intellij.ui.JBColor
import info.chrzanowski.neonglow.settings.GlowSettings
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.PathIterator
import java.awt.geom.Rectangle2D
import kotlin.math.min
import kotlin.math.roundToInt

/** Nine-slice rectangles and tiled path masks keep selection glow bounded for large selections. */
internal class SelectionGlowPainter {
    private data class Key(val width: Int, val height: Int, val sigma: Float, val rgb: Int, val intensity: Float,
        val path: List<Double> = emptyList())

    private val masks = LinkedHashMap<Key, GlowMask>(16, 0.75f, true)
    internal val size: Int get() = masks.size
    internal var bytes: Long = 0
        private set

    fun paint(
        graphics: Graphics2D, bounds: Rectangle2D, colour: Color, background: Color?,
        state: GlowSettings.State, strength: Float, budget: GlowWorkBudget,
    ) {
        val alpha = graphics.composite as? AlphaComposite ?: return
        if (alpha.rule != AlphaComposite.SRC_OVER || alpha.alpha <= 0f || colour.alpha == 0 || bounds.isEmpty) return
        if (!bounds.x.isFinite() || !bounds.y.isFinite() || !bounds.width.isFinite() || !bounds.height.isFinite()) return
        val opacity = state.effectiveBrightness(background?.rgb, JBColor.isBright()) * strength * alpha.alpha * colour.alpha / 255f
        if (opacity <= 0f) return
        val scale = DeviceScale.of(graphics)
        val sigma = GlowMaskRenderer.sigmaFor(state.radiusPx, scale)
        if (!sigma.isFinite() || sigma <= 0f || sigma > 64f) return
        val pad = GaussianBlur.radius(sigma)
        val visible = Rectangle2D.Double(bounds.x - pad / scale, bounds.y - pad / scale,
            bounds.width + 2 * pad / scale, bounds.height + 2 * pad / scale)
        if (graphics.clip?.intersects(visible) == false) return
        val width = (bounds.width * scale).roundToInt()
        val height = (bounds.height * scale).roundToInt()
        if (width <= 0 || height <= 0 || width > Int.MAX_VALUE - 2 * pad || height > Int.MAX_VALUE - 2 * pad) return
        val key = Key(min(width, 2 * pad + 1), min(height, 2 * pad + 1), sigma, colour.rgb, state.intensity)
        val mask = mask(key, Rectangle(0, 0, key.width, key.height), state, budget) ?: return
        val halo = graphics.create() as Graphics2D
        try {
            halo.composite = AlphaComposite.SrcOver.derive(opacity.coerceIn(0f, 1f))
            halo.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            halo.scale(1.0 / scale, 1.0 / scale)
            halo.translate((bounds.x * scale).roundToInt() - pad, (bounds.y * scale).roundToInt() - pad)
            val edgeX = min(2 * pad, (mask.width - 1) / 2)
            val edgeY = min(2 * pad, (mask.height - 1) / 2)
            val sx = intArrayOf(0, edgeX, mask.width - edgeX, mask.width)
            val sy = intArrayOf(0, edgeY, mask.height - edgeY, mask.height)
            val dx = intArrayOf(0, edgeX, width + 2 * pad - edgeX, width + 2 * pad)
            val dy = intArrayOf(0, edgeY, height + 2 * pad - edgeY, height + 2 * pad)
            for (y in 0..2) for (x in 0..2) {
                halo.drawImage(mask.image, dx[x], dy[y], dx[x + 1], dy[y + 1],
                    sx[x], sy[y], sx[x + 1], sy[y + 1], null)
            }
        } finally { halo.dispose() }
    }

    fun paint(
        graphics: Graphics2D, shape: Shape, colour: Color, background: Color?,
        state: GlowSettings.State, strength: Float, budget: GlowWorkBudget,
    ) {
        if (shape is Rectangle2D) return paint(graphics, shape, colour, background, state, strength, budget)
        val alpha = graphics.composite as? AlphaComposite ?: return
        if (alpha.rule != AlphaComposite.SRC_OVER || alpha.alpha <= 0f || colour.alpha == 0) return
        val bounds = shape.bounds2D
        if (bounds.isEmpty || !bounds.x.isFinite() || !bounds.y.isFinite() ||
            !bounds.width.isFinite() || !bounds.height.isFinite()) return
        val opacity = state.effectiveBrightness(background?.rgb, JBColor.isBright()) * strength * alpha.alpha * colour.alpha / 255f
        if (opacity <= 0f) return
        val scale = DeviceScale.of(graphics)
        val sigma = GlowMaskRenderer.sigmaFor(state.radiusPx, scale)
        if (!sigma.isFinite() || sigma <= 0f || sigma > 64f) return
        val pad = GaussianBlur.radius(sigma)
        val deviceShape = AffineTransform.getScaleInstance(scale.toDouble(), scale.toDouble()).createTransformedShape(shape)
        val deviceBounds = deviceShape.bounds2D
        val visible = Rectangle2D.Double(deviceBounds.x - pad, deviceBounds.y - pad,
            deviceBounds.width + 2 * pad, deviceBounds.height + 2 * pad)
        val clip = graphics.clipBounds
        if (clip == null && !GlowWorkBudget.safeRaster(deviceBounds, 1f, sigma * 2)) return
        val region = clip?.let {
            visible.createIntersection(Rectangle2D.Double(it.x * scale.toDouble(), it.y * scale.toDouble(),
                it.width * scale.toDouble(), it.height * scale.toDouble()))
        } ?: visible
        if (region.isEmpty || region.minX < Int.MIN_VALUE + 2.0 * pad || region.minY < Int.MIN_VALUE + 2.0 * pad ||
            region.maxX > Int.MAX_VALUE - 2.0 * pad || region.maxY > Int.MAX_VALUE - 2.0 * pad) return
        val tiles = region.bounds
        val halo = graphics.create() as Graphics2D
        try {
            halo.composite = AlphaComposite.SrcOver.derive(opacity.coerceIn(0f, 1f))
            halo.scale(1.0 / scale, 1.0 / scale)
            for (y in tiles.y until tiles.y + tiles.height step 256) {
                for (x in tiles.x until tiles.x + tiles.width step 256) {
                    val tile = Rectangle(x, y, min(256, tiles.x + tiles.width - x), min(256, tiles.y + tiles.height - y))
                    val source = Rectangle(x - pad, y - pad, tile.width + 2 * pad, tile.height + 2 * pad)
                    if (!deviceShape.intersects(source)) continue
                    val outline = Area(deviceShape).apply {
                        intersect(Area(source))
                        transform(AffineTransform.getTranslateInstance(-source.x.toDouble(), -source.y.toDouble()))
                    }
                    val path = pathKey(outline)
                    val key = Key(source.width, source.height, sigma, colour.rgb, state.intensity, path)
                    val mask = mask(key, outline, state, budget) ?: continue
                    val g = halo.create() as Graphics2D
                    try {
                        g.clip(tile)
                        g.drawImage(mask.image, source.x + mask.offsetX, source.y + mask.offsetY, null)
                    } finally { g.dispose() }
                }
            }
        } finally { halo.dispose() }
    }

    private fun pathKey(shape: Shape): List<Double> {
        val iterator = shape.getPathIterator(null)
        val path = arrayListOf(iterator.windingRule.toDouble())
        val coordinates = DoubleArray(6)
        while (!iterator.isDone) {
            val type = iterator.currentSegment(coordinates)
            path.add(type.toDouble())
            val count = when (type) {
                PathIterator.SEG_CLOSE -> 0
                PathIterator.SEG_QUADTO -> 4
                PathIterator.SEG_CUBICTO -> 6
                else -> 2
            }
            for (i in 0 until count) path.add((coordinates[i] * 1024).roundToInt() / 1024.0)
            iterator.next()
        }
        return path
    }

    private fun mask(key: Key, outline: Shape, state: GlowSettings.State, budget: GlowWorkBudget): GlowMask? =
        masks[key] ?: run {
            if (!GlowWorkBudget.safeRaster(outline.bounds2D, 1f, key.sigma * 2)) return null
            if (!budget.allowImage(state.performanceMode)) return null
            val rendered = GlowMaskRenderer.render(outline, key.sigma, key.rgb, 1f, key.intensity)
            masks[key] = rendered
            bytes += rendered.bytes
            while (masks.size > 32 || bytes > 4L * 1024 * 1024) {
                val iterator = masks.entries.iterator()
                bytes -= iterator.next().value.bytes
                iterator.remove()
            }
            rendered
        }

    fun clear() {
        masks.clear()
        bytes = 0
    }
}