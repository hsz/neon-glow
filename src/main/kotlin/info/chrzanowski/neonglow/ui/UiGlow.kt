package info.chrzanowski.neonglow.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.util.ui.JBSwingUtilities
import info.chrzanowski.neonglow.render.GlowStats
import info.chrzanowski.neonglow.render.GlyphGlowAtlas
import info.chrzanowski.neonglow.render.ImageGlowAtlas
import info.chrzanowski.neonglow.settings.GlowSettings
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.ComponentEvent
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JRootPane
import javax.swing.RootPaneContainer

/** Owns the global paint hook and reversible root coverage for existing and newly opened Swing windows. */
class UiGlow(
    atlas: GlyphGlowAtlas,
    settings: () -> GlowSettings.State,
    powerSave: () -> Boolean,
    stats: GlowStats = GlowStats.DISABLED,
    private val imageAtlas: ImageGlowAtlas = ImageGlowAtlas(),
) : Disposable {

    private val roots = LinkedHashMap<JRootPane, GlowLayeredPane>()
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
    private val windowListener = AWTEventListener { event ->
        val window = event.source as? Window ?: return@AWTEventListener
        val root = (window as? RootPaneContainer)?.rootPane ?: return@AWTEventListener
        when (event.id) {
            WindowEvent.WINDOW_OPENED, ComponentEvent.COMPONENT_SHOWN -> installRoot(root)
            WindowEvent.WINDOW_CLOSED -> restoreRoot(root)
        }
    }

    init {
        Toolkit.getDefaultToolkit().addAWTEventListener(
            windowListener, AWTEvent.WINDOW_EVENT_MASK or AWTEvent.COMPONENT_EVENT_MASK,
        )
        for (window in Window.getWindows()) {
            if (window.isDisplayable) (window as? RootPaneContainer)?.rootPane?.let(::installRoot)
        }
    }

    internal fun installRoot(root: JRootPane) {
        if (disposed || roots.containsKey(root)) return
        val original = root.layeredPane
        val wrapper = GlowLayeredPane(original)
        roots[root] = wrapper
        root.layeredPane = wrapper
        wrapper.add(original, JLayeredPane.DEFAULT_LAYER)
        root.revalidate()
        root.repaint()
    }

    fun repaintAll() {
        for (root in roots.keys) root.repaint()
    }

    fun clearCache() {
        imageAtlas.clear()
    }

    internal fun diagnostics(): String =
        "Swing roots: ${roots.size}; icon masks: ${imageAtlas.size}; icon bytes: ${imageAtlas.bytes}; " +
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
        // The root layered pane is transparent; its content provides the backdrop inherited by plain Swing children.
        var current: Component? = if (component is GlowLayeredPane) component.rootPane?.contentPane else component
        while (current != null) {
            if (current is JComponent && current.isOpaque) return current.background?.takeIf { it.alpha == 255 }
            current = current.parent
        }
        return null
    }

    private fun restoreRoot(root: JRootPane) {
        val wrapper = roots.remove(root) ?: return
        val parent = wrapper.parent as? JLayeredPane
        if (root.layeredPane !== wrapper && parent == null) return
        // RootPane may have added lightweight popups or replaced its content while our wrapper was installed.
        for (child in wrapper.components) {
            if (child === wrapper.original) continue
            val layer = wrapper.getLayer(child)
            val position = wrapper.getPosition(child)
            wrapper.remove(child)
            wrapper.original.add(child, Integer.valueOf(layer), position)
        }
        if (root.layeredPane === wrapper) {
            root.layeredPane = wrapper.original
        } else if (parent != null) {
            val layer = parent.getLayer(wrapper)
            val position = parent.getPosition(wrapper)
            val bounds = wrapper.bounds
            wrapper.remove(wrapper.original)
            parent.remove(wrapper)
            parent.add(wrapper.original, Integer.valueOf(layer), position)
            wrapper.original.bounds = bounds
            parent.revalidate()
            parent.repaint()
        }
        root.revalidate()
        root.repaint()
    }

    override fun dispose() {
        disposed = true
        Toolkit.getDefaultToolkit().removeAWTEventListener(windowListener)
        transform.dispose()
        for (root in roots.keys.toList()) restoreRoot(root)
        clearCache()
    }

    /** Keeps the IDE's original layered pane intact, including its layout, painters and component identities. */
    private class GlowLayeredPane(val original: JLayeredPane) : JLayeredPane() {
        override fun getComponentGraphics(graphics: Graphics): Graphics =
            JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))

        override fun doLayout() {
            original.setBounds(0, 0, width, height)
        }
    }
}