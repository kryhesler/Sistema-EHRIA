package com.example.detectormovimiento

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Convierte la aceleración máxima medida por el teléfono (PGA) en:
 *
 * - Intensidad de Mercalli (cuánto se sacudió ESTE lugar). Es lo que un teléfono mide bien.
 *   Relación de Wald et al. (1999), usada por el USGS en sus mapas ShakeMap.
 * - Magnitud estimada en escala de Richter (energía del sismo en su origen). Un solo teléfono
 *   no conoce la distancia al epicentro, así que se SUPONE una distancia típica y se invierte
 *   la ley de atenuación de Esteva (1970). Es una aproximación con error de ±1 grado o más;
 *   la magnitud oficial la publica INETER.
 */
object EscalaSismica {

    /** Distancia supuesta al foco del sismo (km) para estimar la magnitud. */
    const val DISTANCIA_SUPUESTA_KM = 30.0

    data class Categoria(val nombre: String, val desde: Double, val hasta: Double?)

    /** Categorías de la escala de Richter, de menor a mayor. */
    val CATEGORIAS_RICHTER = listOf(
        Categoria("Micro", 0.0, 2.0),
        Categoria("Menor", 2.0, 4.0),
        Categoria("Ligero", 4.0, 5.0),
        Categoria("Moderado", 5.0, 6.0),
        Categoria("Fuerte", 6.0, 7.0),
        Categoria("Mayor", 7.0, 8.0),
        Categoria("Gran terremoto", 8.0, null),
    )

    /**
     * Esteva (1970): PGA[cm/s²] = 1230 · e^(0.8·M) · (R + 25)^-2, despejando M.
     * Devuelve null si no hay aceleración.
     */
    fun magnitudEstimada(pgaMs2: Float, distanciaKm: Double = DISTANCIA_SUPUESTA_KM): Double? {
        if (!pgaMs2.isFinite() || pgaMs2 <= 0f) return null
        val pgaCm = pgaMs2 * 100.0
        val r = distanciaKm + 25.0
        val m = ln(pgaCm * r * r / 1230.0) / 0.8
        return m.coerceIn(0.0, 9.5)
    }

    /** Aceleración (m/s²) que produciría un sismo de magnitud [m] a la distancia supuesta. */
    fun pgaParaMagnitud(m: Double, distanciaKm: Double = DISTANCIA_SUPUESTA_KM): Double {
        val r = distanciaKm + 25.0
        return 1230.0 * exp(0.8 * m) / (r * r) / 100.0
    }

    fun categoriaRichter(magnitud: Double): Categoria =
        CATEGORIAS_RICHTER.last { magnitud >= it.desde }

    /**
     * Wald et al. (1999): MMI = 3.66·log10(PGA) − 1.66 para intensidades V o más,
     * y MMI = 2.20·log10(PGA) + 1.00 por debajo (PGA en cm/s²). Devuelve 1 a 12.
     */
    fun intensidadMercalli(pgaMs2: Float): Int {
        if (!pgaMs2.isFinite() || pgaMs2 <= 0f) return 1
        val log = log10(pgaMs2 * 100.0)
        val alta = 3.66 * log - 1.66
        val mmi = if (alta >= 5.0) alta else 2.20 * log + 1.00
        return mmi.roundToInt().coerceIn(1, 12)
    }

    fun romano(n: Int): String =
        listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")[n.coerceIn(1, 12) - 1]

    /** Descripción de cómo se siente cada intensidad (términos del USGS). */
    fun descripcionMercalli(n: Int): String = when (n.coerceIn(1, 12)) {
        1 -> "No sentido"
        2, 3 -> "Débil"
        4 -> "Ligero"
        5 -> "Moderado"
        6 -> "Fuerte"
        7 -> "Muy fuerte"
        8 -> "Severo"
        9 -> "Violento"
        else -> "Extremo"
    }
}
