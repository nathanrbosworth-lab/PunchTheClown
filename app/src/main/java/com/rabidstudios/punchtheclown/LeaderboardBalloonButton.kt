package com.rabidstudios.punchtheclown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * Carnival balloon control used to open the online leaderboard from Game Select.
 * It is intentionally self-contained so the approved Game Select artwork does
 * not need to be edited.
 */
class LeaderboardBalloonButton(context: Context) : View(context) {

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(184, 38, 46)
        style = Paint.Style.FILL
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(92, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(232, 182, 75)
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
    }

    private val stringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(232, 182, 75)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(247, 231, 198)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.SERIF,
            android.graphics.Typeface.BOLD
        )
    }

    private var pressedScale = 1f

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Online Leaderboards"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height * 0.39f

        canvas.save()
        canvas.scale(pressedScale, pressedScale, cx, cy)

        val body = RectF(
            width * 0.08f,
            height * 0.04f,
            width * 0.92f,
            height * 0.70f
        )
        canvas.drawOval(body, bodyPaint)
        canvas.drawOval(body, outlinePaint)

        // Small painted highlight gives the balloon a glossy carnival look.
        val highlight = RectF(
            body.left + body.width() * 0.17f,
            body.top + body.height() * 0.13f,
            body.left + body.width() * 0.34f,
            body.top + body.height() * 0.34f
        )
        canvas.drawOval(highlight, highlightPaint)

        val knotTop = body.bottom - dp(2f)
        val knotBottom = height * 0.77f
        val knot = Path().apply {
            moveTo(cx, knotTop)
            lineTo(cx - width * 0.075f, knotBottom)
            lineTo(cx + width * 0.075f, knotBottom)
            close()
        }
        canvas.drawPath(knot, bodyPaint)
        canvas.drawPath(knot, outlinePaint)

        val string = Path().apply {
            moveTo(cx, knotBottom)
            cubicTo(
                cx - width * 0.10f,
                height * 0.84f,
                cx + width * 0.12f,
                height * 0.91f,
                cx - width * 0.02f,
                height * 0.99f
            )
        }
        canvas.drawPath(string, stringPaint)

        textPaint.textSize = width * 0.165f
        val lineGap = textPaint.textSize * 0.90f
        val centerY = body.centerY()
        canvas.drawText("LEADER", cx, centerY - lineGap * 0.18f, textPaint)
        canvas.drawText("BOARD", cx, centerY + lineGap * 0.82f, textPaint)

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedScale = 0.95f
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                pressedScale = 1f
                invalidate()
                if (isEnabled) performClick()
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedScale = 1f
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
