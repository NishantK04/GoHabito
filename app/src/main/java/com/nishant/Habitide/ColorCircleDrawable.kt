package com.nishant.Habitide

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class ColorCircleDrawable(private val color: Int) : Drawable() {

    private val paint = Paint().apply {
        isAntiAlias = true
        this.color = this@ColorCircleDrawable.color
    }

    override fun draw(canvas: Canvas) {
        val radius = Math.min(bounds.width(), bounds.height()) / 2f
        val cx = bounds.centerX().toFloat()
        val cy = bounds.centerY().toFloat()
        canvas.drawCircle(cx, cy, radius, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }
}
