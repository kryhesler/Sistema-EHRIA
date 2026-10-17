package com.example.detectormovimiento

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
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

    /** Valor que llega al borde de la gráfica (m/s²). */
    var rango = 2.5f

    /** false = sin papel milimetrado, solo la línea (para la cara del robot). */
    var cuadricula = true

    fun setGrosorLinea(dp: Float) {
        linePaint.strokeWidth = dp * resources.displayMetrics.density
    }

    /**
     * Para la cara del robot: la línea pasa por detrás de la cara y se desvanece un poco al
     * cruzarla. Devuelve los bordes izquierdo y derecho de la cara, en píxeles.
     */
    var zonaDesvanecida: (() -> Pair<Float, Float>)? = null

    /** Altura (en píxeles) por donde pasa la línea; null = el centro de la vista. */
    var centroLinea: (() -> Float)? = null

    /** Fracción del ancho en la que la línea aparece y desaparece en cada borde. */
    var bordeDesvanecido = 0.1f
        set(valor) {
            field = valor
            claveDegradado = ""
            invalidate()
        }

    /** Altura máxima de la onda en píxeles; null = toda la vista. */
    var alturaOnda: Float? = null

    private var colorLinea = linePaint.color
    private var claveDegradado = ""

    fun limpiar() {
        headIndex = 0
        totalPuntosCount = 0
        invalidate()
    }

    fun agregarPunto(valor: Float) {
        puntos[headIndex] = valor
        headIndex = (headIndex + 1) % maxPoints
        if (totalPuntosCount < maxPoints) {
            totalPuntosCount++
        }
        invalidate()
    }

    fun setStrokeColor(hexColor: String) {
        colorLinea = Color.parseColor(hexColor)
        linePaint.color = colorLinea
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = centroLinea?.invoke() ?: (height / 2f)
        val mitadOnda = ((alturaOnda ?: height) / 2f).coerceAtMost(minOf(centerY, height - centerY))
        aplicarDesvanecido(width)

        // Cuadrícula tipo papel milimetrado
        if (cuadricula) dibujarCuadricula(canvas, width, height)

        if (totalPuntosCount < 2) return

        path.reset()
        val stepX = width / (maxPoints - 1)
        val startIndex = if (totalPuntosCount < maxPoints) 0 else headIndex
        val scaleY = (mitadOnda - 20f).coerceAtLeast(1f) / rango

        for (i in 0 until totalPuntosCount) {
            val index = (startIndex + i) % maxPoints
            val value = puntos[index]
            val px = i * stepX
            val py = centerY - (value * scaleY).coerceIn(-mitadOnda + 10f, mitadOnda - 10f)

            if (i == 0) {
                path.moveTo(px, py)
            } else {
                path.lineTo(px, py)
            }
        }

        canvas.drawPath(path, linePaint)
    }

    /** Degradado horizontal: aparece en los bordes y se atenúa detrás de la cara. */
    private fun aplicarDesvanecido(width: Float) {
        val zona = zonaDesvanecida?.invoke()
        if (zona == null || width <= 0f) {
            if (linePaint.shader != null) linePaint.shader = null
            return
        }
        val (izq, der) = zona
        val clave = "$width|$izq|$der|$colorLinea"
        if (clave == claveDegradado) return
        claveDegradado = clave
        val transicion = width * 0.07f
        fun f(x: Float) = (x / width).coerceIn(0f, 1f)
        fun alfa(a: Float) = (colorLinea and 0x00FFFFFF) or ((a * 255).toInt() shl 24)
        val bordes = bordeDesvanecido.coerceIn(0f, 0.45f)
        // Invisible en los extremos, aparece de a poco hasta [bordes] y se atenúa tras la cara.
        val posiciones = floatArrayOf(
            0f, bordes * 0.7f, bordes,
            f(izq - transicion), f(izq), f(der), f(der + transicion),
            1f - bordes, 1f - bordes * 0.7f, 1f,
        )
        // Las posiciones deben ir en orden creciente.
        for (i in 1 until posiciones.size) posiciones[i] = maxOf(posiciones[i], posiciones[i - 1])
        val colores = intArrayOf(
            alfa(0f), alfa(0f), alfa(1f),
            alfa(1f), alfa(TENUE_DETRAS_DE_LA_CARA), alfa(TENUE_DETRAS_DE_LA_CARA), alfa(1f),
            alfa(1f), alfa(0f), alfa(0f),
        )
        linePaint.shader = LinearGradient(0f, 0f, width, 0f, colores, posiciones, Shader.TileMode.CLAMP)
    }

    private fun dibujarCuadricula(canvas: Canvas, width: Float, height: Float) {
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
    }

    private companion object {
        /** Opacidad de la línea mientras pasa por detrás de la cara. */
        const val TENUE_DETRAS_DE_LA_CARA = 0.28f
    }
}
