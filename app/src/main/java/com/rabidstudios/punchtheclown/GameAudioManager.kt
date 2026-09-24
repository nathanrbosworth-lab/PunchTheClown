package com.rabidstudios.punchtheclown

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

class GameAudioManager(context: Context) {
    private val appContext = context.applicationContext
    private val pool: SoundPool
    private val gridSounds: IntArray
    private var eventPlayer: MediaPlayer? = null

    var enabled: Boolean = true

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(attributes)
            .build()

        gridSounds = intArrayOf(
            pool.load(context, R.raw.grid_01_clown_car, 1),
            pool.load(context, R.raw.grid_02_freesound_squeak, 1),
            pool.load(context, R.raw.grid_03_squeak_3, 1),
            pool.load(context, R.raw.grid_04_squeak_2, 1),
            pool.load(context, R.raw.grid_05_squeak_1, 1),
            pool.load(context, R.raw.grid_06_baby_1, 1),
            pool.load(context, R.raw.grid_07_baby_2, 1),
            pool.load(context, R.raw.grid_08_tiny_1, 1),
            pool.load(context, R.raw.grid_09_baby_4, 1)
        )
    }

    fun playGrid(cell: Int) {
        if (!enabled) return
        if (cell in gridSounds.indices) {
            pool.play(gridSounds[cell], 1f, 1f, 1, 0, 1f)
        }
    }

    fun playWrongThenWin(playWinAfter: Boolean) {
        if (!enabled) return
        stopEventSequence()

        val wrongPlayer = MediaPlayer.create(
            appContext,
            R.raw.wrong_square_silly_trumpet_11
        ) ?: return

        eventPlayer = wrongPlayer
        wrongPlayer.setOnCompletionListener { completed ->
            completed.setOnCompletionListener(null)
            completed.release()
            if (eventPlayer === completed) eventPlayer = null

            if (playWinAfter && enabled) {
                playWin()
            }
        }
        wrongPlayer.start()
    }

    fun playWin() {
        if (!enabled) return
        stopEventSequence()

        val winPlayer = MediaPlayer.create(
            appContext,
            R.raw.win_silly_trumpet_2
        ) ?: return

        eventPlayer = winPlayer
        winPlayer.setOnCompletionListener { completed ->
            completed.setOnCompletionListener(null)
            completed.release()
            if (eventPlayer === completed) eventPlayer = null
        }
        winPlayer.start()
    }

    fun stopEventSequence() {
        eventPlayer?.let { player ->
            runCatching {
                player.setOnCompletionListener(null)
                if (player.isPlaying) player.stop()
            }
            player.release()
        }
        eventPlayer = null
    }

    fun release() {
        stopEventSequence()
        pool.release()
    }
}
