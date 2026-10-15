package info.chrzanowski.neonglow.ui

import com.intellij.ui.Graphics2DDelegate
import info.chrzanowski.neonglow.render.GlowWorkBudget
import info.chrzanowski.neonglow.render.SelectionGlowPainter
import info.chrzanowski.neonglow.settings.GlowSettings
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.Rectangle2D

internal data class SelectionGlowTarget(val colour: Color, val background: Color?, val editor: Boolean)

/** Only selection-coloured fills inside a text selection owner receive a halo. */
internal class SelectionGlowGraphics2D(
    graphics: Graphics2D,
    private val painter: SelectionGlowPainter,
    private val settings: () -> GlowSettings.State,
    private val powerSave: () -> Boolean,
    private val selectionColour: Color?,
    private val textBackground: Color?,
    private val editor: Boolean,
    private val budget: GlowWorkBudget = GlowWorkBudget(),
    private val selectionAt: ((Graphics2D, Rectangle2D) -> SelectionGlowTarget?)? = null,
    val localPolicy: Boolean = false,
) : Graphics2DDelegate(graphics) {

    override fun create(): Graphics = SelectionGlowGraphics2D(myDelegate.create() as Graphics2D,
        painter, settings, powerSave, selectionColour, textBackground, editor, budget, selectionAt, localPolicy)

    override fun create(x: Int, y: Int, width: Int, height: Int): Graphics =
        SelectionGlowGraphics2D(myDelegate.create(x, y, width, height) as Graphics2D,
            painter, settings, powerSave, selectionColour, textBackground, editor, budget, selectionAt, localPolicy)

    override fun fillRect(x: Int, y: Int, width: Int, height: Int) {
        paintSelection(Rectangle(x, y, width, height))
        myDelegate.fillRect(x, y, width, height)
    }

    override fun fill(shape: Shape) {
        paintSelection(shape)
        myDelegate.fill(shape)
    }

    private fun paintSelection(bounds: Shape) {
        val state = settings()
        if (!state.enabled || !state.selectionGlow || state.brightness <= 0f || powerSave()) return
        val colour = paint as? Color ?: return
        val target = if (selectionAt != null) selectionAt.invoke(myDelegate, bounds.bounds2D)
            else selectionColour?.let { SelectionGlowTarget(it, textBackground, editor) }
        if (target == null || colour.rgb != target.colour.rgb || colour.rgb == target.background?.rgb) return
        val strength = if (target.editor) state.editorGlowStrength else state.uiGlowStrength
        if (strength <= 0f) return
        painter.paint(GlowGraphics2D.withoutGlow(myDelegate), bounds, colour, target.background, state, strength, budget)
    }

    companion object {
        fun withoutSelection(graphics: Graphics2D): Graphics2D =
            if (graphics is SelectionGlowGraphics2D) graphics.delegate else graphics
    }
}