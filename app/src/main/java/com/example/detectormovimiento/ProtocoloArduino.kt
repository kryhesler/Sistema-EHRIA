package com.example.detectormovimiento

/**
 * Mensajes de texto entre el teléfono y el Arduino de la estación (una línea por mensaje,
 * 115200 baudios). Debe coincidir con arduino/ehria_estacion/ehria_estacion.ino.
 *
 * Arduino → teléfono:
 *   "EHRIA,<versión>"          al arrancar
 *   "S,<temp °C>,<humedad %>,<distancia cm>"   unas 4 veces por segundo
 *                              ("nan" si el sensor no responde, -1 si el ultrasónico no ve nada)
 *   "PUERTA,<1|0>"             cuando el servo terminó de abrir o cerrar
 *
 * Teléfono → Arduino (una letra y un valor):
 *   "P1" / "P0"   abrir / cerrar la puerta del botiquín (servo)
 *   "L1" / "L0"   luz interior encendida / apagada
 *   "E<modo>"     modo del LED exterior (ver [ModoLed])
 *   "H"           pide el saludo "EHRIA,<versión>"
 */
object ProtocoloArduino {

    data class Lectura(val temperaturaC: Float?, val humedad: Float?, val distanciaCm: Float?)

    sealed class Mensaje {
        data class Datos(val lectura: Lectura) : Mensaje()
        data class Hola(val version: String) : Mensaje()
        data class Puerta(val abierta: Boolean) : Mensaje()
    }

    /** Cómo parpadea el LED de afuera, para que se entienda el estado desde lejos. */
    enum class ModoLed(val codigo: Char) {
        APAGADO('0'),
        NORMAL('N'),   // destello corto cada 3 s: vigilando
        PAUSA('P'),    // parpadeo lento: detección en pausa o desactivada
        AVISO('A'),    // parpadeo rápido: evaluando un movimiento
        ALERTA('R'),   // parpadeo muy rápido: sismo
    }

    fun interpretar(linea: String): Mensaje? {
        val partes = linea.trim().split(',')
        return when (partes.firstOrNull()) {
            "S" -> {
                if (partes.size < 4) return null
                Mensaje.Datos(
                    Lectura(
                        temperaturaC = numero(partes[1])?.takeIf { it in -40f..85f },
                        humedad = numero(partes[2])?.takeIf { it in 0f..100f },
                        distanciaCm = numero(partes[3])?.takeIf { it > 0f && it < 500f },
                    )
                )
            }
            "EHRIA" -> Mensaje.Hola(partes.getOrNull(1)?.trim().orEmpty())
            "PUERTA" -> when (partes.getOrNull(1)?.trim()) {
                "1" -> Mensaje.Puerta(true)
                "0" -> Mensaje.Puerta(false)
                else -> null
            }
            else -> null
        }
    }

    fun comandoPuerta(abrir: Boolean) = if (abrir) "P1\n" else "P0\n"
    fun comandoLuzInterior(encendida: Boolean) = if (encendida) "L1\n" else "L0\n"
    fun comandoLedExterior(modo: ModoLed) = "E${modo.codigo}\n"
    const val COMANDO_SALUDO = "H\n"

    private fun numero(texto: String): Float? = texto.trim().toFloatOrNull()?.takeIf { it.isFinite() }
}
