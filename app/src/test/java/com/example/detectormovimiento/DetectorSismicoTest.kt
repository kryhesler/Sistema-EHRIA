package com.example.detectormovimiento

import com.example.detectormovimiento.DetectorSismico.Fase
import com.example.detectormovimiento.DetectorSismico.Nivel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class DetectorSismicoTest {

    private val pasoMs = 20L // 50 Hz, como SENSOR_DELAY_GAME

    /** Corre el detector de 0 a [hastaMs] y devuelve todos los estados. */
    private fun simular(
        detector: DetectorSismico,
        desdeMs: Long,
        hastaMs: Long,
        magnitud: (Long) -> Float,
    ): List<DetectorSismico.Estado> {
        val estados = mutableListOf<DetectorSismico.Estado>()
        var t = desdeMs
        while (t < hastaMs) {
            estados += detector.procesar(magnitud(t), t)
            t += pasoMs
        }
        return estados
    }

    private fun ruido(random: Random, sigma: Double) = { _: Long -> abs(random.nextGaussian() * sigma).toFloat() }

    @Test
    fun ruidoNormal_noDaAlarma() {
        val estados = simular(DetectorSismico(), 0, 120_000, ruido(Random(1), 0.01))
        assertTrue(estados.none { it.fase == Fase.ALERTA })
    }

    @Test
    fun telefonoViejoRuidoso_noDaFalsaAlarma() {
        // Con el umbral fijo de 0.05 m/s² de antes, este ruido disparaba la alarma.
        val estados = simular(DetectorSismico(), 0, 120_000, ruido(Random(2), 0.08))
        assertTrue(estados.none { it.fase == Fase.ALERTA })
        assertTrue(estados.last().umbral > 0.08f)
    }

    @Test
    fun golpeAislado_noDaAlarma() {
        val r = Random(3)
        val estados = simular(DetectorSismico(), 0, 60_000) { t ->
            if (t in 20_000L until 21_000L) 1.5f else abs(r.nextGaussian() * 0.01).toFloat()
        }
        assertTrue(estados.any { it.fase == Fase.EVALUANDO })
        assertTrue(estados.none { it.fase == Fase.ALERTA })
        assertEquals(Fase.NORMAL, estados.last().fase)
    }

    @Test
    fun sismoSostenido_conPausasCortas_daAlerta() {
        val r = Random(4)
        val detector = DetectorSismico()
        val estados = simular(detector, 0, 40_000) { t ->
            val sacudiendo = t in 10_000L until 25_000L
            // Pausas de 0.5 s cada 3 s: antes reiniciaban el conteo de 10 s.
            val pausa = ((t - 10_000L) % 3_000L) < 500L
            val base = abs(r.nextGaussian() * 0.01).toFloat()
            if (sacudiendo && !pausa) base + abs(0.6 * sin(2 * PI * 2.0 * t / 1000.0)).toFloat() else base
        }
        val primeraAlerta = estados.indexOfFirst { it.fase == Fase.ALERTA }
        assertTrue("Debe confirmar el sismo", primeraAlerta >= 0)
        val tAlerta = primeraAlerta * pasoMs
        assertTrue("Confirma unos 10 s después de empezar ($tAlerta ms)", tAlerta in 19_000L..21_500L)
        assertEquals(1, estados.count { it.alertaNueva })
        assertEquals(Nivel.MODERADO, estados[primeraAlerta].nivel)
        // El pico sin filtrar (para Richter y Mercalli) llega al máximo real de la sacudida.
        assertTrue(estados[primeraAlerta].pgaPicoBruto >= estados[primeraAlerta].pgaPico)
        assertTrue(estados[primeraAlerta].pgaPicoBruto > 0.55f)
        // La alerta sigue visible después de que termina la sacudida.
        assertEquals(Fase.ALERTA, estados.last().fase)
    }

    @Test
    fun alerta_seMantieneYLuegoVuelveANormal() {
        val r = Random(5)
        val detector = DetectorSismico()
        val estados = simular(detector, 0, 80_000) { t ->
            val base = abs(r.nextGaussian() * 0.01).toFloat()
            if (t in 5_000L until 20_000L) base + 2.0f else base
        }
        assertTrue(estados.any { it.fase == Fase.ALERTA && it.nivel == Nivel.FUERTE })
        val enT45s = estados[(45_000L / pasoMs).toInt()]
        assertEquals(Fase.ALERTA, enT45s.fase)
        assertEquals(Fase.NORMAL, estados.last().fase)
    }

    @Test
    fun restablecer_vuelveANormal() {
        val detector = DetectorSismico()
        simular(detector, 0, 20_000) { t -> if (t > 5_000L) 1.0f else 0.005f }
        detector.restablecer()
        val estado = detector.procesar(0.0f, 20_000L)
        assertFalse(estado.fase == Fase.ALERTA)
    }

    @Test
    fun clasificacionPorAceleracionPico() {
        assertEquals(Nivel.LEVE, DetectorSismico.clasificar(0.1f))
        assertEquals(Nivel.MODERADO, DetectorSismico.clasificar(0.5f))
        assertEquals(Nivel.FUERTE, DetectorSismico.clasificar(1.5f))
    }
}
