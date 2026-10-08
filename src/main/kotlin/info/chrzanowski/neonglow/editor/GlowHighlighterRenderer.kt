package info.chrzanowski.neonglow.editor

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.ui.scale.JBUIScale
import info.chrzanowski.neonglow.render.GaussianBlur
import info.chrzanowski.neonglow.render.GlowMaskRenderer
import info.chrzanowski.neonglow.render.GlowStats
import info.chrzanowski.neonglow.render.GlowWorkBudget
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.render.GlyphKey
import info.chrzanowski.neonglow.render.SynthwaveTextStyle
import info.chrzanowski.neonglow.settings.GlowSettings
import info.chrzanowski.neonglow.ui.GlowGraphics2D
import java.awt.AlphaComposite
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Paints a blurred halo under every glyph visible in the current clip, in the editor's background pass
 * ([CustomHighlighterOrder.AFTER_BACKGROUND]) so the glow lands under the text and over the line background.
 *
 * Per paint: the clip is inflated by the blur padding (halos of neighbouring lines spill into it), mapped to a
 * logical line range, and the lexer tokens of those lines are walked with the editor's [EditorEx.getHighlighter].
 * Each non-blank, unfolded token is split into single-line, tab-free segments, laid out by [TokenLayout] and
 * blitted glyph by glyph from the [GlyphGlowAtlas] at integer device-pixel positions. Everything outside the
 * real clip is discarded by the graphics clip, so over-iteration is harmless.
 *
 * When the IDE-wide hook ([GlowGraphics2D]) already glows the editor's graphics, every glyph the editor draws in
 * this pass gets its halo there. The editor only draws the visual lines intersecting the clip, though, so a partial
 * repaint (caret blink, a re-highlighted line) would lose the halos spilling in from the neighbouring lines. In that
 * mode the renderer paints exactly those: glyphs on visual lines outside the clip, through the unwrapped graphics so
 * nothing is glowed twice. This keeps partial repaints consistent without touching Swing's `RepaintManager`.
 *
 * HiDPI: masks are device-pixel images. On a Retina surface `Graphics2D.getTransform()` hides the device scale
 * (it lives in the surface), on an offscreen image it is an explicit `scale(2)`; [JBUIScale.sysScale] covers both.
 * Each mask is drawn through a `1 / sysScale` transform positioned at `devicePixel / sysScale`, so the composite
 * transform is a pure integer device translation and the blit is 1:1 — no resampling, no mushy halo.
 */
