package com.example.detectormovimiento

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Lo que dice el robot de la estación: el saludo con los datos del lugar y el índice de calor.
 * No depende de Android, así que se prueba con pruebas unitarias normales.
 */
object AsistenteEstacion {

    private val espanol: Locale = Locale.forLanguageTag("es")

    data class Datos(
        val temperaturaC: Float?,
        val humedad: Float?,
        val horaDelDia: Int,
        val sismosHoy: List<HistorialRepositorio.RegistroSismo>,
        val deteccionActiva: Boolean,
    )

    /**
     * Índice de calor (sensación térmica) en °C, con la regresión de Rothfusz del Servicio
     * Meteorológico de EE. UU. (NOAA). Con calor moderado usa la fórmula simple de Steadman.
     */
    fun indiceCalor(temperaturaC: Float, humedad: Float): Double {
        val t = temperaturaC * 9.0 / 5.0 + 32.0
        val rh = humedad.toDouble().coerceIn(0.0, 100.0)
        val simple = 0.5 * (t + 61.0 + (t - 68.0) * 1.2 + rh * 0.094)
        val f = if ((simple + t) / 2.0 < 80.0) {
            simple
        } else {
            -42.379 + 2.04901523 * t + 10.14333127 * rh - 0.22475541 * t * rh -
                0.00683783 * t * t - 0.05481717 * rh * rh + 0.00122874 * t * t * rh +
                0.00085282 * t * rh * rh - 0.00000199 * t * t * rh * rh
        }
        return (f - 32.0) * 5.0 / 9.0
    }

    /** Nivel de riesgo por calor según la tabla de la NOAA (null si no hay riesgo). */
    fun riesgoCalor(indiceC: Double): String? = when {
        indiceC >= 54 -> "peligro extremo"
        indiceC >= 41 -> "peligro"
        indiceC >= 32 -> "precaución extrema"
        indiceC >= 27 -> "precaución"
        else -> null
    }

    fun saludo(d: Datos): String {
        val partes = mutableListOf<String>()
        val momento = when (d.horaDelDia) {
            in 5..11 -> "Buenos días"
            in 12..18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        partes += "¡$momento! Soy EHRIA, la estación de alerta de esta comunidad."

        val t = d.temperaturaC
        val h = d.humedad
        when {
            t != null && h != null -> {
                partes += "Aquí hace ${t.roundToInt()} grados y la humedad es de ${h.roundToInt()} por ciento."
                val indice = indiceCalor(t, h)
                when (riesgoCalor(indice)) {
                    "peligro extremo", "peligro" ->
                        partes += "Hace un calor peligroso: toma agua, busca sombra y evita hacer ejercicio."
                    "precaución extrema" ->
                        partes += "Se siente como ${indice.roundToInt()} grados: toma agua y descansa a la sombra."
                    "precaución" -> partes += "Se siente caluroso: recuerda tomar agua."
                }
            }
            t != null -> partes += "Aquí hace ${t.roundToInt()} grados."
        }

        val sismos = d.sismosHoy
        if (!d.deteccionActiva) {
            partes += "La detección de sismos está apagada en este momento."
        } else if (sismos.isEmpty()) {
            partes += "Hoy no se ha registrado actividad sísmica."
        } else {
            val ultimo = sismos.maxBy { it.fechaMs }
            val magnitud = EscalaSismica.magnitudEstimada(ultimo.pgaPicoBruto)
            val cuantos = if (sismos.size == 1) "un sismo" else "${sismos.size} sismos"
            val detalle = magnitud?.let {
                ", el último de magnitud aproximada ${String.format(espanol, "%.1f", it)}"
            }.orEmpty()
            partes += "Hoy se registró $cuantos$detalle."
        }

        partes += "Si alguien necesita primeros auxilios, sácame de la estación y te ayudo a medir su respiración y su pulso."
        return partes.joinToString(" ")
    }
}
