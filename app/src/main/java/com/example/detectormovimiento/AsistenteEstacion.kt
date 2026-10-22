package com.example.detectormovimiento

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Lo que dice el robot de la estación: el saludo con los datos del lugar, el índice de calor y
 * las respuestas del asistente de voz.
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
        /** true si el robot está escuchando su nombre ("Ehria"). */
        val asistenteVoz: Boolean = false,
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

        if (d.temperaturaC != null) partes += clima(d.temperaturaC, d.humedad)
        partes += sismos(d)
        partes += if (d.asistenteVoz) {
            "Si necesitas algo, di mi nombre, Ehria, y luego lo que quieres. Por ejemplo: Ehria, abre el botiquín."
        } else "Si alguien necesita primeros auxilios, sácame de la estación y te ayudo a medir su respiración y su pulso."
        return partes.joinToString(" ")
    }

    /** "Aquí hace 27 grados y la humedad es de 70 por ciento." más un consejo si hace calor. */
    fun clima(temperaturaC: Float, humedad: Float?): String {
        if (humedad == null) return "Aquí hace ${temperaturaC.roundToInt()} grados."
        var texto = "Aquí hace ${temperaturaC.roundToInt()} grados y la humedad es de ${humedad.roundToInt()} por ciento."
        val indice = indiceCalor(temperaturaC, humedad)
        when (riesgoCalor(indice)) {
            "peligro extremo", "peligro" ->
                texto += " Hace un calor peligroso: toma agua, busca sombra y evita hacer ejercicio."
            "precaución extrema" ->
                texto += " Se siente como ${indice.roundToInt()} grados: toma agua y descansa a la sombra."
            "precaución" -> texto += " Se siente caluroso: recuerda tomar agua."
        }
        return texto
    }

    /** Respuesta a "¿qué temperatura hace?". */
    fun respuestaClima(temperaturaC: Float?, humedad: Float?): String = when {
        temperaturaC != null -> clima(temperaturaC, humedad)
        humedad != null -> "No pude leer la temperatura, pero la humedad es de ${humedad.roundToInt()} por ciento."
        else -> "Todavía no tengo datos del sensor de temperatura. Revisa que la estación esté conectada."
    }

    /** Cuántos sismos hubo hoy, o si la detección está apagada. */
    fun sismos(d: Datos): String {
        val sismos = d.sismosHoy
        if (!d.deteccionActiva) return "La detección de sismos está apagada en este momento."
        if (sismos.isEmpty()) return "Hoy no se ha registrado actividad sísmica."
        val ultimo = sismos.maxBy { it.fechaMs }
        val magnitud = EscalaSismica.magnitudEstimada(ultimo.pgaPicoBruto)
        val cuantos = if (sismos.size == 1) "un sismo" else "${sismos.size} sismos"
        val detalle = magnitud?.let {
            ", el último de magnitud aproximada ${String.format(espanol, "%.1f", it)}"
        }.orEmpty()
        return "Hoy se registró $cuantos$detalle."
    }

    fun hora(hora: Int, minuto: Int): String {
        val h12 = if (hora % 12 == 0) 12 else hora % 12
        val parte = when (hora) {
            in 5..11 -> "de la mañana"
            in 12..18 -> "de la tarde"
            else -> "de la noche"
        }
        val minutos = when (minuto) {
            0 -> ""
            15 -> " y cuarto"
            30 -> " y media"
            else -> " y $minuto"
        }
        return if (h12 == 1) "Es la una$minutos $parte." else "Son las $h12$minutos $parte."
    }

    const val PRESENTACION =
        "¡Hola! Soy EHRIA, una estación comunitaria de alerta sísmica y primeros auxilios, hecha con un teléfono reciclado. " +
            "Vigilo el suelo todo el día y, si detecto un sismo, doy la alarma, abro el botiquín y te muestro el punto de encuentro. " +
            "También mido la temperatura y la humedad, y te guío en primeros auxilios, como la RCP o un torniquete. " +
            "Para hablarme, di mi nombre, Ehria, y luego lo que necesitas."

    /** Ejemplos que se muestran y se dicen cuando preguntan qué puede hacer. */
    val EJEMPLOS = listOf(
        "Ehria, abre el botiquín",
        "Ehria, ¿cómo hago RCP?",
        "Ehria, ¿cómo pongo un torniquete?",
        "Ehria, llama a una ambulancia",
        "Ehria, ¿dónde está el punto de encuentro?",
        "Ehria, ¿qué temperatura hace?",
        "Ehria, ¿qué tiene el botiquín?",
        "Ehria, preséntate",
    )

    const val AYUDA =
        "Puedo abrir o cerrar el botiquín, decirte qué tiene, guiarte para hacer RCP, poner un torniquete, " +
            "ayudar a alguien que se atraganta o tratar una quemadura, llamar a una ambulancia, " +
            "mostrarte el punto de encuentro y decirte la temperatura. Dime Ehria y lo que necesitas."

    /** Número de emergencias que se muestra en la llamada simulada (Cruz Roja Nicaragua). */
    const val NUMERO_EMERGENCIA = "128"
    const val NOMBRE_EMERGENCIA = "Cruz Roja"

    const val LLAMANDO = "Llamando a una ambulancia de la $NOMBRE_EMERGENCIA, al $NUMERO_EMERGENCIA."

    /** Tras la llamada simulada: deja claro que no se llamó de verdad. */
    const val LLAMADA_SIMULADA =
        "Esto fue una simulación: todavía no puedo hacer llamadas reales. " +
            "Si es una emergencia, llama tú al $NUMERO_EMERGENCIA desde un teléfono. Mientras tanto, no dejes sola a la persona."

    val NO_ENTENDI = listOf(
        "Perdón, no te entendí. ¿Me lo repites?",
        "No estoy segura de qué necesitas. Puedes preguntarme qué puedo hacer.",
    )
}
