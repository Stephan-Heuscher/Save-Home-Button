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
        // Dashed line: 10dp ON, 10dp OFF
        val dashSize = 10f * context.resources.displayMetrics.density
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dashSize, dashSize), 0f)
    }

    private var ghostPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        // Typeface handling (optional)
    }
    
    private var isSafeHomeMode = false
    private var cornerRadius = 0f

    init {
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
        
        // Update anchor text color (white with half-alpha for ghost effect)
        textPaint.color = 0xFFFFFFFF.toInt()
        textPaint.alpha = AppConstants.GHOST_ALPHA
        
        isSafeHomeMode = settings.tapBehavior == "SAFE_HOME"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isVisible) return

        val start = startPoint
        val end = endPoint

        if (start != null && end != null) {
            // Draw Ghost Anchor Shape
            val radius = (AppConstants.DOT_SIZE_DP * context.resources.displayMetrics.density) / 2
            
            if (isSafeHomeMode) {
                // Draw Rounded Rectangle
                val left = start.x - radius
                val top = start.y - radius
                val right = start.x + radius
                val bottom = start.y + radius
                canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, ghostPaint)
            } else {
                // Draw Circle
                canvas.drawCircle(start.x.toFloat(), start.y.toFloat(), radius, ghostPaint)
            }
            
            // Draw Anchor Emoji "⚓"
            // Calculating vertical centering for text
            val textSize = radius * 1.2f
            textPaint.textSize = textSize
            val fontMetrics = textPaint.fontMetrics
            // Center Y = cy - (descent + ascent) / 2
            val textY = start.y - (fontMetrics.descent + fontMetrics.ascent) / 2
            
            canvas.drawText("⚓", start.x.toFloat(), textY, textPaint)

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
