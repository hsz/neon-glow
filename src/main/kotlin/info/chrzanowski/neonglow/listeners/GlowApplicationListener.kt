package info.chrzanowski.neonglow.listeners

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.application.ApplicationManager
import info.chrzanowski.neonglow.GlowManager
import info.chrzanowski.neonglow.settings.GlowSettings
import info.chrzanowski.neonglow.theme.NeonGlowThemes

/** Starts UI glow even when the IDE opens on the welcome screen without any editors or projects. */
class GlowApplicationListener : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: List<String>) {
        ensureInitialThemeAndInstallUi()
    }

    override fun welcomeScreenDisplayed() {
        ensureInitialThemeAndInstallUi()
    }

    private fun ensureInitialThemeAndInstallUi() {
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