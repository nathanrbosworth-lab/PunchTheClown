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
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.Switch
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
    private var scoreBarHeightPx = 0

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

    // Clown artwork is authored at 900 x 900 pixels. Keep both the ready preview
    // and active play field at the same physical-pixel size.
    private val gameBoardSizePx = 900

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = dark
        window.navigationBarColor = dark
        prefs = getSharedPreferences("punch_the_clown", MODE_PRIVATE)
        audio = GameAudioManager(this)
        audio.enabled = prefs.getBoolean("sound_enabled", true)
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

    private fun space(height: Int): Space = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun approvedArt(drawable: Int, scaleType: ImageView.ScaleType): ImageView {
        return ImageView(this).apply {
            setImageResource(drawable)
            this.scaleType = scaleType
            adjustViewBounds = true
            contentDescription = "Punch the Clown artwork"
        }
    }

    private fun showSplash() {
        inGame = false
        gameFinished = false

        val image = approvedArt(
            R.drawable.punch_clown_splash,
            ImageView.ScaleType.CENTER_CROP
        )
        image.setBackgroundColor(dark)
        setContentView(image)

        handler.postDelayed({ showMenu() }, 1800L)
    }

    private fun showMenu() {
        sessionToken++
        handler.removeCallbacksAndMessages(null)
        audio.stopEventSequence()
        inGame = false
        gameFinished = false

        val root = FrameLayout(this).apply {
            setBackgroundColor(dark)
        }

        val background = ImageView(this).apply {
            setImageResource(R.drawable.game_select_screen)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Punch the Clown carnival game selection"
        }
        root.addView(
            background,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // The artwork already contains the two guidepost signs. These transparent
        // views turn the signs themselves into the game-mode buttons.
        val punchTheClownSign = View(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Punch the Clown"
            setOnClickListener { showReady() }
        }

        val punchingTheClownsSign = View(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Punching the Clowns — Coming Soon"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Punching the Clowns")
                    .setMessage("Coming soon.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        root.addView(punchTheClownSign, FrameLayout.LayoutParams(1, 1))
        root.addView(punchingTheClownsSign, FrameLayout.LayoutParams(1, 1))

        val statsSettingsButton = Button(this).apply {
            text = "STATS / SETTINGS"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(red)
            setOnClickListener { showStatsSettings() }
        }
        root.addView(
            statsSettingsButton,
            FrameLayout.LayoutParams(dp(230), dp(52)).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(72)
            }
        )

        setContentView(root)

        root.post {
            val rootW = root.width.toFloat()
            val rootH = root.height.toFloat()
            val artRatio = 941f / 1672f
            val rootRatio = rootW / rootH

            val artW: Float
            val artH: Float
            val artLeft: Float
            val artTop: Float

            if (rootRatio > artRatio) {
                artH = rootH
                artW = artH * artRatio
                artLeft = (rootW - artW) / 2f
                artTop = 0f
            } else {
                artW = rootW
                artH = artW / artRatio
                artLeft = 0f
                artTop = (rootH - artH) / 2f
            }

            fun placeSign(view: View, left: Float, top: Float, right: Float, bottom: Float) {
                view.layoutParams = FrameLayout.LayoutParams(
                    ((right - left) * artW).toInt(),
                    ((bottom - top) * artH).toInt()
                ).apply {
                    leftMargin = (artLeft + left * artW).toInt()
                    topMargin = (artTop + top * artH).toInt()
                }
            }

            // Normalized bounds align the touch targets with the two painted
            // guidepost signs on the right side of the supplied poster.
            placeSign(punchTheClownSign, 0.52f, 0.47f, 0.98f, 0.63f)
            placeSign(punchingTheClownsSign, 0.52f, 0.63f, 0.98f, 0.80f)
        }
    }

    private fun showStatsSettings() {
        inGame = false
        gameFinished = false
        audio.stopEventSequence()

        val r = root()
        r.addView(space(24))
        r.addView(title("STATS & SETTINGS", 30f))
        r.addView(space(18))

        val best = prefs.getLong("high_score", 0L)
        val highestLevel = prefs.getInt("highest_level", 0)
        val longest = prefs.getInt("longest_sequence", 0)
        val gamesPlayed = prefs.getLong("games_played", 0L)
        val correctPunches = prefs.getLong("correct_punches", 0L)
        val sequencesCompleted = prefs.getLong("sequences_completed", 0L)

        r.addView(statLine("BEST SCORE", "%,d".format(best)))
        r.addView(statLine("HIGHEST LEVEL", highestLevel.toString()))
        r.addView(statLine("LONGEST SEQUENCE", longest.toString()))
        r.addView(statLine("GAMES PLAYED", "%,d".format(gamesPlayed)))
        r.addView(statLine("CORRECT PUNCHES", "%,d".format(correctPunches)))
        r.addView(statLine("SEQUENCES COMPLETED", "%,d".format(sequencesCompleted)))

        r.addView(space(18))

        val soundSwitch = Switch(this).apply {
            text = "Sound"
            textSize = 18f
            setTextColor(cream)
            isChecked = prefs.getBoolean("sound_enabled", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("sound_enabled", checked).apply()
                audio.enabled = checked
                if (!checked) audio.stopEventSequence()
            }
        }
        r.addView(
            soundSwitch,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val hapticsSwitch = Switch(this).apply {
            text = "Haptics"
            textSize = 18f
            setTextColor(cream)
            isChecked = prefs.getBoolean("haptics_enabled", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("haptics_enabled", checked).apply()
            }
        }
        r.addView(
            hapticsSwitch,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        r.addView(space(18))
        r.addView(button("Back to Game Select") { showMenu() })
        setContentView(r)
    }

    private fun statLine(label: String, value: String): TextView = TextView(this).apply {
        text = "$label    $value"
        textSize = 18f
        setTextColor(cream)
        gravity = Gravity.CENTER
        setPadding(0, dp(7), 0, dp(7))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
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
        val readyTitle = title("PUNCH THE CLOWN")
        r.addView(readyTitle)
        readyTitle.post {
            // Move only the title down by exactly one measured title height.
            readyTitle.translationY = readyTitle.height.toFloat()
        }
        r.addView(space(8))
        r.addView(
            Space(this),
            LinearLayout.LayoutParams(1, resources.displayMetrics.heightPixels / 8)
        )

        val preview = ClownBoardView(this, currentClown, showGrid = false).apply {
            inputEnabled = false
        }
        r.addView(
            preview,
            LinearLayout.LayoutParams(
                gameBoardSizePx,
                gameBoardSizePx
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )

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
        audio.stopEventSequence()
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
        r.addView(
            hud,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        statusText = title("WATCH", 24f)
        r.addView(space(8))
        r.addView(statusText)
        r.addView(space(5))

        hud.post {
            // Move the score bar and WATCH / YOUR TURN marker down by exactly
            // one measured score-bar height without moving the 900 x 900 board.
            scoreBarHeightPx = hud.height
            val shift = scoreBarHeightPx.toFloat()
            hud.translationY = shift
            statusText.translationY = shift
        }

        board = ClownBoardView(this, currentClown, showGrid = true).apply {
            inputEnabled = false
            onCellPressed = { cell -> onPlayerTap(cell) }
        }
        // Use equal weighted space above and below the fixed board so the
        // 900 x 900 play field is vertically centered in the available play area.
        r.addView(
            Space(this),
            LinearLayout.LayoutParams(1, 0, 1f)
        )

        r.addView(
            board,
            LinearLayout.LayoutParams(
                gameBoardSizePx,
                gameBoardSizePx
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )

        r.addView(
            Space(this),
            LinearLayout.LayoutParams(1, 0, 1f)
        )

        val quitButton = button("Quit to Menu") {
            AlertDialog.Builder(this)
                .setTitle("Quit game?")
                .setMessage("Your current score will be lost.")
                .setPositiveButton("Quit") { _, _ -> quitToMenu() }
                .setNegativeButton("Keep punching", null)
                .show()
        }.apply {
            // Raise by exactly one button height: the new bottom lands at the old top.
            translationY = -dp(58).toFloat()
        }
        r.addView(quitButton)

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
            val willBeNewHigh = score > prefs.getLong("high_score", 0L)
            audio.playWrongThenWin(willBeNewHigh)

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
            .putLong(
                "sequences_completed",
                prefs.getLong("sequences_completed", 0L) + completedSequences
            )
            .apply()

        if (newHigh) {
            haptic(180L, 165)
        }
        showResults(newHigh)
    }

    private fun showResults(newHigh: Boolean) {
        inGame = false
        val best = prefs.getLong("high_score", 0L)
        val bestLongestSequence = prefs.getInt("longest_sequence", 0)
        val r = root()

        // Match the results/fail screen's starting position to the visible
        // top of the translated gameplay score bar.
        r.addView(
            Space(this),
            LinearLayout.LayoutParams(
                1,
                if (scoreBarHeightPx > 0) scoreBarHeightPx else dp(56)
            )
        )

        if (newHigh) {
            r.addView(title("NEW HIGH SCORE!", 31f))
        } else {
            r.addView(title("YOU PUNCHED\nTHE WRONG CLOWN", 30f))
        }

        r.addView(space(14))
        r.addView(resultLine("SCORE", "%,d".format(score)))
        r.addView(resultLine("BEST", "%,d".format(best)))
        r.addView(resultLine("LEVEL", level.toString()))
        r.addView(resultLine("LONGEST SEQUENCE", bestLongestSequence.toString()))
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
        if (!prefs.getBoolean("haptics_enabled", true)) return
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                durationMs,
                amplitude.coerceIn(1, 255)
            )
        )
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
