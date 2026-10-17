package com.miguel.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class KeyboardPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var paletteName = "black"
    private var heightPercent = 100
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyRect = RectF()

    private data class Palette(val background: Int, val key: Int, val text: Int, val active: Int, val enter: Int = active)

    fun updateSettings(color: String, height: Int) {
        paletteName = color
        heightPercent = height.coerceIn(70, 100)
        invalidate()
    }

    private fun palette(): Palette = when (paletteName) {
        "bubblegum" -> Palette(Color.rgb(255, 183, 213), Color.rgb(244, 143, 177), Color.rgb(45, 20, 30), Color.rgb(255, 105, 160))
        "olive" -> Palette(Color.rgb(107, 125, 50), Color.rgb(85, 107, 47), Color.WHITE, Color.rgb(170, 195, 80))
        "yellow" -> Palette(Color.rgb(255, 242, 166), Color.rgb(244, 211, 94), Color.rgb(55, 45, 10), Color.rgb(210, 165, 0))
        "cloud" -> Palette(Color.rgb(245, 245, 240), Color.WHITE, Color.rgb(25, 25, 25), Color.rgb(120, 120, 120))
        "ferrari" -> Palette(Color.rgb(255, 40, 0), Color.rgb(217, 30, 0), Color.WHITE, Color.rgb(255, 105, 75), Color.rgb(255, 215, 0))
        "nezuko" -> Palette(Color.rgb(55, 18, 28), Color.rgb(245, 145, 190), Color.rgb(55, 20, 30), Color.rgb(230, 70, 95))
        "transparent" -> Palette(Color.TRANSPARENT, Color.argb(175, 20, 20, 20), Color.WHITE, Color.rgb(255, 70, 70))
        "sky" -> Palette(Color.rgb(135, 206, 235), Color.rgb(93, 173, 226), Color.rgb(15, 35, 45), Color.rgb(0, 191, 255))
        else -> Palette(Color.BLACK, Color.rgb(17, 17, 17), Color.WHITE, Color.RED)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = palette()
        if (p.background != Color.TRANSPARENT) canvas.drawColor(p.background) else canvas.drawColor(Color.TRANSPARENT)

        val scale = heightPercent / 100f
        val outer = min(width - dp(16), dp(430)).toFloat()
        val keyboardHeight = dp(170) * scale
        val left = (width - outer) / 2f
        val top = height - keyboardHeight - dp(8)
        val rowGap = dp(3).toFloat()
        val side = dp(3).toFloat()
        val keyHeight = (keyboardHeight - rowGap * 4 - side * 2) / 4f

        drawRow(canvas, listOf("Q","W","E","R","T","Y","U","I","O","P"), top, outer, left, keyHeight, p, 1f)
        drawRow(canvas, listOf("A","S","D","F","G","H","J","K","L","Ñ"), top + keyHeight + rowGap, outer, left, keyHeight, p, 1f)
        drawRow(canvas, listOf("Z","X","C","V","B","N","M"), top + (keyHeight + rowGap) * 2, outer, left, keyHeight, p, 0.7f)

        val y = top + (keyHeight + rowGap) * 3
        drawKey(canvas, left, y, outer * .115f - side, keyHeight, "⇧", p)
        drawKey(canvas, left + outer * .115f + side, y, outer * .115f - side, keyHeight, "123", p)
        drawKey(canvas, left + outer * .235f, y, outer * .26f, keyHeight, "ESPACIO", p)
        drawKey(canvas, left + outer * .505f, y, outer * .24f, keyHeight, "⏎", p, if (paletteName == "ferrari") p.enter else p.key)
    }

    private fun drawRow(canvas: Canvas, keys: List<String>, y: Float, width: Float, left: Float, height: Float, p: Palette, widthFactor: Float) {
        val gap = dp(3).toFloat()
        val total = width - gap * (keys.size - 1)
        val keyWidth = total / keys.size * widthFactor
        val actualTotal = keyWidth * keys.size + gap * (keys.size - 1)
        val start = left + (width - actualTotal) / 2f
        keys.forEachIndexed { index, key ->
            drawKey(canvas, start + index * (keyWidth + gap), y, keyWidth, height, key, p)
        }
    }

    private fun drawKey(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, label: String, p: Palette, fillOverride: Int? = null) {
        keyRect.set(x, y, x + width, y + height)
        paint.style = Paint.Style.FILL
        paint.color = fillOverride ?: p.key
        canvas.drawRoundRect(keyRect, dp(6).toFloat(), dp(6).toFloat(), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1).toFloat()
        paint.color = if (p.background == Color.BLACK) Color.rgb(35, 35, 35) else p.active
        canvas.drawRoundRect(keyRect, dp(6).toFloat(), dp(6).toFloat(), paint)
        paint.style = Paint.Style.FILL
        paint.color = p.text
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = if (label.length > 3) dp(7).toFloat() else dp(12).toFloat()
        val metrics = paint.fontMetrics
        val baseline = y + height / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(label, x + width / 2f, baseline, paint)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
