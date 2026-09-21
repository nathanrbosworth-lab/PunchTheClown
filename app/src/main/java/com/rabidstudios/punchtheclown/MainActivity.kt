package com.rabidstudios.punchtheclown

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import kotlin.math.max
import kotlin.random.Random

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var sessionToken = 0

    private val sequence = mutableListOf<Int>()
    private var playerIndex = 0
    private var score = 0L
    private var level = 1
    private var longestSequence = 0
    private var correctInputs = 0

    private var inGame = false
    private var gameFinished = false
    private var pausedByLifecycle = false

    private lateinit var board: ClownBoardView
    private lateinit var scoreText: TextView
    private lateinit var levelText: TextView
    private lateinit var sequenceText: TextView
    private lateinit var statusText: TextView

    private val cream = Color.rgb(247, 231, 198)
    private val gold = Color.rgb(232, 182, 75)
    private val red = Color.rgb(183, 38, 46)
    private val wood = Color.rgb(54, 31, 22)
    private val dark = Color.rgb(27, 18, 16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = dark
        window.navigationBarColor = dark
        showSplash()
    }

    override fun onPause() {
        super.onPause()
        if (inGame && !gameFinished) {
            pausedByLifecycle = true
            sessionToken++
            handler.removeCallbacksAndMessages(null)
            if (::board.isInitialized) board.inputEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (pausedByLifecycle && inGame && !gameFinished) {
            pausedByLifecycle = false
            handler.post {
                AlertDialog.Builder(this)
                    .setTitle("Hold your punches")
                    .setMessage("The game was paused. Resume by replaying the current sequence?")
                    .setPositiveButton("Resume") { _, _ ->
                        sessionToken++
                        playSequence()
                    }
                    .setNegativeButton("Quit") { _, _ ->
                        quitToMenu()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (inGame && !gameFinished) {
            AlertDialog.Builder(this)
                .setTitle("Quit game?")
                .setMessage("Your current score will be lost.")
                .setPositiveButton("Quit") { _, _ -> quitToMenu() }
                .setNegativeButton("Keep punching", null)
                .show()
        } else {
            showMenu()
        }
    }

    private fun root(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            setBackgroundColor(wood)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun title(text: String, size: Float = 34f): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(cream)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    }

    private fun subtitle(text: String, size: Float = 16f): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(gold)
            gravity = Gravity.CENTER
        }
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 17f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(red)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply {
                topMargin = dp(10)
            }
        }
    }

    private fun space(height: Int): Space {
        return Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(height))
        }
    }

    private fun showSplash() {
        inGame = false
        gameFinished = false
        val r = root()
        r.gravity = Gravity.CENTER
        r.addView(title("🤡", 78f))
        r.addView(space(10))
        r.addView(title("PUNCH THE CLOWN", 36f))
        r.addView(space(8))
        r.addView(subtitle("A ridiculously serious memory game"))
        r.addView(space(24))
        r.addView(title("HONK!", 22f))
        setContentView(r)
        handler.postDelayed({ showMenu() }, 1100L)
    }

    private fun showMenu() {
        sessionToken++
        handler.removeCallbacksAndMessages(null)
        inGame = false
        gameFinished = false

        val r = root()
        r.addView(title("PUNCH THE CLOWN"))
        r.addView(space(8))
        r.addView(subtitle("STEP RIGHT UP"))
        r.addView(space(22))
        r.addView(title("🤡", 74f))
        r.addView(space(24))
        r.addView(button("Punch the Clown") { showReady() })

        val comingSoon = button("Punching the Clowns — Coming Soon") {}
        comingSoon.isEnabled = false
        comingSoon.alpha = 0.5f
        r.addView(comingSoon)

        r.addView(space(26))
        r.addView(subtitle("Watch the pattern. Remember the pattern. Punch it back.", 15f))
        setContentView(r)
    }

    private fun showReady() {
        inGame = false
        val r = root()
        r.addView(title("PUNCH THE CLOWN"))
        r.addView(space(18))
        r.addView(title("🤡", 70f))
        r.addView(space(18))
        r.addView(subtitle("WATCH THE PATTERN.", 18f))
        r.addView(subtitle("REMEMBER THE PATTERN.", 18f))
        r.addView(subtitle("PUNCH IT BACK.", 18f))
        r.addView(space(24))
        r.addView(button("PUNCH IT!") { startNewGame() })
        r.addView(button("Back to Menu") { showMenu() })
        setContentView(r)
    }

    private fun startNewGame() {
        sessionToken++
        handler.removeCallbacksAndMessages(null)
        sequence.clear()
        playerIndex = 0
        score = 0L
        level = 1
        longestSequence = 0
        correctInputs = 0
        inGame = true
        gameFinished = false
        showGame()
        addRound()
    }

    private fun showGame() {
        val r = root()

        val hud = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(dark)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        scoreText = hudItem("SCORE", "0")
        levelText = hudItem("LEVEL", "1")
        sequenceText = hudItem("SEQUENCE", "1")
        hud.addView(scoreText)
        hud.addView(levelText)
        hud.addView(sequenceText)
        r.addView(hud, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        statusText = title("WATCH", 24f)
        r.addView(space(14))
        r.addView(statusText)
        r.addView(space(10))

        board = ClownBoardView(this).apply {
            inputEnabled = false
            onCellPressed = { cell -> onPlayerTap(cell) }
        }

        r.addView(board, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        r.addView(button("Quit to Menu") {
            AlertDialog.Builder(this)
                .setTitle("Quit game?")
                .setMessage("Your current score will be lost.")
                .setPositiveButton("Quit") { _, _ -> quitToMenu() }
                .setNegativeButton("Keep punching", null)
                .show()
        })

        setContentView(r)
        updateHud()
    }

    private fun hudItem(label: String, value: String): TextView {
        return TextView(this).apply {
            text = "$label\n$value"
            textSize = 16f
            setTextColor(cream)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    private fun updateHud() {
        if (::scoreText.isInitialized) scoreText.text = "SCORE\n%,d".format(score)
        if (::levelText.isInitialized) levelText.text = "LEVEL\n$level"
        if (::sequenceText.isInitialized) sequenceText.text = "SEQUENCE\n${sequence.size}"
    }

    private fun addRound() {
        if (!inGame || gameFinished) return
        sequence.add(Random.nextInt(0, 9))
        level = sequence.size
        longestSequence = max(longestSequence, sequence.size - 1)
        updateHud()
        playSequence()
    }

    private fun playSequence() {
        if (!inGame || gameFinished || sequence.isEmpty()) return

        val token = sessionToken
        board.inputEnabled = false
        board.clearMarks()
        statusText.text = "WATCH"

        val signal = max(200L, 650L - (level - 1) * 25L)
        val gap = max(75L, 250L - (level - 1) * 8L)
        var at = 300L

        sequence.forEach { cell ->
            handler.postDelayed({
                if (token != sessionToken || !inGame || gameFinished) return@postDelayed
                board.setHighlighted(cell)
            }, at)
            at += signal

            handler.postDelayed({
                if (token != sessionToken || !inGame || gameFinished) return@postDelayed
                board.setHighlighted(null)
            }, at)
            at += gap
        }

        handler.postDelayed({
            if (token != sessionToken || !inGame || gameFinished) return@postDelayed
            playerIndex = 0
            board.inputEnabled = true
            statusText.text = "YOUR TURN"
        }, at)
    }

    private fun onPlayerTap(cell: Int) {
        if (!inGame || gameFinished || !board.inputEnabled) return

        val expected = sequence[playerIndex]
        board.flashPlayer(cell)

        if (cell != expected) {
            board.inputEnabled = false
            board.markWrong(cell)
            statusText.text = "WRONG CLOWN!"
            gameFinished = true
            sessionToken++
            handler.removeCallbacksAndMessages(null)

            handler.postDelayed({
                longestSequence = max(longestSequence, sequence.size - 1)
                showResults()
            }, 700L)
            return
        }

        score += 100L
        correctInputs++
        playerIndex++
        updateHud()

        if (playerIndex >= sequence.size) {
            board.inputEnabled = false
            longestSequence = max(longestSequence, sequence.size)
            score += level * 100L
            updateHud()
            statusText.text = "NICE!"

            val token = sessionToken
            handler.postDelayed({
                if (token == sessionToken && inGame && !gameFinished) {
                    addRound()
                }
            }, 600L)
        }
    }

    private fun showResults() {
        inGame = false
        val r = root()
        r.addView(title("YOU PUNCHED\nTHE WRONG CLOWN", 31f))
        r.addView(space(22))
        r.addView(resultLine("SCORE", "%,d".format(score)))
        r.addView(resultLine("LEVEL", level.toString()))
        r.addView(resultLine("LONGEST SEQUENCE", longestSequence.toString()))
        r.addView(resultLine("CORRECT PUNCHES", correctInputs.toString()))
        r.addView(space(22))
        r.addView(button("PUNCH AGAIN") { startNewGame() })
        r.addView(button("MAIN MENU") { showMenu() })
        setContentView(r)
    }

    private fun resultLine(label: String, value: String): TextView {
        return TextView(this).apply {
            text = "$label\n$value"
            textSize = 22f
            setTextColor(if (label == "SCORE") gold else cream)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    }

    private fun quitToMenu() {
        sessionToken++
        handler.removeCallbacksAndMessages(null)
        inGame = false
        gameFinished = false
        showMenu()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

class ClownBoardView(
    context: android.content.Context
) : View(context) {

    var onCellPressed: ((Int) -> Unit)? = null
    var inputEnabled: Boolean = false

    private var highlightedCell: Int? = null
    private var wrongCell: Int? = null
    private val clearHandler = Handler(Looper.getMainLooper())

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
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

        paint.color = Color.rgb(37, 20, 15)
        canvas.drawRect(0f, 0f, w, h, paint)

        val stripeW = w / 12f
        repeat(12) { i ->
            paint.color = if (i % 2 == 0) Color.rgb(127, 23, 29) else Color.rgb(241, 215, 166)
            canvas.drawRect(i * stripeW, 0f, (i + 1) * stripeW + 1f, h, paint)
        }

        paint.color = Color.argb(125, 14, 9, 7)
        canvas.drawRect(0f, 0f, w, h, paint)

        val cx = w / 2f
        val cy = h * 0.47f
        val faceR = w * 0.31f

        paint.color = Color.rgb(183, 38, 46)
        canvas.drawCircle(cx - faceR * .82f, cy - faceR * .40f, faceR * .58f, paint)
        canvas.drawCircle(cx + faceR * .82f, cy - faceR * .40f, faceR * .58f, paint)
        canvas.drawCircle(cx - faceR * .92f, cy + faceR * .12f, faceR * .52f, paint)
        canvas.drawCircle(cx + faceR * .92f, cy + faceR * .12f, faceR * .52f, paint)

        paint.color = Color.rgb(255, 244, 232)
        canvas.drawCircle(cx, cy, faceR, paint)

        stroke.color = Color.rgb(62, 36, 24)
        stroke.strokeWidth = w * .014f
        canvas.drawCircle(cx, cy, faceR, stroke)

        paint.color = Color.WHITE
        canvas.drawOval(
            cx - faceR * .62f,
            cy - faceR * .43f,
            cx - faceR * .10f,
            cy - faceR * .05f,
            paint
        )
        canvas.drawOval(
            cx + faceR * .10f,
            cy - faceR * .43f,
            cx + faceR * .62f,
            cy - faceR * .05f,
            paint
        )

        paint.color = Color.rgb(38, 93, 133)
        canvas.drawCircle(cx - faceR * .34f, cy - faceR * .22f, faceR * .10f, paint)
        canvas.drawCircle(cx + faceR * .34f, cy - faceR * .22f, faceR * .10f, paint)

        paint.color = Color.BLACK
        canvas.drawCircle(cx - faceR * .34f, cy - faceR * .22f, faceR * .05f, paint)
        canvas.drawCircle(cx + faceR * .34f, cy - faceR * .22f, faceR * .05f, paint)

        paint.color = Color.rgb(214, 37, 47)
        canvas.drawCircle(cx, cy + faceR * .06f, faceR * .23f, paint)

        stroke.color = Color.rgb(179, 30, 40)
        stroke.strokeWidth = w * .025f
        val smile = Path().apply {
            moveTo(cx - faceR * .52f, cy + faceR * .34f)
            quadTo(cx, cy + faceR * .82f, cx + faceR * .52f, cy + faceR * .34f)
        }
        canvas.drawPath(smile, stroke)

        paint.color = Color.rgb(232, 182, 75)
        val bowY = cy + faceR * 1.20f
        val bow = Path().apply {
            moveTo(cx, bowY)
            lineTo(cx - w * .23f, bowY - h * .055f)
            lineTo(cx - w * .23f, bowY + h * .055f)
            close()
        }
        canvas.drawPath(bow, paint)
        val bow2 = Path().apply {
            moveTo(cx, bowY)
            lineTo(cx + w * .23f, bowY - h * .055f)
            lineTo(cx + w * .23f, bowY + h * .055f)
            close()
        }
        canvas.drawPath(bow2, paint)
        paint.color = Color.rgb(183, 38, 46)
        canvas.drawCircle(cx, bowY, w * .045f, paint)

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
                paint.color = Color.argb(165, 216, 67, 67)
                canvas.drawRect(left + 3f, top + 3f, right - 3f, bottom - 3f, paint)
            }

            stroke.color = Color.argb(35, 255, 255, 255)
            stroke.strokeWidth = 1f
            canvas.drawRect(left, top, right, bottom, stroke)
        }

        stroke.color = Color.rgb(232, 182, 75)
        stroke.strokeWidth = 8f
        canvas.drawRect(4f, 4f, w - 4f, h - 4f, stroke)
    }
}
