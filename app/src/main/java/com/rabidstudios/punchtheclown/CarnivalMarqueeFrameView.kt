package com.rabidstudios.punchtheclown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

enum class CarnivalLightMode {
    READY,
    WATCH,
    PLAYER_TURN,
    PAUSED,
    GAME_OVER
}

/**
 * Decorative midway frame for Punch the Clown.
 *
 * This view deliberately owns no gameplay state and never handles touch input.
 * It can be layered directly over ClownBoardView without changing the board's
 * 3x3 hit geometry.
 */
class CarnivalMarqueeFrameView(context: Context) : View(context) {

    var animationsEnabled: Boolean = true
        set(value) {
            field = value
            if (isAttachedToWindow) restartTicker()
            invalidate()
        }

    private var mode: CarnivalLightMode = CarnivalLightMode.READY
    private var chaseOffset = 0
    private var event: Event? = null
    private var eventStep = 0

    private enum class Event {
        CORRECT,
        LEVEL,
        WRONG
    }

    private val woodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(111, 47, 28)
    }
    private val darkWoodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(61, 28, 20)
    }
    private val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(214, 154, 48)
    }
    private val socketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(50, 24, 16)
    }
    private val bulbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val palette = intArrayOf(
        Color.rgb(244, 67, 54),
        Color.rgb(255, 179, 0),
        Color.rgb(41, 182, 246),
        Color.rgb(102, 187, 106),
        Color.rgb(255, 244, 199)
    )

    private val ticker = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            if (animationsEnabled && mode != CarnivalLightMode.PAUSED) {
                chaseOffset = (chaseOffset + 1) % palette.size
                invalidate()
                postDelayed(this, normalIntervalMs())
            }
        }
    }

    fun setMode(newMode: CarnivalLightMode) {
        event = null
        eventStep = 0
        mode = newMode
        restartTicker()
        invalidate()
    }

    fun flashCorrect() {
        if (!animationsEnabled) return
        event = Event.CORRECT
        eventStep = 0
        invalidate()
        removeCallbacks(clearEvent)
        postDelayed(clearEvent, 130L)
    }

    fun celebrateSequence() {
        if (!animationsEnabled) return
        event = Event.LEVEL
        eventStep = 0
        removeCallbacks(eventTicker)
        post(eventTicker)
    }

    fun flashWrong() {
        if (!animationsEnabled) {
            setMode(CarnivalLightMode.GAME_OVER)
            return
        }
        event = Event.WRONG
        eventStep = 0
        removeCallbacks(eventTicker)
        post(eventTicker)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartTicker()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        removeCallbacks(clearEvent)
        removeCallbacks(eventTicker)
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val s = min(width, height).toFloat()
        val outerInset = s * 0.030f
        val railWidth = s * 0.052f
        val frameRect = RectF(
            outerInset,
            outerInset,
            width - outerInset,
            height - outerInset
        )

        // Layered, weathered-looking carnival trim. This stays intentionally
        // narrow so the existing clown artwork remains the dominant image.
        darkWoodPaint.strokeWidth = railWidth
        canvas.drawRoundRect(frameRect, s * 0.040f, s * 0.040f, darkWoodPaint)

        woodPaint.strokeWidth = railWidth * 0.68f
        canvas.drawRoundRect(frameRect, s * 0.040f, s * 0.040f, woodPaint)

        goldPaint.strokeWidth = s * 0.006f
        canvas.drawRoundRect(frameRect, s * 0.038f, s * 0.038f, goldPaint)

        drawBulbs(canvas, s)
    }

    private fun drawBulbs(canvas: Canvas, s: Float) {
        val positions = bulbPositions()
        val radius = s * 0.0145f

        positions.forEachIndexed { index, p ->
            val color = colorForBulb(index)
            val enabled = animationsEnabled

            val alphaScale = when {
                !enabled -> 0.50f
                mode == CarnivalLightMode.PAUSED -> 0.42f
                else -> 1.0f
            }

            glowPaint.color = withAlpha(color, (58 * alphaScale).toInt())
            canvas.drawCircle(p.first * width, p.second * height, radius * 2.25f, glowPaint)

            canvas.drawCircle(p.first * width, p.second * height, radius * 1.24f, socketPaint)

            bulbPaint.color = withAlpha(color, (255 * alphaScale).toInt())
            canvas.drawCircle(p.first * width, p.second * height, radius, bulbPaint)

            highlightPaint.alpha = (210 * alphaScale).toInt().coerceIn(0, 255)
            canvas.drawCircle(
                p.first * width - radius * 0.28f,
                p.second * height - radius * 0.30f,
                radius * 0.22f,
                highlightPaint
            )
        }
        highlightPaint.alpha = 255
    }

    private fun colorForBulb(index: Int): Int {
        if (!animationsEnabled) {
            return if (index % 2 == 0) palette[1] else palette[4]
        }

        return when (event) {
            Event.CORRECT -> if (index % 3 == 0) Color.WHITE else palette[1]
            Event.LEVEL -> palette[(index + eventStep) % palette.size]
            Event.WRONG -> {
                if (eventStep % 2 == 0) {
                    if (index % 2 == 0) Color.rgb(255, 35, 22) else Color.rgb(255, 120, 20)
                } else {
                    Color.rgb(105, 24, 18)
                }
            }
            null -> when (mode) {
                CarnivalLightMode.PAUSED ->
                    if (index % 2 == 0) palette[1] else palette[4]
                CarnivalLightMode.GAME_OVER ->
                    if ((index + chaseOffset) % 2 == 0) Color.rgb(255, 40, 22) else palette[1]
                else -> palette[(index + chaseOffset) % palette.size]
            }
        }
    }

    private val clearEvent = Runnable {
        event = null
        eventStep = 0
        invalidate()
    }

    private val eventTicker = object : Runnable {
        override fun run() {
            val current = event ?: return
            eventStep++
            invalidate()

            when (current) {
                Event.LEVEL -> {
                    if (eventStep < 7) {
                        postDelayed(this, 85L)
                    } else {
                        event = null
                        eventStep = 0
                        invalidate()
                    }
                }
                Event.WRONG -> {
                    if (eventStep < 5) {
                        postDelayed(this, 200L)
                    } else {
                        event = null
                        eventStep = 0
                        mode = CarnivalLightMode.GAME_OVER
                        restartTicker()
                        invalidate()
                    }
                }
                Event.CORRECT -> Unit
            }
        }
    }

    private fun restartTicker() {
        removeCallbacks(ticker)
        if (animationsEnabled && mode != CarnivalLightMode.PAUSED && isAttachedToWindow) {
            postDelayed(ticker, normalIntervalMs())
        }
    }

    private fun normalIntervalMs(): Long = when (mode) {
        CarnivalLightMode.READY -> 300L
        CarnivalLightMode.WATCH -> 190L
        CarnivalLightMode.PLAYER_TURN -> 250L
        CarnivalLightMode.GAME_OVER -> 250L
        CarnivalLightMode.PAUSED -> Long.MAX_VALUE
    }

    private fun bulbPositions(): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>(32)
        val start = 0.075f
        val end = 0.925f

        // Nine across the top and bottom.
        for (i in 0..8) {
            val t = i / 8f
            val x = start + (end - start) * t
            out += x to 0.055f
        }
        for (i in 8 downTo 0) {
            val t = i / 8f
            val x = start + (end - start) * t
            out += x to 0.945f
        }

        // Seven down each side, excluding the corner-adjacent endpoints.
        for (i in 1..7) {
            val t = i / 8f
            val y = start + (end - start) * t
            out += 0.055f to y
        }
        for (i in 7 downTo 1) {
            val t = i / 8f
            val y = start + (end - start) * t
            out += 0.945f to y
        }
        return out
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
}
