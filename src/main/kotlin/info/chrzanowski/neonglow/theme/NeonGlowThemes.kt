package info.chrzanowski.neonglow.theme

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.options.Scheme

object NeonGlowThemes {
    const val PLUGIN_ID: String = "info.chrzanowski.neonglow"

    const val CLASSIC_ID: String = "info.chrzanowski.neonglow.theme.classic"
    const val MIDNIGHT_ID: String = "info.chrzanowski.neonglow.theme.midnight"
    const val ACCESSIBLE_ID: String = "info.chrzanowski.neonglow.theme.accessible"

    val THEME_IDS: Set<String> = setOf(CLASSIC_ID, MIDNIGHT_ID, ACCESSIBLE_ID)
    val THEME_NAMES: Set<String> = setOf("Neon Glow", "Neon Glow Midnight", "Neon Glow Accessible")

    fun isDeliveredTheme(laf: UIThemeLookAndFeelInfo?): Boolean {
        if (laf == null) return false
        return laf.id in THEME_IDS || laf.name in THEME_NAMES
    }

    fun isDeliveredScheme(scheme: EditorColorsScheme?): Boolean {
        if (scheme == null) return false
        return scheme.name.removePrefix(Scheme.EDITABLE_COPY_PREFIX) in THEME_NAMES
    }

    /**
     * Restores captured user choices if a delivered theme was automatically activated. Never guesses defaults.
     */
    fun restoreUserTheme(
        lafManager: LafManager = LafManager.getInstance(),
        previousLaf: UIThemeLookAndFeelInfo? = null,
        previousScheme: EditorColorsScheme? = null,
    ) {
        val currentLaf = lafManager.currentUIThemeLookAndFeel
        val restoreLaf = isDeliveredTheme(currentLaf) && previousLaf != null && currentLaf != previousLaf
        if (restoreLaf) {
            lafManager.setCurrentLookAndFeel(previousLaf, true)
        }

        val colorsManager = EditorColorsManager.getInstance()
        val currentScheme = colorsManager.globalScheme
        if (isDeliveredScheme(currentScheme) && previousScheme != null && currentScheme !== previousScheme) {
            colorsManager.setGlobalScheme(previousScheme)
        }
        if (restoreLaf) {
            lafManager.updateUI()
        }
    }
}
