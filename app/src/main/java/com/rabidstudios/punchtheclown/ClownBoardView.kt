package com.rabidstudios.punchtheclown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

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
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val clownResIds = intArrayOf(
        R.drawable.clown_illustrated_01,
        R.drawable.clown_illustrated_02,
        R.drawable.clown_illustrated_03,
        R.drawable.clown_illustrated_04,
        R.drawable.clown_illustrated_05,
        R.drawable.clown_illustrated_06,
        R.drawable.clown_illustrated_07,
        R.drawable.clown_illustrated_08,
        R.drawable.clown_illustrated_09,
        R.drawable.clown_illustrated_10
    )

    private val artwork: Bitmap by lazy {
        BitmapFactory.decodeResource(
            resources,
            clownResIds[clownIndex.coerceIn(0, clownResIds.lastIndex)]
        )
    }

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

        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Color.rgb(25, 15, 12))

        drawCenterCrop(canvas, artwork, w, h)

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

                when (cell) {
                    highlightedCell -> {
                        paint.color = Color.argb(115, 255, 220, 70)
                        canvas.drawRect(left, top, right, bottom, paint)
                    }
                    wrongCell -> {
                        paint.color = Color.argb(180, 215, 35, 45)
                        canvas.drawRect(left, top, right, bottom, paint)
                    }
                }

                // The target matrix is intentionally subtle. It exists for
                // targeting but should not look like nine conventional buttons.
                stroke.color = Color.argb(18, 255, 255, 255)
                stroke.strokeWidth = 1f
                canvas.drawRect(left, top, right, bottom, stroke)
            }
        }

        stroke.color = Color.rgb(232, 182, 75)
        stroke.strokeWidth = 7f
        canvas.drawRect(4f, 4f, w - 4f, h - 4f, stroke)
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, viewW: Float, viewH: Float) {
        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val viewRatio = viewW / viewH

        val src = if (bitmapRatio > viewRatio) {
            val srcWidth = (bitmap.height * viewRatio).toInt()
            val left = (bitmap.width - srcWidth) / 2
            android.graphics.Rect(left, 0, left + srcWidth, bitmap.height)
        } else {
            val srcHeight = (bitmap.width / viewRatio).toInt()
            val top = (bitmap.height - srcHeight) / 2
            android.graphics.Rect(0, top, bitmap.width, top + srcHeight)
        }

        canvas.drawBitmap(bitmap, src, RectF(0f, 0f, viewW, viewH), paint)
    }
}
