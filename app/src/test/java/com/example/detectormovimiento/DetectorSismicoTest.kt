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
        // La alerta sigue visible unos segundos después de la sacudida (termina a los 25 s)...
        assertEquals(Fase.ALERTA, estados[(30_000L / pasoMs).toInt()].fase)
        // ...y luego la pantalla vuelve a la normalidad.
        assertEquals(Fase.NORMAL, estados.last().fase)
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
        // La sacudida termina a los 20 s: la alerta dura unos 8 s más y se borra.
        assertEquals(Fase.ALERTA, estados[(25_000L / pasoMs).toInt()].fase)
        assertEquals(Fase.NORMAL, estados[(32_000L / pasoMs).toInt()].fase)
        assertEquals(Fase.NORMAL, estados.last().fase)
    }

    @Test
    fun restablecer_vuelveANormal() {
        val detector = DetectorSismico()
        simular(detector, 0, 20_000) { t -> if (t > 5_000L) 1.0f else 0.005f }
        detector.restablecer()
        val estado = detector.procesar(0.0f, 20_000L)
        assertFalse(estado.fase == Fase.ALERTA)
        // Restablecer de verdad: con el teléfono quieto no vuelve a "evaluando".
        assertEquals(Fase.NORMAL, estado.fase)
    }

    @Test
    fun clasificacionPorAceleracionPico() {
        assertEquals(Nivel.LEVE, DetectorSismico.clasificar(0.1f))
        assertEquals(Nivel.MODERADO, DetectorSismico.clasificar(0.5f))
        assertEquals(Nivel.FUERTE, DetectorSismico.clasificar(1.5f))
    }

    // ---------- Parlante del propio teléfono (sirena y voz) ----------

    /** Resultado de [sismoConSirena]: fases en orden y el último momento en alerta. */
    private class ConSirena(val fases: List<Fase>, val ultimoEnAlertaMs: Long)

    /**
     * Sismo de 5 s hasta [finSismoMs] y, desde que empieza la alerta, una sirena que hace
     * vibrar el teléfono 4.6 s de cada 5.8 s. Con [omitirSirena], esas muestras se omiten.
     */
    private fun sismoConSirena(omitirSirena: Boolean, finSismoMs: Long = 25_000L): ConSirena {
        val d = DetectorSismico()
        val r = Random(7)
        val fases = mutableListOf<Fase>()
        var inicioSirena = -1L
        var ultimoEnAlerta = -1L
        var t = 0L
        while (t < 90_000L) {
            val sismo = if (t in 5_000L until finSismoMs) 0.5f else 0f
            val suena = inicioSirena >= 0 && (t - inicioSirena) % 5_800L < 4_600L
            if (suena && omitirSirena) {
                d.omitir(t)
            } else {
                val vibracionSirena = if (suena) 0.4f else 0f
                val estado = d.procesar(sismo + vibracionSirena + abs(r.nextGaussian() * 0.01).toFloat(), t)
                if (estado.fase == Fase.ALERTA) {
                    if (inicioSirena < 0) inicioSirena = t
                    ultimoEnAlerta = t
                }
                if (fases.lastOrNull() != estado.fase) fases += estado.fase
            }
            t += pasoMs
        }
        return ConSirena(fases, ultimoEnAlerta)
    }

    @Test
    fun sirenaSinOmitir_mantieneLaAlertaParaSiempre() {
        // El error que se veía: la vibración de la propia sirena no deja terminar la alerta.
        assertEquals(Fase.ALERTA, sismoConSirena(omitirSirena = false).fases.last())
    }

    @Test
    fun sirenaOmitida_laAlertaTerminaPocoDespuesDelSismo() {
        val r = sismoConSirena(omitirSirena = true)
        assertEquals(Fase.NORMAL, r.fases.last())
        // El sismo termina a los 25 s: la alerta no dura más de ~15 s después.
        assertTrue("terminó a los ${r.ultimoEnAlertaMs} ms", r.ultimoEnAlertaMs in 30_000L..40_000L)
    }

    @Test
    fun sirenaOmitida_sismoLargoNoCortaLaAlerta() {
        // Un minuto de sacudida con la sirena: una sola alerta, sin cortes en medio.
        val r = sismoConSirena(omitirSirena = true, finSismoMs = 65_000L)
        assertEquals(listOf(Fase.CALIBRANDO, Fase.NORMAL, Fase.EVALUANDO, Fase.ALERTA, Fase.NORMAL), r.fases)
    }

    @Test
    fun soloVibracionDelParlante_noEsSismo() {
        // La voz suena 12 s seguidos: si esas muestras se omiten, no hay alerta.
        val d = DetectorSismico()
        var t = 0L
        while (t < 30_000L) {
            if (t in 8_000L until 20_000L) d.omitir(t) else d.procesar(0.005f, t)
            t += pasoMs
        }
        assertEquals(Fase.NORMAL, d.procesar(0.005f, t).fase)
    }
}
