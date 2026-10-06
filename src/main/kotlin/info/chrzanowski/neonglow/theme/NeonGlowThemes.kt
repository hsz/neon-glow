package info.chrzanowski.neonglow.theme

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme

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
        return scheme.name in THEME_NAMES
    }

    /**
     * Restores the user's previous look and feel and editor color scheme if a delivered theme was automatically activated.
     */
    fun restoreUserTheme(
        lafManager: LafManager = LafManager.getInstance(),
        previousLaf: UIThemeLookAndFeelInfo? = null,
        previousScheme: EditorColorsScheme? = null,
    ) {
        val currentLaf = lafManager.currentUIThemeLookAndFeel
        if (isDeliveredTheme(currentLaf)) {
            val targetLaf = if (previousLaf != null && !isDeliveredTheme(previousLaf)) {
                previousLaf
            } else {
                lafManager.defaultDarkLaf ?: lafManager.defaultLightLaf
            }
            if (targetLaf != null && !isDeliveredTheme(targetLaf)) {
                lafManager.setCurrentLookAndFeel(targetLaf, false)
            }
        }

        val colorsManager = EditorColorsManager.getInstance()
        val currentScheme = colorsManager.globalScheme
        if (isDeliveredScheme(currentScheme)) {
            val targetScheme = if (previousScheme != null && !isDeliveredScheme(previousScheme)) {
                previousScheme
            } else {
                val darkLaf = lafManager.currentUIThemeLookAndFeel?.isDark ?: true
                val defaultSchemeName = if (darkLaf) "Darcula" else "Default"
                colorsManager.getScheme(defaultSchemeName)
            }
            if (targetScheme != null && !isDeliveredScheme(targetScheme)) {
                colorsManager.setGlobalScheme(targetScheme)
            }
        }
    }
}