class GlowHighlighterRenderer(
    private val atlas: GlyphGlowAtlas,
    private val settings: GlowSettings,
    private val stats: GlowStats = GlowStats.DISABLED,
) : CustomHighlighterRenderer {

    private val clip = Rectangle()
    private val point = Point()
    private val layout = TokenLayout()
    private val blit = AffineTransform()
    private var graphicsContext: FontRenderContext? = null
    private var editorContext: FontRenderContext? = null
    private var workBudget = GlowWorkBudget()

    /** User-space y range `[skipTop, skipBottom)` of the visual lines the editor draws itself in this pass. */
    private var skipTop = 0
    private var skipBottom = 0

    /** Glyphs blitted by the last [paint] (tests, statistics). */
    var lastGlyphCount: Int = 0
        private set

    /** Segments that took the per-character layout path during the last [paint] (statistics). */
    var lastFallbackSegments: Int = 0
        private set

    override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.AFTER_BACKGROUND

    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
        lastGlyphCount = 0
        lastFallbackSegments = 0
        workBudget = GlowWorkBudget()
        val state = settings.state
        if (!state.enabled || !state.editorText || state.editorGlowStrength <= 0f || state.brightness <= 0f || PowerSaveMode.isEnabled()) return
        if (!state.regularText && !state.synthwaveStyle) return
        val source = g as? Graphics2D ?: return
        val alpha = source.composite as? AlphaComposite ?: return
        if (alpha.rule != AlphaComposite.SRC_OVER || alpha.alpha == 0f) return
        val hooked = GlowGraphics2D.isGlowing(source)
        // Masks are plain images; never let the hook treat them as icons and halo the halo.
        val halo = GlowGraphics2D.withoutGlow(source).create() as Graphics2D
        try {
            halo.composite = alpha.derive(alpha.alpha * state.editorGlowStrength)
            if (!stats.enabled) {
                paintGlow(editor, state, halo, hooked)
                return
            }
            val start = System.nanoTime()
            paintGlow(editor, state, halo, hooked)
            stats.recordPaint((System.nanoTime() - start) / 1_000, lastGlyphCount, lastFallbackSegments, atlas)
        } finally {
            halo.dispose()
        }
    }

    /**
     * [onlyOutsideClip] restricts painting to glyphs on visual lines the editor does not draw in this pass (see the
     * class comment); otherwise every glyph of the inflated clip range is painted.
     */
    private fun paintGlow(editor: Editor, state: GlowSettings.State, g: Graphics, onlyOutsideClip: Boolean) {
        val editorEx = editor as? EditorEx ?: return
        val document = editor.document
        if (document.textLength == 0) return
        val g2 = g as Graphics2D
        g2.getClipBounds(clip)
        if (clip.isEmpty) return

        if (onlyOutsideClip) {
            // Same visual-line range as EditorPainter: yToVisualLine(clip.y) .. yToVisualLine(clip.maxY - 1).
            skipTop = editor.visualLineToY(editor.yToVisualLine(clip.y))
            skipBottom = editor.visualLineToY(editor.yToVisualLine(clip.y + clip.height - 1) + 1)
        } else {
            skipTop = 0
            skipBottom = 0
        }

        val radius = state.radiusPx
        val sysScale = JBUIScale.sysScale(g2).takeIf { it > 0f } ?: 1f
        val padUser = repaintInflation(radius, state.synthwaveStyle)
        clip.grow(padUser, padUser)

        val lineCount = document.lineCount
        point.setLocation(0, clip.y)
        val startLine = editor.xyToLogicalPosition(point).line
        if (startLine >= lineCount) return
        point.setLocation(0, clip.y + clip.height)
        val endLine = min(editor.xyToLogicalPosition(point).line, lineCount - 1)

        if (!onlyOutsideClip) {
            paintLines(editorEx, state, g2, sysScale, radius, startLine, endLine)
            return
        }
        // Walk only the logical lines above and below the drawn visual lines; the boundary lines stay included
        // because a soft-wrapped line may be drawn only partially, and the per-glyph check drops the drawn part.
        point.setLocation(0, skipTop)
        val drawnStart = editor.xyToLogicalPosition(point).line
        point.setLocation(0, skipBottom - 1)
        val drawnEnd = editor.xyToLogicalPosition(point).line
        if (drawnEnd <= drawnStart) {
            paintLines(editorEx, state, g2, sysScale, radius, startLine, endLine)
            return
        }
        if (drawnStart >= startLine) paintLines(editorEx, state, g2, sysScale, radius, startLine, min(drawnStart, endLine))
        if (drawnEnd <= endLine) paintLines(editorEx, state, g2, sysScale, radius, max(drawnEnd, startLine), endLine)
    }

    private fun paintLines(
        editor: EditorEx, state: GlowSettings.State, g2: Graphics2D, sysScale: Float, radius: Float,
        startLine: Int, endLine: Int,
    ) {
        val document = editor.document
        val startOffset = document.getLineStartOffset(startLine)
        val endOffset = document.getLineEndOffset(endLine)
        if (endOffset <= startOffset) return

        atlas.intensity = state.intensity
        val scheme = editor.colorsScheme
        val backgroundRgb = scheme.defaultBackground.rgb
        val effectiveBrightness = state.effectiveBrightness(backgroundRgb)
        val defaultForeground = scheme.defaultForeground.rgb
        val text = document.immutableCharSequence
        val frc = editorFontRenderContext(g2)
        val foldingModel = editor.foldingModel

        val oldInterpolation = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION)
        val alpha = g2.composite as AlphaComposite
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        try {
            val iterator = editor.highlighter.createIterator(startOffset)
            while (!iterator.atEnd() && iterator.start < endOffset) {
                val tokenStart = maxOf(iterator.start, startOffset)
                val tokenEnd = min(iterator.end, endOffset)
                if (tokenEnd > tokenStart && !foldingModel.isOffsetCollapsed(tokenStart)) {
                    val attributes = iterator.textAttributes
                    val argb = (attributes?.foregroundColor?.rgb ?: defaultForeground) or ALPHA_MASK
                    val style = attributes?.fontType ?: Font.PLAIN
                    val font = scheme.getFont(EditorFontType.forJavaStyle(style))
                    val layered = state.synthwaveStyle && SynthwaveTextStyle.rule(argb, backgroundRgb) != null
                    if (layered || state.regularText) {
                        g2.composite = alpha.derive(alpha.alpha * if (layered) 1f else effectiveBrightness)
                        paintSegments(editor, text, tokenStart, tokenEnd, font, style, argb, frc, sysScale, radius, g2)
                    }
                }
                iterator.advance()
            }
        } finally {
            g2.composite = alpha
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation ?: RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        }
    }

    /** [TokenLayout.editorFontRenderContext] of the graphics' context, cached while the context stays the same. */
    private fun editorFontRenderContext(g2: Graphics2D): FontRenderContext {
        val context = g2.fontRenderContext
        val cached = editorContext
        if (cached != null && context == graphicsContext) return cached
        val editorFrc = TokenLayout.editorFontRenderContext(context)
        graphicsContext = context
        editorContext = editorFrc
        return editorFrc
    }

    /** Splits `text[start, end)` at line breaks, tabs and surrounding whitespace and paints each piece. */
    private fun paintSegments(
        editor: Editor, text: CharSequence, start: Int, end: Int, font: Font, style: Int, argb: Int,
        frc: FontRenderContext, sysScale: Float, radius: Float, g2: Graphics2D,
    ) {
        var i = start
        while (i < end) {
            while (i < end && isSeparator(text[i])) i++
            if (i >= end) return
            val segmentStart = i
            while (i < end && !isSeparator(text[i])) i++
            paintSegment(editor, text, segmentStart, i, font, style, argb, frc, sysScale, radius, g2)
        }
    }

    private fun isSeparator(c: Char): Boolean = c == '\n' || c == '\t' || c == '\r'

    private fun paintSegment(
        editor: Editor, text: CharSequence, start: Int, end: Int, font: Font, style: Int, argb: Int,
        frc: FontRenderContext, sysScale: Float, radius: Float, g2: Graphics2D,
    ) {
        val count = layout.layout(editor, text, start, end, font, style, frc)
        if (layout.usedFallback) lastFallbackSegments++
        if (count == 0) return
        val xs = layout.xs
        val ys = layout.ys
        val codes = layout.codes
        val fonts = layout.fonts
        val blank = layout.blank
        val state = settings.state
        val backgroundRgb = editor.colorsScheme.defaultBackground.rgb
        val effectiveBrightness = state.effectiveBrightness(backgroundRgb)
        val rule = if (state.synthwaveStyle) SynthwaveTextStyle.rule(argb, backgroundRgb) else null
        val layered = rule != null
        val inverseScale = 1.0 / sysScale
        var keyFont: Font? = null
        var family = ""
        for (i in 0 until count) {
            if (blank[i]) continue
            if (ys[i] >= skipTop && ys[i] < skipBottom) continue
            val glyphFont = fonts[i]!!
            if (glyphFont !== keyFont) {
                keyFont = glyphFont
                family = glyphFont.family
            }
            val key = GlyphKey(codes[i], family, glyphFont.style, glyphFont.size2D, argb, sysScale, radius,
                synthwaveStyle = layered, brightness = if (layered) effectiveBrightness else 1f, textStyleRule = rule)
            var mask = atlas.find(key)
            if (mask == null) {
                if (!workBudget.allowGlyph(state.performanceMode)) continue
                val outline = layout.outline(i)
                val maxRadius = if (layered) SynthwaveTextStyle.maxRadius(radius) else radius
                if (!GlowWorkBudget.safeRaster(outline.bounds2D, sysScale, maxRadius)) continue
                mask = atlas.render(key, outline)
            }
            val deviceX = (xs[i] * sysScale).roundToInt() + mask.offsetX
            val deviceY = (ys[i] * sysScale).roundToInt() + mask.offsetY
            blit.setTransform(inverseScale, 0.0, 0.0, inverseScale, deviceX * inverseScale, deviceY * inverseScale)
            g2.drawImage(mask.image, blit, null)
            lastGlyphCount++
        }
    }

    companion object {
        private const val ALPHA_MASK = 0xFF shl 24

        /**
         * User-space pixels a halo may spill beyond its glyph for a given glow radius: the raster padding `3σ`
         * converted back to user space (`σ = radius · scale / 2`, so `3σ / scale = 1.5 · radius`), rounded up.
         * SynthWave-style text uses the widest shadow layer. Clips are inflated and edited lines repainted by
         * this amount.
         */
        fun repaintInflation(radiusPx: Float, synthwaveStyle: Boolean = false): Int =
            GaussianBlur.radius(GlowMaskRenderer.sigmaFor(
                if (synthwaveStyle) SynthwaveTextStyle.maxRadius(radiusPx) else radiusPx, 1f)) + 1
    }
}
