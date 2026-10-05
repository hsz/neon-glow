package info.chrzanowski.neonglow.listeners

import com.intellij.ide.AppLifecycleListener
import info.chrzanowski.neonglow.GlowManager

/** Starts UI glow even when the IDE opens on the welcome screen without any editors or projects. */
class GlowApplicationListener : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: List<String>) {
        GlowManager.getInstance().installUi()
    }

    override fun welcomeScreenDisplayed() {
        GlowManager.getInstance().installUi()
    }
}