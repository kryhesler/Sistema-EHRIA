package com.example.detectormovimiento

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class SismografoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maxPoints = 200
    private val puntos = FloatArray(maxPoints)
    private var headIndex = 0
    private var totalPuntosCount = 0

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#223344")
        strokeWidth = 1f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.parseColor("#00E676")
        strokeWidth = 4f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val path = Path()

    fun agregarPunto(valor: Float) {
        puntos[headIndex] = valor
        headIndex = (headIndex + 1) % maxPoints
        if (totalPuntosCount < maxPoints) {
            totalPuntosCount++
        }
        invalidate()
    }

    fun setStrokeColor(hexColor: String) {
        linePaint.color = Color.parseColor(hexColor)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2f

        // Cuadrícula tipo papel milimetrado
        val gridSpacing = 20f * resources.displayMetrics.density
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height, gridPaint)
            x += gridSpacing
        }
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width, y, gridPaint)
            y += gridSpacing
        }

        if (totalPuntosCount < 2) return

        path.reset()
        val stepX = width / (maxPoints - 1)
        val startIndex = if (totalPuntosCount < maxPoints) 0 else headIndex
        val scaleY = (height / 2f - 20f) / 2.5f

        for (i in 0 until totalPuntosCount) {
            val index = (startIndex + i) % maxPoints
            val value = puntos[index]
            val px = i * stepX
            val py = centerY - (value * scaleY).coerceIn(-height / 2f + 10f, height / 2f - 10f)

            if (i == 0) {
                path.moveTo(px, py)
            } else {
                path.lineTo(px, py)
            }
        }

        canvas.drawPath(path, linePaint)
    }
}