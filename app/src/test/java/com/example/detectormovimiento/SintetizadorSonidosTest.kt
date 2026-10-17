package com.example.detectormovimiento

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SintetizadorSonidosTest {

    private val fs = SintetizadorSonidos.FRECUENCIA_MUESTREO

    /** Frecuencia aproximada contando cruces por cero. */
    private fun frecuencia(pcm: ShortArray, desde: Int, hasta: Int): Double {
        var cruces = 0
        for (i in desde + 1 until hasta) {
            if ((pcm[i - 1] < 0) != (pcm[i] < 0)) cruces++
        }
        return cruces / 2.0 / ((hasta - desde).toDouble() / fs)
    }

    @Test
    fun tono_duracionFrecuenciaYSinChasquidos() {
        val pcm = SintetizadorSonidos.tono(1_000.0, 200)
        assertEquals(200L, SintetizadorSonidos.duracionMs(pcm))
        assertEquals(1_000.0, frecuencia(pcm, 0, pcm.size), 30.0)
        // Empieza y termina en silencio (la envolvente evita el "clic").
        assertTrue(abs(pcm.first().toInt()) < 200)
        assertTrue(abs(pcm.last().toInt()) < 200)
    }

    @Test
    fun sirena_subeYBajaDeTono() {
        val pcm = SintetizadorSonidos.sirena(1_000)
        val ventana = fs / 20
        val alInicio = frecuencia(pcm, 0, ventana)
        val alMedio = frecuencia(pcm, pcm.size / 2 - ventana / 2, pcm.size / 2 + ventana / 2)
        assertTrue("debe subir ($alInicio -> $alMedio)", alMedio > alInicio + 400)
        assertTrue(pcm.maxOf { abs(it.toInt()) } > 20_000)  // fuerte
    }

    @Test
    fun latidos_respetanElRitmo() {
        val pcm = SintetizadorSonidos.latidos(bpm = 75, cantidad = 4)
        // 4 latidos a 75 lpm = 4 × 800 ms
        assertEquals(3_200.0, SintetizadorSonidos.duracionMs(pcm).toDouble(), 30.0)
    }

    @Test
    fun ningunSonidoSatura() {
        val todos = listOf(
            SintetizadorSonidos.tic(), SintetizadorSonidos.inicio(), SintetizadorSonidos.exito(),
            SintetizadorSonidos.error(), SintetizadorSonidos.detenido(), SintetizadorSonidos.preAlerta(),
            SintetizadorSonidos.despejado(), SintetizadorSonidos.latido(), SintetizadorSonidos.sirena(700),
        )
        for (pcm in todos) {
            assertTrue(pcm.isNotEmpty())
            assertTrue(pcm.all { it > Short.MIN_VALUE })
        }
    }
}
