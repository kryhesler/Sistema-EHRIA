package com.example.detectormovimiento

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Cara del robot de la estación: dos ojos y una boca dibujados con formas simples.
 * Parpadea sola, mira hacia los lados, mueve la boca mientras habla y cambia según la
 * [expresion]. Se redibuja a ~30 cuadros por segundo solo mientras está visible.
 */
class CaraRobotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Expresion { DORMIDO, DESPIERTO, FELIZ, ATENTO, PREOCUPADO, ALERTA }

    var expresion = Expresion.DORMIDO
        set(valor) {
            if (field != valor) {
                field = valor
                cambioMs = SystemClock.uptimeMillis()
                invalidate()
            }
        }

    /** true mientras la voz está hablando: la boca se mueve. */
    var hablando = false

    private val pintura = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brillo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trazo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
    private val rect = RectF()
    private val path = Path()

    private var cambioMs = 0L
    private var proximoParpadeoMs = 0L
    private var inicioParpadeoMs = -1L
    private var proximaMiradaMs = 0L
    private var miradaObjetivo = 0f
    private var mirada = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val ahora = SystemClock.uptimeMillis()
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val color = when (expresion) {
            Expresion.ALERTA -> if ((ahora / 350) % 2 == 0L) Color.parseColor("#F87171") else Color.parseColor("#DC2626")
            Expresion.PREOCUPADO -> Color.parseColor("#FBBF24")
            Expresion.FELIZ -> Color.parseColor("#4ADE80")
            Expresion.DORMIDO -> Color.parseColor("#1E5A7A")
            else -> Color.parseColor("#38BDF8")
        }
        pintura.color = color
        trazo.color = color
        brillo.color = color
        brillo.alpha = 40

        actualizarMirada(ahora)
        val escala = min(w, h * 1.6f)
        val anchoOjo = escala * 0.17f
        val altoOjo = escala * 0.26f
        val separacion = escala * 0.2f
        val cx = w / 2f + mirada * escala * 0.06f
        val cyOjos = h * 0.42f

        // Ojos
        for (lado in listOf(-1f, 1f)) {
            val ox = cx + lado * separacion
            when (expresion) {
                Expresion.DORMIDO -> {
                    // Ojos cerrados: un arco suave hacia abajo
                    trazo.strokeWidth = altoOjo * 0.12f
                    path.reset()
                    path.moveTo(ox - anchoOjo / 2, cyOjos)
                    path.quadTo(ox, cyOjos + altoOjo * 0.22f, ox + anchoOjo / 2, cyOjos)
                    canvas.drawPath(path, trazo)
                }
                Expresion.FELIZ -> {
                    // Ojos sonrientes: arco hacia arriba
                    trazo.strokeWidth = altoOjo * 0.16f
                    path.reset()
                    path.moveTo(ox - anchoOjo / 2, cyOjos + altoOjo * 0.1f)
                    path.quadTo(ox, cyOjos - altoOjo * 0.45f, ox + anchoOjo / 2, cyOjos + altoOjo * 0.1f)
                    canvas.drawPath(path, trazo)
                }
                else -> {
                    var alto = altoOjo * factorParpadeo(ahora)
                    var ancho = anchoOjo
                    if (expresion == Expresion.ALERTA) {
                        alto *= 1.15f; ancho *= 1.15f
                    }
                    rect.set(ox - ancho / 2, cyOjos - alto / 2, ox + ancho / 2, cyOjos + alto / 2)
                    val radio = min(ancho, alto) * 0.45f
                    rect.inset(-ancho * 0.18f, -alto * 0.12f)
                    canvas.drawRoundRect(rect, radio * 1.4f, radio * 1.4f, brillo)
                    rect.inset(ancho * 0.18f, alto * 0.12f)
                    canvas.drawRoundRect(rect, radio, radio, pintura)
                    if (expresion == Expresion.PREOCUPADO) {
                        // Ceja inclinada
                        trazo.strokeWidth = altoOjo * 0.08f
                        val y = cyOjos - altoOjo * 0.75f
                        canvas.drawLine(ox - anchoOjo * 0.5f, y + lado * -altoOjo * 0.08f, ox + anchoOjo * 0.5f, y + lado * altoOjo * 0.08f, trazo)
                    }
                }
            }
        }

        // Boca
        val cyBoca = h * 0.78f
        val anchoBoca = escala * 0.22f
        trazo.strokeWidth = escala * 0.022f
        when {
            hablando && expresion != Expresion.DORMIDO -> {
                val apertura = 0.25f + 0.75f * abs(sin(ahora / 70.0) * sin(ahora / 190.0)).toFloat()
                val alto = escala * 0.09f * apertura
                rect.set(cx - anchoBoca * 0.35f, cyBoca - alto / 2, cx + anchoBoca * 0.35f, cyBoca + alto / 2)
                canvas.drawRoundRect(rect, alto, alto, pintura)
            }
            expresion == Expresion.ALERTA -> {
                val r = escala * 0.05f
                canvas.drawCircle(cx, cyBoca, r, trazo)
            }
            expresion == Expresion.PREOCUPADO -> {
                path.reset()
                path.moveTo(cx - anchoBoca / 2, cyBoca + escala * 0.02f)
                path.quadTo(cx, cyBoca - escala * 0.05f, cx + anchoBoca / 2, cyBoca + escala * 0.02f)
                canvas.drawPath(path, trazo)
            }
            expresion == Expresion.DORMIDO -> {
                canvas.drawLine(cx - anchoBoca * 0.2f, cyBoca, cx + anchoBoca * 0.2f, cyBoca, trazo)
            }
            else -> {
                val curva = if (expresion == Expresion.FELIZ) 0.09f else 0.05f
                path.reset()
                path.moveTo(cx - anchoBoca / 2, cyBoca - escala * 0.01f)
                path.quadTo(cx, cyBoca + escala * curva, cx + anchoBoca / 2, cyBoca - escala * 0.01f)
                canvas.drawPath(path, trazo)
            }
        }

        // "Z" flotando mientras duerme
        if (expresion == Expresion.DORMIDO) {
            val ciclo = (ahora % 3000L) / 3000f
            texto.color = color
            texto.alpha = (255 * (1f - ciclo)).toInt()
            texto.textSize = escala * (0.06f + 0.04f * ciclo)
            val yZ = (cyOjos - altoOjo * (0.4f + 0.5f * ciclo)).coerceAtLeast(texto.textSize)
            canvas.drawText("z", cx + separacion + anchoOjo * 0.7f, yZ, texto)
        }

        if (isShown) postInvalidateDelayed(33L)
    }

    /** 1 = ojo abierto; baja hasta casi 0 durante un parpadeo de 140 ms cada 3–6 s. */
    private fun factorParpadeo(ahora: Long): Float {
        if (proximoParpadeoMs == 0L) proximoParpadeoMs = ahora + 2_000L
        if (inicioParpadeoMs < 0 && ahora >= proximoParpadeoMs) inicioParpadeoMs = ahora
        if (inicioParpadeoMs < 0) return 1f
        val t = (ahora - inicioParpadeoMs) / 140f
        if (t >= 1f) {
            inicioParpadeoMs = -1L
            proximoParpadeoMs = ahora + 3_000L + (Math.random() * 3_000).toLong()
            return 1f
        }
        return (0.1f + 0.9f * abs(1f - 2f * t))
    }

    private fun actualizarMirada(ahora: Long) {
        miradaObjetivo = when (expresion) {
            Expresion.ATENTO -> 0.8f
            Expresion.DESPIERTO -> {
                if (ahora >= proximaMiradaMs) {
                    proximaMiradaMs = ahora + 2_500L + (Math.random() * 3_000).toLong()
                    listOf(-0.6f, 0f, 0f, 0.6f).random()
                } else miradaObjetivo
            }
            else -> 0f
        }
        mirada += (miradaObjetivo - mirada) * 0.15f
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) invalidate()
    }
}
