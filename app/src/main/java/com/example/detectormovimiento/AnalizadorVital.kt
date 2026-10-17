package com.example.detectormovimiento

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Estima respiración y pulso con el acelerómetro apoyado sobre el pecho.
 *
 * - Respiración: al respirar el teléfono se inclina un poco, y eso cambia cómo se reparte
 *   la gravedad entre los ejes X/Y/Z. Se filtra cada eje por debajo de 0.8 Hz, se elige
 *   el eje que más se mueve y se busca su periodo con autocorrelación.
 * - Pulso (sismocardiografía): cada latido produce una pequeña vibración en el eje Z.
 *   Se filtra entre 5 y 20 Hz, se calcula su energía y se busca el periodo de esa energía.
 *   Necesita al menos 40 muestras por segundo.
 *
 * Si la señal no es clara, el valor queda en null: nunca se inventa un número.
 * Es una medición orientativa, no un dispositivo médico.
 */
object AnalizadorVital {

    enum class EstadoMedicion {
        OK,
        MUESTRA_INSUFICIENTE,
        SIN_MOVIMIENTO,
        MUCHO_MOVIMIENTO,
        RESPIRACION_IRREGULAR,
    }

    data class Resultado(
        val estado: EstadoMedicion,
        val rpm: Int?,
        val bpm: Int?,
        val frecuenciaMuestreoHz: Double,
    )

    private const val DURACION_MINIMA_S = 10.0
    private const val FRECUENCIA_BASE_MAX_HZ = 100
    private const val FRECUENCIA_MIN_PULSO_HZ = 40
    private const val DESCARTE_INICIAL_S = 1.0
    private const val SALTO_MAXIMO_NS = 500_000_000L

    private const val MOVIMIENTO_EXCESIVO = 1.0      // m/s², desviación estándar de un eje
    private const val AMPLITUD_MIN_RESPIRACION = 0.004 // m/s², por debajo: mesa / objeto sólido
    private const val CORRELACION_MIN = 0.4

    /**
     * Nunca lanza excepciones: con datos dañados del sensor devuelve MUESTRA_INSUFICIENTE.
     */
    fun analizar(tiemposNs: LongArray, x: FloatArray, y: FloatArray, z: FloatArray): Resultado = try {
        analizarSinProteger(tiemposNs, x, y, z)
    } catch (_: Exception) {
        Resultado(EstadoMedicion.MUESTRA_INSUFICIENTE, null, null, 0.0)
    }

    private fun analizarSinProteger(tiemposNs: LongArray, x: FloatArray, y: FloatArray, z: FloatArray): Resultado {
        val muestras = limpiarMuestras(tiemposNs, x, y, z)
        val n = muestras.t.size

        if (n < 50) return Resultado(EstadoMedicion.MUESTRA_INSUFICIENTE, null, null, 0.0)
        val t = muestras.t
        val duracion = t[n - 1]
        if (duracion < DURACION_MINIMA_S) {
            return Resultado(EstadoMedicion.MUESTRA_INSUFICIENTE, null, null, 0.0)
        }

        val fsMedida = (n - 1) / duracion
        val fs = min(fsMedida.roundToInt(), FRECUENCIA_BASE_MAX_HZ)
        if (fs < 5) return Resultado(EstadoMedicion.MUESTRA_INSUFICIENTE, null, null, fsMedida)

        val ejes = listOf(muestras.x, muestras.y, muestras.z).map { quitarMedia(remuestrear(t, it, fs.toDouble())) }

        if (ejes.any { desviacion(it) > MOVIMIENTO_EXCESIVO }) {
            return Resultado(EstadoMedicion.MUCHO_MOVIMIENTO, null, null, fsMedida)
        }

        // ---------- Respiración ----------
        val paso = max(1, (fs / 10.0).roundToInt())
        val fsResp = fs.toDouble() / paso
        val descarte = (DESCARTE_INICIAL_S * fs).toInt()
        val candidatos = ejes.map { eje ->
            var s = quitarTendencia(eje)
            s = Biquad.pasaBajos(0.8, fs.toDouble()).aplicar(s)
            s = Biquad.pasaBajos(0.8, fs.toDouble()).aplicar(s)
            s = s.copyOfRange(descarte, s.size)
            quitarMedia(DoubleArray(s.size / paso) { s[it * paso] })
        }
        val respiracion = candidatos.maxByOrNull { desviacion(it) }!!
        if (desviacion(respiracion) < AMPLITUD_MIN_RESPIRACION) {
            return Resultado(EstadoMedicion.SIN_MOVIMIENTO, null, null, fsMedida)
        }
        val picoResp = picoAutocorrelacion(respiracion, fsResp, 2.0, 10.0)
        val rpm = picoResp
            ?.takeIf { it.second >= CORRELACION_MIN }
            ?.let { porMinuto(it.first) }

        // ---------- Pulso ----------
        val bpm = if (fs >= FRECUENCIA_MIN_PULSO_HZ) estimarPulso(ejes[2], fs.toDouble()) else null

        val estado = if (rpm == null) EstadoMedicion.RESPIRACION_IRREGULAR else EstadoMedicion.OK
        return Resultado(estado, rpm, bpm, fsMedida)
    }

