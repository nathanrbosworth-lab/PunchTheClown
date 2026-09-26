package com.rabidstudios.punchtheclown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * All Beaning the Clowns gameplay tuning lives here so the first M3 test build
 * can be balanced without hunting through rendering or Activity code.
 */
object BeaningTuning {
    const val HITS_PER_LEVEL = 10
    const val SCORE_PER_HIT = 100L
    const val MISS_LIMIT = 3

    const val START_REACTION_MS = 1500L
    const val REACTION_DECREMENT_PER_LEVEL_MS = 50L
    const val MIN_REACTION_MS = 600L

    const val SPAWN_DELAY_MIN_MS = 250L
    const val SPAWN_DELAY_MAX_MS = 600L
    const val POPUP_MS = 200L
    const val FALL_MS = 250L
    const val READY_MS = 500L
    const val GO_MS = 500L

    const val RECENT_CLOWN_HISTORY = 3
    const val TEST_CLOWN_COUNT = 5
    const val TEST_MAX_SIMULTANEOUS_TARGETS = 5

    fun reactionWindowForLevel(level: Int): Long = max(
        MIN_REACTION_MS,
        START_REACTION_MS - (level - 1).coerceAtLeast(0) * REACTION_DECREMENT_PER_LEVEL_MS
    )

    fun simultaneousTargetsForLevel(level: Int): Int = min(
        TEST_MAX_SIMULTANEOUS_TARGETS,
        1 + level.coerceAtLeast(1) / 5
    )
}

class BeaningBoardView(context: Context) : View(context) {

    companion object {
        // Layout measurements were authored against the approved 941 x 1672 booth.
        // Keep them independent from the packaged bitmap resolution so artwork can
        // be compressed without moving targets or HUD elements.
        private const val LOGICAL_BOARD_WIDTH = 941f
        private const val LOGICAL_BOARD_HEIGHT = 1672f
    }

    enum class Phase { POPPING, ACTIVE, HIT_FALLING, MISS_FALLING }

    private data class Target(
        val slot: Int,
        val clown: Int,
        var phase: Phase,
        var phaseStartedAt: Long,
        val reactionWindowMs: Long
    )

    var onHit: (() -> Unit)? = null
    var onMiss: ((Int) -> Unit)? = null
    var onGameOverStarted: (() -> Unit)? = null
    var onGameOverFinished: (() -> Unit)? = null

    var previewMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var previewClown: Int = 0
        set(value) {
            field = value.coerceIn(0, BeaningTuning.TEST_CLOWN_COUNT - 1)
            invalidate()
        }

    var previewSlot: Int = 4
        set(value) {
            field = value.coerceIn(0, 8)
            invalidate()
        }

