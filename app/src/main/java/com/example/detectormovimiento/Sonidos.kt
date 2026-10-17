package com.example.detectormovimiento

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/**
 * Reproduce los sonidos generados por [SintetizadorSonidos].
 *
 * - Los efectos (pitidos, latidos, avisos de signos vitales) usan el volumen multimedia y se
 *   pueden silenciar con [efectosActivos].
 * - La sirena y los avisos de sismo usan el volumen de ALARMA y suenan siempre, por seguridad.
 *
 * Cualquier fallo de audio se ignora: la información sigue en pantalla.
 */
class Sonidos {

    var efectosActivos = true

    private val handler = Handler(Looper.getMainLooper())
    private val enCurso = mutableSetOf<AudioTrack>()
    private var sirena: AudioTrack? = null

    /** Efecto corto (respeta [efectosActivos]). */
    fun efecto(pcm: ShortArray) {
        if (efectosActivos) reproducir(pcm, alarma = false)
    }

    /** Sonido de emergencia: suena aunque los efectos estén silenciados. */
    fun alarma(pcm: ShortArray) = reproducir(pcm, alarma = true)

    fun iniciarSirena(rapida: Boolean) {
        detenerSirena()
        try {
            val pcm = SintetizadorSonidos.sirena(if (rapida) 700 else 1_400)
            val pista = crear(pcm, alarma = true)
            pista.setLoopPoints(0, pcm.size, -1)
            pista.play()
            sirena = pista
        } catch (_: Exception) {
            sirena = null
        }
    }

    /** Baja la sirena mientras habla la voz, para que se entienda. */
    fun volumenSirena(volumen: Float) {
        try {
            sirena?.setVolume(volumen)
        } catch (_: Exception) {
            // Ignorar
        }
    }

    fun detenerSirena() {
        val pista = sirena ?: return
        sirena = null
        try {
            pista.stop()
        } catch (_: Exception) {
            // Ignorar
        }
        pista.release()
    }

    fun detenerTodo() {
        detenerSirena()
        for (pista in enCurso.toList()) liberar(pista)
    }

    private fun reproducir(pcm: ShortArray, alarma: Boolean) {
        try {
            val pista = crear(pcm, alarma)
            enCurso += pista
            pista.play()
            handler.postDelayed({ liberar(pista) }, SintetizadorSonidos.duracionMs(pcm) + 300L)
        } catch (_: Exception) {
            // Sin audio: la información sigue en pantalla
        }
    }

    private fun liberar(pista: AudioTrack) {
        if (!enCurso.remove(pista)) return
        try {
            pista.stop()
        } catch (_: Exception) {
            // Ignorar
        }
        pista.release()
    }

    private fun crear(pcm: ShortArray, alarma: Boolean): AudioTrack {
        val atributos = AudioAttributes.Builder()
            .setUsage(if (alarma) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val formato = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SintetizadorSonidos.FRECUENCIA_MUESTREO)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val pista = AudioTrack.Builder()
            .setAudioAttributes(atributos)
            .setAudioFormat(formato)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        pista.write(pcm, 0, pcm.size)
        return pista
    }
}
