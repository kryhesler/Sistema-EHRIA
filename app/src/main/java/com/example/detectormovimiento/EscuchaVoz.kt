package com.example.detectormovimiento

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Escucha continua con el reconocedor de voz del teléfono, para el asistente de la estación.
 *
 * - Mientras [activa] es true, vuelve a escuchar en cuanto termina cada frase.
 * - Si el reconocedor falla (sin internet, ocupado, etc.), espera un poco y lo reintenta.
 * - Todo ocurre en el hilo principal, como pide SpeechRecognizer.
 *
 * Nota: en algunos teléfonos el reconocedor suena con un pitido corto cada vez que empieza.
 */
class EscuchaVoz(
    private val contexto: Context,
    private val alOirParcial: (String) -> Unit,
    private val alOirFrase: (List<String>) -> Unit,
    private val alCambiarNivel: (Float) -> Unit,
    private val alCambiarEstado: (Estado) -> Unit,
) {
    enum class Estado { APAGADA, ESCUCHANDO, SIN_PERMISO, NO_DISPONIBLE, SIN_CONEXION }

    var estado = Estado.APAGADA
        private set(valor) {
            if (field != valor) {
                field = valor
                alCambiarEstado(valor)
            }
        }

    private val handler = Handler(Looper.getMainLooper())
    private var reconocedor: SpeechRecognizer? = null
    private var activa = false
    private var escuchando = false
    private var inicioMs = 0L
    private var erroresSeguidos = 0

    fun disponible(): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(contexto)
    } catch (_: Exception) {
        false
    }

    /** Empieza (o sigue) escuchando. */
    fun iniciar() {
        if (activa) return
        if (!disponible()) {
            estado = Estado.NO_DISPONIBLE
            return
        }
        activa = true
        erroresSeguidos = 0
        escuchar()
    }

    /** Deja de escuchar (por ejemplo, mientras habla el robot, para no oírse a sí mismo). */
    fun detener() {
        activa = false
        handler.removeCallbacks(reintentar)
        handler.removeCallbacks(vigilante)
        if (escuchando) {
            try {
                reconocedor?.cancel()
            } catch (_: Exception) {
                // Ignorar
            }
            escuchando = false
        }
        alCambiarNivel(0f)
        if (estado == Estado.ESCUCHANDO || estado == Estado.SIN_CONEXION) estado = Estado.APAGADA
    }

    fun destruir() {
        detener()
        try {
            reconocedor?.destroy()
        } catch (_: Exception) {
            // Ignorar
        }
        reconocedor = null
    }

    private val reintentar = Runnable { escuchar() }

    /** Si el reconocedor se queda callado (pasa en algunos teléfonos), se reinicia. */
    private val vigilante = Runnable {
        if (escuchando && SystemClock.elapsedRealtime() - inicioMs >= LIMITE_SIN_RESPUESTA_MS) {
            recrear()
            programar(300L)
        }
    }

    private fun escuchar() {
        if (!activa) return
        val r = reconocedor ?: try {
            SpeechRecognizer.createSpeechRecognizer(contexto).also {
                it.setRecognitionListener(oyente)
                reconocedor = it
            }
        } catch (_: Exception) {
            estado = Estado.NO_DISPONIBLE
            activa = false
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, idioma())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, contexto.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Ayuda a que el reconocedor escriba bien el nombre y las palabras de primeros auxilios.
                putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, PALABRAS_CLAVE)
            }
        }
        try {
            r.startListening(intent)
            escuchando = true
            inicioMs = SystemClock.elapsedRealtime()
            handler.removeCallbacks(vigilante)
            handler.postDelayed(vigilante, LIMITE_SIN_RESPUESTA_MS)
        } catch (_: Exception) {
            recrear()
            programar(2_000L)
        }
    }

    private fun programar(esperaMs: Long) {
        handler.removeCallbacks(reintentar)
        if (activa) handler.postDelayed(reintentar, esperaMs)
    }

    private fun recrear() {
        escuchando = false
        try {
            reconocedor?.destroy()
        } catch (_: Exception) {
            // Ignorar
        }
        reconocedor = null
    }

    private fun idioma(): String {
        val local = Locale.getDefault()
        return if (local.language == "es") local.toLanguageTag() else "es-US"
    }

    private val oyente = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            estado = Estado.ESCUCHANDO
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // El nivel llega más o menos entre -2 y 10 dB.
            alCambiarNivel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            alCambiarNivel(0f)
        }

        override fun onError(error: Int) {
            escuchando = false
            handler.removeCallbacks(vigilante)
            alCambiarNivel(0f)
            if (!activa) return
            erroresSeguidos++
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Nadie habló: lo normal. Se vuelve a escuchar enseguida.
                    erroresSeguidos = 0
                    programar(250L)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    activa = false
                    estado = Estado.SIN_PERMISO
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER, ERROR_SERVIDOR_DESCONECTADO,
                ERROR_IDIOMA_NO_SOPORTADO, ERROR_IDIOMA_NO_DISPONIBLE -> {
                    // Sin internet y sin el paquete de voz sin conexión: se reintenta más tarde.
                    estado = Estado.SIN_CONEXION
                    programar(10_000L)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, ERROR_DEMASIADAS_SOLICITUDES -> {
                    recrear()
                    programar(1_500L)
                }
                else -> {
                    if (erroresSeguidos >= 5) recrear()
                    programar(if (erroresSeguidos >= 5) 3_000L else 500L)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            escuchando = false
            erroresSeguidos = 0
            handler.removeCallbacks(vigilante)
            alCambiarNivel(0f)
            estado = Estado.ESCUCHANDO
            val frases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            if (frases.isNotEmpty()) alOirFrase(frases)
            programar(250L)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(alOirParcial)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val LIMITE_SIN_RESPUESTA_MS = 15_000L
        // Códigos de Android 12 y 13 (aquí con su número para compilar igual en versiones viejas).
        const val ERROR_DEMASIADAS_SOLICITUDES = 10
        const val ERROR_SERVIDOR_DESCONECTADO = 11
        const val ERROR_IDIOMA_NO_SOPORTADO = 12
        const val ERROR_IDIOMA_NO_DISPONIBLE = 13
        val PALABRAS_CLAVE = arrayListOf(
            "Ehria", "botiquín", "RCP", "torniquete", "ambulancia", "punto de encuentro", "atragantamiento", "quemadura",
        )
    }
}
