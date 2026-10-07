package com.rabidstudios.punchtheclown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.min

/**
 * Compact illuminated carnival marquee used for mode selection on secondary
 * screens. It intentionally echoes the ornate in-game Punch frame: deep red
 * fascia, layered gold trim, warm sockets, and chasing multicolor bulbs.
 */
class GameModeMarqueeButtonView(
    context: Context,
    private val label: String,
    private val selected: Boolean,
    private val animationsEnabled: Boolean = true
) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    private var chaseOffset = 0

    private val palette = intArrayOf(
        Color.rgb(244, 67, 54),
        Color.rgb(255, 179, 0),
        Color.rgb(41, 182, 246),
        Color.rgb(102, 187, 106),
        Color.rgb(255, 244, 199)
    )

    private val ticker = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || !selected || !animationsEnabled) return
            chaseOffset = (chaseOffset + 1) % palette.size
            invalidate()
            postDelayed(this, 240L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (selected && animationsEnabled) post(ticker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val outer = RectF(w * 0.015f, h * 0.06f, w * 0.985f, h * 0.94f)
        val middle = RectF(w * 0.035f, h * 0.10f, w * 0.965f, h * 0.90f)
        val face = RectF(w * 0.055f, h * 0.14f, w * 0.945f, h * 0.86f)
        val radius = h * 0.20f

        // Heavy carnival cabinet edge.
        fill.color = Color.rgb(61, 29, 18)
        canvas.drawRoundRect(outer, radius, radius, fill)

        stroke.strokeWidth = maxOf(2f, h * 0.050f)
        stroke.color = Color.rgb(225, 157, 43)
        canvas.drawRoundRect(middle, radius * 0.86f, radius * 0.86f, stroke)

        // Deep painted fascia with a subtle inner highlight.
        fill.color = if (selected) Color.rgb(146, 32, 36) else Color.rgb(92, 44, 42)
        canvas.drawRoundRect(face, radius * 0.72f, radius * 0.72f, fill)

        stroke.strokeWidth = maxOf(1f, h * 0.018f)
        stroke.color = if (selected) Color.rgb(255, 209, 92) else Color.rgb(165, 119, 55)
        canvas.drawRoundRect(
            RectF(w * 0.075f, h * 0.18f, w * 0.925f, h * 0.82f),
            radius * 0.58f,
            radius * 0.58f,
            stroke
        )

        drawBulbs(canvas, outer, h)

        textPaint.textSize = h * 0.25f
        val maxWidth = w * 0.68f
        while (textPaint.textSize > h * 0.14f && textPaint.measureText(label) > maxWidth) {
            textPaint.textSize *= 0.94f
        }
        textPaint.color = if (selected) Color.rgb(255, 241, 202) else Color.rgb(224, 208, 178)
        textPaint.setShadowLayer(h * 0.028f, 0f, h * 0.018f, Color.rgb(48, 15, 11))

        val fm = textPaint.fontMetrics
        val baseline = h / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, w / 2f, baseline, textPaint)

        if (selected) {
            stroke.strokeWidth = maxOf(1f, h * 0.012f)
            stroke.color = Color.argb(115, 255, 230, 150)
            canvas.drawRoundRect(
                RectF(w * 0.095f, h * 0.22f, w * 0.905f, h * 0.78f),
                radius * 0.48f,
                radius * 0.48f,
                stroke
            )
        }
    }

    private fun drawBulbs(canvas: Canvas, bounds: RectF, h: Float) {
        val positions = ArrayList<Pair<Float, Float>>()
        val topCount = 8
        val sideCount = 2

        for (i in 0 until topCount) {
            val t = (i + 0.5f) / topCount
            positions += (bounds.left + bounds.width() * t) to bounds.top
        }
        for (i in 0 until sideCount) {
            val t = (i + 1f) / (sideCount + 1f)
            positions += bounds.right to (bounds.top + bounds.height() * t)
        }
        for (i in topCount - 1 downTo 0) {
            val t = (i + 0.5f) / topCount
            positions += (bounds.left + bounds.width() * t) to bounds.bottom
        }
        for (i in sideCount - 1 downTo 0) {
            val t = (i + 1f) / (sideCount + 1f)
            positions += bounds.left to (bounds.top + bounds.height() * t)
        }

        val r = min(width, height) * 0.040f
        positions.forEachIndexed { index, point ->
            val color = when {
                selected && animationsEnabled -> palette[(index + chaseOffset) % palette.size]
                selected -> if (index % 2 == 0) palette[1] else palette[4]
                else -> Color.rgb(176, 112, 45)
            }

            fill.color = Color.argb(if (selected) 85 else 35, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawCircle(point.first, point.second, r * 1.65f, fill)

            fill.color = Color.rgb(72, 35, 19)
            canvas.drawCircle(point.first, point.second, r * 1.10f, fill)

            fill.color = color
            canvas.drawCircle(point.first, point.second, r * 0.78f, fill)

            fill.color = Color.argb(if (selected) 210 else 120, 255, 255, 244)
            canvas.drawCircle(
                point.first - r * 0.20f,
                point.second - r * 0.22f,
                r * 0.18f,
                fill
            )
        }
    }
}
