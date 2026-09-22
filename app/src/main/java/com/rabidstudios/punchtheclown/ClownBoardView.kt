package com.rabidstudios.punchtheclown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

class ClownBoardView(
    context: Context,
    private val clownIndex: Int,
    private val showGrid: Boolean
) : View(context) {

    var onCellPressed: ((Int) -> Unit)? = null
    var inputEnabled: Boolean = false

    private var highlightedCell: Int? = null
    private var wrongCell: Int? = null
    private val clearHandler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private data class Palette(
        val hair: Int,
        val accent: Int,
        val eye: Int,
        val backdrop: Int,
        val bow: Int,
        val style: Int
    )

    private val palettes = listOf(
        Palette(0xffb7262e.toInt(), 0xffe8b64b.toInt(), 0xff265d85.toInt(), 0xff7f171d.toInt(), 0xffe8b64b.toInt(), 0),
        Palette(0xff3c6fa0.toInt(), 0xffc62828.toInt(), 0xff2e7d32.toInt(), 0xff2b2035.toInt(), 0xfff3c75b.toInt(), 1),
        Palette(0xffe15b31.toInt(), 0xff7b2f9d.toInt(), 0xff22577a.toInt(), 0xff5a2b20.toInt(), 0xff5aa469.toInt(), 2),
        Palette(0xfff2a33a.toInt(), 0xffa32035.toInt(), 0xff4a6c8a.toInt(), 0xff26374a.toInt(), 0xffd7c26b.toInt(), 3),
        Palette(0xff7d3fb2.toInt(), 0xffe53935.toInt(), 0xff376f5b.toInt(), 0xff4c1f38.toInt(), 0xfff0b54a.toInt(), 4),
        Palette(0xffb91c5c.toInt(), 0xff3b82a0.toInt(), 0xff2f855a.toInt(), 0xff6c253e.toInt(), 0xffefc95c.toInt(), 5),
        Palette(0xff357a8a.toInt(), 0xffd94a38.toInt(), 0xff6a4c93.toInt(), 0xff2d4b52.toInt(), 0xfff0a83d.toInt(), 6),
        Palette(0xffd85627.toInt(), 0xff4776a8.toInt(), 0xff2f6f4e.toInt(), 0xff59301f.toInt(), 0xffd9bd5a.toInt(), 7),
        Palette(0xff5c3d91.toInt(), 0xffbe3434.toInt(), 0xff39798b.toInt(), 0xff2b2438.toInt(), 0xffdca844.toInt(), 8),
        Palette(0xffb23a48.toInt(), 0xffd7922c.toInt(), 0xff356c8a.toInt(), 0xff4a2525.toInt(), 0xff527d5b.toInt(), 9)
    )

    fun setHighlighted(cell: Int?) {
        highlightedCell = cell
        wrongCell = null
        invalidate()
    }

    fun clearMarks() {
        highlightedCell = null
        wrongCell = null
        invalidate()
    }

    fun markWrong(cell: Int) {
        wrongCell = cell
        highlightedCell = null
        invalidate()
    }

    fun flashPlayer(cell: Int) {
        highlightedCell = cell
        invalidate()
        clearHandler.removeCallbacksAndMessages(null)
        clearHandler.postDelayed({
            if (wrongCell == null) {
                highlightedCell = null
                invalidate()
            }
        }, 120L)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inputEnabled) return true
        if (event.action == MotionEvent.ACTION_UP) {
            val col = ((event.x / width) * 3f).toInt().coerceIn(0, 2)
            val row = ((event.y / height) * 3f).toInt().coerceIn(0, 2)
            onCellPressed?.invoke(row * 3 + col)
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = palettes[clownIndex.coerceIn(0, palettes.lastIndex)]
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h * 0.47f
        val faceR = minOf(w * 0.31f, h * 0.29f)

        paint.color = p.backdrop
        canvas.drawRect(0f, 0f, w, h, paint)
        val stripeW = w / 12f
        repeat(12) { i ->
            paint.color = if (i % 2 == 0) p.backdrop else Color.rgb(239, 216, 170)
            paint.alpha = if (i % 2 == 0) 255 else 185
            canvas.drawRect(i * stripeW, 0f, (i + 1) * stripeW + 1f, h, paint)
        }
        paint.alpha = 255
        paint.color = Color.argb(110, 20, 10, 8)
        canvas.drawRect(0f, 0f, w, h, paint)

        drawHair(canvas, cx, cy, faceR, p)

        paint.color = Color.rgb(255, 244, 232)
        canvas.drawCircle(cx, cy, faceR, paint)
        stroke.color = Color.rgb(62, 36, 24)
        stroke.strokeWidth = max(4f, w * .013f)
        canvas.drawCircle(cx, cy, faceR, stroke)

        if (p.style % 3 == 0) {
            paint.color = p.accent
            canvas.drawCircle(cx - faceR * .55f, cy + faceR * .02f, faceR * .13f, paint)
            canvas.drawCircle(cx + faceR * .55f, cy + faceR * .02f, faceR * .13f, paint)
        }

        paint.color = Color.WHITE
        canvas.drawOval(cx - faceR * .62f, cy - faceR * .45f, cx - faceR * .08f, cy - faceR * .04f, paint)
        canvas.drawOval(cx + faceR * .08f, cy - faceR * .45f, cx + faceR * .62f, cy - faceR * .04f, paint)
        paint.color = p.eye
        canvas.drawCircle(cx - faceR * .34f, cy - faceR * .23f, faceR * .095f, paint)
        canvas.drawCircle(cx + faceR * .34f, cy - faceR * .23f, faceR * .095f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(cx - faceR * .34f, cy - faceR * .23f, faceR * .045f, paint)
        canvas.drawCircle(cx + faceR * .34f, cy - faceR * .23f, faceR * .045f, paint)

        if (p.style % 2 == 1) {
            stroke.color = p.accent
            stroke.strokeWidth = faceR * .055f
            canvas.drawLine(cx - faceR * .58f, cy - faceR * .52f, cx - faceR * .14f, cy - faceR * .46f, stroke)
            canvas.drawLine(cx + faceR * .14f, cy - faceR * .46f, cx + faceR * .58f, cy - faceR * .52f, stroke)
        }

        paint.color = p.accent
        canvas.drawCircle(cx, cy + faceR * .05f, faceR * .22f, paint)

        stroke.color = if (p.style % 4 == 0) p.accent else Color.rgb(165, 28, 38)
        stroke.strokeWidth = max(6f, w * .020f)
        val smile = Path().apply {
            moveTo(cx - faceR * .52f, cy + faceR * .35f)
            quadTo(cx, cy + faceR * (.70f + (p.style % 3) * .04f), cx + faceR * .52f, cy + faceR * .35f)
        }
        canvas.drawPath(smile, stroke)

        drawBow(canvas, cx, cy + faceR * 1.20f, w, h, p)

        if (showGrid) {
            val cellW = w / 3f
            val cellH = h / 3f
            for (cell in 0..8) {
                val row = cell / 3
                val col = cell % 3
                val left = col * cellW
                val top = row * cellH
                val right = left + cellW
                val bottom = top + cellH
                if (cell == highlightedCell) {
                    paint.color = Color.argb(125, 255, 213, 64)
                    canvas.drawRect(left + 3f, top + 3f, right - 3f, bottom - 3f, paint)
                } else if (cell == wrongCell) {
                    paint.color = Color.argb(180, 216, 67, 67)
                    canvas.drawRect(left + 3f, top + 3f, right - 3f, bottom - 3f, paint)
                }
                stroke.color = Color.argb(25, 255, 255, 255)
                stroke.strokeWidth = 1f
                canvas.drawRect(left, top, right, bottom, stroke)
            }
        }

        stroke.color = Color.rgb(232, 182, 75)
        stroke.strokeWidth = 8f
        canvas.drawRect(4f, 4f, w - 4f, h - 4f, stroke)
    }

    private fun drawHair(canvas: Canvas, cx: Float, cy: Float, r: Float, p: Palette) {
        paint.color = p.hair
        when (p.style % 5) {
            0 -> {
                canvas.drawCircle(cx - r * .82f, cy - r * .40f, r * .58f, paint)
                canvas.drawCircle(cx + r * .82f, cy - r * .40f, r * .58f, paint)
                canvas.drawCircle(cx - r * .92f, cy + r * .12f, r * .50f, paint)
                canvas.drawCircle(cx + r * .92f, cy + r * .12f, r * .50f, paint)
            }
            1 -> {
                repeat(7) { i ->
                    val x = cx - r * .88f + i * r * .29f
                    canvas.drawCircle(x, cy - r * .78f, r * .31f, paint)
                }
            }
            2 -> {
                val hat = Path().apply {
                    moveTo(cx - r * .95f, cy - r * .65f)
                    lineTo(cx, cy - r * 1.45f)
                    lineTo(cx + r * .90f, cy - r * .60f)
                    close()
                }
                canvas.drawPath(hat, paint)
                paint.color = p.accent
                canvas.drawCircle(cx, cy - r * 1.38f, r * .14f, paint)
            }
            3 -> {
                canvas.drawCircle(cx - r * .88f, cy - r * .12f, r * .52f, paint)
                canvas.drawCircle(cx + r * .88f, cy - r * .12f, r * .52f, paint)
                paint.color = p.accent
                canvas.drawRect(cx - r * .70f, cy - r * 1.08f, cx + r * .70f, cy - r * .78f, paint)
                canvas.drawRect(cx - r * .43f, cy - r * 1.72f, cx + r * .43f, cy - r * 1.02f, paint)
            }
            else -> {
                repeat(9) { i ->
                    val angle = Math.toRadians((i * 40.0) - 160.0)
                    val x = cx + kotlin.math.cos(angle).toFloat() * r * .95f
                    val y = cy + kotlin.math.sin(angle).toFloat() * r * .95f
                    canvas.drawCircle(x, y, r * .28f, paint)
                }
            }
        }
    }

    private fun drawBow(canvas: Canvas, cx: Float, bowY: Float, w: Float, h: Float, p: Palette) {
        paint.color = p.bow
        val left = Path().apply {
            moveTo(cx, bowY)
            lineTo(cx - w * .22f, bowY - h * .052f)
            lineTo(cx - w * .22f, bowY + h * .052f)
            close()
        }
        val right = Path().apply {
            moveTo(cx, bowY)
            lineTo(cx + w * .22f, bowY - h * .052f)
            lineTo(cx + w * .22f, bowY + h * .052f)
            close()
        }
        canvas.drawPath(left, paint)
        canvas.drawPath(right, paint)
        paint.color = p.accent
        canvas.drawCircle(cx, bowY, w * .045f, paint)
    }
}
