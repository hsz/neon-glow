package info.chrzanowski.idesynthwave.listeners

import com.intellij.ide.AppLifecycleListener
import info.chrzanowski.idesynthwave.GlowManager

/** Starts UI glow even when the IDE opens on the welcome screen without any editors or projects. */
class GlowApplicationListener : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: List<String>) {
        GlowManager.getInstance().installUi()
    }

    override fun welcomeScreenDisplayed() {
        GlowManager.getInstance().installUi()
    }
}