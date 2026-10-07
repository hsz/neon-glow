package info.chrzanowski.neonglow.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.util.ui.JBSwingUtilities
import info.chrzanowski.neonglow.render.GlowStats
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.render.ImageGlowAtlas
import info.chrzanowski.neonglow.settings.GlowSettings
import java.awt.Color
import java.awt.Component
import java.awt.Window
import javax.swing.JComponent

/** Owns the global paint hook for Swing components and IDE windows. */
class UiGlow(
    atlas: GlyphGlowAtlas,
    settings: () -> GlowSettings.State,
    powerSave: () -> Boolean,
    stats: GlowStats = GlowStats.DISABLED,
    private val imageAtlas: ImageGlowAtlas = ImageGlowAtlas(),
) : Disposable {

    private var disposed = false
    private val transform = JBSwingUtilities.addGlobalCGTransform { component, graphics ->
        val editorText = isEditorText(component)
        val background = textBackground(component)
        when {
            disposed -> graphics
            GlowGraphics2D.isWrapped(graphics) -> GlowGraphics2D.withTextTarget(graphics, editorText, background)
            else -> GlowGraphics2D(graphics, atlas, settings, { disposed || powerSave() }, stats, imageAtlas,
                editorText = editorText, textBackground = background)
        }
    }

    fun repaintAll() {
        for (window in Window.getWindows()) {
            if (window.isDisplayable) window.repaint()
        }
    }

    fun clearCache() {
        imageAtlas.clear()
    }

    internal fun diagnostics(): String =
        "icon masks: ${imageAtlas.size}; icon bytes: ${imageAtlas.bytes}; " +
            "icon hits/misses: ${imageAtlas.hits}/${imageAtlas.misses}"

    private fun isEditorText(component: Component): Boolean {
        var current: Component? = component
        while (current != null) {
            if (current is EditorComponentImpl || current is EditorGutterComponentEx) return true
            current = current.parent
        }
        return false
    }

    private fun textBackground(component: Component): Color? {
        var current: Component? = component
        while (current != null) {
            if (current is JComponent && current.isOpaque) return current.background?.takeIf { it.alpha == 255 }
            current = current.parent
        }
        return null
    }

    override fun dispose() {
        disposed = true
        transform.dispose()
        clearCache()
    }
}