    private fun estimarPulso(zSinMedia: DoubleArray, fs: Double): Int? {
        var s = Biquad.pasaAltos(5.0, fs).aplicar(zSinMedia)
        s = Biquad.pasaAltos(5.0, fs).aplicar(s)
        s = Biquad.pasaBajos(min(20.0, 0.4 * fs), fs).aplicar(s)
        val descarte = (DESCARTE_INICIAL_S * fs).toInt()
        s = s.copyOfRange(descarte, s.size)

        var energia = DoubleArray(s.size) { s[it] * s[it] }
        energia = Biquad.pasaBajos(4.0, fs).aplicar(energia)
        energia = Biquad.pasaBajos(4.0, fs).aplicar(energia)
        energia = quitarMedia(energia.copyOfRange(descarte, energia.size))

        // 40 a 150 latidos por minuto
        val pico = picoAutocorrelacion(energia, fs, 0.4, 1.5) ?: return null
        if (pico.second < CORRELACION_MIN) return null
        return porMinuto(pico.first)
    }

    /** Convierte un periodo en segundos a "por minuto"; null si el número no es válido. */
    private fun porMinuto(periodoS: Double): Int? {
        val valor = 60.0 / periodoS
        return if (valor.isFinite() && valor > 0) valor.roundToInt() else null
    }

    private class Muestras(val t: DoubleArray, val x: FloatArray, val y: FloatArray, val z: FloatArray)

    /**
     * Los teléfonos reales a veces entregan muestras repetidas, desordenadas (al reiniciar el
     * sensor llegan eventos viejos en lote) o con valores NaN. Se ordenan por tiempo y se
     * descartan las inválidas. Los tiempos quedan en segundos desde la primera muestra.
     */
    private fun limpiarMuestras(tiemposNs: LongArray, x: FloatArray, y: FloatArray, z: FloatArray): Muestras {
        val n = minOf(tiemposNs.size, x.size, y.size, z.size)
        val orden = (0 until n)
            .filter { x[it].isFinite() && y[it].isFinite() && z[it].isFinite() }
            .sortedBy { tiemposNs[it] }
        val crecientes = ArrayList<Int>(orden.size)
        for (i in orden) {
            if (crecientes.isEmpty() || tiemposNs[i] > tiemposNs[crecientes.last()]) crecientes.add(i)
        }
        // Si hay un salto de tiempo grande (p. ej. un evento viejo al inicio), quedarse con
        // el tramo continuo más largo.
        var mejorInicio = 0
        var mejorFin = 0
        var inicio = 0
        for (k in 1..crecientes.size) {
            val corte = k == crecientes.size ||
                tiemposNs[crecientes[k]] - tiemposNs[crecientes[k - 1]] > SALTO_MAXIMO_NS
            if (corte) {
                if (k - inicio > mejorFin - mejorInicio) {
                    mejorInicio = inicio
                    mejorFin = k
                }
                inicio = k
            }
        }
        val validos = crecientes.subList(mejorInicio, mejorFin)
        if (validos.isEmpty()) return Muestras(DoubleArray(0), FloatArray(0), FloatArray(0), FloatArray(0))
        val t0 = tiemposNs[validos[0]]
        return Muestras(
            DoubleArray(validos.size) { (tiemposNs[validos[it]] - t0) / 1e9 },
            FloatArray(validos.size) { x[validos[it]] },
            FloatArray(validos.size) { y[validos[it]] },
            FloatArray(validos.size) { z[validos[it]] },
        )
    }

