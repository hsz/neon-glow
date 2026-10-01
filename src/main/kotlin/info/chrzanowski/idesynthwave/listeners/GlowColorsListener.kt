package info.chrzanowski.idesynthwave.listeners

import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import info.chrzanowski.idesynthwave.GlowManager

/**
 * Drops the cached glow masks and repaints every glowing editor whenever the global colour scheme is switched or
 * edited (the LaF switch changes the scheme too), so the halos pick up the new token colours.
 */
class GlowColorsListener : EditorColorsListener {

    override fun globalSchemeChange(scheme: EditorColorsScheme?) {
        GlowManager.getInstance().invalidate()
    }
}
