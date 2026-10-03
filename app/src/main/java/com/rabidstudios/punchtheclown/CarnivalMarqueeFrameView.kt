package com.rabidstudios.punchtheclown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
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
 * Thick, ornate midway frame based on the approved Punch board mockup.
 *
 * It is a pure overlay: it owns no gameplay state, never consumes touch
 * input, and leaves ClownBoardView's 3x3 hit geometry unchanged.
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

    private val darkWood = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val redWood = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val amberEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(234, 116, 30)
    }
    private val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(221, 159, 56)
    }
    private val antiqueGold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(170, 103, 29)
    }
    private val scratch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val socketOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(74, 37, 20)
    }
    private val socketInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(31, 18, 14)
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
        val inset = s * 0.032f
        val rect = RectF(inset, inset, width - inset, height - inset)
        val corner = s * 0.055f

        // Heavy distressed red carnival wood like the approved mockup.
        darkWood.strokeWidth = s * 0.112f
        darkWood.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                Color.rgb(73, 29, 18),
                Color.rgb(135, 48, 24),
                Color.rgb(83, 30, 19)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, corner, corner, darkWood)

        redWood.strokeWidth = s * 0.087f
        redWood.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                Color.rgb(124, 43, 25),
                Color.rgb(189, 69, 27),
                Color.rgb(104, 35, 24)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, corner, corner, redWood)

        // Orange inner bevel and antique-gold trim.
        amberEdge.strokeWidth = s * 0.012f
        val innerInset = s * 0.073f
        canvas.drawRoundRect(
            RectF(innerInset, innerInset, width - innerInset, height - innerInset),
            s * 0.024f,
            s * 0.024f,
            amberEdge
        )

        gold.strokeWidth = s * 0.0055f
        canvas.drawRoundRect(rect, corner, corner, gold)
        canvas.drawRoundRect(
            RectF(s * 0.082f, s * 0.082f, width - s * 0.082f, height - s * 0.082f),
            s * 0.020f,
            s * 0.020f,
            gold
        )

        drawWeathering(canvas, s)
        drawOrnaments(canvas, s)
        drawBulbs(canvas, s)
    }

    private fun drawWeathering(canvas: Canvas, s: Float) {
        // Deterministic short scratches keep the frame visually distressed
        // without random flicker between animation frames.
        val rail = s * 0.105f
        for (i in 0 until 34) {
            val u = ((i * 37) % 97) / 97f
            val v = ((i * 61 + 13) % 89) / 89f
            val horizontal = i % 2 == 0

            scratch.strokeWidth = if (i % 3 == 0) s * 0.0024f else s * 0.0015f
            scratch.color = if (i % 4 == 0) {
                Color.argb(120, 244, 170, 76)
            } else {
                Color.argb(105, 61, 26, 20)
            }

            if (horizontal) {
                val y = if (i % 4 < 2) s * (0.035f + v * 0.060f) else height - s * (0.035f + v * 0.060f)
                val x = s * (0.11f + u * 0.72f)
                canvas.drawLine(x, y, x + s * (0.018f + (i % 5) * 0.006f), y + s * 0.002f, scratch)
            } else {
                val x = if (i % 4 < 2) s * (0.035f + u * 0.060f) else width - s * (0.035f + u * 0.060f)
                val y = s * (0.12f + v * 0.70f)
                canvas.drawLine(x, y, x + s * 0.002f, y + s * (0.020f + (i % 4) * 0.006f), scratch)
            }
        }
    }

    private fun drawOrnaments(canvas: Canvas, s: Float) {
        gold.strokeWidth = s * 0.007f

        // Top and bottom center carved flourishes.
        drawCenterCrest(canvas, width / 2f, s * 0.050f, s, top = true)
        drawCenterCrest(canvas, width / 2f, height - s * 0.050f, s, top = false)

        // Curled corner scrollwork.
        val r = s * 0.058f
        val centers = arrayOf(
            Triple(s * 0.067f, s * 0.067f, 180f),
            Triple(width - s * 0.067f, s * 0.067f, 270f),
            Triple(s * 0.067f, height - s * 0.067f, 90f),
            Triple(width - s * 0.067f, height - s * 0.067f, 0f)
        )
        centers.forEach { (cx, cy, start) ->
            val arc = RectF(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(arc, start, 205f, false, gold)
            canvas.drawCircle(cx, cy, s * 0.012f, antiqueGold)
            canvas.drawCircle(cx, cy, s * 0.006f, gold)
        }
    }

    private fun drawCenterCrest(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        s: Float,
        top: Boolean
    ) {
        val sign = if (top) 1f else -1f
        val p = Path().apply {
            moveTo(cx, cy - sign * s * 0.010f)
            cubicTo(
                cx - s * 0.020f, cy + sign * s * 0.010f,
                cx - s * 0.028f, cy + sign * s * 0.035f,
                cx - s * 0.046f, cy + sign * s * 0.045f
            )
            moveTo(cx, cy - sign * s * 0.010f)
            cubicTo(
                cx + s * 0.020f, cy + sign * s * 0.010f,
                cx + s * 0.028f, cy + sign * s * 0.035f,
                cx + s * 0.046f, cy + sign * s * 0.045f
            )
        }
        canvas.drawPath(p, gold)
        canvas.drawCircle(cx, cy, s * 0.022f, antiqueGold)
        canvas.drawCircle(cx, cy, s * 0.011f, gold)
    }

    private fun drawBulbs(canvas: Canvas, s: Float) {
        val positions = bulbPositions()
        val radius = s * 0.024f

        positions.forEachIndexed { index, p ->
            val color = colorForBulb(index)
            val alphaScale = when {
                !animationsEnabled -> 0.52f
                mode == CarnivalLightMode.PAUSED -> 0.42f
                else -> 1.0f
            }
            val cx = p.first * width
            val cy = p.second * height

            glowPaint.color = withAlpha(color, (78 * alphaScale).toInt())
            canvas.drawCircle(cx, cy, radius * 2.05f, glowPaint)

            canvas.drawCircle(cx, cy, radius * 1.24f, socketOuter)
            canvas.drawCircle(cx, cy, radius * 1.08f, socketInner)

            bulbPaint.color = withAlpha(color, (255 * alphaScale).toInt())
            canvas.drawCircle(cx, cy, radius * 0.88f, bulbPaint)

            // Warm luminous center and glass highlight.
            glowPaint.color = withAlpha(Color.WHITE, (85 * alphaScale).toInt())
            canvas.drawCircle(cx, cy, radius * 0.55f, glowPaint)

            highlightPaint.alpha = (230 * alphaScale).toInt().coerceIn(0, 255)
            canvas.drawCircle(
                cx - radius * 0.28f,
                cy - radius * 0.31f,
                radius * 0.18f,
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
        // Matches the approved mockup: six large bulbs across the top and
        // bottom, five down each side.
        val topXs = floatArrayOf(0.060f, 0.250f, 0.415f, 0.570f, 0.745f, 0.940f)
        val sideYs = floatArrayOf(0.205f, 0.360f, 0.515f, 0.665f, 0.820f)

        val out = ArrayList<Pair<Float, Float>>(22)
        topXs.forEach { x -> out += x to 0.060f }
        sideYs.forEach { y -> out += 0.945f to y }
        topXs.reversedArray().forEach { x -> out += x to 0.940f }
        sideYs.reversedArray().forEach { y -> out += 0.055f to y }
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
