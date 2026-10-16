package com.example.detectormovimiento

import com.example.detectormovimiento.AnalizadorVital.EstadoMedicion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class AnalizadorVitalTest {

    private class Muestras(val t: LongArray, val x: FloatArray, val y: FloatArray, val z: FloatArray)

    /**
     * Genera [segundos] de acelerómetro a [hz] (con fluctuación de tiempo, como un teléfono real).
     * Cada función recibe el tiempo en segundos.
     */
    private fun generar(
        segundos: Double,
        hz: Double,
        semilla: Long,
        ruido: Double,
        fx: (Double) -> Double,
        fy: (Double) -> Double,
        fz: (Double) -> Double,
    ): Muestras {
        val r = Random(semilla)
        val n = (segundos * hz).toInt()
        val t = LongArray(n)
        val x = FloatArray(n)
        val y = FloatArray(n)
        val z = FloatArray(n)
        for (i in 0 until n) {
            val ts = i / hz + (r.nextDouble() - 0.5) * 0.2 / hz
            t[i] = 5_000_000_000L + (ts * 1e9).toLong()
            x[i] = (fx(ts) + r.nextGaussian() * ruido).toFloat()
            y[i] = (fy(ts) + r.nextGaussian() * ruido).toFloat()
            z[i] = (fz(ts) + r.nextGaussian() * ruido).toFloat()
        }
        return Muestras(t, x, y, z)
    }

    /** Vibración de cada latido: un pulso corto de 15 Hz. */
    private fun latidos(ts: Double, bpm: Double): Double {
        val periodo = 60.0 / bpm
        var suma = 0.0
        var k = 0
        while (k * periodo <= ts + 0.2) {
            val d = ts - k * periodo
            suma += 0.05 * sin(2 * PI * 15.0 * d) * exp(-(d / 0.03) * (d / 0.03))
            k++
        }
        return suma
    }

    private fun analizar(m: Muestras) = AnalizadorVital.analizar(m.t, m.x, m.y, m.z)

    @Test
    fun pecho_15rpm_72bpm() {
        val m = generar(
            20.0, 100.0, 1, 0.005,
            fx = { 0.0 },
            fy = { t -> 0.25 * sin(2 * PI * 0.25 * t) },
            fz = { t -> 9.81 + 0.012 * sin(2 * PI * 0.25 * t) + latidos(t, 72.0) },
        )
        val r = analizar(m)
        assertEquals(EstadoMedicion.OK, r.estado)
        assertNotNull(r.rpm)
        assertEquals(15.0, r.rpm!!.toDouble(), 1.0)
        assertNotNull(r.bpm)
        assertEquals(72.0, r.bpm!!.toDouble(), 3.0)
    }

    @Test
    fun pecho_respiracionLenta_y_pulsoRapido() {
        val m = generar(
            20.0, 200.0, 2, 0.005,
            fx = { t -> 0.15 * sin(2 * PI * (9.0 / 60.0) * t) },
            fy = { 0.0 },
            fz = { t -> 9.81 + latidos(t, 110.0) },
        )
        val r = analizar(m)
        assertEquals(9.0, r.rpm!!.toDouble(), 1.0)
        assertEquals(110.0, r.bpm!!.toDouble(), 4.0)
    }

    @Test
    fun mesa_sinMovimiento_noInventaValores() {
        val m = generar(20.0, 100.0, 3, 0.01, { 0.0 }, { 0.0 }, { 9.81 })
        val r = analizar(m)
        assertEquals(EstadoMedicion.SIN_MOVIMIENTO, r.estado)
        assertNull(r.rpm)
        assertNull(r.bpm)
    }

    @Test
    fun personaMoviendose_seRechaza() {
        val m = generar(
            20.0, 100.0, 4, 0.05,
            fx = { t -> 3.0 * sin(2 * PI * 1.3 * t) },
            fy = { 0.0 },
            fz = { 9.81 },
        )
        assertEquals(EstadoMedicion.MUCHO_MOVIMIENTO, analizar(m).estado)
    }

    @Test
    fun telefonoLento_midioRespiracion_peroNoPulso() {
        val m = generar(
            20.0, 15.0, 5, 0.005,
            fx = { 0.0 },
            fy = { t -> 0.25 * sin(2 * PI * 0.3 * t) },
            fz = { 9.81 },
        )
        val r = analizar(m)
        assertEquals(18.0, r.rpm!!.toDouble(), 1.0)
        assertNull(r.bpm)
    }

    @Test
    fun muestraCorta_esInsuficiente() {
        val m = generar(5.0, 100.0, 6, 0.005, { 0.0 }, { 0.0 }, { 9.81 })
        assertEquals(EstadoMedicion.MUESTRA_INSUFICIENTE, analizar(m).estado)
    }
}
