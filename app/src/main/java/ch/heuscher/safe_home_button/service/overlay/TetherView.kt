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
        color = 0xFFFFFFFF.toInt()
        alpha = AppConstants.TETHER_ALPHA
        strokeWidth = AppConstants.TETHER_STROKE_WIDTH_DP * context.resources.displayMetrics.density
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        // Dashed line: 10dp ON, 10dp OFF
        val dashSize = 10f * context.resources.displayMetrics.density
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dashSize, dashSize), 0f)
    }

    private val textPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
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
        // Update anchor text color with ghost alpha for semi-transparency
        textPaint.color = 0xFFFFFFFF.toInt()
        textPaint.alpha = AppConstants.GHOST_ALPHA
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isVisible) return

        val start = startPoint
        val end = endPoint

        if (start != null && end != null) {
            val radius = (AppConstants.DOT_SIZE_DP * context.resources.displayMetrics.density) / 2

            // Draw Anchor Emoji (no background shape)
            val textSize = radius * 1.2f
            textPaint.textSize = textSize
            val fontMetrics = textPaint.fontMetrics
            val textY = start.y - (fontMetrics.descent + fontMetrics.ascent) / 2

            canvas.drawText("\u2693", start.x.toFloat(), textY, textPaint)

            // Draw dashed Tether Line
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
