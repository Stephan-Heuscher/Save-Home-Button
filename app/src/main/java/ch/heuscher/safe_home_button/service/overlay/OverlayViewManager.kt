package ch.heuscher.safe_home_button.service.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import ch.heuscher.safe_home_button.R
import ch.heuscher.safe_home_button.domain.model.DotPosition
import ch.heuscher.safe_home_button.domain.model.OverlayMode
import ch.heuscher.safe_home_button.domain.model.OverlaySettings
import ch.heuscher.safe_home_button.util.AppConstants

/**
 * Navigation bar position on screen
 */
enum class NavBarPosition {
    BOTTOM, LEFT, RIGHT, NONE
}

/**
 * Manages the overlay view creation, positioning, and appearance.
 * Handles the floating dot display.
 */
class OverlayViewManager(
    private val context: Context,
    private val windowManager: WindowManager
) {
    companion object {
        private const val TAG = "OverlayViewManager"
    }

    private var floatingView: View? = null  // The overlay container
    private var floatingDot: ImageView? = null  // The button view
    private var floatingDotHalo: View? = null
    private var tooltipContainer: android.widget.FrameLayout? = null  // Container for tooltip
    private var layoutParams: WindowManager.LayoutParams? = null  // Window params for positioning
    private var touchListener: View.OnTouchListener? = null
    private var fadeAnimator: ValueAnimator? = null
    private var haloAnimator: ValueAnimator? = null
    private val fadeHandler = Handler(Looper.getMainLooper())
    private var fadeRunnable: Runnable? = null

    // Cache nav bar height, position
    private var cachedNavBarHeight: Int? = null
    private var cachedNavBarPosition: NavBarPosition = NavBarPosition.NONE

    /**
     * Creates and adds the overlay view to the window.
     */
    fun createOverlayView(): View {
        if (floatingView != null) return floatingView!!

        floatingView = LayoutInflater.from(context).inflate(R.layout.overlay_layout, null)
        floatingDot = floatingView?.findViewById<ImageView>(R.id.floating_dot)
        tooltipContainer = floatingView?.findViewById(R.id.tooltip_container)

        // Listen for insets to get accurate nav bar height
        floatingView?.setOnApplyWindowInsetsListener { view, insets ->
            // Clear cache so next call to getNavigationBarHeight recalculates
            cachedNavBarHeight = null
            cachedNavBarPosition = NavBarPosition.NONE
            // Trigger recalculation by calling getNavigationBarMargin
            getNavigationBarMargin()
            insets
        }

        setupLayoutParams()
        windowManager.addView(floatingView, layoutParams)

        touchListener?.let { listener ->
            // Only attach touch listener to the button itself, not the container
            floatingDot?.setOnTouchListener(listener)
        }

        return floatingView!!
    }

    /**
     * Removes the overlay view from the window.
     */
    fun removeOverlayView() {
        fadeAnimator?.cancel()
        fadeAnimator = null
        haloAnimator?.cancel()
        haloAnimator = null
        fadeRunnable?.let { fadeHandler.removeCallbacks(it) }
        fadeRunnable = null
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
        floatingDot = null
        layoutParams = null
    }

    /**
     * Brings the button view to front by removing and re-adding it to WindowManager.
     * This ensures the button appears above other overlay windows (like tooltips).
     */
    fun bringToFront() {
        val view = floatingView ?: return
        val params = layoutParams ?: return

        try {
            // Remove view from window manager
            windowManager.removeView(view)

            // Re-add view with same parameters (this brings it to front)
            windowManager.addView(view, params)

            // Re-attach touch listener after re-adding
            touchListener?.let { listener ->
                floatingDot?.setOnTouchListener(listener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bring button to front", e)
        }
    }

    /**
     * Updates the overlay appearance based on settings.
     */
    fun updateAppearance(settings: OverlaySettings) {
        showNormalDot(settings)
    }

    /**
     * Updates the position of the overlay window.
     * Uses absolute Top-Left coordinates (Gravity.TOP | Gravity.LEFT).
     */
    fun updatePosition(position: DotPosition) {
        layoutParams?.let { params ->
            // Use direct coordinates for Top-Left gravity
            params.x = position.x
            params.y = position.y

            floatingView?.let { windowManager.updateViewLayout(it, params) }
        }
    }

    /**
     * Gets the current position of the overlay window.
     * Returns absolute Top-Left coordinates.
     */
    fun getCurrentPosition(): DotPosition? {
        return layoutParams?.let { params ->
            DotPosition(params.x, params.y)
        }
    }

    /**
     * Gets the tooltip container to add tooltip views.
     */
    fun getTooltipContainer(): android.widget.FrameLayout? {
        return tooltipContainer
    }

    /**
     * Sets the visibility of the overlay view.
     */
    fun setVisibility(visibility: Int) {
        floatingView?.visibility = visibility
    }

    /**
     * Fades in the overlay view over the specified duration.
     * Uses manual Handler-based animation to bypass system animator settings.
     */
    fun fadeIn(duration: Long = 300L) {
        floatingView?.apply {
            // Check animator duration scale
            val animatorScale = try {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
            } catch (e: Exception) {
                1.0f
            }

            // Cancel any ongoing animations
            fadeRunnable?.let { fadeHandler.removeCallbacks(it) }

            // Set initial alpha
            alpha = 0f
            visibility = View.VISIBLE

            // Manual animation using Handler - bypasses system animator settings
            val frameIntervalMs = 16L // ~60fps
            val totalFrames = (duration / frameIntervalMs).toInt()
            var currentFrame = 0
            val startTime = System.currentTimeMillis()

            fadeRunnable = object : Runnable {
                override fun run() {
                    currentFrame++
                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

                    floatingView?.alpha = progress

                    if (progress < 1f) {
                        fadeHandler.postDelayed(this, frameIntervalMs)
                    } else {
                        floatingView?.alpha = 1f
                    }
                }
            }

            fadeHandler.post(fadeRunnable!!)
        } ?: Log.w(TAG, "fadeIn: floatingView is null, cannot animate")
    }

    /**
     * Registers a touch listener for gesture detection on overlay elements.
     * Only attaches to the button itself, not the container.
     */
    fun setTouchListener(listener: View.OnTouchListener) {
        touchListener = listener
        // Only attach touch listener to the button itself, not the container
        floatingDot?.setOnTouchListener(listener)
    }

    /**
     * Get the navigation bar margin (actual nav bar height + safety margin)
     */
    fun getNavigationBarMargin(): Int {
        val density = context.resources.displayMetrics.density
        val detectedHeightPx = getNavigationBarHeight()

        // If WindowInsets returns 0 (transparent/gesture nav), use safe minimum from constants
        val minNavBarHeightPx = (AppConstants.NAV_BAR_MIN_HEIGHT_DP * density).toInt()
        val navBarHeightPx = if (detectedHeightPx == 0) minNavBarHeightPx else detectedHeightPx

        val safetyMarginPx = (AppConstants.NAV_BAR_SAFETY_MARGIN_DP * density).toInt()
        val totalMarginPx = navBarHeightPx + safetyMarginPx

        return totalMarginPx
    }

    /**
     * Calculates constrained position within screen bounds.
     * Accounts for button being centered in larger layout.
     * Adds virtual border at navigation bar to prevent overlap.
     * Ensures button is always below status bar.
     */
    fun constrainPositionToBounds(x: Int, y: Int): Pair<Int, Int> {
        val screenSize = getScreenSize()
        val layoutSize = (AppConstants.OVERLAY_LAYOUT_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val buttonSize = (AppConstants.DOT_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val offset = (layoutSize - buttonSize) / 2

        // Get navigation bar margin (actual height + safety margin)
        val navBarMargin = getNavigationBarMargin()
        
        // Apply margin based on nav bar position
        val minX: Int
        val maxX: Int
        val minY: Int
        val maxY: Int

        when (cachedNavBarPosition) {
            NavBarPosition.BOTTOM -> {
                // Nav bar at bottom - constrain bottom edge
                minX = -offset
                maxX = screenSize.x - buttonSize - offset
                minY = -offset
                maxY = screenSize.y - buttonSize - offset - navBarMargin
            }
            NavBarPosition.LEFT -> {
                // Nav bar on left - constrain left edge
                minX = -offset + navBarMargin
                maxX = screenSize.x - buttonSize - offset
                minY = -offset
                maxY = screenSize.y - buttonSize - offset
            }
            NavBarPosition.RIGHT -> {
                // Nav bar on right - constrain right edge
                minX = -offset
                maxX = screenSize.x - buttonSize - offset - navBarMargin
                minY = -offset
                maxY = screenSize.y - buttonSize - offset
            }
            NavBarPosition.NONE -> {
                // Should not happen (we guess from rotation now), but fallback to bottom
                minX = -offset
                maxX = screenSize.x - buttonSize - offset
                minY = -offset
                maxY = screenSize.y - buttonSize - offset - navBarMargin
            }
        }

        val constrainedX = x.coerceIn(minX, maxX)
        val constrainedY = y.coerceIn(minY, maxY)

        if (System.currentTimeMillis() % 100 == 0L) { // Don't spam logs too much
            Log.v(TAG, "Constraint: Input=($x,$y), Bounds=[x:$minX-$maxX, y:$minY-$maxY], NavBarMargin=$navBarMargin, Result=($constrainedX,$constrainedY)")
        }

        return Pair(constrainedX, constrainedY)
    }

    private fun setupLayoutParams() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Use fixed layout size to contain the button
        // Button is 48dp, use slightly larger container for visual effects if needed
        val layoutSize = (AppConstants.OVERLAY_LAYOUT_SIZE_DP * context.resources.displayMetrics.density).toInt()

        layoutParams = WindowManager.LayoutParams(
            layoutSize,
            layoutSize,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Use TOP | LEFT for absolute positioning
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0

            // Fix for Android 11+ (API 30+): Ensure window ignores system bar insets
            // This prevents the coordinate system from being shifted down by the status bar
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
            }

            // Allow window to extend into display cutout area (notch)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun showNormalDot(settings: OverlaySettings) {
        floatingDot?.visibility = View.VISIBLE

        floatingDot?.let { dotView ->
            val drawable = GradientDrawable().apply {
                // Square shape only for SAFE_HOME mode, circle for others
                if (settings.tapBehavior == "SAFE_HOME") {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8f * context.resources.displayMetrics.density
                    dotView.setImageResource(R.drawable.ic_home_white)
                } else {
                    shape = GradientDrawable.OVAL
                    dotView.setImageDrawable(null)
                }
                setColor(settings.getColorWithAlpha())
                setStroke(
                    (AppConstants.DOT_STROKE_WIDTH_DP * context.resources.displayMetrics.density).toInt(),
                    android.graphics.Color.WHITE
                )
            }
            dotView.background = drawable
        }
    }

    private fun getScreenSize(): Point {
        val size = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.maximumWindowMetrics
            val bounds = windowMetrics.bounds
            size.x = bounds.width()
            size.y = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(size)
        }
        return size
    }

    /**
     * Get current screen rotation
     */
    private fun getCurrentRotation(): Int {
        // Use windowManager.defaultDisplay for all versions since Service context
        // is not associated with a display (would crash on Android R+)
        @Suppress("DEPRECATION")
        return windowManager.defaultDisplay.rotation
    }

    /**
     * Guess nav bar position based on screen rotation (for transparent/gesture nav bars)
     */
    private fun guessNavBarPositionFromRotation(rotation: Int): NavBarPosition {
        return when (rotation) {
            android.view.Surface.ROTATION_0 -> NavBarPosition.BOTTOM // Portrait
            android.view.Surface.ROTATION_90 -> NavBarPosition.RIGHT // Landscape (rotated left)
            android.view.Surface.ROTATION_180 -> NavBarPosition.BOTTOM // Portrait upside down
            android.view.Surface.ROTATION_270 -> NavBarPosition.LEFT // Landscape (rotated right)
            else -> NavBarPosition.BOTTOM
        }
    }

    /**
     * Calculate the navigation bar height using WindowInsets
     * This gets the ACTUAL current nav bar height, not a default value
     * Handles both portrait (bottom) and landscape (side) nav bars
     * Returns height in pixels (px)
     */
    private fun getNavigationBarHeight(): Int {
        // Return cached value if available
        cachedNavBarHeight?.let { return it }

        val density = context.resources.displayMetrics.density
        // Sanity check cap
        val maxNavBarHeightPx = (AppConstants.NAV_BAR_MAX_HEIGHT_DP * density).toInt()

        // Try to get nav bar from window insets (most accurate)
        floatingView?.let { view ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val insets = view.rootWindowInsets
                if (insets != null) {
                    val navBarInsets = insets.getInsets(android.view.WindowInsets.Type.navigationBars())

                    // Check all sides
                    val bottomPx = navBarInsets.bottom
                    val leftPx = navBarInsets.left
                    val rightPx = navBarInsets.right

                    // Determine position based on which side has the largest inset
                    val navBarHeightPx: Int
                    when {
                        bottomPx > 0 && bottomPx >= leftPx && bottomPx >= rightPx -> {
                            cachedNavBarPosition = NavBarPosition.BOTTOM
                            navBarHeightPx = bottomPx
                        }
                        leftPx > 0 && leftPx >= bottomPx && leftPx >= rightPx -> {
                            cachedNavBarPosition = NavBarPosition.LEFT
                            navBarHeightPx = leftPx
                        }
                        rightPx > 0 && rightPx >= bottomPx && rightPx >= leftPx -> {
                            cachedNavBarPosition = NavBarPosition.RIGHT
                            navBarHeightPx = rightPx
                        }
                        else -> {
                            // All insets are 0 - transparent/gesture nav bar
                            // Guess position based on rotation
                            val rotation = getCurrentRotation()
                            cachedNavBarPosition = guessNavBarPositionFromRotation(rotation)
                            navBarHeightPx = 0
                        }
                    }

                    // Apply cap
                    val finalHeight = navBarHeightPx.coerceAtMost(maxNavBarHeightPx)
                    if (finalHeight != navBarHeightPx) {
                        Log.w(TAG, "Nav bar height capped! Original: $navBarHeightPx, Capped: $finalHeight")
                    }

                    cachedNavBarHeight = finalHeight
                    return finalHeight
                }
            } else {
                // For older Android versions, use rootWindowInsets
                @Suppress("DEPRECATION")
                val insets = view.rootWindowInsets
                if (insets != null) {
                    @Suppress("DEPRECATION")
                    val rawHeightPx = insets.systemWindowInsetBottom
                    
                    val navBarHeightPx = if (rawHeightPx > 0) {
                        cachedNavBarPosition = NavBarPosition.BOTTOM
                        rawHeightPx
                    } else {
                        // Guess from rotation for transparent nav
                        val rotation = getCurrentRotation()
                        cachedNavBarPosition = guessNavBarPositionFromRotation(rotation)
                        0
                    }

                    // Apply cap
                    val finalHeight = navBarHeightPx.coerceAtMost(maxNavBarHeightPx)
                    cachedNavBarHeight = finalHeight
                    return finalHeight
                }
            }
        }

        // Fallback: use system resources
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val rawHeightPx = if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }

        if (rawHeightPx > 0) {
            cachedNavBarPosition = NavBarPosition.BOTTOM
        } else {
            // Guess from rotation for transparent nav
            val rotation = getCurrentRotation()
            cachedNavBarPosition = guessNavBarPositionFromRotation(rotation)
        }

        // Apply cap
        val finalHeight = rawHeightPx.coerceAtMost(maxNavBarHeightPx)
        cachedNavBarHeight = finalHeight
        return finalHeight
    }

    private fun getDotSize(): Int {
        // Return the actual layout size to account for the halo
        return (AppConstants.OVERLAY_LAYOUT_SIZE_DP * context.resources.displayMetrics.density).toInt()
    }

    /**
     * Get status bar height using WindowInsets or resources
     */
    fun getStatusBarHeight(): Int {
        var insetsHeight = 0
        var resourceHeight = 0
        
        // Get height from resources (stable physical height)
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            resourceHeight = context.resources.getDimensionPixelSize(resourceId)
        }

        // Try WindowInsets (dynamic height)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use maximumWindowMetrics to get insets for a fullscreen window
            val windowMetrics = windowManager.maximumWindowMetrics
            val insets = windowMetrics.windowInsets
            val statusBarInsets = insets.getInsets(android.view.WindowInsets.Type.statusBars())
            insetsHeight = statusBarInsets.top
            
            val displayCutout = insets.displayCutout
            if (displayCutout != null) {
                val safeInsetTop = displayCutout.safeInsetTop
                if (safeInsetTop > insetsHeight) {
                    insetsHeight = safeInsetTop
                }
            }
        } else {
            // Fallback for older versions using the view's insets
            floatingView?.let { view ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("DEPRECATION")
                    val insets = view.rootWindowInsets
                    if (insets != null) {
                        @Suppress("DEPRECATION")
                        insetsHeight = insets.systemWindowInsetTop
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val displayCutout = insets.displayCutout
                            if (displayCutout != null) {
                                val safeInsetTop = displayCutout.safeInsetTop
                                if (safeInsetTop > insetsHeight) {
                                    insetsHeight = safeInsetTop
                                }
                            }
                        }
                    }
                }
            }
        }

        // Additional fallback: Try Display.getCutout() directly (API 29+)
        // This helps when WindowMetrics returns 0 for insets (e.g. in some Service contexts)
        if (insetsHeight == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.display ?: @Suppress("DEPRECATION") windowManager.defaultDisplay
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay
                }
                
                val cutout = display?.cutout
                if (cutout != null) {
                    val safeInsetTop = cutout.safeInsetTop
                    if (safeInsetTop > insetsHeight) {
                        insetsHeight = safeInsetTop
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get display cutout from Display object", e)
            }
        }

        // Use the maximum value to ensure we never go under the physical status bar area
        // even if the system reports a smaller value (e.g. during animations)
        val finalHeight = kotlin.math.max(insetsHeight, resourceHeight)
        
        return finalHeight
    }

    /**
     * Shows or hides the halo effect for drag mode.
     * Halo is disabled - always keep it hidden.
     */
    fun setDragMode(enabled: Boolean) {
        floatingDotHalo?.let { haloView ->
            // Always hide halo - no visual feedback for drag mode
            haloAnimator?.cancel()
            haloAnimator = null
            haloView.visibility = View.GONE
        }
    }
}