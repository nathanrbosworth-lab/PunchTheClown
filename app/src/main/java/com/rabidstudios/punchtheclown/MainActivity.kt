package com.rabidstudios.punchtheclown

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import kotlin.math.max
import kotlin.random.Random

class MainActivity : Activity() {

    private enum class ActiveGameMode { NONE, PUNCH, BEANING }

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
    private var activeMode = ActiveGameMode.NONE
    private var scoreBarHeightPx = 0
    private var readyBoardTopPx = 0

    private lateinit var board: ClownBoardView
    private lateinit var beaningBoard: BeaningBoardView
    private lateinit var beaningRoot: FrameLayout
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

    // Approved six-button wooden sign sheet: 2 columns x 3 rows.
    private val woodSignSheet: Bitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.wood_sign_buttons)
    }

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
            when (activeMode) {
                ActiveGameMode.PUNCH -> if (::board.isInitialized) board.inputEnabled = false
                ActiveGameMode.BEANING -> if (::beaningBoard.isInitialized) beaningBoard.pauseForInterruption()
                ActiveGameMode.NONE -> Unit
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (pausedByLifecycle && inGame && !gameFinished) {
            pausedByLifecycle = false
            handler.post {
                when (activeMode) {
                    ActiveGameMode.PUNCH -> {
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
                    ActiveGameMode.BEANING -> showBeaningPauseDialog()
                    ActiveGameMode.NONE -> Unit
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (inGame && !gameFinished) {
            if (activeMode == ActiveGameMode.BEANING) {
                if (::beaningBoard.isInitialized) beaningBoard.pauseForInterruption()
                showBeaningPauseDialog()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Quit game?")
                    .setMessage("Your current score will be lost.")
                    .setPositiveButton("Quit") { _, _ -> quitToMenu() }
                    .setNegativeButton("Keep punching", null)
                    .show()
            }
        } else {
            showMenu()
        }
    }

    private fun root(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun withCarnivalBackground(content: View): FrameLayout {
        return FrameLayout(this).apply {
            setBackgroundColor(dark)

            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.carnival_background)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = null
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            // A light dim keeps the existing cream/gold text readable without
            // obscuring the carnival artwork.
            addView(
                View(this@MainActivity).apply {
                    setBackgroundColor(Color.argb(72, 0, 0, 0))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            addView(
                content,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
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

    private fun button(
        text: String,
        widthPx: Int = 360,
        heightPx: Int = 170,
        onClick: () -> Unit
    ): ImageButton {
        val key = text.trim().uppercase()
        val (column, row) = when (key) {
            "PUNCH IT!" -> 0 to 0
            "BACK TO MAIN" -> 1 to 0
            "QUIT TO MENU" -> 0 to 1
            "PUNCH AGAIN" -> 1 to 1
            "MAIN MENU" -> 0 to 2
            "BACK TO GAME SELECT" -> 1 to 2
            else -> 0 to 0
        }

        val cellWidth = woodSignSheet.width / 2
        val cellHeight = woodSignSheet.height / 3
        val signBitmap = Bitmap.createBitmap(
            woodSignSheet,
            column * cellWidth,
            row * cellHeight,
            cellWidth,
            cellHeight
        )

        return ImageButton(this).apply {
            setImageBitmap(signBitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = false
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            contentDescription = text
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                widthPx,
                heightPx
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(2)
            }
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

    private fun marqueeTitle(widthDp: Int, heightDp: Int): ImageView {
        return ImageView(this).apply {
            setImageResource(R.drawable.punch_clown_marquee)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = false
            contentDescription = "Punch the Clown"
            layoutParams = LinearLayout.LayoutParams(
                dp(widthDp),
                dp(heightDp)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
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
        activeMode = ActiveGameMode.NONE
        inGame = false
        gameFinished = false
        pausedByLifecycle = false

        val root = FrameLayout(this).apply {
            setBackgroundColor(dark)
        }

        val background = ImageView(this).apply {
            setImageResource(R.drawable.game_select_screen)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "Punch the Clown carnival game selection"
        }
        root.addView(
            background,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // The supplied artwork contains all three guidepost signs. Transparent
        // views make the painted signs themselves the only visible controls.
        val punchTheClownSign = View(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Punch the Clown"
            setOnClickListener { showReady() }
        }

        val beaningTheClownsSign = View(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Beaning the Clowns"
            setOnClickListener { showBeaningReady() }
        }

        val statsSettingsSign = View(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Stats and Settings"
            setOnClickListener { showStatsSettings() }
        }

        root.addView(punchTheClownSign, FrameLayout.LayoutParams(1, 1))
        root.addView(beaningTheClownsSign, FrameLayout.LayoutParams(1, 1))
        root.addView(statsSettingsSign, FrameLayout.LayoutParams(1, 1))
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

            // Bounds measured against the approved 941 x 1672 carnival menu.
            placeSign(punchTheClownSign, 0.488f, 0.431f, 0.968f, 0.579f)
            placeSign(beaningTheClownsSign, 0.490f, 0.585f, 0.965f, 0.719f)
            placeSign(statsSettingsSign, 0.495f, 0.720f, 0.951f, 0.842f)
        }
    }

    private fun showStatsSettings() {
        activeMode = ActiveGameMode.NONE
        inGame = false
        gameFinished = false
        audio.stopEventSequence()

        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        r.addView(space(12))
        r.addView(title("STATS & SETTINGS", 30f))
        r.addView(space(14))

        r.addView(subtitle("PUNCH THE CLOWN", 20f))
        r.addView(space(4))
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

        r.addView(space(16))
        r.addView(subtitle("BEANING THE CLOWNS", 20f))
        r.addView(space(4))
        r.addView(statLine("BEST SCORE", "%,d".format(prefs.getLong("beaning_high_score", 0L))))
        r.addView(statLine("HIGHEST LEVEL", prefs.getInt("beaning_highest_level", 0).toString()))
        r.addView(statLine("TOTAL CLOWNS HIT", "%,d".format(prefs.getLong("beaning_total_hits", 0L))))
        r.addView(statLine("TOTAL MISSES", "%,d".format(prefs.getLong("beaning_total_misses", 0L))))
        r.addView(statLine("GAMES PLAYED", "%,d".format(prefs.getLong("beaning_games_played", 0L))))
        r.addView(statLine("LONGEST RUN", prefs.getInt("beaning_longest_run", 0).toString()))

        r.addView(space(18))
        r.addView(subtitle("SHARED SETTINGS", 18f))
        r.addView(space(4))

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

        r.addView(space(12))
        r.addView(button("Back to Game Select") { showMenu() })
        r.addView(space(20))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(
                r,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        setContentView(withCarnivalBackground(scroll))
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
        activeMode = ActiveGameMode.NONE
        inGame = false
        chooseClown()

        val r = root()
        r.addView(marqueeTitle(210, 140))
        r.addView(space(4))
        r.addView(
            Space(this),
            LinearLayout.LayoutParams(
                1,
                max(0, resources.displayMetrics.heightPixels / 8 - dp(58))
            )
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
        preview.post {
            // Remember the exact on-screen board position used by the ready
            // screen so gameplay can keep the board perfectly stationary.
            readyBoardTopPx = preview.top
        }

        r.addView(space(8))
        r.addView(subtitle("WATCH THE SQUEAKS.", 17f))
        r.addView(subtitle("REMEMBER THE PATTERN.", 17f))
        r.addView(subtitle("PUNCH IT BACK.", 17f))
        r.addView(space(8))
        // Keep the compact wooden signs side by side on the ready screen.
        // This preserves vertical room below them for future ad placement.
        val readyButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        readyButtons.addView(
            button("PUNCH IT!") { startNewGame() }.apply {
                (layoutParams as LinearLayout.LayoutParams).apply {
                    marginEnd = dp(4)
                    marginStart = dp(4)
                }
            }
        )
        readyButtons.addView(
            button("Back to Main") { showMenu() }.apply {
                (layoutParams as LinearLayout.LayoutParams).apply {
                    marginEnd = dp(4)
                    marginStart = dp(4)
                }
            }
        )
        r.addView(
            readyButtons,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(withCarnivalBackground(r))
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
        activeMode = ActiveGameMode.PUNCH
        showGame()
        addRound()
    }

    private fun showGame() {
        val r = root()
        r.addView(marqueeTitle(170, 113))

        statusText = title("WATCH", 24f)
        r.addView(space(6))
        r.addView(statusText)
        r.addView(space(4))

        // Keep the board, score panel, and quit control as one visual stack.
        // The whole stack is shifted together so the 900 x 900 board stays in
        // exactly the same place as it was on the ready screen.
        val playStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        board = ClownBoardView(this, currentClown, showGrid = true).apply {
            inputEnabled = false
            onCellPressed = { cell -> onPlayerTap(cell) }
        }
        playStack.addView(
            board,
            LinearLayout.LayoutParams(
                gameBoardSizePx,
                gameBoardSizePx
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )

        val hud = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 27, 18, 16))
                setStroke(dp(2), gold)
                cornerRadius = dp(12).toFloat()
            }
        }

        scoreText = hudItem("SCORE", "0")
        levelText = hudItem("LEVEL", "1")
        sequenceText = hudItem("SEQUENCE", "1")
        hud.addView(scoreText)
        hud.addView(levelText)
        hud.addView(sequenceText)

        playStack.addView(
            hud,
            LinearLayout.LayoutParams(
                gameBoardSizePx,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(8)
            }
        )

        val quitButton = button("Quit to Menu") {
            AlertDialog.Builder(this)
                .setTitle("Quit game?")
                .setMessage("Your current score will be lost.")
                .setPositiveButton("Quit") { _, _ -> quitToMenu() }
                .setNegativeButton("Keep punching", null)
                .show()
        }
        playStack.addView(quitButton)

        // Equal flexible space centers the gameplay stack when room permits.
        // Its final translation below locks the board to the ready-screen
        // position, while the score panel stays directly underneath it.
        r.addView(
            Space(this),
            LinearLayout.LayoutParams(1, 0, 1f)
        )
        r.addView(
            playStack,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        r.addView(
            Space(this),
            LinearLayout.LayoutParams(1, 0, 1f)
        )

        playStack.post {
            val boardTopInRoot = playStack.top + board.top
            if (readyBoardTopPx > 0) {
                playStack.translationY = (readyBoardTopPx - boardTopInRoot).toFloat()
            }
            scoreBarHeightPx = hud.height
        }

        setContentView(withCarnivalBackground(r))
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
        activeMode = ActiveGameMode.NONE
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
        setContentView(withCarnivalBackground(r))
    }

    private fun showBeaningReady() {
        sessionToken++
        handler.removeCallbacksAndMessages(null)
        audio.stopEventSequence()
        activeMode = ActiveGameMode.NONE
        inGame = false
        gameFinished = false
        pausedByLifecycle = false

        val root = FrameLayout(this).apply {
            setBackgroundColor(dark)
        }
        val preview = BeaningBoardView(this).apply {
            previewMode = true
            previewClown = 0
            previewSlot = 4
            contentDescription = "Beaning the Clowns ready screen with Bubbles"
        }
        root.addView(
            preview,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Blue mounted carnival placard, styled after the painted arrow signs
        // on the approved game-select artwork. The booth is not resized or moved.
        val instructions = BeaningCarnivalSignView(
            this,
            "HIT THE CLOWNS.\nDON'T HIT EMPTY SPOTS.\nTHREE MISSES AND YOU'RE OUT.",
            BeaningCarnivalSignView.Scheme.BLUE,
            mountedSolidly = true
        ).apply {
            visibility = View.INVISIBLE
            contentDescription = "Beaning the Clowns instructions"
        }
        root.addView(
            instructions,
            FrameLayout.LayoutParams(dp(280), dp(70)).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
        )

        val readyPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            translationY = dp(145).toFloat()
        }

        readyPanel.addView(beaningWoodButton("STEP RIGHT UP!", true) { startBeaningGame() })
        readyPanel.addView(beaningWoodButton("BACK TO GAME SELECT", false) { showMenu() })

        root.addView(
            readyPanel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        )
        setContentView(root)

        // BeaningBoardView renders the approved 941 x 1672 artwork without
        // changing its scale. Fit the placard into the unused top margin and
        // keep it somewhat narrower than the booth.
        root.post {
            val rootW = root.width.toFloat()
            val rootH = root.height.toFloat().coerceAtLeast(1f)
            val boardRatio = 941f / 1672f
            val rootRatio = rootW / rootH
            val boardTop = if (rootRatio > boardRatio) {
                0f
            } else {
                val boardHeight = rootW / boardRatio
                (rootH - boardHeight) / 2f
            }

            val signWidth = (root.width * 0.80f).toInt()
            val desiredHeight = (signWidth * 0.19f).toInt()
            val availableHeight = maxOf(1, (boardTop - dp(6)).toInt())
            val signHeight = minOf(desiredHeight, availableHeight)
            val originalTopMargin = maxOf(0, ((boardTop - signHeight) / 2f).toInt())
            val topMargin = originalTopMargin + (signHeight * 1.5f).toInt()

            instructions.layoutParams = FrameLayout.LayoutParams(
                signWidth,
                signHeight
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                this.topMargin = topMargin
            }
            instructions.visibility = View.VISIBLE
        }
    }

    private fun startBeaningGame() {
        sessionToken++
        handler.removeCallbacksAndMessages(null)
        audio.stopEventSequence()
        activeMode = ActiveGameMode.BEANING
        inGame = true
        gameFinished = false
        pausedByLifecycle = false

        beaningRoot = FrameLayout(this).apply {
            setBackgroundColor(dark)
        }
        beaningBoard = BeaningBoardView(this).apply {
            resetForNewGame()
            contentDescription = "Beaning the Clowns carnival game board"
            onHit = {
                audio.playBeanHit()
                haptic(28L, 85)
            }
            onMiss = {
                audio.playBeanMiss()
                haptic(82L, 180)
            }
            onGameOverStarted = {
                audio.playBeanGameOver()
            }
            onGameOverFinished = {
                finishBeaningGameAndShowResults()
            }
        }
        beaningRoot.addView(
            beaningBoard,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(beaningRoot)
        runBeaningReadyGo(resuming = false)
    }

    private fun runBeaningReadyGo(resuming: Boolean) {
        if (!::beaningRoot.isInitialized || !::beaningBoard.isInitialized) return
        sessionToken++
        val token = sessionToken
        handler.removeCallbacksAndMessages(null)

        val cue = TextView(this).apply {
            text = "READY"
            textSize = 52f
            setTextColor(cream)
            gravity = Gravity.CENTER
            setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
            setShadowLayer(8f, 0f, dp(2).toFloat(), Color.BLACK)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        beaningRoot.addView(
            cue,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(120)
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        handler.postDelayed({
            if (token != sessionToken || activeMode != ActiveGameMode.BEANING || gameFinished) return@postDelayed
            cue.text = "GO!"
        }, BeaningTuning.READY_MS)

        handler.postDelayed({
            if (token != sessionToken || activeMode != ActiveGameMode.BEANING || gameFinished) return@postDelayed
            beaningRoot.removeView(cue)
            if (resuming) beaningBoard.resumeAfterInterruption() else beaningBoard.startGame()
        }, BeaningTuning.READY_MS + BeaningTuning.GO_MS)
    }

    private fun showBeaningPauseDialog() {
        if (activeMode != ActiveGameMode.BEANING || !inGame || gameFinished) return
        if (::beaningBoard.isInitialized) beaningBoard.pauseForInterruption()
        AlertDialog.Builder(this)
            .setTitle("GAME PAUSED")
            .setMessage("Beaning the Clowns is paused.")
            .setPositiveButton("Resume") { _, _ -> runBeaningReadyGo(resuming = true) }
            .setNegativeButton("Quit") { _, _ -> quitToMenu() }
            .setCancelable(false)
            .show()
    }

    private fun finishBeaningGameAndShowResults() {
        if (gameFinished || !::beaningBoard.isInitialized) return
        gameFinished = true
        inGame = false
        activeMode = ActiveGameMode.NONE
        beaningBoard.stopGame()

        val oldHigh = prefs.getLong("beaning_high_score", 0L)
        val oldHighestLevel = prefs.getInt("beaning_highest_level", 0)
        val oldLongestRun = prefs.getInt("beaning_longest_run", 0)
        val newHigh = beaningBoard.score > oldHigh

        prefs.edit()
            .putLong("beaning_high_score", max(oldHigh, beaningBoard.score))
            .putInt("beaning_highest_level", max(oldHighestLevel, beaningBoard.level))
            .putInt("beaning_longest_run", max(oldLongestRun, beaningBoard.longestRun))
            .putLong("beaning_games_played", prefs.getLong("beaning_games_played", 0L) + 1L)
            .putLong("beaning_total_hits", prefs.getLong("beaning_total_hits", 0L) + beaningBoard.clownsHit)
            .putLong("beaning_total_misses", prefs.getLong("beaning_total_misses", 0L) + beaningBoard.misses)
            .apply()

        showBeaningResults(newHigh)
    }

    private fun showBeaningResults(newHigh: Boolean) {
        if (!::beaningRoot.isInitialized || !::beaningBoard.isInitialized) return

        beaningRoot.addView(
            View(this).apply {
                setBackgroundColor(Color.argb(138, 0, 0, 0))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = beaningWoodBackground(false)
        }
        panel.addView(TextView(this).apply {
            text = "THAT'S THREE MISSES!"
            textSize = 27f
            setTextColor(cream)
            gravity = Gravity.CENTER
            setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        })
        if (newHigh) {
            panel.addView(TextView(this).apply {
                text = "NEW HIGH SCORE!"
                textSize = 19f
                setTextColor(gold)
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(4), 0, dp(5))
            })
        }

        val bestScore = prefs.getLong("beaning_high_score", 0L)
        val totalMisses = prefs.getLong("beaning_total_misses", 0L)
        panel.addView(beaningResultLine("SCORE", "%,d".format(beaningBoard.score)))
        panel.addView(beaningResultLine("BEST SCORE", "%,d".format(bestScore)))
        panel.addView(beaningResultLine("LEVEL REACHED", beaningBoard.level.toString()))
        panel.addView(beaningResultLine("CLOWNS HIT", beaningBoard.clownsHit.toString()))
        panel.addView(beaningResultLine("LONGEST RUN", beaningBoard.longestRun.toString()))
        panel.addView(beaningResultLine("TOTAL MISSES", "%,d".format(totalMisses)))
        panel.addView(space(4))
        panel.addView(beaningWoodButton("BEAN AGAIN", true) { startBeaningGame() })
        panel.addView(beaningWoodButton("BACK TO GAME SELECT", false) { showMenu() })

        beaningRoot.addView(
            panel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                leftMargin = dp(28)
                rightMargin = dp(28)
            }
        )
    }

    private fun beaningResultLine(label: String, value: String): TextView = TextView(this).apply {
        text = "$label    $value"
        textSize = 18f
        setTextColor(if (label == "SCORE" || label == "BEST SCORE") gold else cream)
        gravity = Gravity.CENTER
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun beaningWoodBackground(primary: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(if (primary) Color.rgb(126, 42, 31) else Color.rgb(72, 41, 25))
        setStroke(dp(3), gold)
        cornerRadius = dp(10).toFloat()
    }

    private fun beaningWoodButton(
        text: String,
        primary: Boolean,
        onClick: () -> Unit
    ): BeaningCarnivalSignView {
        val label = when (text.trim().uppercase()) {
            "STEP RIGHT UP!" -> "STEP\nRIGHT UP!"
            "BEAN AGAIN" -> "BEAN\nAGAIN"
            else -> text.trim().uppercase()
        }
        return BeaningCarnivalSignView(
            this,
            label,
            BeaningCarnivalSignView.Scheme.RED,
            mountedSolidly = false
        ).apply {
            isClickable = true
            isFocusable = true
            contentDescription = text
            setOnClickListener { onClick() }
            // Beaning buttons are intentionally 25% larger than the prior
            // 0.3.x sizing while preserving their existing proportions.
            layoutParams = LinearLayout.LayoutParams(
                if (primary) 450 else 388,
                if (primary) 213 else 178
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(if (primary) 2 else 0)
            }
        }
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
        if (activeMode == ActiveGameMode.BEANING && ::beaningBoard.isInitialized) {
            beaningBoard.stopGame()
        }
        activeMode = ActiveGameMode.NONE
        inGame = false
        gameFinished = false
        pausedByLifecycle = false
        showMenu()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
