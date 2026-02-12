package ch.heuscher.safe_home_button.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.View

/**
 * Manages the fullscreen overlay window for drawing the tether line.
 * This window is:
 * - Fullscreen
 * - Transparent
 * - Passthrough (Not Touchable)
 * - Below the button overlay
 */
class TetherOverlayManager(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var tetherView: TetherView? = null
    private var isVisible = false

    fun createTetherOverlay() {
        if (tetherView != null) return

        tetherView = TetherView(context)
        
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            format = PixelFormat.TRANSLUCENT
        }

        // Add view to WindowManager
        windowManager.addView(tetherView, params)
    }

    fun removeTetherOverlay() {
        tetherView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore if already removed
            }
        }
        tetherView = null
        isVisible = false
    }

    fun setTether(anchor: Point, current: Point) {
        if (tetherView == null) createTetherOverlay()
        
        tetherView?.setTetherPoints(anchor, current)
        tetherView?.visibility = View.VISIBLE
        isVisible = true
    }

    fun hideTether() {
        tetherView?.clearTether()
        tetherView?.visibility = View.GONE
        isVisible = false
    }

    fun isTetherVisible(): Boolean = isVisible
}
