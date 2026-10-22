package com.example.detectormovimiento

import android.content.SharedPreferences

/**
 * Punto de encuentro para evacuar, configurado desde la pestaña Estación. La dirección es
 * la de una persona parada frente a la estación, mirándola.
 */
data class PuntoEncuentro(
    val nombre: String,
    val direccion: Direccion,
    val distanciaM: Int?,
    val indicaciones: String,
) {
    /** [grados]: 0 = al frente, 90 = a la derecha (sentido del reloj). */
    enum class Direccion(val flecha: String, val texto: String, val grados: Float) {
        FRENTE("↑", "al frente", 0f),
        FRENTE_DERECHA("↗", "al frente, a la derecha", 45f),
        DERECHA("→", "a tu derecha", 90f),
        ATRAS_DERECHA("↘", "atrás, a la derecha", 135f),
        ATRAS("↓", "detrás de ti", 180f),
        ATRAS_IZQUIERDA("↙", "atrás, a la izquierda", 225f),
        IZQUIERDA("←", "a tu izquierda", 270f),
        FRENTE_IZQUIERDA("↖", "al frente, a la izquierda", 315f),
    }

    /** "A unos 80 metros, a tu derecha" (o solo la dirección si no se sabe la distancia). */
    fun ubicacion(): String {
        val distancia = distanciaM?.let { "a unos $it metros, " }.orEmpty()
        return (distancia + direccion.texto).replaceFirstChar { it.uppercase() }
    }

    /** Lo que dice el robot cuando le preguntan por el punto de encuentro. */
    fun descripcionVoz(): String {
        val partes = mutableListOf("El punto de encuentro es $nombre.", "Queda ${ubicacion().replaceFirstChar { it.lowercase() }}.")
        if (indicaciones.isNotBlank()) partes += indicaciones.trim().let { if (it.last() in ".!?") it else "$it." }
        partes += "Camina sin correr, sin gritar y sin empujar."
        return partes.joinToString(" ")
    }

    fun guardar(prefs: SharedPreferences) {
        prefs.edit()
            .putString(PREF_NOMBRE, nombre)
            .putString(PREF_DIRECCION, direccion.name)
            .putInt(PREF_DISTANCIA, distanciaM ?: -1)
            .putString(PREF_INDICACIONES, indicaciones)
            .apply()
    }

    companion object {
        private const val PREF_NOMBRE = "encuentro_nombre"
        private const val PREF_DIRECCION = "encuentro_direccion"
        private const val PREF_DISTANCIA = "encuentro_distancia"
        private const val PREF_INDICACIONES = "encuentro_indicaciones"

        /** null si todavía no se configuró. */
        fun cargar(prefs: SharedPreferences): PuntoEncuentro? {
            val nombre = prefs.getString(PREF_NOMBRE, null)?.takeIf { it.isNotBlank() } ?: return null
            val direccion = Direccion.entries.firstOrNull { it.name == prefs.getString(PREF_DIRECCION, null) } ?: Direccion.FRENTE
            return PuntoEncuentro(
                nombre = nombre,
                direccion = direccion,
                distanciaM = prefs.getInt(PREF_DISTANCIA, -1).takeIf { it > 0 },
                indicaciones = prefs.getString(PREF_INDICACIONES, "").orEmpty(),
            )
        }

        fun borrar(prefs: SharedPreferences) {
            prefs.edit().remove(PREF_NOMBRE).remove(PREF_DIRECCION).remove(PREF_DISTANCIA).remove(PREF_INDICACIONES).apply()
        }
    }
}
