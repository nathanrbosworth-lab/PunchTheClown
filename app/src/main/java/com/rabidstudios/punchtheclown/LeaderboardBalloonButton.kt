package com.rabidstudios.punchtheclown

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView

/**
 * Painted carnival balloon control used to open the online leaderboard from
 * Game Select. The approved artwork is packaged as base64 in res/raw so the
 * repository and CI remain text-safe while the runtime view displays the
 * original transparent WebP.
 */
class LeaderboardBalloonButton(context: Context) : ImageButton(context) {

    private var floatAnimator: AnimatorSet? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(0, 0, 0, 0)
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = false
        isClickable = true
        isFocusable = true
        contentDescription = "Online Leaderboards"

        val encoded = resources.openRawResource(R.raw.leaderboard_balloon)
            .bufferedReader()
            .use { it.readText() }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { startFloatingAnimation() }
    }

    override fun onDetachedFromWindow() {
        floatAnimator?.cancel()
        floatAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pivotX = w / 2f
        // Pivot near the balloon knot so the small rotation reads as a tethered sway.
        pivotY = h * 0.78f
    }

    private fun startFloatingAnimation() {
        floatAnimator?.cancel()

        val easing = AccelerateDecelerateInterpolator()
        val bob = ObjectAnimator.ofFloat(
            this,
            View.TRANSLATION_Y,
            0f,
            -dp(10f)
        ).apply {
            duration = 1600L
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = easing
        }

        val sway = ObjectAnimator.ofFloat(
            this,
            View.ROTATION,
            -3.5f,
            3.5f
        ).apply {
            duration = 1600L
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = easing
        }

        floatAnimator = AnimatorSet().apply {
            playTogether(bob, sway)
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate()
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(80L)
                    .start()
            }

            MotionEvent.ACTION_UP -> {
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120L)
                    .start()
                if (isEnabled) performClick()
            }

            MotionEvent.ACTION_CANCEL -> {
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120L)
                    .start()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
