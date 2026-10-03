package com.rabidstudios.punchtheclown

import android.content.Context
import android.graphics.BitmapFactory
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
 * Punch marquee overlay using the approved ornate red carnival frame extracted
 * from the reference mockup. The artwork is static; animated bulb color and
 * gameplay reactions are drawn independently over its original bulb sockets.
 *
 * This view never consumes touch input and does not alter ClownBoardView's
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

    private val frameBitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.punch_ornate_frame)
    }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val socketOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(225, 74, 37, 20)
    }
    private val socketInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(235, 31, 18, 14)
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

        // The reference frame needs to be slightly taller than the square
        // gameplay board so its top/bottom rails cover the board's own cream
        // edge strips. The board itself remains exactly 900 x 900 and its
        // touch geometry is unchanged.
        val verticalOverhang = height * FRAME_VERTICAL_OVERHANG
        canvas.drawBitmap(
            frameBitmap,
            null,
            RectF(
                0f,
                -verticalOverhang,
                width.toFloat(),
                height + verticalOverhang
            ),
            framePaint
        )

        drawBulbs(canvas, min(width, height).toFloat())
    }

    private fun drawBulbs(canvas: Canvas, s: Float) {
        val radius = s * 0.0185f
        bulbPositions.forEachIndexed { index, p ->
            val color = colorForBulb(index)
            val alphaScale = when {
                !animationsEnabled -> 0.50f
                mode == CarnivalLightMode.PAUSED -> 0.42f
                else -> 1.0f
            }
            val cx = p.first * width
            val cy = ((p.second - 0.5f) * FRAME_VERTICAL_SCALE + 0.5f) * height

            // The extracted frame contains its original warm bulb halo.
            // These layers recolor the glass/socket while allowing that halo
            // to remain visible around the animated bulb.
            glowPaint.color = withAlpha(color, (105 * alphaScale).toInt())
            canvas.drawCircle(cx, cy, radius * 1.85f, glowPaint)

            canvas.drawCircle(cx, cy, radius * 1.12f, socketOuter)
            canvas.drawCircle(cx, cy, radius * 0.98f, socketInner)

            bulbPaint.color = withAlpha(color, (255 * alphaScale).toInt())
            canvas.drawCircle(cx, cy, radius * 0.82f, bulbPaint)

            glowPaint.color = withAlpha(Color.WHITE, (78 * alphaScale).toInt())
            canvas.drawCircle(cx, cy, radius * 0.48f, glowPaint)

            highlightPaint.alpha = (225 * alphaScale).toInt().coerceIn(0, 255)
            canvas.drawCircle(
                cx - radius * 0.23f,
                cy - radius * 0.25f,
                radius * 0.16f,
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

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )

    companion object {
        // 6% vertical stretch: approximately 3% beyond the board at both the
        // top and bottom. This covers the board's cream strips while keeping
        // width and gameplay coordinates untouched.
        private const val FRAME_VERTICAL_SCALE = 1.06f
        private const val FRAME_VERTICAL_OVERHANG = 0.03f

        // Normalized centers measured from the approved 512 x 512 frame asset.
        // Order runs clockwise so the existing chase animation travels around
        // the physical bulb sockets exactly.
        private val bulbPositions = listOf(
            33.96f / 512f to 41.92f / 512f,
            128.14f / 512f to 40.59f / 512f,
            213.46f / 512f to 36.24f / 512f,
            290.82f / 512f to 36.28f / 512f,
            379.12f / 512f to 39.77f / 512f,
            477.23f / 512f to 40.85f / 512f,

            482.49f / 512f to 109.90f / 512f,
            483.78f / 512f to 182.51f / 512f,
            483.17f / 512f to 261.58f / 512f,
            483.63f / 512f to 333.73f / 512f,
            483.22f / 512f to 414.65f / 512f,

            479.29f / 512f to 467.10f / 512f,
            378.50f / 512f to 466.11f / 512f,
            294.23f / 512f to 467.37f / 512f,
            210.48f / 512f to 466.71f / 512f,
            128.89f / 512f to 466.19f / 512f,
            30.78f / 512f to 467.17f / 512f,

            28.02f / 512f to 414.99f / 512f,
            27.25f / 512f to 332.94f / 512f,
            27.06f / 512f to 260.22f / 512f,
            26.99f / 512f to 181.90f / 512f,
            28.49f / 512f to 110.08f / 512f
        )
    }
}
