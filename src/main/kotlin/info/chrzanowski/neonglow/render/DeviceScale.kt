package info.chrzanowski.neonglow.render

import com.intellij.ui.scale.JBUIScale
import java.awt.Graphics2D
import java.awt.GraphicsDevice
import kotlin.math.abs

/**
 * Device pixels per user-space unit of [graphics].
 *
 * On a screen surface the scale lives in the device configuration, which [JBUIScale.sysScale] resolves. On an
 * offscreen image (or printer) the scale is an explicit transform; [JBUIScale.sysScale] only reads it when the JRE
 * manages HiDPI, which is never the case in headless or IDE-managed HiDPI environments (Linux), so it is read here
 * directly, keeping masks for different scales distinct everywhere.
 */
object DeviceScale {
    fun of(graphics: Graphics2D): Float {
        val gc = graphics.deviceConfiguration
        val scale = if (gc == null || gc.device.type == GraphicsDevice.TYPE_IMAGE_BUFFER ||
            gc.device.type == GraphicsDevice.TYPE_PRINTER) abs(graphics.transform.scaleX).toFloat()
        else JBUIScale.sysScale(graphics)
        return scale.takeIf { it.isFinite() && it > 0f } ?: 1f
    }
}
