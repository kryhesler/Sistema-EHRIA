package com.example.detectormovimiento

import java.text.Normalizer
import java.util.Locale

/**
 * Entiende lo que se le dice al robot de la estación: detecta su nombre ("Ehria") y la orden.
 * No depende de Android, así que se prueba con pruebas unitarias normales.
 */
object ComandosVoz {

    enum class Comando {
        ABRIR_BOTIQUIN, CERRAR_BOTIQUIN, CONTENIDO_BOTIQUIN,
        RCP, TORNIQUETE, ATRAGANTAMIENTO, QUEMADURA, AMBULANCIA,
        PUNTO_ENCUENTRO, CLIMA, SISMOS, HORA,
        PRESENTARSE, AYUDA, SALUDO, GRACIAS,
        // Controles de una guía paso a paso
        DETENER, SIGUIENTE, ANTERIOR, REPETIR,
    }

    /** [llamado]: se dijo el nombre del robot. [resto]: la orden que acompaña al nombre. */
    data class Escucha(val llamado: Boolean, val resto: String)

    /**
     * Cómo suele escribir el reconocedor de voz el nombre "Ehria" (sin tildes, en minúsculas).
     * "aria" y "elia" aparecen cuando se dice rápido; casi nunca se dicen por otra razón.
     */
    private val nombres = setOf("ehria", "eria", "heria", "herria", "erria", "ehrya", "erya", "ejria", "aria", "elia")

    /** Minúsculas, sin tildes ni signos: "¿Qué TEMPERATURA hace?" → "que temperatura hace". */
    fun normalizar(texto: String): String {
        val sinTildes = Normalizer.normalize(texto.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return sinTildes.replace(Regex("[^a-z0-9ñ ]+"), " ").replace(Regex("\\s+"), " ").trim()
    }

    /** Busca el nombre del robot y devuelve lo que se dijo después. */
    fun escuchar(texto: String): Escucha {
        val palabras = normalizar(texto).split(' ').filter { it.isNotEmpty() }
        for (i in palabras.indices) {
            val unido = if (i + 1 < palabras.size) palabras[i] + palabras[i + 1] else ""
            val salto = when {
                palabras[i] in nombres -> 1
                // "e ria", "eh ria": el reconocedor a veces lo parte en dos.
                palabras[i] in setOf("e", "eh", "he") && unido.drop(palabras[i].length) == "ria" -> 2
                else -> 0
            }
            if (salto > 0) {
                // "Hola, Ehria" o "abre el botiquín, Ehria": si no hay nada después, vale lo de antes.
                val despues = palabras.drop(i + salto).joinToString(" ")
                return Escucha(true, despues.ifEmpty { palabras.take(i).joinToString(" ") })
            }
        }
        return Escucha(false, palabras.joinToString(" "))
    }

    /** La orden contenida en [texto] (ya sin el nombre), o null si no se entendió. */
    fun interpretar(texto: String): Comando? {
        val t = normalizar(texto)
        if (t.isEmpty()) return null
        fun hay(patron: String) = Regex("\\b(?:$patron)").containsMatchIn(t)

        // Controles cortos ("para", "siguiente"): solo si es casi lo único que se dijo, porque
        // "para" o "sigue" también aparecen dentro de otras frases.
        val corta = t.split(' ').size <= 3
        if (corta) {
            when {
                hay("para$|para ya|ya para|detente|deten|alto$|basta|callate|silencio|cancela|stop|termina") -> return Comando.DETENER
                hay("siguiente|continua|sigue$|proximo|listo$|ya esta") -> return Comando.SIGUIENTE
                hay("anterior|atras$|regresa|vuelve") -> return Comando.ANTERIOR
                hay("repite|repetir|otra vez|de nuevo|no entendi") -> return Comando.REPETIR
            }
        }

        return when {
            hay("ambulancia|llam\\w* (?:a |al )?(?:la |los |una |un )?(?:emergencia|cruz roja|bomberos|doctor|medico|ayuda|911|128)") -> Comando.AMBULANCIA
            hay("rcp|reanima|resucita|masaje cardiaco|compresiones|no respira|paro cardi|no tiene pulso") -> Comando.RCP
            hay("torniquete|hemorragia|sangr") -> Comando.TORNIQUETE
            hay("atragant|ahogando|ahoga|heimlich|se trago|algo en la garganta") -> Comando.ATRAGANTAMIENTO
            hay("quema|quemo") -> Comando.QUEMADURA
            hay("botiquin|puerta") && hay("que tiene|que hay|contiene|contenido|que trae|que lleva|dentro|como (?:se )?usa|para que sirve") ->
                Comando.CONTENIDO_BOTIQUIN
            hay("cierra|cerrar|cierre") && hay("botiquin|puerta") -> Comando.CERRAR_BOTIQUIN
            hay("botiquin") || (hay("abre|abrir|abra") && hay("puerta")) -> Comando.ABRIR_BOTIQUIN
            hay("punto de (?:encuentro|reunion)|evacu|nos reunimos|zona segura|a donde (?:vamos|voy|salgo)|mapa") -> Comando.PUNTO_ENCUENTRO
            hay("temperatura|humedad|grados|calor|clima|frio|que tiempo") -> Comando.CLIMA
            hay("sismo|temblor|tembl|terremoto") -> Comando.SISMOS
            hay("que hora") -> Comando.HORA
            hay("presenta|quien eres|que eres|como te llamas") -> Comando.PRESENTARSE
            hay("que (?:puedes|sabes) hacer|ayuda|comandos|como funcionas|que haces") -> Comando.AYUDA
            hay("gracias") -> Comando.GRACIAS
            hay("hola|buenos dias|buenas") -> Comando.SALUDO
            else -> null
        }
    }
}
