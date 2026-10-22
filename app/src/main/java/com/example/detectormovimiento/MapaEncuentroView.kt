package com.example.detectormovimiento

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Mapa esquemático del punto de encuentro, visto desde arriba: "Estás aquí" frente a la
 * estación, una ruta animada y el punto de encuentro en la dirección configurada.
 * No usa internet: se dibuja con formas simples. Se anima solo mientras está visible.
 */
class MapaEncuentroView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var punto: PuntoEncuentro? = null
        set(valor) {
            field = valor
            invalidate()
        }

    private val densidad = resources.displayMetrics.density
    private val cuadricula = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111A2B")
        strokeWidth = 1.5f * densidad
    }
    private val calle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0E1626")
        style = Paint.Style.FILL
    }
    private val ruta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = VERDE
        style = Paint.Style.STROKE
        strokeWidth = 5f * densidad
        strokeCap = Paint.Cap.ROUND
    }
    private val relleno = Paint(Paint.ANTI_ALIAS_FLAG)
    private val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.manrope_semibold)
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val ahora = SystemClock.uptimeMillis()
        val p = punto

        // Fondo: manzanas y calles para que se lea como un mapa.
        val paso = 46f * densidad
        var x = (w / 2f) % paso
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, cuadricula); x += paso
        }
        var y = (h / 2f) % paso
        while (y < h) {
            canvas.drawLine(0f, y, w, y, cuadricula); y += paso
        }

        // Distancia en pantalla: más lejos en metros = más largo, dentro de un rango legible.
        val metros = (p?.distanciaM ?: 80).coerceIn(10, 400)
        val angulo = Math.toRadians((p?.direccion?.grados ?: 0f).toDouble())
        val ux = sin(angulo).toFloat()
        val uy = -cos(angulo).toFloat()
        // Que los dos extremos (con sus textos) queden dentro de la vista.
        val margenX = 80f * densidad
        val margenY = 64f * densidad
        val largo = minOf(
            min(w, h) * (0.42f + 0.28f * ((metros - 10) / 390f)),
            if (abs(ux) > 0.01f) (w - 2 * margenX) / abs(ux) else Float.MAX_VALUE,
            if (abs(uy) > 0.01f) (h - 2 * margenY) / abs(uy) else Float.MAX_VALUE,
        ).coerceAtLeast(60f * densidad)
        val dx = ux * largo
        val dy = uy * largo
        // El tramo completo queda centrado en la vista.
        val tuX = w / 2f - dx / 2f
        val tuY = h / 2f - dy / 2f
        val metaX = tuX + dx
        val metaY = tuY + dy

        // Calle ancha por donde va la ruta
        calle.strokeWidth = 30f * densidad
        calle.style = Paint.Style.STROKE
        calle.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(tuX, tuY, metaX, metaY, calle)

        // Ruta punteada que avanza hacia el punto de encuentro
        val fase = -((ahora % 900L) / 900f) * 22f * densidad
        ruta.pathEffect = DashPathEffect(floatArrayOf(12f * densidad, 10f * densidad), fase)
        val radioMeta = 30f * densidad
        val fraccion = ((largo - radioMeta - 10f * densidad) / largo).coerceAtLeast(0f)
        canvas.drawLine(tuX, tuY, tuX + dx * fraccion, tuY + dy * fraccion, ruta)
        ruta.pathEffect = null
        dibujarPunta(canvas, tuX + dx * fraccion, tuY + dy * fraccion, atan2(dy, dx))

        // Distancia a mitad del camino
        p?.distanciaM?.let {
            texto.textSize = 15f * densidad
            texto.color = Color.parseColor("#CBD5E1")
            val mx = tuX + dx / 2f
            val my = tuY + dy / 2f
            // Al costado de la ruta, para no taparla.
            val nx = -dy / largo * 26f * densidad
            val ny = dx / largo * 26f * densidad
            canvas.drawText("≈ $it m", mx + nx, my + ny + texto.textSize / 3f, texto)
        }

        dibujarEstasAqui(canvas, tuX, tuY, ahora)
        dibujarPuntoEncuentro(canvas, metaX, metaY, radioMeta, ahora)

        if (isShown) postInvalidateDelayed(33L)
    }

    private fun dibujarPunta(canvas: Canvas, x: Float, y: Float, angulo: Float) {
        val t = 14f * densidad
        path.reset()
        path.moveTo(x + cos(angulo) * t, y + sin(angulo) * t)
        path.lineTo(x + cos(angulo + 2.4f) * t, y + sin(angulo + 2.4f) * t)
        path.lineTo(x + cos(angulo - 2.4f) * t, y + sin(angulo - 2.4f) * t)
        path.close()
        relleno.color = VERDE
        canvas.drawPath(path, relleno)
    }

    /** Punto azul que late, con la estación justo enfrente. */
    private fun dibujarEstasAqui(canvas: Canvas, x: Float, y: Float, ahora: Long) {
        val latido = (ahora % 1600L) / 1600f
        relleno.color = AZUL
        relleno.alpha = (90 * (1f - latido)).toInt()
        canvas.drawCircle(x, y, (12f + 22f * latido) * densidad, relleno)
        relleno.alpha = 255
        relleno.color = Color.WHITE
        canvas.drawCircle(x, y, 11f * densidad, relleno)
        relleno.color = AZUL
        canvas.drawCircle(x, y, 8f * densidad, relleno)

        // La estación EHRIA, delante de la persona
        val ey = y - 24f * densidad
        rect.set(x - 17f * densidad, ey - 6f * densidad, x + 17f * densidad, ey + 6f * densidad)
        relleno.color = Color.parseColor("#334155")
        canvas.drawRoundRect(rect, 4f * densidad, 4f * densidad, relleno)
        texto.textSize = 8f * densidad
        texto.color = Color.parseColor("#CBD5E1")
        canvas.drawText("EHRIA", x, ey + 3f * densidad, texto)

        texto.textSize = 13f * densidad
        texto.color = Color.WHITE
        canvas.drawText("Estás aquí", x, y + 30f * densidad, texto)
    }

    /** Señal de punto de encuentro: cuadro verde con cuatro flechas hacia el centro. */
    private fun dibujarPuntoEncuentro(canvas: Canvas, x: Float, y: Float, radio: Float, ahora: Long) {
        val pulso = 1f + 0.06f * sin(ahora / 260.0).toFloat()
        val r = radio * pulso
        relleno.color = VERDE
        relleno.alpha = 50
        rect.set(x - r * 1.35f, y - r * 1.35f, x + r * 1.35f, y + r * 1.35f)
        canvas.drawRoundRect(rect, r * 0.5f, r * 0.5f, relleno)
        relleno.alpha = 255
        rect.set(x - r, y - r, x + r, y + r)
        canvas.drawRoundRect(rect, r * 0.3f, r * 0.3f, relleno)

        relleno.color = Color.WHITE
        for (i in 0 until 4) {
            val a = Math.toRadians(i * 90.0 + 45.0)
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            // Flecha desde la esquina hacia el centro
            val px = x + ca * r * 0.78f
            val py = y + sa * r * 0.78f
            val tx = x + ca * r * 0.3f
            val ty = y + sa * r * 0.3f
            path.reset()
            path.moveTo(tx, ty)
            path.lineTo(px - sa * r * 0.2f, py + ca * r * 0.2f)
            path.lineTo(px + sa * r * 0.2f, py - ca * r * 0.2f)
            path.close()
            canvas.drawPath(path, relleno)
        }
        canvas.drawCircle(x, y, r * 0.14f, relleno)

        // Nombre debajo (o encima si no cabe)
        val nombre = punto?.nombre?.let { recortar(it, 22) } ?: "Punto de encuentro"
        texto.textSize = 14f * densidad
        texto.color = Color.WHITE
        val abajo = y + r * 1.35f + 20f * densidad
        val ty = if (abajo < height - 6f * densidad) abajo else y - r * 1.35f - 10f * densidad
        val mitad = texto.measureText(nombre) / 2f + 4f * densidad
        val tx = x.coerceIn(mitad, width - mitad)
        canvas.drawText(nombre, tx, ty, texto)
    }

    private fun recortar(t: String, max: Int) = if (t.length <= max) t else t.take(max - 1).trimEnd() + "…"

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) invalidate()
    }

    private companion object {
        val VERDE = Color.parseColor("#22C55E")
        val AZUL = Color.parseColor("#38BDF8")
    }
}