    /**
     * Devuelve (periodo en segundos, correlación normalizada) del primer pico claro de la
     * autocorrelación entre [periodoMin] y [periodoMax], o null si no hay ninguno.
     * Se toma el primer máximo local que llegue al 85 % del mayor, para no confundir
     * el periodo con sus múltiplos.
     */
    internal fun picoAutocorrelacion(
        s: DoubleArray,
        fs: Double,
        periodoMin: Double,
        periodoMax: Double,
    ): Pair<Double, Double>? {
        val n = s.size
        val energia = s.sumOf { it * it } / n
        if (n < 4 || energia <= 0.0) return null

        val kMin = max(2, floor(periodoMin * fs).toInt())
        val kMax = min(n / 2, ceil(periodoMax * fs).toInt())
        if (kMax - kMin < 2) return null

        val r = DoubleArray(kMax + 2)
        for (k in (kMin - 1)..(kMax + 1)) {
            var suma = 0.0
            for (i in 0 until n - k) suma += s[i] * s[i + k]
            r[k] = suma / (n - k) / energia
        }

        val maximosLocales = (kMin..kMax).filter { r[it] >= r[it - 1] && r[it] >= r[it + 1] }
        val mayor = maximosLocales.maxOfOrNull { r[it] } ?: return null
        // Con señales reales y ruidosas todos los máximos pueden ser negativos: no hay ritmo.
        // (Antes este caso hacía fallar el `first` y cerraba la app al terminar de medir.)
        if (!mayor.isFinite() || mayor <= 0.0) return null
        val k = maximosLocales.firstOrNull { r[it] >= 0.85 * mayor } ?: return null

        val denominador = r[k - 1] - 2 * r[k] + r[k + 1]
        val ajuste = 0.5 * (r[k - 1] - r[k + 1]) / denominador
        val delta = if (denominador != 0.0 && ajuste.isFinite()) ajuste.coerceIn(-0.5, 0.5) else 0.0
        if (!r[k].isFinite()) return null
        return Pair((k + delta) / fs, r[k])
    }

    /** Interpolación lineal a una rejilla de tiempo uniforme. */
    internal fun remuestrear(t: DoubleArray, v: FloatArray, fs: Double): DoubleArray {
        val total = floor(t.last() * fs).toInt() + 1
        val salida = DoubleArray(total)
        var j = 0
        for (i in 0 until total) {
            val ti = i / fs
            while (j < t.size - 2 && t[j + 1] < ti) j++
            val t0 = t[j]
            val t1 = t[j + 1]
            val f = if (t1 > t0) ((ti - t0) / (t1 - t0)).coerceIn(0.0, 1.0) else 0.0
            salida[i] = v[j] + (v[j + 1] - v[j]) * f
        }
        return salida
    }

    internal fun quitarMedia(s: DoubleArray): DoubleArray {
        if (s.isEmpty()) return s
        val media = s.average()
        return DoubleArray(s.size) { s[it] - media }
    }

    internal fun quitarTendencia(s: DoubleArray): DoubleArray {
        val n = s.size
        if (n < 2) return s
        val mediaX = (n - 1) / 2.0
        val mediaY = s.average()
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            num += (i - mediaX) * (s[i] - mediaY)
            den += (i - mediaX) * (i - mediaX)
        }
        val pendiente = num / den
        return DoubleArray(n) { s[it] - mediaY - pendiente * (it - mediaX) }
    }

    internal fun desviacion(s: DoubleArray): Double {
        if (s.isEmpty()) return 0.0
        val media = s.average()
        return sqrt(s.sumOf { (it - media) * (it - media) } / s.size)
    }

    /** Filtro de segundo orden (Robert Bristow-Johnson, "Audio EQ Cookbook"). */
    internal class Biquad private constructor(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double,
    ) {
        fun aplicar(entrada: DoubleArray): DoubleArray {
            var x1 = 0.0
            var x2 = 0.0
            var y1 = 0.0
            var y2 = 0.0
            return DoubleArray(entrada.size) { i ->
                val x0 = entrada[i]
                val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = x0
                y2 = y1; y1 = y0
                y0
            }
        }

        companion object {
            private const val Q = 0.7071

            fun pasaBajos(fc: Double, fs: Double): Biquad {
                val w0 = 2 * PI * fc / fs
                val alpha = sin(w0) / (2 * Q)
                val c = cos(w0)
                val a0 = 1 + alpha
                return Biquad((1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
            }

            fun pasaAltos(fc: Double, fs: Double): Biquad {
                val w0 = 2 * PI * fc / fs
                val alpha = sin(w0) / (2 * Q)
                val c = cos(w0)
                val a0 = 1 + alpha
                return Biquad((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
            }
        }
    }
}
