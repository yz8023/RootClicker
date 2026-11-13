package com.roottap.clicker.service.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View
import com.roottap.clicker.core.ClickPoint

/**
 * Draws one dashed line per swipe point, from A to B, so a swipe is visible on screen instead of
 * being two unrelated dots.
 */
class SwipeLinesView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val accent = 0xFF1B7FFF.toInt()

    var points: List<ClickPoint> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(14f * density, 9f * density), 0f)
    }

    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    init {
        setBackgroundColor(0x00000000)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        points.forEach { point ->
            if (!point.isSwipe) return@forEach
            val alpha = if (point.enabled) 255 else 110
            linePaint.alpha = alpha
            endPaint.alpha = alpha
            canvas.drawLine(
                point.x.toFloat(),
                point.y.toFloat(),
                point.x2.toFloat(),
                point.y2.toFloat(),
                linePaint,
            )
            canvas.drawCircle(point.x2.toFloat(), point.y2.toFloat(), 7f * density, endPaint)
        }
    }
}
