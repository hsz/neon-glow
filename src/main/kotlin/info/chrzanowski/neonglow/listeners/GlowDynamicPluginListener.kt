package info.chrzanowski.neonglow.listeners

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import info.chrzanowski.neonglow.settings.GlowSettings
import info.chrzanowski.neonglow.theme.NeonGlowThemes

/**
 * Ensures that when the Neon Glow plugin is dynamically installed or loaded, bundled themes
 * are not automatically activated, preserving the user's active Look and Feel and color scheme.
 */
class GlowDynamicPluginListener : DynamicPluginListener {

    private var previousLaf: UIThemeLookAndFeelInfo? = null
    private var previousScheme: EditorColorsScheme? = null

    override fun beforePluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        if (pluginDescriptor.pluginId.idString == NeonGlowThemes.PLUGIN_ID) {
            val application = ApplicationManager.getApplication()
            if (application == null || application.isDisposed) return
            val lafManager = LafManager.getInstance()
            val currentLaf = lafManager.currentUIThemeLookAndFeel
            previousLaf = currentLaf
            val currentScheme = EditorColorsManager.getInstance().globalScheme
            previousScheme = currentScheme
        }
    }

    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        if (pluginDescriptor.pluginId.idString == NeonGlowThemes.PLUGIN_ID) {
            val application = ApplicationManager.getApplication()
            if (application == null || application.isDisposed) return
            val settings = GlowSettings.getInstance()
            settings.state.initialThemePreserved = true
            val lafManager = LafManager.getInstance()
            // On first install our beforePluginLoaded listener is not registered yet, but theme activation is still queued.
            val capturedLaf = previousLaf ?: lafManager.currentUIThemeLookAndFeel
            val capturedScheme = previousScheme ?: EditorColorsManager.getInstance().globalScheme
            previousLaf = null
            previousScheme = null
            NeonGlowThemes.restoreUserTheme(lafManager, capturedLaf, capturedScheme)
            application.invokeLater {
                if (!application.isDisposed) {
                    NeonGlowThemes.restoreUserTheme(lafManager, capturedLaf, capturedScheme)
                }
            }
        }
    }
}
