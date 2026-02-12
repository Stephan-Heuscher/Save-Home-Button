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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isVisible) return

        val start = startPoint
        val end = endPoint

        if (start != null && end != null) {
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