    private val handler = Handler(Looper.getMainLooper())
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(58, 0, 0, 0) }
    private val hudFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(247, 231, 198)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
    }
    private val hudStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(70, 37, 20)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
    }
    private val bulbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val boardBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.beaning_board)
    }

    private val targetBitmaps: Array<Bitmap> by lazy {
        arrayOf(
            BitmapFactory.decodeResource(resources, R.drawable.beaning_target_00_bubbles),
            BitmapFactory.decodeResource(resources, R.drawable.beaning_target_01_noodles),
            BitmapFactory.decodeResource(resources, R.drawable.beaning_target_02_sparky),
            BitmapFactory.decodeResource(resources, R.drawable.beaning_target_03_patches),
            BitmapFactory.decodeResource(resources, R.drawable.beaning_target_04_giggles)
        )
    }

    private val boardRect = RectF()
    private val targets = linkedMapOf<Int, Target>()
    private val recentClowns = ArrayDeque<Int>()

    private var generation = 0
    private var pendingSpawns = 0
    private var running = false
    private var paused = false
    private var gameOver = false
    private var frozenAtMs = 0L
    private var finalAnimatedSlot: Int? = null

    var score: Long = 0L
        private set
    var level: Int = 1
        private set
    var clownsHit: Int = 0
        private set
    var misses: Int = 0
        private set
    var currentRun: Int = 0
        private set
    var longestRun: Int = 0
        private set

    private val slotXs = floatArrayOf(260f, 469f, 678f)
    private val slotBaselines = floatArrayOf(800f, 1030f, 1258f)

    fun resetForNewGame() {
        generation++
        handler.removeCallbacksAndMessages(null)
        targets.clear()
        recentClowns.clear()
        pendingSpawns = 0
        score = 0L
        level = 1
        clownsHit = 0
        misses = 0
        currentRun = 0
        longestRun = 0
        running = false
        paused = false
        gameOver = false
        frozenAtMs = 0L
        finalAnimatedSlot = null
        previewMode = false
        invalidate()
    }

    fun startGame() {
        if (gameOver) return
        generation++
        running = true
        paused = false
        ensureDesiredTargets()
        invalidate()
    }

    fun pauseForInterruption() {
        if (!running || gameOver || paused) return
        generation++
        paused = true
        running = false
        pendingSpawns = 0
        handler.removeCallbacksAndMessages(null)
        invalidate()
    }

    /** Resume only after the Activity has finished the visual READY… GO! cue. */
    fun resumeAfterInterruption() {
        if (gameOver) return
        generation++
        paused = false
        running = true
        pendingSpawns = 0
        val now = SystemClock.uptimeMillis()
        val removeSlots = mutableListOf<Int>()

        targets.values.forEach { target ->
            when (target.phase) {
                Phase.POPPING, Phase.ACTIVE -> {
                    target.phase = Phase.ACTIVE
                    target.phaseStartedAt = now
                    scheduleTimeout(target)
                }
                Phase.HIT_FALLING, Phase.MISS_FALLING -> removeSlots += target.slot
            }
        }
        removeSlots.forEach { targets.remove(it) }
        ensureDesiredTargets()
        invalidate()
    }

    fun stopGame() {
        generation++
        running = false
        paused = false
        pendingSpawns = 0
        handler.removeCallbacksAndMessages(null)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!running || paused || gameOver || previewMode) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                handleTap(event.getX(index), event.getY(index))
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        val point = viewToBoard(x, y) ?: return
        val slot = slotAt(point.first, point.second) ?: return
        val target = targets[slot]

        if (target != null && target.phase == Phase.ACTIVE) {
            registerHit(target)
        } else {
            registerMiss(finalSlot = if (target != null) slot else null)
        }
    }

    private fun registerHit(target: Target) {
        if (!running || gameOver || target.phase != Phase.ACTIVE) return

        val oldLevel = level
        target.phase = Phase.HIT_FALLING
        target.phaseStartedAt = SystemClock.uptimeMillis()

        clownsHit++
        score += BeaningTuning.SCORE_PER_HIT
        currentRun++
        longestRun = max(longestRun, currentRun)
        level = clownsHit / BeaningTuning.HITS_PER_LEVEL + 1
        onHit?.invoke()

        val token = generation
        handler.postDelayed({
            if (token != generation || gameOver) return@postDelayed
            targets.remove(target.slot)
            invalidate()
        }, BeaningTuning.FALL_MS)

        scheduleSpawn(target.slot)

        if (level > oldLevel && BeaningTuning.simultaneousTargetsForLevel(level) > BeaningTuning.simultaneousTargetsForLevel(oldLevel)) {
            ensureDesiredTargets()
        }

        invalidate()
        postInvalidateOnAnimation()
    }

    private fun registerMiss(finalSlot: Int?) {
        if (!running || gameOver) return
        misses++
        currentRun = 0
        onMiss?.invoke(misses)

        if (misses >= BeaningTuning.MISS_LIMIT) {
            beginGameOver(finalSlot)
        }
        invalidate()
    }

    private fun handleTimeout(slot: Int, clown: Int, token: Int) {
        if (token != generation || !running || paused || gameOver) return
        val target = targets[slot] ?: return
        if (target.clown != clown || target.phase != Phase.ACTIVE) return

        target.phase = Phase.MISS_FALLING
        target.phaseStartedAt = SystemClock.uptimeMillis()
        registerMiss(finalSlot = slot)

        if (!gameOver) {
            handler.postDelayed({
                if (token != generation || gameOver) return@postDelayed
                targets.remove(slot)
                invalidate()
            }, BeaningTuning.FALL_MS)
            scheduleSpawn(slot)
        }
        invalidate()
        postInvalidateOnAnimation()
    }

    private fun beginGameOver(finalSlot: Int?) {
        if (gameOver) return
        gameOver = true
        running = false
        paused = false
        frozenAtMs = SystemClock.uptimeMillis()
        finalAnimatedSlot = finalSlot
        finalSlot?.let { slot ->
            targets[slot]?.let { target ->
                if (target.phase == Phase.POPPING) {
                    target.phase = Phase.MISS_FALLING
                    target.phaseStartedAt = frozenAtMs
                }
            }
        }
        generation++
        pendingSpawns = 0
        handler.removeCallbacksAndMessages(null)
        onGameOverStarted?.invoke()

        val token = generation
        handler.postDelayed({
            if (token != generation) return@postDelayed
            onGameOverFinished?.invoke()
        }, BeaningTuning.FALL_MS)
        invalidate()
        postInvalidateOnAnimation()
    }

    private fun ensureDesiredTargets() {
        if (!running || paused || gameOver) return
        val desired = BeaningTuning.simultaneousTargetsForLevel(level)
        val live = targets.values.count { it.phase == Phase.POPPING || it.phase == Phase.ACTIVE }
        val needed = desired - live - pendingSpawns
        repeat(max(0, needed)) { scheduleSpawn(null) }
    }

    private fun scheduleSpawn(avoidSlot: Int?) {
        if (!running || paused || gameOver) return
        pendingSpawns++
        val token = generation
        val delay = Random.nextLong(
            BeaningTuning.SPAWN_DELAY_MIN_MS,
            BeaningTuning.SPAWN_DELAY_MAX_MS + 1L
        )
        handler.postDelayed({
            pendingSpawns = max(0, pendingSpawns - 1)
            if (token != generation || !running || paused || gameOver) return@postDelayed

            val desired = BeaningTuning.simultaneousTargetsForLevel(level)
            val live = targets.values.count { it.phase == Phase.POPPING || it.phase == Phase.ACTIVE }
            if (live >= desired) return@postDelayed

            if (!spawnOne(avoidSlot)) {
                scheduleSpawn(avoidSlot)
            }
        }, delay)
    }

    private fun spawnOne(avoidSlot: Int?): Boolean {
        val occupied = targets.keys
        val allFree = (0..8).filter { it !in occupied }
        if (allFree.isEmpty()) return false
        val preferred = if (avoidSlot != null && allFree.size > 1) allFree.filter { it != avoidSlot } else allFree
        val slot = (if (preferred.isNotEmpty()) preferred else allFree).random()

        val activeClowns = targets.values.map { it.clown }.toSet()
        val available = (0 until BeaningTuning.TEST_CLOWN_COUNT).filter { it !in activeClowns }
        if (available.isEmpty()) return false
        val preferredClowns = available.filter { it !in recentClowns }
        val clown = (if (preferredClowns.isNotEmpty()) preferredClowns else available).random()

        recentClowns.addLast(clown)
        while (recentClowns.size > BeaningTuning.RECENT_CLOWN_HISTORY) recentClowns.removeFirst()

        val now = SystemClock.uptimeMillis()
        val target = Target(
            slot = slot,
            clown = clown,
            phase = Phase.POPPING,
            phaseStartedAt = now,
            reactionWindowMs = BeaningTuning.reactionWindowForLevel(level)
        )
        targets[slot] = target

        val token = generation
        handler.postDelayed({
            if (token != generation || !running || paused || gameOver) return@postDelayed
            val current = targets[slot] ?: return@postDelayed
            if (current !== target || current.phase != Phase.POPPING) return@postDelayed
            current.phase = Phase.ACTIVE
            current.phaseStartedAt = SystemClock.uptimeMillis()
            scheduleTimeout(current)
            invalidate()
        }, BeaningTuning.POPUP_MS)

        invalidate()
        postInvalidateOnAnimation()
        return true
    }

    private fun scheduleTimeout(target: Target) {
        val token = generation
        handler.postDelayed({
            handleTimeout(target.slot, target.clown, token)
        }, target.reactionWindowMs)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        computeBoardRect()
        canvas.drawColor(Color.rgb(27, 18, 16))
        canvas.drawBitmap(boardBitmap, null, boardRect, bitmapPaint)

        if (previewMode) {
            drawTarget(canvas, previewSlot, previewClown, Phase.ACTIVE, 1f, SystemClock.uptimeMillis())
        } else {
            val now = SystemClock.uptimeMillis()
            targets.values.sortedBy { it.slot }.forEach { target ->
                val effectiveNow = if (gameOver && target.slot != finalAnimatedSlot) frozenAtMs else now
                drawTargetState(canvas, target, effectiveNow)
            }
            drawHud(canvas)
        }

        if (previewMode || targets.isNotEmpty()) {
            drawShelfMasks(canvas)
        }

        val animating = previewMode.not() && targets.values.any {
            it.phase == Phase.POPPING || it.phase == Phase.HIT_FALLING || it.phase == Phase.MISS_FALLING
        }
        if (animating && (!gameOver || finalAnimatedSlot != null)) postInvalidateOnAnimation()
    }

    private fun drawTargetState(canvas: Canvas, target: Target, now: Long) {
        val elapsed = (now - target.phaseStartedAt).coerceAtLeast(0L)
        when (target.phase) {
            Phase.POPPING -> {
                val p = (elapsed.toFloat() / BeaningTuning.POPUP_MS).coerceIn(0f, 1f)
                drawTarget(canvas, target.slot, target.clown, target.phase, p, now)
            }
            Phase.ACTIVE -> drawTarget(canvas, target.slot, target.clown, target.phase, 1f, now)
            Phase.HIT_FALLING, Phase.MISS_FALLING -> {
                val p = (elapsed.toFloat() / BeaningTuning.FALL_MS).coerceIn(0f, 1f)
                drawTarget(canvas, target.slot, target.clown, target.phase, p, now)
            }
        }
    }

    private fun drawTarget(
        canvas: Canvas,
        slot: Int,
        clown: Int,
        phase: Phase,
        progress: Float,
        @Suppress("UNUSED_PARAMETER") now: Long
    ) {
        val row = slot / 3
        val col = slot % 3
        val scaleX = boardRect.width() / LOGICAL_BOARD_WIDTH
        val scaleY = boardRect.height() / LOGICAL_BOARD_HEIGHT
        val cx = boardRect.left + slotXs[col] * scaleX
        val baseline = boardRect.top + slotBaselines[row] * scaleY
        val targetH = 176f * scaleY
        val targetW = 126f * scaleX

        var visible = 1f
        var fall = 0f
        if (phase == Phase.POPPING) visible = progress
        if (phase == Phase.HIT_FALLING || phase == Phase.MISS_FALLING) fall = progress

        val shadowScale = if (fall > 0f) 1f - fall * 0.72f else visible
        shadowPaint.alpha = (58f * shadowScale.coerceIn(0f, 1f)).toInt()
        val shadowW = targetW * 0.72f * shadowScale.coerceAtLeast(0.18f)
        val shadowH = 12f * scaleY * shadowScale.coerceAtLeast(0.15f)
        canvas.drawOval(
            RectF(cx - shadowW / 2f, baseline - shadowH / 2f, cx + shadowW / 2f, baseline + shadowH / 2f),
            shadowPaint
        )

        val popOffset = if (phase == Phase.POPPING) targetH * (1f - visible) * 0.92f else 0f
        val fallScaleY = if (fall > 0f) 1f - fall * 0.82f else 1f
        val fallDrop = if (fall > 0f) targetH * 0.16f * fall else 0f

        canvas.save()
        val clipTopBoard = when (row) {
            0 -> 642f
            1 -> 864f
            else -> 1092f
        }
        canvas.clipRect(
            boardRect.left + 118f * scaleX,
            boardRect.top + clipTopBoard * scaleY,
            boardRect.left + 819f * scaleX,
            baseline + 12f * scaleY
        )
        canvas.scale(1f, fallScaleY, cx, baseline)
        val dst = RectF(
            cx - targetW / 2f,
            baseline - targetH + popOffset + fallDrop,
            cx + targetW / 2f,
            baseline + popOffset + fallDrop
        )
        canvas.drawBitmap(targetBitmaps[clown.coerceIn(targetBitmaps.indices)], null, dst, bitmapPaint)
        canvas.restore()
    }

    private fun drawShelfMasks(canvas: Canvas) {
        val scaleX = boardRect.width() / LOGICAL_BOARD_WIDTH
        val scaleY = boardRect.height() / LOGICAL_BOARD_HEIGHT
        val blockerBands = arrayOf(
            792 to 844,
            1020 to 1070,
            1248 to 1303
        )
        blockerBands.forEach { (top, bottom) ->
            val bitmapScaleX = boardBitmap.width / LOGICAL_BOARD_WIDTH
            val bitmapScaleY = boardBitmap.height / LOGICAL_BOARD_HEIGHT
            val src = Rect(
                (118f * bitmapScaleX).toInt(),
                (top * bitmapScaleY).toInt(),
                (819f * bitmapScaleX).toInt(),
                (bottom * bitmapScaleY).toInt()
            )
            val dst = RectF(
                boardRect.left + 118f * scaleX,
                boardRect.top + top * scaleY,
                boardRect.left + 819f * scaleX,
                boardRect.top + bottom * scaleY
            )
            canvas.drawBitmap(boardBitmap, src, dst, bitmapPaint)
        }
    }

    private fun drawHud(canvas: Canvas) {
        val scaleX = boardRect.width() / LOGICAL_BOARD_WIDTH
        val scaleY = boardRect.height() / LOGICAL_BOARD_HEIGHT

        drawHudNumber(canvas, score.toString(), 871f, 846f, 72f, 42f, scaleX, scaleY)
        drawHudNumber(canvas, level.toString(), 871f, 1015f, 72f, 42f, scaleX, scaleY)

        // Lower each lit miss bulb by half of its 50-board-unit rendered height.
        val bulbYs = floatArrayOf(842f, 927f, 1012f)
        for (i in 0 until min(misses, BeaningTuning.MISS_LIMIT)) {
            val cx = boardRect.left + 74f * scaleX
            val cy = boardRect.top + bulbYs[i] * scaleY
            val r = 25f * min(scaleX, scaleY)
            bulbPaint.color = Color.argb(215, 255, 35, 18)
            canvas.drawCircle(cx, cy, r, bulbPaint)
            bulbPaint.color = Color.argb(220, 255, 154, 62)
            canvas.drawCircle(cx - r * 0.16f, cy - r * 0.18f, r * 0.48f, bulbPaint)
            bulbPaint.color = Color.argb(235, 255, 235, 184)
            canvas.drawCircle(cx - r * 0.30f, cy - r * 0.34f, r * 0.15f, bulbPaint)
        }
    }

    private fun drawHudNumber(
        canvas: Canvas,
        text: String,
        bx: Float,
        by: Float,
        maxWidthBoard: Float,
        startSizeBoard: Float,
        scaleX: Float,
        scaleY: Float
    ) {
        val cx = boardRect.left + bx * scaleX
        val cy = boardRect.top + by * scaleY
        var size = startSizeBoard * min(scaleX, scaleY)
        val maxWidth = maxWidthBoard * scaleX
        hudFillPaint.textSize = size
        hudStrokePaint.textSize = size
        while (hudFillPaint.measureText(text) > maxWidth && size > 14f) {
            size *= 0.9f
            hudFillPaint.textSize = size
            hudStrokePaint.textSize = size
        }
        val y = cy - (hudFillPaint.ascent() + hudFillPaint.descent()) / 2f
        hudStrokePaint.strokeWidth = max(2f, size * 0.10f)
        canvas.drawText(text, cx, y, hudStrokePaint)
        canvas.drawText(text, cx, y, hudFillPaint)
    }

    private fun viewToBoard(x: Float, y: Float): Pair<Float, Float>? {
        if (!boardRect.contains(x, y)) return null
        val bx = (x - boardRect.left) * LOGICAL_BOARD_WIDTH / boardRect.width()
        val by = (y - boardRect.top) * LOGICAL_BOARD_HEIGHT / boardRect.height()
        return bx to by
    }

    private fun slotAt(x: Float, y: Float): Int? {
        val halfW = 84f
        val height = 192f
        for (row in 0..2) {
            val baseline = slotBaselines[row]
            for (col in 0..2) {
                val cx = slotXs[col]
                if (x in (cx - halfW)..(cx + halfW) && y in (baseline - height)..(baseline + 10f)) {
                    return row * 3 + col
                }
            }
        }
        return null
    }

    private fun computeBoardRect() {
        val sourceRatio = LOGICAL_BOARD_WIDTH / LOGICAL_BOARD_HEIGHT
        val viewRatio = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        if (viewRatio > sourceRatio) {
            val drawH = height.toFloat()
            val drawW = drawH * sourceRatio
            val left = (width - drawW) / 2f
            boardRect.set(left, 0f, left + drawW, drawH)
        } else {
            val drawW = width.toFloat()
            val drawH = drawW / sourceRatio
            val top = (height - drawH) / 2f
            boardRect.set(0f, top, drawW, top + drawH)
        }
    }
}
