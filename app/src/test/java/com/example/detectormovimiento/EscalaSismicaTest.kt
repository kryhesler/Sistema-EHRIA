package com.example.detectormovimiento

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalaSismicaTest {

    @Test
    fun magnitudEstimada_valoresConocidos() {
        // Esteva (1970) con 30 km: 35 cm/s² ≈ 5.6 y 120 cm/s² ≈ 7.1
        assertEquals(5.57, EscalaSismica.magnitudEstimada(0.35f)!!, 0.02)
        assertEquals(7.11, EscalaSismica.magnitudEstimada(1.20f)!!, 0.02)
    }

    @Test
    fun magnitudEstimada_esLaInversaDeLaAtenuacion() {
        for (m in listOf(3.0, 4.5, 6.0, 7.5)) {
            val pga = EscalaSismica.pgaParaMagnitud(m).toFloat()
            assertEquals(m, EscalaSismica.magnitudEstimada(pga)!!, 0.01)
        }
    }

    @Test
    fun magnitudEstimada_casosLimite() {
        assertNull(EscalaSismica.magnitudEstimada(0f))
        assertNull(EscalaSismica.magnitudEstimada(Float.NaN))
        assertEquals(9.5, EscalaSismica.magnitudEstimada(1_000f)!!, 0.0)
        assertTrue(EscalaSismica.magnitudEstimada(0.2f)!! < EscalaSismica.magnitudEstimada(0.4f)!!)
    }

    @Test
    fun categoriasDeRichter() {
        assertEquals("Micro", EscalaSismica.categoriaRichter(1.5).nombre)
        assertEquals("Menor", EscalaSismica.categoriaRichter(2.0).nombre)
        assertEquals("Ligero", EscalaSismica.categoriaRichter(4.99).nombre)
        assertEquals("Moderado", EscalaSismica.categoriaRichter(5.6).nombre)
        assertEquals("Fuerte", EscalaSismica.categoriaRichter(6.2).nombre)
        assertEquals("Mayor", EscalaSismica.categoriaRichter(7.1).nombre)
        assertEquals("Gran terremoto", EscalaSismica.categoriaRichter(8.4).nombre)
    }

    @Test
    fun intensidadMercalli_waldEtAl1999() {
        assertEquals(1, EscalaSismica.intensidadMercalli(0f))
        assertEquals(4, EscalaSismica.intensidadMercalli(0.35f))   // 35 cm/s²
        assertEquals(6, EscalaSismica.intensidadMercalli(1.20f))   // 120 cm/s²
        assertEquals(8, EscalaSismica.intensidadMercalli(4.0f))    // 400 cm/s²
        assertEquals("VI", EscalaSismica.romano(6))
        assertEquals("Fuerte", EscalaSismica.descripcionMercalli(6))
    }
}
