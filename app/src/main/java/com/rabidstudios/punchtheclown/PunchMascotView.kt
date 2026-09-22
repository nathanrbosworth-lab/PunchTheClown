package com.rabidstudios.punchtheclown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class PunchMascotView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h * 0.52f
        val r = minOf(w, h) * 0.30f

        paint.color = Color.rgb(27, 18, 16)
        canvas.drawRoundRect(6f, 6f, w - 6f, h - 6f, 28f, 28f, paint)
        stroke.color = Color.rgb(232, 182, 75)
        stroke.strokeWidth = 7f
        canvas.drawRoundRect(8f, 8f, w - 8f, h - 8f, 28f, 28f, stroke)

        paint.color = Color.rgb(183, 38, 46)
        val hat = Path().apply {
            moveTo(cx - r * 0.9f, cy - r * 0.72f)
            lineTo(cx - r * 0.25f, cy - r * 1.55f)
            lineTo(cx + r * 0.2f, cy - r * 0.72f)
            lineTo(cx + r * 0.78f, cy - r * 1.35f)
            lineTo(cx + r * 0.95f, cy - r * 0.45f)
            close()
        }
        canvas.drawPath(hat, paint)
        paint.color = Color.rgb(247, 231, 198)
        canvas.drawCircle(cx, cy, r, paint)
        stroke.color = Color.rgb(57, 30, 22)
        stroke.strokeWidth = 6f
        canvas.drawCircle(cx, cy, r, stroke)

        paint.color = Color.BLACK
        canvas.drawCircle(cx - r * .34f, cy - r * .20f, r * .08f, paint)
        canvas.drawCircle(cx + r * .34f, cy - r * .20f, r * .08f, paint)
        paint.color = Color.rgb(214, 37, 47)
        canvas.drawCircle(cx, cy + r * .02f, r * .19f, paint)

        stroke.color = Color.rgb(120, 15, 25)
        stroke.strokeWidth = 7f
        val grin = Path().apply {
            moveTo(cx - r * .5f, cy + r * .34f)
            quadTo(cx, cy + r * .78f, cx + r * .58f, cy + r * .22f)
        }
        canvas.drawPath(grin, stroke)

        paint.color = Color.rgb(232, 182, 75)
        canvas.drawCircle(cx - r * .92f, cy - r * 1.05f, r * .11f, paint)
        canvas.drawCircle(cx + r * .80f, cy - r * 1.34f, r * .11f, paint)
    }
}
