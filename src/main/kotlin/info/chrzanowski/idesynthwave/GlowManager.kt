package info.chrzanowski.idesynthwave

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import info.chrzanowski.idesynthwave.editor.EditorGlow
import info.chrzanowski.idesynthwave.render.GlowStats
import info.chrzanowski.idesynthwave.render.GlyphGlowAtlas
import info.chrzanowski.idesynthwave.settings.GlowSettings

/**
 * Application-wide glow state: the shared [GlyphGlowAtlas] and the set of editors that currently carry a glow, so
 * that theme and settings changes can drop the cached masks and repaint every editor at once.
 */
@Service(Service.Level.APP)
class GlowManager {

    val atlas: GlyphGlowAtlas = GlyphGlowAtlas()

    /** Paint-cost statistics, active only with `-Dide.synthwave.debug=true`. */
    val stats: GlowStats = GlowStats()

    private val glows = LinkedHashSet<EditorGlow>()

    /** Editors currently carrying a glow (snapshot). */
    val attached: List<EditorGlow> get() = glows.toList()

    fun register(glow: EditorGlow) {
        glows.add(glow)
    }

    fun unregister(glow: EditorGlow) {
        glows.remove(glow)
    }

    /** Repaints every glowing editor; cached masks stay valid. */
    fun repaintAll() {
        for (glow in glows) glow.repaint()
    }

    /** Drops every cached mask (colour scheme changed) and repaints every glowing editor. */
    fun invalidate() {
        atlas.clear()
        repaintAll()
    }

    /** Whether the glow is painted at all; persisted in [GlowSettings]. Changing it repaints every editor. */
    var isEnabled: Boolean
        get() = GlowSettings.getInstance().state.enabled
        set(value) {
            val settings = GlowSettings.getInstance()
            if (settings.state.enabled == value) return
            settings.state.enabled = value
            repaintAll()
        }

    /**
     * Stores [state] (normalised) and makes it visible immediately: the atlas is cleared because radius and
     * intensity are baked into the masks, and every glowing editor is repainted.
     */
    fun applySettings(state: GlowSettings.State) {
        val settings = GlowSettings.getInstance()
        if (settings.state == state.normalized()) return
        settings.loadState(state)
        invalidate()
    }

    companion object {
        @JvmStatic
        fun getInstance(): GlowManager = ApplicationManager.getApplication().getService(GlowManager::class.java)
    }
}
