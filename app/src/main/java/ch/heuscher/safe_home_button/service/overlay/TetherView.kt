package ch.heuscher.safe_home_button.service.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.util.AttributeSet
import android.view.View
import ch.heuscher.safe_home_button.util.AppConstants

/**
 * A fullscreen view that draws a "tether" line between the
 * anchored position and the actual position of the button.
 */
class TetherView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = 0xFFFFFFFF.toInt() // White by default, can be themed
        alpha = AppConstants.TETHER_ALPHA
        strokeWidth = AppConstants.TETHER_STROKE_WIDTH_DP * context.resources.displayMetrics.density
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private var ghostPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private var iconDrawable: android.graphics.drawable.Drawable? = null
    private var isSafeHomeMode = false
    private var cornerRadius = 0f

    init {
        // Load default icon
        iconDrawable = androidx.core.content.ContextCompat.getDrawable(context, ch.heuscher.safe_home_button.R.drawable.ic_home_white)
        cornerRadius = 8f * context.resources.displayMetrics.density
    }

    private var startPoint: Point? = null
    private var endPoint: Point? = null
    private var isVisible = false

    fun setTetherPoints(start: Point, end: Point) {
        startPoint = start
        endPoint = end
        isVisible = true
        invalidate()
    }

    fun clearTether() {
        isVisible = false
        invalidate()
    }

    fun updateAppearance(settings: ch.heuscher.safe_home_button.domain.model.OverlaySettings) {
        val baseColor = settings.color
        // Apply GHOST_ALPHA to the base color
        // settings.color is ARGB. We want to override Alpha.
        val r = android.graphics.Color.red(baseColor)
        val g = android.graphics.Color.green(baseColor)
        val b = android.graphics.Color.blue(baseColor)
        
        ghostPaint.color = android.graphics.Color.argb(AppConstants.GHOST_ALPHA, r, g, b)
        
        isSafeHomeMode = settings.tapBehavior == "SAFE_HOME"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isVisible) return

        val start = startPoint
        val end = endPoint

        if (start != null && end != null) {
            // Draw Ghost Anchor
            val radius = (AppConstants.DOT_SIZE_DP * context.resources.displayMetrics.density) / 2
            
            if (isSafeHomeMode) {
                // Draw Rounded Rectangle
                val left = start.x - radius
                val top = start.y - radius
                val right = start.x + radius
                val bottom = start.y + radius
                canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, ghostPaint)
                
                // Draw Icon
                iconDrawable?.let { icon ->
                    val iconSize = (radius * 1.2).toInt() // Approx icon size
                    val l = (start.x - iconSize / 2).toInt()
                    val t = (start.y - iconSize / 2).toInt()
                    icon.setBounds(l, t, l + iconSize, t + iconSize)
                    icon.alpha = AppConstants.GHOST_ALPHA 
                    icon.draw(canvas)
                }
            } else {
                // Draw Circle
                canvas.drawCircle(start.x.toFloat(), start.y.toFloat(), radius, ghostPaint)
            }

            // Draw Tether Line
            canvas.drawLine(
                start.x.toFloat(),
                start.y.toFloat(),
                end.x.toFloat(),
                end.y.toFloat(),
                paint
            )
        }
    }
}
