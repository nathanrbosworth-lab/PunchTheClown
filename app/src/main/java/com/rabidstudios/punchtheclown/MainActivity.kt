package com.rabidstudios.punchtheclown

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
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
    private var completedSequences = 0
    private var currentClown = 0
    private var inGame = false
    private var gameFinished = false
    private var pausedByLifecycle = false

    private lateinit var board: ClownBoardView
    private lateinit var scoreText: TextView
    private lateinit var levelText: TextView
    private lateinit var sequenceText: TextView
    private lateinit var statusText: TextView
    private lateinit var audio: GameAudioManager
    private lateinit var prefs: android.content.SharedPreferences

    private val cream = Color.rgb(247, 231, 198)
    private val gold = Color.rgb(232, 182, 75)
    private val red = Color.rgb(183, 38, 46)
    private val wood = Color.rgb(54, 31, 22)
    private val dark = Color.rgb(27, 18, 16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = dark
        window.navigationBarColor = dark
        prefs = getSharedPreferences("punch_the_clown", MODE_PRIVATE)
        audio = GameAudioManager(this)
        showSplash()
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
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
                    .setTitle("HOLD YOUR PUNCHES")
                    .setMessage("The game was paused. Resume by replaying the current sequence?")
                    .setPositiveButton("Resume") { _, _ ->
                        sessionToken++
                        playSequence()
                    }
                    .setNegativeButton("Quit") { _, _ -> quitToMenu() }
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
            setPadding(dp(20), dp(20), dp(20), dp(20))
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
            ).apply { topMargin = dp(10) }
        }
    }

    private fun artImage(resId: Int, scale: ImageView.ScaleType): ImageView {
        return ImageView(this).apply {
            setImageResource(resId)
            scaleType = scale
            adjustViewBounds = true
            setBackgroundColor(dark)
        }
    }

    private fun space(height: Int): Space = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun showSplash() {
        inGame = false
        gameFinished = false
        val r = root().apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.BLACK)
        }
        val splash = artImage(R.drawable.punch_clown_splash, ImageView.ScaleType.CENTER_CROP)
        r.addView(
            splash,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        setContentView(r)
        handler.postDelayed({ showMenu() }, 1800L)
    }

    private fun showMenu() {
        sessionToken++
        handler.removeCallbacksAndMessages(null)
        inGame = false
        gameFinished = false

        val r = root()
        r.addView(title("PUNCH THE CLOWN"))
        r.addView(space(4))
        r.addView(subtitle("STEP RIGHT UP"))
        r.addView(space(8))

        val hero = artImage(R.drawable.punch_clown_hero, ImageView.ScaleType.CENTER_CROP)
        r.addView(
            hero,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(8)
            }
        )

        val high = prefs.getLong("high_score", 0L)
        val longest = prefs.getInt("longest_sequence", 0)
        r.addView(subtitle("PERSONAL BEST  %,d    •    LONGEST  %d".format(high, longest), 15f))
        r.addView(space(6))
        r.addView(button("Punch the Clown") { showReady() })

        val comingSoon = button("Punching the Clowns — Coming Soon") {}
        comingSoon.isEnabled = false
        comingSoon.alpha = 0.5f
        r.addView(comingSoon)

        r.addView(space(10))
        r.addView(subtitle("Watch the squeaks. Remember the pattern. Punch it back.", 14f))
        setContentView(r)
    }

    private fun chooseClown(): Int {
        val last = prefs.getInt("last_clown", -1)
        var next = Random.nextInt(0, 10)
        if (last in 0..9) {
            while (next == last) next = Random.nextInt(0, 10)
        }
        currentClown = next
        prefs.edit().putInt("last_clown", next).apply()
        return next
    }

    private fun showReady() {
        inGame = false
        chooseClown()
        val r = root()
        r.addView(title("PUNCH THE CLOWN"))
        r.addView(space(8))

        val preview = ClownBoardView(this, currentClown, showGrid = false).apply {
            inputEnabled = false
        }
        r.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        r.addView(space(8))
        r.addView(subtitle("WATCH THE SQUEAKS.", 17f))
        r.addView(subtitle("REMEMBER THE PATTERN.", 17f))
        r.addView(subtitle("PUNCH IT BACK.", 17f))
        r.addView(space(8))
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
        completedSequences = 0
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
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        scoreText = hudItem("SCORE", "0")
        levelText = hudItem("LEVEL", "1")
        sequenceText = hudItem("SEQUENCE", "1")
        hud.addView(scoreText)
        hud.addView(levelText)
        hud.addView(sequenceText)
        r.addView(hud, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        statusText = title("WATCH", 24f)
        r.addView(space(10))
        r.addView(statusText)
        r.addView(space(6))

        board = ClownBoardView(this, currentClown, showGrid = true).apply {
            inputEnabled = false
            onCellPressed = { cell -> onPlayerTap(cell) }
        }
        r.addView(board, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

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

    private fun hudItem(label: String, value: String): TextView = TextView(this).apply {
        text = "$label\n$value"
        textSize = 15f
        setTextColor(cream)
        gravity = Gravity.CENTER
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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
                audio.playGrid(cell)
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
            haptic(110L, 210)
            audio.playWrong()

            handler.postDelayed({
                longestSequence = max(longestSequence, sequence.size - 1)
                finishGameAndShowResults()
            }, 950L)
            return
        }

        audio.playGrid(cell)
        haptic(28L, 85)
        score += 100L
        correctInputs++
        playerIndex++
        updateHud()

        if (playerIndex >= sequence.size) {
            board.inputEnabled = false
            longestSequence = max(longestSequence, sequence.size)
            completedSequences++
            score += level * 100L
            updateHud()
            statusText.text = "NICE!"
            haptic(42L, 105)

            val token = sessionToken
            handler.postDelayed({
                if (token == sessionToken && inGame && !gameFinished) addRound()
            }, 600L)
        }
    }

    private fun finishGameAndShowResults() {
        val oldHigh = prefs.getLong("high_score", 0L)
        val oldHighestLevel = prefs.getInt("highest_level", 0)
        val oldLongest = prefs.getInt("longest_sequence", 0)
        val newHigh = score > oldHigh

        prefs.edit()
            .putLong("high_score", max(oldHigh, score))
            .putInt("highest_level", max(oldHighestLevel, level))
            .putInt("longest_sequence", max(oldLongest, longestSequence))
            .putLong("games_played", prefs.getLong("games_played", 0L) + 1L)
            .putLong("correct_punches", prefs.getLong("correct_punches", 0L) + correctInputs)
            .putLong("sequences_completed", prefs.getLong("sequences_completed", 0L) + completedSequences)
            .apply()

        if (newHigh) {
            audio.playWin()
            haptic(180L, 165)
        }
        showResults(newHigh)
    }

    private fun showResults(newHigh: Boolean) {
        inGame = false
        val best = prefs.getLong("high_score", 0L)
        val r = root()
        if (newHigh) {
            r.addView(title("NEW HIGH SCORE!", 31f))
            r.addView(subtitle("SILLY TRUMPET APPROVED", 14f))
        } else {
            r.addView(title("YOU PUNCHED\nTHE WRONG CLOWN", 30f))
        }
        r.addView(space(14))
        r.addView(resultLine("SCORE", "%,d".format(score)))
        r.addView(resultLine("BEST", "%,d".format(best)))
        r.addView(resultLine("LEVEL", level.toString()))
        r.addView(resultLine("LONGEST SEQUENCE", longestSequence.toString()))
        r.addView(resultLine("CORRECT PUNCHES", correctInputs.toString()))
        r.addView(space(14))
        r.addView(button("PUNCH AGAIN") {
            chooseClown()
            startNewGame()
        })
        r.addView(button("MAIN MENU") { showMenu() })
        setContentView(r)
    }

    private fun resultLine(label: String, value: String): TextView = TextView(this).apply {
        text = "$label\n$value"
        textSize = 21f
        setTextColor(if (label == "SCORE" || label == "BEST") gold else cream)
        gravity = Gravity.CENTER
        setPadding(0, dp(6), 0, dp(6))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun haptic(durationMs: Long, amplitude: Int) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
    }

    private fun quitToMenu() {
        sessionToken++
        handler.removeCallbacksAndMessages(null)
        inGame = false
        gameFinished = false
        showMenu()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
