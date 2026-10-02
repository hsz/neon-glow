package info.chrzanowski.idesynthwave.ui

import com.intellij.ui.Graphics2DDelegate
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ImageUtil
import info.chrzanowski.idesynthwave.render.*
import info.chrzanowski.idesynthwave.settings.GlowSettings
import java.awt.*
import java.awt.font.GlyphVector
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.awt.image.ImageObserver
import java.awt.image.RenderedImage
import java.text.AttributedCharacterIterator
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Adds text and icon under-glow, preserving component layout, clipping and input handling. */
class GlowGraphics2D(
    graphics: Graphics2D,
    private val atlas: GlyphGlowAtlas,
    private val settings: () -> GlowSettings.State,
    private val powerSave: () -> Boolean,
    private val stats: GlowStats = GlowStats.DISABLED,
    private val imageAtlas: ImageGlowAtlas = ImageGlowAtlas(),
    private val imageScale: Float = JBUIScale.sysScale(graphics).takeIf { it > 0f } ?: 1f,
    private val surfaceScale: Double = (imageScale / (hypot(graphics.transform.scaleX, graphics.transform.shearY)
        .takeIf { it.isFinite() && it > 0.0 } ?: imageScale.toDouble())).coerceAtLeast(1.0),
    private val editorText: Boolean = false,
    private val budget: GlowWorkBudget = GlowWorkBudget(),
    private val textBackground: Color? = null,
) : Graphics2DDelegate(graphics) {

    private val enabled: Boolean get() = settings().enabled && settings().brightness > 0f && !powerSave()
    private var activeTextTarget: Boolean? = null
    private var activeTextBackground: Color? = null
    private var paintedTextBackground: Color? = null
    private var hasPaintedTextBackground = false
    private var lastTextBackgroundFill: BackgroundFill? = null
    private val currentTextBackground: Color? get() = if (hasPaintedTextBackground) paintedTextBackground
        else activeTextBackground ?: textBackground
    private val textEnabled: Boolean get() = isTextEnabled(activeTextTarget ?: editorText)
    private val textStrength: Float get() = settings().let {
        if (activeTextTarget ?: editorText) it.editorGlowStrength else it.uiGlowStrength
    }
    private val iconsEnabled: Boolean get() = enabled && settings().icons && settings().iconGlowStrength > 0f

    private fun isTextEnabled(editor: Boolean): Boolean = enabled && settings().let {
        (it.regularText || it.synthwaveStyle) &&
            if (editor) it.editorText && it.editorGlowStrength > 0f else it.uiText && it.uiGlowStrength > 0f
    }

    override fun create(): Graphics =
        GlowGraphics2D(myDelegate.create() as Graphics2D, atlas, settings, powerSave, stats, imageAtlas, imageScale,
            surfaceScale, activeTextTarget ?: editorText, budget, currentTextBackground).also {
            it.lastTextBackgroundFill = lastTextBackgroundFill
        }

    override fun fillRect(x: Int, y: Int, width: Int, height: Int) {
        if (settings().synthwaveStyle) recordBackground(Rectangle(x, y, width, height), paint as? Color)
        myDelegate.fillRect(x, y, width, height)
    }

    override fun fill(shape: Shape) {
        recordBackground(shape, paint as? Color)
        myDelegate.fill(shape)
    }

    override fun clearRect(x: Int, y: Int, width: Int, height: Int) {
        if (settings().synthwaveStyle) recordBackground(Rectangle(x, y, width, height), background, clearing = true)
        myDelegate.clearRect(x, y, width, height)
    }

    private fun recordBackground(area: Shape, colour: Color?, clearing: Boolean = false) {
        if (!settings().synthwaveStyle || area.bounds2D.isEmpty) return
        val alpha = composite as? AlphaComposite
        val solid = colour?.takeIf {
            it.alpha == 255 && (clearing || alpha?.alpha == 1f &&
                (alpha.rule == AlphaComposite.SRC_OVER || alpha.rule == AlphaComposite.SRC))
        }
        val visible = java.awt.geom.Area(area)
        clip?.let { visible.intersect(java.awt.geom.Area(it)) }
        if (visible.isEmpty) return
        lastTextBackgroundFill = BackgroundFill(transform.createTransformedShape(visible), solid)
        val bounds = clip?.bounds ?: return
        if (bounds.isEmpty || !area.contains(bounds)) return
        hasPaintedTextBackground = true
        paintedTextBackground = solid
    }

    private fun glyphBackground(glyphs: GlyphVector, x: Float, y: Float): Color? {
        val fill = lastTextBackgroundFill ?: return currentTextBackground
        val positioned = AffineTransform.getTranslateInstance(x.toDouble(), y.toDouble())
            .createTransformedShape(glyphs.visualBounds)
        val bounds = transform.createTransformedShape(positioned).bounds2D
        return when {
            fill.area.contains(bounds) -> fill.colour
            fill.area.intersects(bounds) -> null
            else -> currentTextBackground
        }
    }

    private data class BackgroundFill(val area: Shape, val colour: Color?)

    override fun drawImage(image: Image?, x: Int, y: Int, observer: ImageObserver?): Boolean {
        paintImageGlow(image, AffineTransform.getTranslateInstance(x.toDouble(), y.toDouble()))
        return myDelegate.drawImage(image, x, y, observer)
    }

    override fun drawImage(image: Image?, x: Int, y: Int, width: Int, height: Int, observer: ImageObserver?): Boolean {
        paintSizedImageGlow(image, x, y, width, height)
        return myDelegate.drawImage(image, x, y, width, height, observer)
    }

    override fun drawImage(image: Image?, x: Int, y: Int, background: Color?, observer: ImageObserver?): Boolean {
        paintImageGlow(image, AffineTransform.getTranslateInstance(x.toDouble(), y.toDouble()))
        return myDelegate.drawImage(image, x, y, background, observer)
    }

    override fun drawImage(
        image: Image?, x: Int, y: Int, width: Int, height: Int, background: Color?, observer: ImageObserver?,
    ): Boolean {
        paintSizedImageGlow(image, x, y, width, height)
        return myDelegate.drawImage(image, x, y, width, height, background, observer)
    }

    override fun drawImage(
        image: Image?, dx1: Int, dy1: Int, dx2: Int, dy2: Int,
        sx1: Int, sy1: Int, sx2: Int, sy2: Int, observer: ImageObserver?,
    ): Boolean {
        paintRegionImageGlow(image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2)
        return myDelegate.drawImage(image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer)
    }

    override fun drawImage(
        image: Image?, dx1: Int, dy1: Int, dx2: Int, dy2: Int,
        sx1: Int, sy1: Int, sx2: Int, sy2: Int, background: Color?, observer: ImageObserver?,
    ): Boolean {
        paintRegionImageGlow(image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2)
        return myDelegate.drawImage(image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, background, observer)
    }

    override fun drawImage(image: Image?, transform: AffineTransform?, observer: ImageObserver?): Boolean {
        if (transform != null) paintImageGlow(image, transform)
        return myDelegate.drawImage(image, transform, observer)
    }

    override fun drawImage(image: BufferedImage?, op: BufferedImageOp?, x: Int, y: Int) {
        if (iconsEnabled && image != null && image.width <= MAX_ICON_DEVICE_SIZE && image.height <= MAX_ICON_DEVICE_SIZE) {
            paintImageGlow(op?.filter(image, null) ?: image, AffineTransform.getTranslateInstance(x.toDouble(), y.toDouble()))
        }
        myDelegate.drawImage(image, op, x, y)
    }

    override fun drawRenderedImage(image: RenderedImage?, transform: AffineTransform?) {
        if (image is BufferedImage && transform != null) paintImageGlow(image, transform)
        myDelegate.drawRenderedImage(image, transform)
    }

    private fun paintSizedImageGlow(image: Image?, x: Int, y: Int, width: Int, height: Int) {
        if (!iconsEnabled) return
        val pixels = imagePixels(image) ?: return
        val bounds = imageBounds(pixels)
        paintRegionImageGlow(pixels, x, y, x + width, y + height, 0, 0, bounds.width, bounds.height)
    }

    private fun paintRegionImageGlow(
        image: Image?, dx1: Int, dy1: Int, dx2: Int, dy2: Int,
        sx1: Int, sy1: Int, sx2: Int, sy2: Int,
    ) {
        if (!iconsEnabled || sx1 == sx2 || sy1 == sy2) return
        val mapping = AffineTransform.getTranslateInstance(dx1.toDouble(), dy1.toDouble())
        mapping.scale((dx2.toDouble() - dx1) / (sx2.toDouble() - sx1), (dy2.toDouble() - dy1) / (sy2.toDouble() - sy1))
        mapping.translate(-sx1.toDouble(), -sy1.toDouble())
        paintImageGlow(image, mapping, Rectangle(minOf(sx1, sx2), minOf(sy1, sy2),
            kotlin.math.abs(sx2 - sx1), kotlin.math.abs(sy2 - sy1)))
    }

    private fun paintImageGlow(image: Image?, mapping: AffineTransform, source: Rectangle? = null) {
        if (!iconsEnabled) return
        val alpha = composite as? AlphaComposite ?: return
        if (alpha.rule != AlphaComposite.SRC_OVER || alpha.alpha == 0f) return
        val pixels = imagePixels(image) ?: return
        val full = imageBounds(pixels)
        // Avoid processing screenshots, backgrounds or large offscreen UI buffers as icons.
        if (full.width > MAX_ICON_DEVICE_SIZE || full.height > MAX_ICON_DEVICE_SIZE) return
        val crop = source?.intersection(full) ?: full
        if (crop.isEmpty) return
        val device = AffineTransform.getScaleInstance(surfaceScale, surfaceScale)
        device.concatenate(transform)
        device.concatenate(mapping)
        device.translate(crop.x.toDouble(), crop.y.toDouble())
        val matrix = DoubleArray(6)
        device.getMatrix(matrix)
        if (matrix.any { !it.isFinite() } || device.determinant == 0.0) return
        val bounds = device.createTransformedShape(Rectangle(0, 0, crop.width, crop.height)).bounds
        if (bounds.isEmpty || bounds.width > MAX_ICON_DEVICE_SIZE || bounds.height > MAX_ICON_DEVICE_SIZE ||
            bounds.width / imageScale > MAX_ICON_SIZE || bounds.height / imageScale > MAX_ICON_SIZE) return
        val local = AffineTransform.getTranslateInstance(-bounds.x.toDouble(), -bounds.y.toDouble())
        local.concatenate(device)
        val state = settings()
        val pad = kotlin.math.ceil(state.radiusPx * imageScale * 1.5).toInt()
        val visible = Rectangle(bounds)
        visible.grow(pad, pad)
        val deviceClip = clip?.let { transform.createTransformedShape(it) }?.let {
            AffineTransform.getScaleInstance(surfaceScale, surfaceScale).createTransformedShape(it).bounds
        }
        if (deviceClip != null && !visible.intersects(deviceClip)) return
        val mask = imageAtlas.getIfAllowed(pixels, crop, local, bounds.width, bounds.height,
            state.radiusPx, imageScale, state.intensity) { budget.allowImage(state.performanceMode) } ?: return
        val halo = myDelegate.create() as Graphics2D
        try {
            // Clip is already in device space; keep it while undoing any image-specific HiDPI unscaling.
            halo.transform = AffineTransform.getScaleInstance(1.0 / surfaceScale, 1.0 / surfaceScale)
            halo.composite = alpha.derive(alpha.alpha * state.brightness * state.iconGlowStrength)
            halo.drawImage(mask.image, bounds.x + mask.offsetX, bounds.y + mask.offsetY, null)
        } finally {
            halo.dispose()
        }
    }

    private fun imagePixels(image: Image?): BufferedImage? {
        if (image == null) return null
        if (image is BufferedImage) {
            if (ImageUtil.getRealWidth(image) !in 1..MAX_ICON_DEVICE_SIZE ||
                ImageUtil.getRealHeight(image) !in 1..MAX_ICON_DEVICE_SIZE) return null
            return ImageUtil.toBufferedImage(image)
        }
        val width = image.getWidth(null)
        val height = image.getHeight(null)
        if (width !in 1..MAX_ICON_DEVICE_SIZE || height !in 1..MAX_ICON_DEVICE_SIZE) return null
        val raster = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = raster.createGraphics()
        return try {
            // Never block the EDT waiting for an asynchronously loaded image.
            if (g.drawImage(image, 0, 0, null)) raster else null
        } finally {
            g.dispose()
        }
    }

    private fun imageBounds(image: BufferedImage): Rectangle =
        Rectangle(0, 0, image.raster.width, image.raster.height)

    override fun drawString(text: String, x: Int, y: Int) = drawString(text, x.toFloat(), y.toFloat())

    override fun drawString(text: String, x: Float, y: Float) {
        if (!textEnabled || text.isEmpty()) {
            myDelegate.drawString(text, x, y)
            return
        }
        TextLayout(text, font, fontRenderContext).draw(this, x, y)
    }

    override fun drawString(iterator: AttributedCharacterIterator, x: Int, y: Int) =
        drawString(iterator, x.toFloat(), y.toFloat())

    override fun drawString(iterator: AttributedCharacterIterator, x: Float, y: Float) {
        if (!textEnabled || iterator.beginIndex == iterator.endIndex) {
            myDelegate.drawString(iterator, x, y)
            return
        }
        TextLayout(iterator, fontRenderContext).draw(this, x, y)
    }

    override fun drawChars(data: CharArray, offset: Int, length: Int, x: Int, y: Int) =
        drawString(String(data, offset, length), x, y)

    override fun drawBytes(data: ByteArray, offset: Int, length: Int, x: Int, y: Int) =
        drawString(String(data, offset, length, Charsets.ISO_8859_1), x, y)

    override fun drawGlyphVector(glyphs: GlyphVector, x: Float, y: Float) {
        if (textEnabled) {
            val foreground = paint as? Color
            val alpha = composite as? AlphaComposite
            val rule = if (settings().synthwaveStyle && alpha?.rule == AlphaComposite.SRC_OVER)
                foreground?.let { SynthwaveTextStyle.rule(it.rgb, glyphBackground(glyphs, x, y)?.rgb) } else null
            val start = if (stats.enabled) System.nanoTime() else 0L
            val count = paintGlow(glyphs, x, y, rule)
            if (stats.enabled && count > 0) stats.recordPaint((System.nanoTime() - start) / 1_000, count, 0, atlas)
            if (rule != null) {
                val core = myDelegate.create() as Graphics2D
                try {
                    core.color = Color((foreground!!.alpha shl 24) or rule.foregroundRgb, true)
                    core.drawGlyphVector(glyphs, x, y)
                } finally { core.dispose() }
                return
            }
        }
        myDelegate.drawGlyphVector(glyphs, x, y)
    }

    private fun paintGlow(glyphs: GlyphVector, x: Float, y: Float, rule: SynthwaveTextStyle.Rule?): Int {
        val foreground = paint as? Color ?: return 0
        val alpha = composite as? AlphaComposite ?: return 0
        if (alpha.rule != AlphaComposite.SRC_OVER || foreground.alpha == 0) return 0
        val state = settings()
        val layered = rule != null
        if (!layered && !state.regularText) return 0
        atlas.intensity = state.intensity
        val scale = JBUIScale.sysScale(myDelegate).takeIf { it > 0f } ?: 1f
        val glyphFont = glyphs.font
        val halo = myDelegate.create() as Graphics2D
        var count = 0
        try {
            halo.composite = alpha.derive(alpha.alpha * foreground.alpha / 255f *
                textStrength * if (layered) 1f else state.brightness)
            halo.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            val blit = AffineTransform()
            for (i in 0 until glyphs.numGlyphs) {
                val bounds = glyphs.getGlyphVisualBounds(i).bounds2D
                if (bounds.isEmpty) continue
                val radius = if (layered) SynthwaveTextStyle.maxRadius(state.radiusPx) else state.radiusPx
                if (!GlowWorkBudget.safeRaster(bounds, scale, radius)) continue
                val pad = radius * 1.5 + 1
                if (clip != null && !clip.intersects(bounds.x + x - pad, bounds.y + y - pad,
                        bounds.width + 2 * pad, bounds.height + 2 * pad)) continue
                val position = glyphs.getGlyphPosition(i)
                val key = GlyphKey(
                    glyphs.getGlyphCode(i), glyphFont.family, glyphFont.style, glyphFont.size2D,
                    foreground.rgb, scale, state.radiusPx, glyphFont, glyphs.getGlyphTransform(i),
                    layered, if (layered) state.brightness else 1f, rule,
                )
                var mask = atlas.find(key)
                if (mask == null) {
                    if (!budget.allowGlyph(state.performanceMode)) continue
                    mask = atlas.render(key, glyphs.getGlyphOutline(i, -position.x.toFloat(), -position.y.toFloat()))
                }
                val deviceX = ((x + position.x) * scale).roundToInt() + mask.offsetX
                val deviceY = ((y + position.y) * scale).roundToInt() + mask.offsetY
                blit.setTransform(
                    1.0 / scale,
                    0.0,
                    0.0,
                    1.0 / scale,
                    deviceX / scale.toDouble(),
                    deviceY / scale.toDouble(),
                )
                halo.drawImage(mask.image, blit, null)
                count++
            }
        } finally {
            halo.dispose()
        }
        return count
    }

    private inline fun withTextTarget(editor: Boolean, background: Color?, paint: () -> Unit) {
        // An outer scope wins over inherited scopes; never change a sibling component's policy.
        if (activeTextTarget != null) {
            paint()
            return
        }
        activeTextTarget = editor
        activeTextBackground = background
        try { paint() } finally {
            activeTextTarget = null
            activeTextBackground = null
        }
    }

    private class TextTargetGraphics(graphics: Graphics2D, val editor: Boolean, val textBackground: Color?) : Graphics2DDelegate(graphics) {
        private val glow = checkNotNull(find(graphics))

        override fun create(): Graphics = withTextTarget(myDelegate.create() as Graphics2D, editor, textBackground)
        override fun drawString(text: String, x: Int, y: Int) = glow.withTextTarget(editor, textBackground) { myDelegate.drawString(text, x, y) }
        override fun drawString(text: String, x: Float, y: Float) = glow.withTextTarget(editor, textBackground) { myDelegate.drawString(text, x, y) }
        override fun drawString(text: AttributedCharacterIterator, x: Int, y: Int) =
            glow.withTextTarget(editor, textBackground) { myDelegate.drawString(text, x, y) }
        override fun drawString(text: AttributedCharacterIterator, x: Float, y: Float) =
            glow.withTextTarget(editor, textBackground) { myDelegate.drawString(text, x, y) }
        override fun drawGlyphVector(glyphs: GlyphVector, x: Float, y: Float) =
            glow.withTextTarget(editor, textBackground) { myDelegate.drawGlyphVector(glyphs, x, y) }
        override fun drawChars(data: CharArray, offset: Int, length: Int, x: Int, y: Int) =
            glow.withTextTarget(editor, textBackground) { myDelegate.drawChars(data, offset, length, x, y) }
        override fun drawBytes(data: ByteArray, offset: Int, length: Int, x: Int, y: Int) =
            glow.withTextTarget(editor, textBackground) { myDelegate.drawBytes(data, offset, length, x, y) }
    }

    companion object {
        private const val MAX_ICON_SIZE = 128
        private const val MAX_ICON_DEVICE_SIZE = 512

        /** Nested IDE transforms must not add another halo to an already wrapped graphics. */
        fun isWrapped(graphics: Graphics2D): Boolean = find(graphics) != null

        fun isGlowing(graphics: Graphics2D): Boolean {
            val glow = find(graphics) ?: return false
            return glow.isTextEnabled(textTarget(graphics) ?: glow.editorText)
        }

        internal fun withTextTarget(graphics: Graphics2D, editor: Boolean, background: Color? = null): Graphics2D {
            val glow = find(graphics) ?: return graphics
            val currentBackground = textBackground(graphics)
            val targetBackground = background ?: currentBackground
            return if ((textTarget(graphics) ?: glow.editorText) == editor && currentBackground == targetBackground) graphics
                else TextTargetGraphics(graphics, editor, targetBackground)
        }

        private fun textBackground(graphics: Graphics2D): Color? {
            var current = graphics
            while (current is Graphics2DDelegate) {
                if (current is TextTargetGraphics) return current.textBackground
                if (current is GlowGraphics2D) return current.currentTextBackground
                current = current.delegate
            }
            return null
        }

        private fun textTarget(graphics: Graphics2D): Boolean? {
            var current = graphics
            while (current is Graphics2DDelegate) {
                if (current is TextTargetGraphics) return current.editor
                if (current is GlowGraphics2D) return current.activeTextTarget ?: current.editorText
                current = current.delegate
            }
            return null
        }

        private fun find(graphics: Graphics2D): GlowGraphics2D? {
            var current = graphics
            while (current is Graphics2DDelegate) {
                if (current is GlowGraphics2D) return current
                current = current.delegate
            }
            return null
        }
    }
}