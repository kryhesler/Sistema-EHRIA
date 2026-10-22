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
import androidx.core.content.res.ResourcesCompat
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

    enum class Expresion {
        DORMIDO, DESPIERTO, FELIZ, ATENTO, PREOCUPADO, ALERTA,
        /** Oyó su nombre: la boca sigue el volumen de la voz. */
        ESCUCHANDO,
        /** Pensando qué responder: mira hacia arriba con puntos suspensivos. */
        PENSANDO,
        /** No entendió: un ojo entrecerrado y un signo de pregunta. */
        CONFUNDIDO,
        SORPRENDIDO,
        TRISTE,
        GUINO,
        /** Hace calor: ojos cansados y una gota de sudor. */
        CALOR,
        /** Guiando primeros auxilios: mirada firme. */
        CONCENTRADO,
        /** Ojos de corazón que laten al ritmo de [ritmoBpm] (metrónomo de la RCP). */
        CORAZON,
    }

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

    /** Volumen de la voz que escucha (0 a 1), para la expresión [Expresion.ESCUCHANDO]. */
    var nivelVoz = 0f
        set(valor) {
            // Sube rápido y baja despacio, para que no tiemble.
            field = if (valor > field) valor else field * 0.7f + valor * 0.3f
        }

    /** Latidos por minuto de los ojos de corazón. */
    var ritmoBpm = 70
        set(valor) {
            field = valor.coerceIn(30, 200)
            inicioRitmoMs = SystemClock.uptimeMillis()
        }

    /** Color del fondo, para dibujar los párpados encima de los ojos. */
    var colorFondo = Color.parseColor("#04070D")

    private val pintura = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brillo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val parpado = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trazo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.manrope_semibold)
    }
    private val rect = RectF()
    private val path = Path()

    private var cambioMs = 0L
    private var inicioRitmoMs = 0L
    private var proximoParpadeoMs = 0L
    private var inicioParpadeoMs = -1L
    private var proximaMiradaMs = 0L
    private var miradaObjetivo = 0f
    private var mirada = 0f
    private var miradaVertical = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val ahora = SystemClock.uptimeMillis()
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val color = colorDe(expresion, ahora)
        pintura.color = color
        trazo.color = color
        brillo.color = color
        brillo.alpha = 40
        parpado.color = colorFondo

        actualizarMirada(ahora)
        // Ojos y boca forman un bloque fijo en el centro de la pantalla; al mirar a los lados
        // solo se mueven los ojos, no la cara.
        val escala = escala(w, h)
        // Al cambiar de expresión, los ojos "rebotan" un instante.
        val rebote = if (cambioMs == 0L) 1f else {
            val t = ((ahora - cambioMs) / 220f).coerceIn(0f, 1f)
            0.82f + 0.18f * (1f - (1f - t) * (1f - t))
        }
        val anchoOjo = escala * 0.17f * rebote
        val altoOjo = escala * 0.26f * rebote
        val separacion = escala * 0.2f
        val cx = w / 2f
        val cxOjos = cx + mirada * escala * 0.035f
        val cyBase = centroY(h) - escala * 0.26f * 0.37f
        val cyOjos = cyBase + miradaVertical * escala * 0.035f

        for (lado in listOf(-1f, 1f)) dibujarOjo(canvas, cxOjos + lado * separacion, cyOjos, lado, anchoOjo, altoOjo, ahora)

        dibujarBoca(canvas, cx, cyBase + escala * 0.26f * 1.05f, escala, ahora)
        dibujarAdornos(canvas, cx, cyOjos, separacion, anchoOjo, altoOjo, escala, color, ahora)

        if (isShown) postInvalidateDelayed(33L)
    }

    private fun colorDe(e: Expresion, ahora: Long): Int = when (e) {
        Expresion.ALERTA -> if ((ahora / 350) % 2 == 0L) Color.parseColor("#F87171") else Color.parseColor("#DC2626")
        Expresion.PREOCUPADO -> Color.parseColor("#FBBF24")
        Expresion.FELIZ, Expresion.GUINO -> Color.parseColor("#4ADE80")
        Expresion.DORMIDO -> Color.parseColor("#1E5A7A")
        Expresion.ESCUCHANDO -> Color.parseColor("#22D3EE")
        Expresion.PENSANDO -> Color.parseColor("#A78BFA")
        Expresion.CONFUNDIDO -> Color.parseColor("#FB923C")
        Expresion.SORPRENDIDO -> Color.parseColor("#7DD3FC")
        Expresion.TRISTE -> Color.parseColor("#60A5FA")
        Expresion.CALOR -> Color.parseColor("#F97316")
        Expresion.CORAZON -> Color.parseColor("#F43F5E")
        else -> Color.parseColor("#38BDF8")
    }

    private fun dibujarOjo(canvas: Canvas, ox: Float, cy: Float, lado: Float, anchoOjo: Float, altoOjo: Float, ahora: Long) {
        when {
            expresion == Expresion.DORMIDO -> {
                // Ojos cerrados: un arco suave hacia abajo
                trazo.strokeWidth = altoOjo * 0.12f
                path.reset()
                path.moveTo(ox - anchoOjo / 2, cy)
                path.quadTo(ox, cy + altoOjo * 0.22f, ox + anchoOjo / 2, cy)
                canvas.drawPath(path, trazo)
                return
            }
            expresion == Expresion.FELIZ || (expresion == Expresion.GUINO && lado > 0) -> {
                // Ojo sonriente: arco hacia arriba
                trazo.strokeWidth = altoOjo * 0.16f
                path.reset()
                path.moveTo(ox - anchoOjo / 2, cy + altoOjo * 0.1f)
                path.quadTo(ox, cy - altoOjo * 0.45f, ox + anchoOjo / 2, cy + altoOjo * 0.1f)
                canvas.drawPath(path, trazo)
                return
            }
            expresion == Expresion.CORAZON -> {
                val fase = ((ahora - inicioRitmoMs).coerceAtLeast(0L) % (60_000L / ritmoBpm)) / (60_000f / ritmoBpm)
                val latido = 1f + 0.22f * (1f - fase * 4f).coerceAtLeast(0f)
                val s = anchoOjo * 1.25f * latido
                corazon(ox, cy, s * 1.2f)
                canvas.drawPath(path, brillo)
                corazon(ox, cy, s)
                canvas.drawPath(path, pintura)
                return
            }
        }

        var alto = altoOjo
        var ancho = anchoOjo
        val parpadea = expresion != Expresion.SORPRENDIDO && expresion != Expresion.ALERTA
        if (parpadea) alto *= factorParpadeo(ahora)
        when (expresion) {
            Expresion.ALERTA -> { alto *= 1.15f; ancho *= 1.15f }
            Expresion.SORPRENDIDO -> { ancho *= 1.25f; alto *= 1.02f }
            Expresion.ESCUCHANDO -> { val s = 1.06f + 0.08f * nivelVoz; ancho *= s; alto *= s }
            Expresion.PENSANDO -> alto *= 0.85f
            Expresion.CONFUNDIDO -> if (lado > 0) alto *= 0.45f
            else -> Unit
        }
        rect.set(ox - ancho / 2, cy - alto / 2, ox + ancho / 2, cy + alto / 2)
        val radio = if (expresion == Expresion.SORPRENDIDO) min(ancho, alto) / 2f else min(ancho, alto) * 0.45f
        rect.inset(-ancho * 0.18f, -alto * 0.12f)
        canvas.drawRoundRect(rect, radio * 1.4f, radio * 1.4f, brillo)
        rect.inset(ancho * 0.18f, alto * 0.12f)
        canvas.drawRoundRect(rect, radio, radio, pintura)

        // Párpados: se tapa parte del ojo con el color del fondo.
        val arriba = cy - alto / 2 - alto * 0.25f
        val afuera = ox + lado * ancho
        val adentro = ox - lado * ancho
        when (expresion) {
            Expresion.TRISTE -> triangulo(canvas, adentro, arriba, afuera, arriba, afuera, cy - alto / 2 + alto * 0.55f)
            Expresion.CONCENTRADO -> triangulo(canvas, adentro, arriba, afuera, arriba, adentro, cy - alto / 2 + alto * 0.38f)
            Expresion.CALOR -> {
                rect.set(ox - ancho, arriba, ox + ancho, cy - alto / 2 + alto * 0.42f)
                canvas.drawRect(rect, parpado)
            }
            else -> Unit
        }

        // Cejas
        trazo.strokeWidth = altoOjo * 0.08f
        when (expresion) {
            Expresion.PREOCUPADO -> {
                val y = cy - altoOjo * 0.75f
                canvas.drawLine(ox - anchoOjo * 0.5f, y + lado * -altoOjo * 0.08f, ox + anchoOjo * 0.5f, y + lado * altoOjo * 0.08f, trazo)
            }
            Expresion.SORPRENDIDO -> {
                val y = cy - altoOjo * 0.85f
                path.reset()
                path.moveTo(ox - anchoOjo * 0.5f, y + altoOjo * 0.06f)
                path.quadTo(ox, y - altoOjo * 0.12f, ox + anchoOjo * 0.5f, y + altoOjo * 0.06f)
                canvas.drawPath(path, trazo)
            }
            Expresion.CONFUNDIDO -> if (lado < 0) {
                val y = cy - altoOjo * 0.82f
                canvas.drawLine(ox - anchoOjo * 0.5f, y + altoOjo * 0.06f, ox + anchoOjo * 0.5f, y - altoOjo * 0.1f, trazo)
            }
            else -> Unit
        }
    }

    private fun triangulo(
        canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, relleno: Paint = parpado,
    ) {
        path.reset()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.close()
        canvas.drawPath(path, relleno)
    }

    /** Deja en [path] un corazón centrado en (x, y) de ancho aproximado [s]. */
    private fun corazon(x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y + s * 0.45f)
        path.cubicTo(x - s * 0.95f, y - s * 0.1f, x - s * 0.5f, y - s * 0.75f, x, y - s * 0.28f)
        path.cubicTo(x + s * 0.5f, y - s * 0.75f, x + s * 0.95f, y - s * 0.1f, x, y + s * 0.45f)
        path.close()
    }

    private fun dibujarBoca(canvas: Canvas, cx: Float, cyBoca: Float, escala: Float, ahora: Long) {
        val anchoBoca = escala * 0.22f
        trazo.strokeWidth = escala * 0.022f
        when {
            hablando && expresion != Expresion.DORMIDO -> {
                val apertura = 0.25f + 0.75f * abs(sin(ahora / 70.0) * sin(ahora / 190.0)).toFloat()
                val alto = escala * 0.09f * apertura
                rect.set(cx - anchoBoca * 0.35f, cyBoca - alto / 2, cx + anchoBoca * 0.35f, cyBoca + alto / 2)
                canvas.drawRoundRect(rect, alto, alto, pintura)
            }
            expresion == Expresion.ESCUCHANDO -> {
                // Barras que suben y bajan con el volumen de quien habla.
                val barra = escala * 0.018f
                val hueco = escala * 0.014f
                for (i in -2..2) {
                    val variacion = 0.55f + 0.45f * abs(sin(ahora / 95.0 + i * 1.3)).toFloat()
                    val alto = escala * (0.016f + 0.07f * nivelVoz * variacion * (1f - abs(i) * 0.18f))
                    val x = cx + i * (barra + hueco)
                    rect.set(x - barra / 2, cyBoca - alto / 2, x + barra / 2, cyBoca + alto / 2)
                    canvas.drawRoundRect(rect, barra / 2, barra / 2, pintura)
                }
            }
            expresion == Expresion.ALERTA -> canvas.drawCircle(cx, cyBoca, escala * 0.05f, trazo)
            expresion == Expresion.SORPRENDIDO -> canvas.drawCircle(cx, cyBoca + escala * 0.01f, escala * 0.035f, trazo)
            expresion == Expresion.PREOCUPADO || expresion == Expresion.TRISTE -> {
                val ancho = if (expresion == Expresion.TRISTE) anchoBoca * 0.75f else anchoBoca
                val curva = if (expresion == Expresion.TRISTE) 0.07f else 0.05f
                path.reset()
                path.moveTo(cx - ancho / 2, cyBoca + escala * 0.02f)
                path.quadTo(cx, cyBoca - escala * curva, cx + ancho / 2, cyBoca + escala * 0.02f)
                canvas.drawPath(path, trazo)
            }
            expresion == Expresion.CONFUNDIDO -> {
                // Línea ondulada
                path.reset()
                val inicio = cx - anchoBoca * 0.4f
                path.moveTo(inicio, cyBoca)
                val tramo = anchoBoca * 0.8f / 4
                for (i in 0 until 4) {
                    val dy = if (i % 2 == 0) -escala * 0.02f else escala * 0.02f
                    path.quadTo(inicio + tramo * (i + 0.5f), cyBoca + dy, inicio + tramo * (i + 1), cyBoca)
                }
                canvas.drawPath(path, trazo)
            }
            expresion == Expresion.PENSANDO -> {
                canvas.drawLine(cx - anchoBoca * 0.05f, cyBoca + escala * 0.01f, cx + anchoBoca * 0.3f, cyBoca - escala * 0.005f, trazo)
            }
            expresion == Expresion.CONCENTRADO -> {
                canvas.drawLine(cx - anchoBoca * 0.3f, cyBoca, cx + anchoBoca * 0.3f, cyBoca, trazo)
            }
            expresion == Expresion.CALOR -> {
                // Boca abierta, jadeando despacio
                val alto = escala * (0.035f + 0.015f * sin(ahora / 260.0).toFloat())
                rect.set(cx - anchoBoca * 0.22f, cyBoca - alto / 2, cx + anchoBoca * 0.22f, cyBoca + alto / 2)
                canvas.drawRoundRect(rect, alto / 2, alto / 2, pintura)
            }
            expresion == Expresion.DORMIDO -> {
                canvas.drawLine(cx - anchoBoca * 0.2f, cyBoca, cx + anchoBoca * 0.2f, cyBoca, trazo)
            }
            else -> {
                val sonrisa = expresion == Expresion.FELIZ || expresion == Expresion.GUINO || expresion == Expresion.CORAZON
                val curva = if (sonrisa) 0.09f else 0.05f
                // El guiño lleva una sonrisa de lado.
                val desvio = if (expresion == Expresion.GUINO) anchoBoca * 0.15f else 0f
                path.reset()
                path.moveTo(cx - anchoBoca / 2, cyBoca - escala * 0.01f)
                path.quadTo(cx + desvio, cyBoca + escala * curva, cx + anchoBoca / 2, cyBoca - escala * (0.01f + desvio / escala))
                canvas.drawPath(path, trazo)
            }
        }
    }

    private fun dibujarAdornos(
        canvas: Canvas, cx: Float, cyOjos: Float, separacion: Float, anchoOjo: Float, altoOjo: Float,
        escala: Float, color: Int, ahora: Long,
    ) {
        texto.color = color
        when (expresion) {
            Expresion.DORMIDO -> {
                // "z" flotando mientras duerme
                val ciclo = (ahora % 3000L) / 3000f
                texto.alpha = (255 * (1f - ciclo)).toInt()
                texto.textSize = escala * (0.06f + 0.04f * ciclo)
                val yZ = (cyOjos - altoOjo * (0.4f + 0.5f * ciclo)).coerceAtLeast(texto.textSize)
                canvas.drawText("z", cx + separacion + anchoOjo * 0.7f, yZ, texto)
            }
            Expresion.PENSANDO -> {
                // Tres puntos que aparecen uno tras otro arriba a la derecha
                val visibles = ((ahora % 1600L) / 400L).toInt()
                for (i in 0 until visibles.coerceAtMost(3)) {
                    val r = escala * (0.012f + 0.006f * i)
                    canvas.drawCircle(cx + separacion + anchoOjo * (0.9f + 0.45f * i), cyOjos - altoOjo * (0.7f + 0.25f * i), r, pintura)
                }
            }
            Expresion.CONFUNDIDO -> {
                texto.alpha = 255
                texto.textSize = escala * 0.11f
                val y = cyOjos - altoOjo * 0.75f + escala * 0.01f * sin(ahora / 300.0).toFloat()
                canvas.drawText("?", cx + separacion + anchoOjo * 0.75f, y, texto)
            }
            Expresion.CALOR -> {
                // Gota de sudor que resbala por el costado
                val ciclo = (ahora % 1800L) / 1800f
                val x = cx + separacion + anchoOjo * 1.1f
                val y = cyOjos - altoOjo * 0.5f + altoOjo * 0.9f * ciclo
                val r = escala * 0.022f
                pintura.color = Color.parseColor("#7DD3FC")
                pintura.alpha = (255 * (1f - ciclo * ciclo)).toInt()
                canvas.drawCircle(x, y, r, pintura)
                triangulo(canvas, x, y - r * 2.2f, x - r * 0.95f, y - r * 0.3f, x + r * 0.95f, y - r * 0.3f, pintura)
                pintura.alpha = 255
                pintura.color = color
            }
            Expresion.ESCUCHANDO -> {
                // Ondas a los costados, más fuertes cuanto más alto se habla
                trazo.strokeWidth = escala * 0.012f
                trazo.alpha = (60 + 195 * nivelVoz).toInt()
                for (lado in listOf(-1f, 1f)) {
                    val x = cx + lado * (separacion + anchoOjo * 1.15f)
                    for (i in 0..1) {
                        val r = escala * (0.05f + 0.035f * i)
                        rect.set(x - r, cyOjos - r, x + r, cyOjos + r)
                        canvas.drawArc(rect, if (lado < 0) 135f else -45f, 90f, false, trazo)
                    }
                }
                trazo.alpha = 255
            }
            else -> Unit
        }
    }

    private fun escala(w: Float, h: Float) = min(w * 0.9f, h * 1.2f)

    /** Centro vertical de la cara: un poco arriba del centro, para dejar lugar a los subtítulos. */
    private fun centroY(h: Float) = h * 0.44f

    /** Borde izquierdo y derecho de la cara (con su brillo), en píxeles de esta vista. */
    fun limitesHorizontales(): Pair<Float, Float> {
        val e = escala(width.toFloat(), height.toFloat())
        val medio = e * 0.2f + e * 0.17f * 0.75f
        return Pair(width / 2f - medio, width / 2f + medio)
    }

    /** Altura de la cara en esta vista (para que la señal de fondo pase por en medio). */
    fun centroVertical(): Float = centroY(height.toFloat())

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
            Expresion.PENSANDO -> 0.7f
            Expresion.DESPIERTO -> {
                if (ahora >= proximaMiradaMs) {
                    proximaMiradaMs = ahora + 2_500L + (Math.random() * 3_000).toLong()
                    listOf(-0.6f, 0f, 0f, 0.6f).random()
                } else miradaObjetivo
            }
            else -> 0f
        }
        val verticalObjetivo = when (expresion) {
            Expresion.PENSANDO -> -1f
            Expresion.ESCUCHANDO -> -0.3f
            Expresion.TRISTE -> 0.4f
            else -> 0f
        }
        mirada += (miradaObjetivo - mirada) * 0.15f
        miradaVertical += (verticalObjetivo - miradaVertical) * 0.15f
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) invalidate()
    }
}
