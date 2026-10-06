package info.chrzanowski.neonglow.ui

import com.intellij.ide.ui.LafManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import info.chrzanowski.neonglow.GlowManager
import info.chrzanowski.neonglow.settings.GlowSettings
import info.chrzanowski.neonglow.theme.NeonGlowThemes

/** Also initializes the hook when the plugin is loaded into an already running project. */
class GlowStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val application = ApplicationManager.getApplication()
        if (application != null && !application.isDisposed) {
            val settings = GlowSettings.getInstance()
            if (!settings.state.initialThemePreserved) {
                settings.state.initialThemePreserved = true
                NeonGlowThemes.restoreUserTheme(LafManager.getInstance())
            }
        }
        GlowManager.getInstance().installUi()
    }
}