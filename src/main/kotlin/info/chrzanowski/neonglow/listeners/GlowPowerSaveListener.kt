package info.chrzanowski.neonglow.listeners

import com.intellij.ide.PowerSaveMode
import info.chrzanowski.neonglow.GlowManager

/**
 * Repaints every glowing editor when Power Save mode is toggled: the renderer paints nothing while
 * [PowerSaveMode.isEnabled] is `true`, so the repaint hides or restores the glow. Cached masks stay valid.
 */
class GlowPowerSaveListener : PowerSaveMode.Listener {

    override fun powerSaveStateChanged() {
        GlowManager.getInstance().repaintAll()
    }
}
