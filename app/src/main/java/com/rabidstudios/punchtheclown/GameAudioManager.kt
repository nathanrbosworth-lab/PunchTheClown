package com.rabidstudios.punchtheclown

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class GameAudioManager(context: Context) {
    private val pool: SoundPool
    private val gridSounds: IntArray
    private val wrongSound: Int
    private val winSound: Int

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(5).setAudioAttributes(attributes).build()

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
        wrongSound = pool.load(context, R.raw.wrong_square_silly_trumpet_11, 1)
        winSound = pool.load(context, R.raw.win_silly_trumpet_2, 1)
    }

    fun playGrid(cell: Int) {
        if (cell in gridSounds.indices) pool.play(gridSounds[cell], 1f, 1f, 1, 0, 1f)
    }

    fun playWrong() = pool.play(wrongSound, 1f, 1f, 2, 0, 1f)
    fun playWin() = pool.play(winSound, 1f, 1f, 2, 0, 1f)
    fun release() = pool.release()
}
