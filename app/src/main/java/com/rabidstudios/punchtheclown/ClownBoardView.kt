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
import kotlin.math.min

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

    private val clownResources = intArrayOf(
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

    private val clownBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(
            resources,
            clownResources[clownIndex.coerceIn(0, clownResources.lastIndex)]
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

        paint.color = Color.rgb(31, 18, 15)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val dst = fitCenterDestination(clownBitmap.width, clownBitmap.height)
        canvas.drawBitmap(clownBitmap, null, dst, paint)

        if (showGrid) {
            val gridW = width / 3f
            val gridH = height / 3f

            for (cell in 0..8) {
                val row = cell / 3
                val col = cell % 3
                val left = col * gridW
                val top = row * gridH
                val right = left + gridW
                val bottom = top + gridH

                when (cell) {
                    highlightedCell -> {
                        paint.color = Color.argb(118, 255, 215, 64)
                        canvas.drawRect(left + 2f, top + 2f, right - 2f, bottom - 2f, paint)
                    }
                    wrongCell -> {
                        paint.color = Color.argb(185, 220, 35, 45)
                        canvas.drawRect(left + 2f, top + 2f, right - 2f, bottom - 2f, paint)
                    }
                }

                stroke.color = Color.argb(18, 255, 255, 255)
                stroke.strokeWidth = 1f
                canvas.drawRect(left, top, right, bottom, stroke)
            }
        }

        stroke.color = Color.rgb(232, 182, 75)
        stroke.strokeWidth = 6f
        canvas.drawRect(3f, 3f, width - 3f, height - 3f, stroke)
    }

    private fun fitCenterDestination(sourceWidth: Int, sourceHeight: Int): RectF {
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val scale = min(
            viewW / sourceWidth.toFloat(),
            viewH / sourceHeight.toFloat()
        )
        val drawW = sourceWidth * scale
        val drawH = sourceHeight * scale
        val left = (viewW - drawW) / 2f
        val top = (viewH - drawH) / 2f

        return RectF(
            left,
            top,
            left + drawW,
            top + drawH
        )
    }
}
