package com.example.detectormovimiento

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Genera los sonidos de la app como audio PCM de 16 bits (mono), sin archivos de audio.
 * No depende de Android, así que se prueba con pruebas unitarias normales.
 */
object SintetizadorSonidos {

    const val FRECUENCIA_MUESTREO = 22_050

    fun muestras(ms: Int) = FRECUENCIA_MUESTREO * ms / 1000

    fun duracionMs(pcm: ShortArray) = pcm.size * 1000L / FRECUENCIA_MUESTREO

    /** Tono puro con subida y bajada suaves (sin chasquidos al empezar y terminar). */
    fun tono(hz: Double, ms: Int, volumen: Double = 0.6): ShortArray {
        val n = muestras(ms)
        val rampa = min(n / 4, muestras(10)).coerceAtLeast(1)
        return ShortArray(n) { i ->
            val envolvente = min(1.0, min(i, n - 1 - i).toDouble() / rampa)
            aMuestra(sin(2 * PI * hz * i / FRECUENCIA_MUESTREO) * envolvente * volumen)
        }
    }

    fun silencio(ms: Int) = ShortArray(muestras(ms))

    fun unir(vararg partes: ShortArray): ShortArray {
        val total = ShortArray(partes.sumOf { it.size })
        var pos = 0
        for (p in partes) {
            p.copyInto(total, pos)
            pos += p.size
        }
        return total
    }

    /**
     * Un ciclo de sirena: el tono sube de [grave] a [agudo] y vuelve a bajar. Pensada para
     * repetirse en bucle. Lleva un armónico para que se escuche fuerte en parlantes pequeños.
     */
    fun sirena(cicloMs: Int, grave: Double = 650.0, agudo: Double = 1350.0, volumen: Double = 0.9): ShortArray {
        val n = muestras(cicloMs)
        var fase = 0.0
        return ShortArray(n) { i ->
            val avance = i.toDouble() / n
            // Sube en la primera mitad del ciclo y baja en la segunda (forma de triángulo suave).
            val mezcla = (1 - kotlin.math.cos(2 * PI * avance)) / 2
            val hz = grave + (agudo - grave) * mezcla
            fase += 2 * PI * hz / FRECUENCIA_MUESTREO
            aMuestra((0.8 * sin(fase) + 0.2 * sin(3 * fase)) * volumen)
        }
    }

    /** Un latido "lub-dub": dos golpes graves que se apagan rápido. */
    fun latido(volumen: Double = 0.85): ShortArray = unir(
        golpe(140.0, 130, volumen),
        silencio(70),
        golpe(180.0, 100, volumen * 0.8),
    )

    /** [cantidad] latidos al ritmo de [bpm] latidos por minuto. */
    fun latidos(bpm: Int, cantidad: Int): ShortArray {
        val periodoMs = 60_000 / bpm.coerceIn(30, 220)
        val uno = latido()
        val espera = (periodoMs - duracionMs(uno).toInt()).coerceAtLeast(60)
        return unir(*Array(cantidad) { unir(uno, silencio(espera)) })
    }

    fun tic() = tono(1_000.0, 60, 0.5)
    fun inicio() = tono(880.0, 260, 0.6)
    fun exito() = unir(tono(660.0, 110), silencio(25), tono(880.0, 200))
    fun error() = unir(tono(494.0, 150), silencio(25), tono(370.0, 280))
    fun detenido() = unir(tono(587.0, 110, 0.5), tono(440.0, 160, 0.5))
    fun preAlerta() = unir(tono(740.0, 160, 0.9), silencio(90), tono(740.0, 160, 0.9), silencio(90), tono(988.0, 300, 0.9))
    fun despejado() = unir(tono(523.0, 150), tono(659.0, 150), tono(784.0, 320))

    private fun golpe(hz: Double, ms: Int, volumen: Double): ShortArray {
        val n = muestras(ms)
        return ShortArray(n) { i ->
            val t = i.toDouble() / FRECUENCIA_MUESTREO
            val ataque = min(1.0, i / (muestras(4).toDouble().coerceAtLeast(1.0)))
            val caida = exp(-t * 28)
            // Fundamental grave + segundo armónico: en un parlante de teléfono se oye como un golpe.
            aMuestra((0.7 * sin(2 * PI * hz * t) + 0.3 * sin(4 * PI * hz * t)) * ataque * caida * volumen)
        }
    }

    private fun aMuestra(valor: Double): Short =
        (valor.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
}
