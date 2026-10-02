package info.chrzanowski.idesynthwave

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.colors.EditorColorsManager
import info.chrzanowski.idesynthwave.editor.EditorGlow
import info.chrzanowski.idesynthwave.render.GlowStats
import info.chrzanowski.idesynthwave.render.GlyphGlowAtlas
import info.chrzanowski.idesynthwave.settings.GlowSettings
import info.chrzanowski.idesynthwave.ui.UiGlow
import java.util.concurrent.CopyOnWriteArraySet
import javax.swing.SwingUtilities

/**
 * Application-wide glow state: the shared [GlyphGlowAtlas] and the set of editors that currently carry a glow, so
 * that theme and settings changes can drop the cached masks and repaint every editor and IDE window at once.
 */
@Service(Service.Level.APP)
class GlowManager : Disposable {

    val atlas: GlyphGlowAtlas = GlyphGlowAtlas()

    /** Paint-cost statistics, active only with `-Dide.synthwave.debug=true`. */
    val stats: GlowStats = GlowStats()

    private val glows = CopyOnWriteArraySet<EditorGlow>()
    private var uiGlow: UiGlow? = null

    @Volatile
    private var disposed = false

    fun installUi() {
        val application = ApplicationManager.getApplication()
        if (disposed || application.isHeadlessEnvironment || application.isDisposed) return
        if (!SwingUtilities.isEventDispatchThread()) {
            application.invokeLater { if (!application.isDisposed) installUi() }
            return
        }
        if (uiGlow == null) {
            val settings = GlowSettings.getInstance()
            uiGlow = UiGlow(atlas, { settings.state }, { disposed || PowerSaveMode.isEnabled() }, stats)
        }
    }

    /** Editors currently carrying a glow (snapshot). */
    val attached: List<EditorGlow> get() = glows.toTypedArray().toList()

    fun register(glow: EditorGlow) {
        if (!disposed) glows.add(glow)
    }

    fun unregister(glow: EditorGlow) {
        glows.remove(glow)
    }

    /** Repaints every glowing editor and IDE window; cached masks stay valid. */
    fun repaintAll() {
        if (!SwingUtilities.isEventDispatchThread()) {
            ApplicationManager.getApplication().invokeLater { if (!disposed) repaintAll() }
            return
        }
        for (glow in glows) glow.repaint()
        uiGlow?.repaintAll()
    }

    /** Drops every cached mask (colour scheme changed) and repaints every glowing editor. */
    fun invalidate() {
        if (disposed) return
        if (!SwingUtilities.isEventDispatchThread()) {
            ApplicationManager.getApplication().invokeLater { if (!disposed) invalidate() }
            return
        }
        atlas.clear()
        uiGlow?.clearCache()
        repaintAll()
    }

    /** Safe to share: no project names, document contents, file paths or machine identifiers. Read on the EDT. */
    fun diagnostics(): String {
        check(SwingUtilities.isEventDispatchThread())
        val settings = GlowSettings.getInstance().state
        return buildString {
            appendLine("IDE Synthwave diagnostics")
            appendLine("IDE build: ${ApplicationInfo.getInstance().build.asString()}")
            appendLine("Java: ${System.getProperty("java.version")}; OS: ${System.getProperty("os.name")}")
            appendLine("Editor scheme: ${EditorColorsManager.getInstance().globalScheme.name}")
            appendLine("Power Save: ${PowerSaveMode.isEnabled()}; UI hook installed: ${uiGlow != null}; disposed: $disposed")
            appendLine("Settings: $settings")
            appendLine("Attached editors: ${glows.size}")
            appendLine("Glyph masks: ${atlas.size}; bytes: ${atlas.bytes}/${atlas.maxBytes}; hits/misses: ${atlas.hits}/${atlas.misses}; evictions: ${atlas.evictions}")
            appendLine(uiGlow?.diagnostics() ?: "Swing roots: 0; icon cache not installed")
            appendLine("Paint timing enabled: ${stats.enabled}")
            if (stats.enabled) appendLine(stats.lastReport)
            append("Native and browser-rendered surfaces are outside Swing glow coverage.")
        }
    }

    /** Whether the glow is painted at all; persisted in [GlowSettings]. Changing it repaints every editor. */
    var isEnabled: Boolean
        get() = GlowSettings.getInstance().state.enabled
        set(value) {
            if (disposed) return
            if (!SwingUtilities.isEventDispatchThread()) {
                ApplicationManager.getApplication().invokeLater { if (!disposed) isEnabled = value }
                return
            }
            val settings = GlowSettings.getInstance()
            if (settings.state.enabled == value) return
            settings.state.enabled = value
            repaintAll()
        }

    /** Stores a normalized snapshot; opacity/target-only edits repaint without discarding warm masks. */
    fun applySettings(state: GlowSettings.State) {
        if (disposed) return
        val normalized = state.normalized()
        if (!SwingUtilities.isEventDispatchThread()) {
            ApplicationManager.getApplication().invokeLater { if (!disposed) applySettings(normalized) }
            return
        }
        val settings = GlowSettings.getInstance()
        val old = settings.state
        if (old == normalized) return
        settings.loadState(normalized)
        if (old.radiusPx != normalized.radiusPx || old.intensity != normalized.intensity ||
            old.synthwaveStyle != normalized.synthwaveStyle ||
            normalized.synthwaveStyle && old.brightness != normalized.brightness) invalidate()
        else repaintAll()
    }

    override fun dispose() {
        disposed = true
        val glow = uiGlow
        uiGlow = null
        val cleanup = {
            for (editorGlow in glows) editorGlow.detach()
            glows.clear()
            glow?.dispose()
            atlas.clear()
        }
        if (SwingUtilities.isEventDispatchThread()) cleanup()
        else SwingUtilities.invokeLater(cleanup)
    }

    companion object {
        @JvmStatic
        fun getInstance(): GlowManager = ApplicationManager.getApplication().getService(GlowManager::class.java)
    }
}
