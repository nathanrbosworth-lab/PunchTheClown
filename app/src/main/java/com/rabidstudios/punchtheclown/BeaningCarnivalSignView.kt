package com.rabidstudios.punchtheclown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class BeaningCarnivalSignView(
    context: Context,
    private val label: String,
    private val scheme: Scheme,
    private val mountedSolidly: Boolean = false
) : View(context) {

    enum class Scheme { RED, BLUE }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val bodyTop = if (mountedSolidly) h * 0.08f else h * 0.20f
        val bodyBottom = h * 0.92f

        val sign = Path().apply {
            moveTo(w * 0.06f, bodyTop)
            lineTo(w * 0.84f, bodyTop)
            lineTo(w * 0.98f, (bodyTop + bodyBottom) / 2f)
            lineTo(w * 0.84f, bodyBottom)
            lineTo(w * 0.06f, bodyBottom)
            lineTo(w * 0.02f, h * 0.74f)
            lineTo(w * 0.02f, h * 0.30f)
            close()
        }

        fill.color = Color.rgb(48, 28, 20)
        canvas.drawPath(sign, fill)

        canvas.save()
        canvas.clipPath(sign)
        val plankColors = when (scheme) {
            Scheme.RED -> intArrayOf(
                Color.rgb(167, 40, 39),
                Color.rgb(143, 29, 31),
                Color.rgb(177, 46, 40)
            )
            Scheme.BLUE -> intArrayOf(
                Color.rgb(18, 81, 118),
                Color.rgb(13, 69, 104),
                Color.rgb(20, 87, 123)
            )
        }
        val plankH = (bodyBottom - bodyTop) / 3f
        for (i in 0..2) {
            fill.color = plankColors[i]
            canvas.drawRect(0f, bodyTop + i * plankH, w, bodyTop + (i + 1) * plankH, fill)
            if (i > 0) {
                stroke.color = Color.argb(160, 45, 20, 12)
                stroke.strokeWidth = maxOf(1f, h * 0.010f)
                canvas.drawLine(0f, bodyTop + i * plankH, w, bodyTop + i * plankH, stroke)
            }
        }

        stroke.strokeWidth = maxOf(1f, h * 0.006f)
        repeat(18) { i ->
            val y = bodyTop + 8f + ((i * 37) % maxOf(10, (bodyBottom - bodyTop - 16f).toInt()))
            val x = ((i * 53) % maxOf(20, (w * 0.76f).toInt())).toFloat() + w * 0.08f
            val len = w * (0.08f + ((i % 5) * 0.018f))
            stroke.color = if (i % 3 == 0) Color.argb(55, 255, 225, 170) else Color.argb(65, 24, 9, 5)
            canvas.drawLine(x, y, min(w * 0.83f, x + len), y + (i % 3 - 1), stroke)
        }
        canvas.restore()

        stroke.style = Paint.Style.STROKE
        stroke.strokeJoin = Paint.Join.ROUND
        stroke.color = Color.rgb(87, 46, 24)
        stroke.strokeWidth = maxOf(4f, h * 0.065f)
        canvas.drawPath(sign, stroke)
        stroke.color = Color.rgb(226, 160, 48)
        stroke.strokeWidth = maxOf(2f, h * 0.030f)
        canvas.drawPath(sign, stroke)

        drawBulb(canvas, w * 0.07f, bodyTop + (bodyBottom - bodyTop) * 0.22f, h)
        drawBulb(canvas, w * 0.07f, bodyTop + (bodyBottom - bodyTop) * 0.78f, h)
        drawBulb(canvas, w * 0.90f, (bodyTop + bodyBottom) / 2f, h)

        if (mountedSolidly) {
            drawStar(canvas, w * 0.16f, (bodyTop + bodyBottom) / 2f, h * 0.13f)
            drawStar(canvas, w * 0.79f, (bodyTop + bodyBottom) / 2f, h * 0.13f)
        } else {
            drawStar(canvas, w * 0.17f, (bodyTop + bodyBottom) / 2f, h * 0.14f)
            drawStar(canvas, w * 0.74f, (bodyTop + bodyBottom) / 2f, h * 0.14f)
        }

        val lines = label.split("\n")
        val maxTextWidth = if (mountedSolidly) w * 0.60f else w * 0.55f
        var size = h * if (mountedSolidly) 0.24f else 0.30f
        textPaint.textSize = size
        while (size > h * 0.12f && lines.maxOf { textPaint.measureText(it) } > maxTextWidth) {
            size *= 0.94f
            textPaint.textSize = size
        }
        textPaint.color = Color.rgb(247, 231, 198)
        textPaint.setShadowLayer(maxOf(2f, h * 0.02f), h * 0.012f, h * 0.018f, Color.rgb(76, 27, 16))

        val fm = textPaint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent) * 0.90f
        val totalHeight = lineHeight * lines.size
        var y = (bodyTop + bodyBottom) / 2f - totalHeight / 2f - fm.ascent
        val cx = w * 0.49f
        lines.forEach { line ->
            canvas.drawText(line, cx, y, textPaint)
            y += lineHeight
        }
    }

    private fun drawBulb(canvas: Canvas, cx: Float, cy: Float, h: Float) {
        val r = h * 0.045f
        fill.color = Color.rgb(117, 52, 20)
        canvas.drawCircle(cx, cy, r * 1.35f, fill)
        fill.color = Color.rgb(255, 172, 40)
        canvas.drawCircle(cx, cy, r, fill)
        fill.color = Color.rgb(255, 244, 190)
        canvas.drawCircle(cx - r * 0.22f, cy - r * 0.24f, r * 0.34f, fill)
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) radius else radius * 0.44f
            val angle = -Math.PI / 2.0 + i * Math.PI / 5.0
            val x = cx + cos(angle).toFloat() * r
            val y = cy + sin(angle).toFloat() * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        fill.color = Color.rgb(232, 164, 30)
        canvas.drawPath(path, fill)
        stroke.color = Color.rgb(137, 76, 20)
        stroke.strokeWidth = maxOf(1f, radius * 0.10f)
        canvas.drawPath(path, stroke)
    }
}
