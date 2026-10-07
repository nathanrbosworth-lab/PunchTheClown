package com.rabidstudios.punchtheclown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.min

/**
 * Compact wooden carnival marquee used for mode selection on secondary screens.
 *
 * The visual language deliberately matches the game's carved wooden signs first,
 * then adds restrained marquee lighting: warm cream lettering, brass/gold trim,
 * visible wood grain, and six recessed warm bulbs. Selected signs glow softly;
 * unselected signs keep the same carved wood but leave the bulbs dark.
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
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var glowPhase = 0

    private val ticker = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || !selected || !animationsEnabled) return
            glowPhase = (glowPhase + 1) % 8
            invalidate()
            postDelayed(this, 280L)
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

        val outer = RectF(w * 0.018f, h * 0.055f, w * 0.982f, h * 0.945f)
        val trim = RectF(w * 0.038f, h * 0.095f, w * 0.962f, h * 0.905f)
        val face = RectF(w * 0.058f, h * 0.135f, w * 0.942f, h * 0.865f)

        val outerPath = signPath(outer)
        val trimPath = signPath(trim)
        val facePath = signPath(face)

        // Deep carved outer edge.
        fill.shader = LinearGradient(
            0f,
            outer.top,
            0f,
            outer.bottom,
            intArrayOf(
                Color.rgb(72, 38, 23),
                Color.rgb(42, 22, 15),
                Color.rgb(28, 15, 11)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(outerPath, fill)
        fill.shader = null

        // Brass/gold inlay. Brighter on the active mode, aged on the inactive mode.
        stroke.strokeWidth = maxOf(2f, h * 0.038f)
        stroke.color = if (selected) Color.rgb(226, 164, 61) else Color.rgb(126, 91, 47)
        canvas.drawPath(trimPath, stroke)

        stroke.strokeWidth = maxOf(1f, h * 0.013f)
        stroke.color = if (selected) Color.rgb(255, 218, 127) else Color.rgb(91, 62, 34)
        canvas.drawPath(signPath(RectF(w * 0.050f, h * 0.118f, w * 0.950f, h * 0.882f)), stroke)

        // Warm walnut face: noticeably brighter when selected, but never painted red.
        val topWood = if (selected) Color.rgb(142, 79, 39) else Color.rgb(104, 61, 37)
        val midWood = if (selected) Color.rgb(112, 57, 31) else Color.rgb(78, 43, 29)
        val bottomWood = if (selected) Color.rgb(76, 38, 24) else Color.rgb(55, 31, 23)
        fill.shader = LinearGradient(
            0f,
            face.top,
            0f,
            face.bottom,
            intArrayOf(topWood, midWood, bottomWood),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(facePath, fill)
        fill.shader = null

        drawWoodGrain(canvas, facePath, face, h)

        // Inner carved lip.
        stroke.strokeWidth = maxOf(1f, h * 0.012f)
        stroke.color = if (selected) Color.argb(180, 247, 190, 99) else Color.argb(135, 53, 27, 18)
        canvas.drawPath(signPath(RectF(w * 0.073f, h * 0.165f, w * 0.927f, h * 0.835f)), stroke)

        drawBulbs(canvas, face, h)

        // Cream hand-painted carnival lettering with a carved dark drop shadow.
        textPaint.textSize = h * 0.255f
        val maxWidth = w * 0.69f
        while (textPaint.textSize > h * 0.135f && textPaint.measureText(label) > maxWidth) {
            textPaint.textSize *= 0.94f
        }
        textPaint.color = if (selected) Color.rgb(255, 233, 180) else Color.rgb(221, 202, 165)
        textPaint.setShadowLayer(h * 0.026f, 0f, h * 0.022f, Color.rgb(34, 18, 12))

        val fm = textPaint.fontMetrics
        val baseline = h / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, w / 2f, baseline, textPaint)

        // Selected mode gets only a restrained amber edge glow.
        if (selected) {
            stroke.strokeWidth = maxOf(1f, h * 0.010f)
            stroke.color = Color.argb(78, 255, 198, 84)
            canvas.drawPath(signPath(RectF(w * 0.082f, h * 0.185f, w * 0.918f, h * 0.815f)), stroke)
        }
    }

    private fun signPath(rect: RectF): Path {
        val x = rect.width()
        val y = rect.height()
        val notchX = x * 0.055f
        val shoulderX = x * 0.13f
        val crown = y * 0.075f
        val corner = y * 0.20f

        return Path().apply {
            moveTo(rect.left + shoulderX, rect.top + crown)
            cubicTo(
                rect.left + x * 0.28f, rect.top - crown * 0.25f,
                rect.right - x * 0.28f, rect.top - crown * 0.25f,
                rect.right - shoulderX, rect.top + crown
            )
            quadTo(rect.right - notchX, rect.top + crown, rect.right - notchX * 0.55f, rect.top + corner)
            quadTo(rect.right, rect.centerY(), rect.right - notchX * 0.55f, rect.bottom - corner)
            quadTo(rect.right - notchX, rect.bottom - crown, rect.right - shoulderX, rect.bottom - crown)
            cubicTo(
                rect.right - x * 0.28f, rect.bottom + crown * 0.25f,
                rect.left + x * 0.28f, rect.bottom + crown * 0.25f,
                rect.left + shoulderX, rect.bottom - crown
            )
            quadTo(rect.left + notchX, rect.bottom - crown, rect.left + notchX * 0.55f, rect.bottom - corner)
            quadTo(rect.left, rect.centerY(), rect.left + notchX * 0.55f, rect.top + corner)
            quadTo(rect.left + notchX, rect.top + crown, rect.left + shoulderX, rect.top + crown)
            close()
        }
    }

    private fun drawWoodGrain(canvas: Canvas, facePath: Path, face: RectF, h: Float) {
        canvas.save()
        canvas.clipPath(facePath)

        grainPaint.strokeWidth = maxOf(1f, h * 0.008f)
        grainPaint.color = if (selected) Color.argb(62, 49, 24, 14) else Color.argb(52, 31, 17, 12)

        val rows = floatArrayOf(0.24f, 0.38f, 0.54f, 0.69f, 0.79f)
        rows.forEachIndexed { index, factor ->
            val y = face.top + face.height() * factor
            val wave = h * (if (index % 2 == 0) 0.028f else 0.020f)
            val p = Path().apply {
                moveTo(face.left - face.width() * 0.04f, y)
                cubicTo(
                    face.left + face.width() * 0.22f, y - wave,
                    face.left + face.width() * 0.38f, y + wave,
                    face.left + face.width() * 0.53f, y
                )
                cubicTo(
                    face.left + face.width() * 0.69f, y - wave,
                    face.left + face.width() * 0.82f, y + wave,
                    face.right + face.width() * 0.04f, y
                )
            }
            canvas.drawPath(p, grainPaint)
        }

        // A few short darker knots keep the plaque hand-crafted rather than flat.
        grainPaint.strokeWidth = maxOf(1f, h * 0.010f)
        grainPaint.color = Color.argb(if (selected) 50 else 40, 38, 19, 12)
        canvas.drawArc(
            RectF(
                face.left + face.width() * 0.18f,
                face.top + face.height() * 0.29f,
                face.left + face.width() * 0.27f,
                face.top + face.height() * 0.47f
            ),
            190f,
            135f,
            false,
            grainPaint
        )
        canvas.drawArc(
            RectF(
                face.left + face.width() * 0.72f,
                face.top + face.height() * 0.58f,
                face.left + face.width() * 0.80f,
                face.top + face.height() * 0.76f
            ),
            12f,
            135f,
            false,
            grainPaint
        )

        canvas.restore()
    }

    private fun drawBulbs(canvas: Canvas, face: RectF, h: Float) {
        val leftX = face.left + face.width() * 0.035f
        val rightX = face.right - face.width() * 0.035f
        val ys = floatArrayOf(
            face.top + face.height() * 0.22f,
            face.centerY(),
            face.bottom - face.height() * 0.22f
        )

        val r = min(width, height) * 0.041f
        val pulse = if (!animationsEnabled) {
            1.0f
        } else {
            floatArrayOf(0.82f, 0.88f, 0.96f, 1.0f, 0.96f, 0.88f, 0.82f, 0.88f)[glowPhase]
        }

        fun bulb(x: Float, y: Float) {
            // Brass recessed socket.
            fill.color = Color.rgb(48, 29, 19)
            canvas.drawCircle(x, y, r * 1.22f, fill)

            stroke.strokeWidth = maxOf(1f, h * 0.011f)
            stroke.color = if (selected) Color.rgb(188, 128, 48) else Color.rgb(92, 65, 42)
            canvas.drawCircle(x, y, r * 1.05f, stroke)

            if (selected) {
                val glowAlpha = (105 * pulse).toInt().coerceIn(0, 255)
                fill.color = Color.argb(glowAlpha, 255, 173, 40)
                canvas.drawCircle(x, y, r * 1.95f, fill)

                fill.color = Color.rgb(255, 191, 57)
                canvas.drawCircle(x, y, r * 0.76f, fill)

                fill.color = Color.rgb(255, 247, 205)
                canvas.drawCircle(x - r * 0.18f, y - r * 0.20f, r * 0.23f, fill)
            } else {
                // Visible but unlit bulb/socket.
                fill.color = Color.rgb(95, 71, 50)
                canvas.drawCircle(x, y, r * 0.72f, fill)

                fill.color = Color.argb(95, 213, 185, 137)
                canvas.drawCircle(x - r * 0.16f, y - r * 0.17f, r * 0.18f, fill)
            }
        }

        ys.forEach { y ->
            bulb(leftX, y)
            bulb(rightX, y)
        }
    }
}
