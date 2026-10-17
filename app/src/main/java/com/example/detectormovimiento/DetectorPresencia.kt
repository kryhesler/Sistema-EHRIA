package com.example.detectormovimiento

/**
 * Decide si hay alguien frente a la estación a partir del sensor ultrasónico.
 *
 * - Llega alguien: [lecturasParaEntrar] lecturas seguidas a menos de [entradaCm].
 * - Se fue: [msParaSalir] sin ninguna lectura a menos de [salidaCm].
 * El margen entre las dos distancias evita que despierte y se duerma sin parar.
 */
class DetectorPresencia(
    private val entradaCm: Float = 120f,
    private val salidaCm: Float = 160f,
    private val lecturasParaEntrar: Int = 3,
    private val msParaSalir: Long = 20_000L,
) {
    enum class Evento { LLEGO, SE_FUE }

    var presente = false
        private set

    private var seguidasCerca = 0
    private var ultimaCercaMs = 0L

    fun procesar(distanciaCm: Float?, ahoraMs: Long): Evento? {
        val cerca = distanciaCm != null && distanciaCm < entradaCm
        val dentroDelMargen = distanciaCm != null && distanciaCm < salidaCm

        if (!presente) {
            seguidasCerca = if (cerca) seguidasCerca + 1 else 0
            if (seguidasCerca >= lecturasParaEntrar) {
                presente = true
                ultimaCercaMs = ahoraMs
                return Evento.LLEGO
            }
            return null
        }

        if (dentroDelMargen) ultimaCercaMs = ahoraMs
        if (ahoraMs - ultimaCercaMs >= msParaSalir) {
            presente = false
            seguidasCerca = 0
            return Evento.SE_FUE
        }
        return null
    }

    fun reiniciar() {
        presente = false
        seguidasCerca = 0
    }
}
