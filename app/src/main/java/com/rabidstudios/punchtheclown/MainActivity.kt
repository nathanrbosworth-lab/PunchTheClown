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
import android.util.Base64
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
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream
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
    private lateinit var localStats: LocalStatsRepository
    private lateinit var leaderboardGateway: LeaderboardGateway
    private var punchMarqueeFrame: CarnivalMarqueeFrameView? = null
    private var playGamesManualSignInAttempted = false
    private var playGamesConnectedNoticeShown = false

    private val cream = Color.rgb(247, 231, 198)
    private val gold = Color.rgb(232, 182, 75)
    private val red = Color.rgb(183, 38, 46)
    private val wood = Color.rgb(54, 31, 22)
    private val dark = Color.rgb(27, 18, 16)

    // Locked illustrated-art canvas and Punch play field. The clown subject is
    // authored inside the centered 720 x 720 safe-art area (90 px margins).
    private val gameBoardSizePx = PunchArtworkSpec.CANVAS_SIZE_PX

    // Approved six-button wooden sign sheet: 2 columns x 3 rows.
    // Apply the exact approved no-post pixel patch to the existing game asset.
    // This preserves the original artwork rather than re-rendering the signs.
    private val woodSignSheet: Bitmap by lazy {
        val source = BitmapFactory.decodeResource(resources, R.drawable.wood_sign_buttons)
        applyApprovedNoPostPatch(source)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = dark
        window.navigationBarColor = dark
        prefs = getSharedPreferences("punch_the_clown", MODE_PRIVATE)
        localStats = LocalStatsRepository(prefs)
        leaderboardGateway = PlayGamesLeaderboardGateway(this)
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
                ActiveGameMode.PUNCH -> {
                    if (::board.isInitialized) board.inputEnabled = false
                    punchMarqueeFrame?.setMode(CarnivalLightMode.PAUSED)
                }
                ActiveGameMode.BEANING -> if (::beaningBoard.isInitialized) beaningBoard.pauseForInterruption()
                ActiveGameMode.NONE -> Unit
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::leaderboardGateway.isInitialized) {
            refreshPlayGamesAuthentication()
        }
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

    private fun applyApprovedNoPostPatch(source: Bitmap): Bitmap {
        require(source.width == 720 && source.height == 510) {
            "Unexpected Punch sign sheet dimensions: ${source.width}x${source.height}"
        }

        val encoded = resources.openRawResource(R.raw.wood_sign_no_posts_patch)
            .bufferedReader()
            .use { it.readText().trim() }
        val compressed = Base64.decode(encoded, Base64.DEFAULT)
        val bytes = InflaterInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val patched = source.copy(Bitmap.Config.ARGB_8888, true)
        val runCount = data.int
        repeat(runCount) {
            val y = data.short.toInt() and 0xffff
            val x = data.short.toInt() and 0xffff
            val length = data.short.toInt() and 0xffff
            val pixels = IntArray(length)
            for (i in 0 until length) {
                val red = data.get().toInt() and 0xff
                val green = data.get().toInt() and 0xff
                val blue = data.get().toInt() and 0xff
                val alpha = data.get().toInt() and 0xff
                pixels[i] = Color.argb(alpha, red, green, blue)
            }
            patched.setPixels(pixels, 0, length, x, y, length, 1)
        }
        return patched
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

    private fun framedPunchBoard(
        boardView: ClownBoardView,
        mode: CarnivalLightMode
    ): FrameLayout {
        val frame = CarnivalMarqueeFrameView(this).apply {
            animationsEnabled = prefs.getBoolean("carnival_lights_enabled", true)
            setMode(mode)
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        punchMarqueeFrame = frame

        return FrameLayout(this).apply {
            addView(
                boardView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                frame,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun showSplash() {
        inGame = false
        gameFinished = false

        val image = ImageView(this).apply {
            setImageResource(R.drawable.rabid_studios_splash)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = false
            setBackgroundColor(Color.BLACK)
            contentDescription = "Rabid Studios"
        }
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

        val leaderboardBalloon = LeaderboardBalloonButton(this).apply {
            setOnClickListener {
                showOnlineLeaderboards(GameMode.PUNCH, forceReload = true)
            }
        }

        root.addView(punchTheClownSign, FrameLayout.LayoutParams(1, 1))
        root.addView(beaningTheClownsSign, FrameLayout.LayoutParams(1, 1))
        root.addView(statsSettingsSign, FrameLayout.LayoutParams(1, 1))
        root.addView(leaderboardBalloon, FrameLayout.LayoutParams(1, 1))
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

            // Standalone carnival balloon for online rankings. This occupies
            // the unused upper-left of the approved Game Select artwork and
            // does not alter the three painted guidepost hit regions.
            placeSign(leaderboardBalloon, 0.035f, 0.055f, 0.300f, 0.235f)
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

        r.addView(statsSectionSign("PUNCH THE CLOWN"))
        r.addView(space(4))
        val punchStats = localStats.punchStats()

        r.addView(statLine("BEST SCORE", "%,d".format(punchStats.highScore)))
        r.addView(statLine("HIGHEST LEVEL", punchStats.highestLevel.toString()))
        r.addView(statLine("LONGEST SEQUENCE", punchStats.longestSequence.toString()))
        r.addView(statLine("GAMES PLAYED", "%,d".format(punchStats.gamesPlayed)))
        r.addView(statLine("CORRECT PUNCHES", "%,d".format(punchStats.correctPunches)))
        r.addView(statLine("SEQUENCES COMPLETED", "%,d".format(punchStats.sequencesCompleted)))

        r.addView(space(16))
        r.addView(statsSectionSign("BEANING THE CLOWNS"))
        r.addView(space(4))
        val beaningStats = localStats.beaningStats()
        r.addView(statLine("BEST SCORE", "%,d".format(beaningStats.highScore)))
        r.addView(statLine("HIGHEST LEVEL", beaningStats.highestLevel.toString()))
        r.addView(statLine("TOTAL CLOWNS HIT", "%,d".format(beaningStats.totalHits)))
        r.addView(statLine("TOTAL MISSES", "%,d".format(beaningStats.totalMisses)))
        r.addView(statLine("GAMES PLAYED", "%,d".format(beaningStats.gamesPlayed)))
        r.addView(statLine("LONGEST RUN", beaningStats.longestRun.toString()))

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

        val carnivalLightsSwitch = Switch(this).apply {
            text = "Carnival Lights"
            textSize = 18f
            setTextColor(cream)
            isChecked = prefs.getBoolean("carnival_lights_enabled", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("carnival_lights_enabled", checked).apply()
                punchMarqueeFrame?.animationsEnabled = checked
            }
        }
        r.addView(
            carnivalLightsSwitch,
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

    private fun showOnlineLeaderboards(
        mode: GameMode,
        forceReload: Boolean = false
    ) {
        activeMode = ActiveGameMode.NONE
        inGame = false
        gameFinished = false
        audio.stopEventSequence()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }

        content.addView(leaderboardMarqueeTitle())
        content.addView(space(10))

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        modeRow.addView(
            leaderboardModeButton(
                label = "PUNCH",
                selected = mode == GameMode.PUNCH
            ) {
                showOnlineLeaderboards(GameMode.PUNCH, forceReload = true)
            },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                marginEnd = dp(4)
            }
        )
        modeRow.addView(
            leaderboardModeButton(
                label = "BEANING",
                selected = mode == GameMode.BEANING
            ) {
                showOnlineLeaderboards(GameMode.BEANING, forceReload = true)
            },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                marginStart = dp(4)
            }
        )
        content.addView(
            modeRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(space(12))
        val status = TextView(this).apply {
            text = "LOADING..."
            textSize = 16f
            setTextColor(gold)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        content.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        content.addView(
            results,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(space(12))
        content.addView(
            leaderboardRefreshSign {
                showOnlineLeaderboards(mode, forceReload = true)
            }
        )
        content.addView(space(8))
        content.addView(button("Back to Game Select") { showMenu() })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        setContentView(withCarnivalBackground(scroll))

        fun loadWhenAuthenticated() {
            status.text = "LOADING..."
            results.removeAllViews()
            leaderboardGateway.loadSnapshot(
                mode = mode,
                forceReload = forceReload
            ) { snapshot, error ->
                runOnUiThread {
                    if (snapshot == null) {
                        status.text = "COULDN'T LOAD LEADERBOARD"
                        results.removeAllViews()
                        results.addView(
                            leaderboardMessage(
                                error?.message ?: "Play Games returned no leaderboard data."
                            )
                        )
                    } else {
                        status.text = "CONNECTED TO GOOGLE PLAY GAMES"
                        renderLeaderboardSnapshot(results, snapshot)
                    }
                }
            }
        }

        when (leaderboardGateway.authState) {
            LeaderboardAuthState.AUTHENTICATED -> loadWhenAuthenticated()

            LeaderboardAuthState.UNCONFIGURED -> {
                status.text = "PLAY GAMES NOT CONFIGURED"
                results.addView(
                    leaderboardMessage("Online leaderboards are not configured in this build.")
                )
            }

            else -> {
                status.text = "SIGNING IN TO GOOGLE PLAY GAMES..."
                leaderboardGateway.requestSignIn { state ->
                    runOnUiThread {
                        if (state == LeaderboardAuthState.AUTHENTICATED) {
                            loadWhenAuthenticated()
                        } else {
                            status.text = "SIGN-IN REQUIRED"
                            results.removeAllViews()
                            results.addView(
                                leaderboardMessage(
                                    "Sign in to Google Play Games to view online rankings."
                                )
                            )
                            results.addView(space(8))
                            results.addView(
                                leaderboardNavButton("SIGN IN TO PLAY GAMES") {
                                    showOnlineLeaderboards(mode, forceReload = true)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun renderLeaderboardSnapshot(
        container: LinearLayout,
        snapshot: LeaderboardSnapshot
    ) {
        container.removeAllViews()

        container.addView(space(12))
        container.addView(leaderboardSectionTitle("YOUR RANK"))
        val player = snapshot.playerScore
        if (player == null) {
            container.addView(
                leaderboardMessage("No online score submitted yet.")
            )
        } else {
            container.addView(leaderboardScoreRow(player))
        }

        container.addView(space(16))
        container.addView(leaderboardSectionTitle("TOP 50"))
        if (snapshot.top50.isEmpty()) {
            container.addView(
                leaderboardMessage("No public scores are available yet.")
            )
        } else {
            snapshot.top50.forEach { score ->
                container.addView(leaderboardScoreRow(score))
            }
        }

        container.addView(space(16))
        container.addView(leaderboardSectionTitle("AROUND YOU"))
        if (snapshot.nearbyScores.isEmpty()) {
            container.addView(
                leaderboardMessage("No nearby rankings are available yet.")
            )
        } else {
            snapshot.nearbyScores.forEach { score ->
                container.addView(leaderboardScoreRow(score))
            }
        }
    }

    private fun leaderboardMarqueeTitle(): FrameLayout {
        val signWidth = (resources.displayMetrics.widthPixels * 0.90f).toInt()
        val signHeight = (signWidth * 0.30f).toInt()

        return FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.rgb(113, 28, 30))
                setStroke(dp(5), Color.rgb(94, 49, 23))
                cornerRadius = dp(14).toFloat()
            }

            val inner = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(36), dp(15), dp(36), dp(15))
                addView(TextView(this@MainActivity).apply {
                    text = "ONLINE LEADERBOARDS"
                    textSize = 25f
                    setTextColor(cream)
                    gravity = Gravity.CENTER
                    setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
                    setShadowLayer(dp(3).toFloat(), 0f, dp(2).toFloat(), Color.rgb(56, 20, 16))
                })
                addView(TextView(this@MainActivity).apply {
                    text = "ALL-TIME PUBLIC SCORES"
                    textSize = 12f
                    setTextColor(gold)
                    gravity = Gravity.CENTER
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
            }
            addView(
                inner,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            addView(
                object : View(this@MainActivity) {
                    private val bulbPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    private val socketPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(75, 39, 19)
                    }

                    override fun onDraw(canvas: android.graphics.Canvas) {
                        super.onDraw(canvas)
                        val w = width.toFloat()
                        val h = height.toFloat()
                        val r = minOf(w, h) * 0.032f
                        val topY = h * 0.10f
                        val bottomY = h * 0.90f
                        val leftX = w * 0.035f
                        val rightX = w * 0.965f

                        fun bulb(x: Float, y: Float, warm: Boolean) {
                            canvas.drawCircle(x, y, r * 1.35f, socketPaint)
                            bulbPaint.color = if (warm) Color.rgb(255, 184, 55) else Color.rgb(255, 241, 190)
                            canvas.drawCircle(x, y, r, bulbPaint)
                            bulbPaint.color = Color.argb(185, 255, 255, 255)
                            canvas.drawCircle(x - r * 0.24f, y - r * 0.24f, r * 0.25f, bulbPaint)
                        }

                        val across = 9
                        for (i in 0 until across) {
                            val x = w * (0.08f + i * (0.84f / (across - 1)))
                            bulb(x, topY, i % 2 == 0)
                            bulb(x, bottomY, i % 2 != 0)
                        }
                        bulb(leftX, h * 0.33f, true)
                        bulb(leftX, h * 0.67f, false)
                        bulb(rightX, h * 0.33f, false)
                        bulb(rightX, h * 0.67f, true)
                    }
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "Online Leaderboards. All-time public scores."
            layoutParams = LinearLayout.LayoutParams(signWidth, signHeight).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
    }

    private fun leaderboardSectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 20f
        setTextColor(gold)
        gravity = Gravity.CENTER
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun leaderboardMessage(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(cream)
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(8), dp(6), dp(8))
    }

    private fun leaderboardScoreRow(score: RankedScore): TextView = TextView(this).apply {
        val rankText = score.displayRank.ifBlank {
            if (score.rank > 0L) "#${score.rank}" else "—"
        }
        text = "$rankText   ${score.displayName}   ${score.formattedScore}"
        textSize = if (score.isCurrentPlayer) 18f else 16f
        setTextColor(if (score.isCurrentPlayer) gold else cream)
        gravity = Gravity.CENTER_VERTICAL
        setTypeface(
            typeface,
            if (score.isCurrentPlayer) {
                android.graphics.Typeface.BOLD
            } else {
                android.graphics.Typeface.NORMAL
            }
        )
        setPadding(dp(8), dp(7), dp(8), dp(7))
        background = GradientDrawable().apply {
            setColor(
                if (score.isCurrentPlayer) {
                    Color.argb(190, 92, 51, 25)
                } else {
                    Color.argb(135, 37, 24, 18)
                }
            )
            setStroke(
                dp(1),
                if (score.isCurrentPlayer) gold else Color.argb(150, 232, 182, 75)
            )
            cornerRadius = dp(6).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(3)
        }
    }

    private fun leaderboardModeButton(
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ): Button = Button(this).apply {
        text = label
        textSize = 16f
        setTextColor(if (selected) dark else cream)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        background = GradientDrawable().apply {
            setColor(if (selected) gold else Color.rgb(92, 51, 25))
            setStroke(dp(2), gold)
            cornerRadius = dp(8).toFloat()
        }
        setOnClickListener { onClick() }
    }

    private fun leaderboardRefreshSign(
        onClick: () -> Unit
    ): ImageButton {
        val encoded = "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"
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val signWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
        val signHeight = (signWidth * bitmap.height.toFloat() / bitmap.width.toFloat()).toInt()

        return ImageButton(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = false
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            isClickable = true
            isFocusable = true
            contentDescription = "Refresh online leaderboard"
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(signWidth, signHeight).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
    }

    private fun leaderboardNavButton(
        label: String,
        onClick: () -> Unit
    ): Button = Button(this).apply {
        text = label
        textSize = 16f
        setTextColor(cream)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        background = beaningWoodBackground(primary = true)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54)
        ).apply {
            leftMargin = dp(18)
            rightMargin = dp(18)
        }
    }

    private fun statsSectionSign(label: String): BeaningCarnivalSignView {
        val signWidth = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        val signHeight = (signWidth * 0.19f).toInt()
        return BeaningCarnivalSignView(
            this,
            label,
            BeaningCarnivalSignView.Scheme.BLUE,
            mountedSolidly = true,
            bothEndsPointed = true,
            weathered = true
        ).apply {
            contentDescription = "$label stats section"
            layoutParams = LinearLayout.LayoutParams(signWidth, signHeight).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
    }

    private fun statLine(label: String, value: String): TextView = TextView(this).apply {
        text = "$label    $value"
        textSize = 14f
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
        val framedPreview = framedPunchBoard(preview, CarnivalLightMode.READY)
        r.addView(
            framedPreview,
            LinearLayout.LayoutParams(
                gameBoardSizePx,
                gameBoardSizePx
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )
        framedPreview.post {
            // Remember the exact on-screen framed-board position used by the
            // ready screen so gameplay keeps both board and marquee stationary.
            readyBoardTopPx = framedPreview.top
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
        val framedBoard = framedPunchBoard(board, CarnivalLightMode.WATCH)
        playStack.addView(
            framedBoard,
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
            val boardTopInRoot = playStack.top + framedBoard.top
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
        punchMarqueeFrame?.setMode(CarnivalLightMode.WATCH)

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
            punchMarqueeFrame?.setMode(CarnivalLightMode.PLAYER_TURN)
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
            punchMarqueeFrame?.flashWrong()
            val willBeNewHigh = score > localStats.punchStats().highScore
            audio.playWrongThenWin(willBeNewHigh)

            handler.postDelayed({
                longestSequence = max(longestSequence, sequence.size - 1)
                finishGameAndShowResults()
            }, 950L)
            return
        }

        audio.playGrid(cell)
        haptic(28L, 85)
        punchMarqueeFrame?.flashCorrect()
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
            punchMarqueeFrame?.celebrateSequence()

            val token = sessionToken
            handler.postDelayed({
                if (token == sessionToken && inGame && !gameFinished) addRound()
            }, 600L)
        }
    }

    private fun finishGameAndShowResults() {
        val result = GameResult(
            mode = GameMode.PUNCH,
            score = score,
            level = level,
            longestRun = longestSequence,
            hits = correctInputs,
            completedRounds = completedSequences
        )
        val newHigh = localStats.record(result)
        queueAndSubmitOnlineScore(result)

        if (newHigh) {
            haptic(180L, 165)
        }
        showResults(newHigh)
    }

    private fun showResults(newHigh: Boolean) {
        inGame = false
        activeMode = ActiveGameMode.NONE
        val punchStats = localStats.punchStats()
        val best = punchStats.highScore
        val bestLongestSequence = punchStats.longestSequence
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

        // Approved Punch-style Beaning instruction artwork.
        // The booth, placard position, and placard dimensions remain unchanged.
        val instructions = ImageView(this).apply {
            setImageResource(R.drawable.beaning_sign_instructions)
            scaleType = ImageView.ScaleType.FIT_XY
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

        val result = GameResult(
            mode = GameMode.BEANING,
            score = beaningBoard.score,
            level = beaningBoard.level,
            longestRun = beaningBoard.longestRun,
            hits = beaningBoard.clownsHit,
            misses = beaningBoard.misses
        )
        val newHigh = localStats.record(result)
        queueAndSubmitOnlineScore(result)

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

        val beaningStats = localStats.beaningStats()
        val bestScore = beaningStats.highScore
        val totalMisses = beaningStats.totalMisses
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
    ): ImageView {
        val drawableRes = when (text.trim().uppercase()) {
            "STEP RIGHT UP!" -> R.drawable.beaning_sign_step_right_up
            "BEAN AGAIN" -> R.drawable.beaning_sign_bean_again
            "BACK TO GAME SELECT" -> R.drawable.beaning_sign_back_to_game_select
            else -> error("Unknown Beaning button: $text")
        }
        return ImageView(this).apply {
            setImageResource(drawableRes)
            scaleType = ImageView.ScaleType.FIT_XY
            isClickable = true
            isFocusable = true
            contentDescription = text
            setOnClickListener { onClick() }
            // Preserve the approved 0.3.8 sign dimensions.
            layoutParams = LinearLayout.LayoutParams(
                if (primary) 563 else 485,
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

    private fun refreshPlayGamesAuthentication() {
        leaderboardGateway.refreshAuthentication { state ->
            when (state) {
                LeaderboardAuthState.AUTHENTICATED -> {
                    if (!playGamesConnectedNoticeShown) {
                        playGamesConnectedNoticeShown = true
                        Toast.makeText(
                            this,
                            "Google Play Games connected",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    flushPendingOnlineScores()
                }

                LeaderboardAuthState.SIGNED_OUT -> {
                    if (!playGamesManualSignInAttempted) {
                        playGamesManualSignInAttempted = true
                        leaderboardGateway.requestSignIn { signInState ->
                            if (signInState == LeaderboardAuthState.AUTHENTICATED) {
                                playGamesConnectedNoticeShown = true
                                Toast.makeText(
                                    this,
                                    "Google Play Games connected",
                                    Toast.LENGTH_SHORT
                                ).show()
                                flushPendingOnlineScores()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Google Play Games sign-in not completed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }

                LeaderboardAuthState.ERROR -> {
                    Toast.makeText(
                        this,
                        "Google Play Games is unavailable",
                        Toast.LENGTH_LONG
                    ).show()
                }

                LeaderboardAuthState.UNCONFIGURED,
                LeaderboardAuthState.CHECKING -> Unit
            }
        }
    }

    private fun queueAndSubmitOnlineScore(result: GameResult) {
        if (result.score <= 0L) return

        localStats.queuePendingOnlineScore(result.mode, result.score)

        if (leaderboardGateway.authState == LeaderboardAuthState.AUTHENTICATED) {
            submitPendingOnlineScore(result.mode, showFeedback = true)
        } else {
            Toast.makeText(
                this,
                "${leaderboardLabel(result.mode)} score %,d saved for online retry"
                    .format(result.score),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun flushPendingOnlineScores() {
        if (leaderboardGateway.authState != LeaderboardAuthState.AUTHENTICATED) return
        submitPendingOnlineScore(GameMode.PUNCH, showFeedback = false)
        submitPendingOnlineScore(GameMode.BEANING, showFeedback = false)
    }

    private fun submitPendingOnlineScore(
        mode: GameMode,
        showFeedback: Boolean
    ) {
        val pendingScore = localStats.pendingOnlineScore(mode)
        if (pendingScore <= 0L) return

        leaderboardGateway.submitScore(mode, pendingScore) { success ->
            if (success) {
                localStats.clearPendingOnlineScore(mode, pendingScore)
                if (showFeedback) {
                    Toast.makeText(
                        this,
                        "${leaderboardLabel(mode)} leaderboard score submitted: %,d"
                            .format(pendingScore),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else if (showFeedback) {
                Toast.makeText(
                    this,
                    "${leaderboardLabel(mode)} leaderboard submission failed — %,d saved for retry"
                        .format(pendingScore),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun leaderboardLabel(mode: GameMode): String = when (mode) {
        GameMode.PUNCH -> "Punch"
        GameMode.BEANING -> "Beaning"
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
