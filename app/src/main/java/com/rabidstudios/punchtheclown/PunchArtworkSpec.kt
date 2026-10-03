package com.rabidstudios.punchtheclown

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * Locked production geometry for illustrated Punch the Clown artwork.
 *
 * Every illustrated clown asset remains a 900 x 900 canvas. The clown and all
 * critical props must be authored inside the centered 720 x 720 safe-art box,
 * leaving 90 pixels of composition margin on every side for the locked ornate
 * frame.
 */
object PunchArtworkSpec {
    const val CANVAS_SIZE_PX = 900
    const val SAFE_ART_SIZE_PX = 720
    const val SAFE_MARGIN_PX = (CANVAS_SIZE_PX - SAFE_ART_SIZE_PX) / 2

    const val SAFE_LEFT_PX = SAFE_MARGIN_PX
    const val SAFE_TOP_PX = SAFE_MARGIN_PX
    const val SAFE_RIGHT_PX = SAFE_LEFT_PX + SAFE_ART_SIZE_PX
    const val SAFE_BOTTOM_PX = SAFE_TOP_PX + SAFE_ART_SIZE_PX

    fun requireValidCanvas(bitmap: Bitmap) {
        require(
            bitmap.width == CANVAS_SIZE_PX &&
                bitmap.height == CANVAS_SIZE_PX
        ) {
            "Punch illustrated clown assets must be " + CANVAS_SIZE_PX + "x" + CANVAS_SIZE_PX +
                "; received " + bitmap.width + "x" + bitmap.height + "."
        }
    }

    fun safeArtRectForView(viewWidth: Int, viewHeight: Int): RectF {
        val sx = viewWidth.toFloat() / CANVAS_SIZE_PX
        val sy = viewHeight.toFloat() / CANVAS_SIZE_PX
        return RectF(
            SAFE_LEFT_PX * sx,
            SAFE_TOP_PX * sy,
            SAFE_RIGHT_PX * sx,
            SAFE_BOTTOM_PX * sy
        )
    }
}
